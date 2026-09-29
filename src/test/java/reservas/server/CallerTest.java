package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reservas.protocol.TestJson;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;
import reservas.protocol.Role;
import reservas.protocol.Status;

class CallerTest {
  private static final Instant T0 = Instant.parse("2026-09-09T14:32:10Z");
  private static final String TOKEN = "a".repeat(64);
  private static final String BAD_REQUEST = "Token em formato invalido";

  private final SqliteStore store = SqliteStore.openInMemory();
  private final TokenGenerator tokens = () -> "b".repeat(64);
  private User joao;

  @BeforeEach
  void openSession() {
    joao = store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
    store.createSession(TOKEN, joao.id(), T0).orElseThrow();
  }

  @AfterEach
  void closeStore() {
    store.close();
  }

  private Caller authenticate(String json, Instant now) {
    return Caller.authenticate(new Request(TestJson.object(json), BAD_REQUEST), new ServerContext(store,
        Clock.fixed(now, ZoneOffset.UTC), tokens));
  }

  private ProtocolException fails(String json, Instant now) {
    return assertThrows(ProtocolException.class, () -> authenticate(json, now));
  }

  private static String withToken(String token) {
    return "{'op':'read_user','token':'" + token + "'}";
  }

  @Test
  void findsTheOwnerOfALiveSession() {
    Caller caller = authenticate(withToken(TOKEN), T0);
    assertEquals(joao, caller.user());
    assertEquals(TOKEN, caller.session().token());
  }

  @Test
  void renewsTheIdleTimeout() {
    Instant later = T0.plusSeconds(600);

    assertEquals(later, authenticate(withToken(TOKEN), later).session().lastUsed());
    assertEquals(later, store.findSession(TOKEN).orElseThrow().lastUsed());
  }

  // Timeout conta do ultimo uso, não do login
  @Test
  void countsTheTimeoutFromLastUse() {
    authenticate(withToken(TOKEN), T0.plusSeconds(1500));

    assertEquals(T0.plusSeconds(3000), authenticate(withToken(TOKEN), T0.plusSeconds(3000)).session().lastUsed());
  }

  @Test
  void treatsAnAbsentTokenAsUnauthorized() {
    for (String json : new String[] { "{'op':'read_user'}", withToken("") }) {
      ProtocolException failure = fails(json, T0);
      assertEquals(Status.UNAUTHORIZED, failure.status(), json);
      assertEquals("Token invalido ou expirado", failure.wireMessage(), json);
      assertTrue(failure.getMessage().contains("ausente ou vazio"), failure.getMessage());
    }
  }

  @Test
  void treatsAMalformedTokenAsBadRequest() {
    for (String json : new String[] { withToken("abc"), withToken("A".repeat(64)), withToken("g".repeat(64)),
        "{'op':'read_user','token':null}" }) {
      ProtocolException failure = fails(json, T0);
      assertEquals(Status.BAD_REQUEST, failure.status(), json);
      assertEquals(BAD_REQUEST, failure.wireMessage(), json);
    }
  }

  @Test
  void refusesATokenThatOpensNoSession() {
    ProtocolException failure = fails(withToken("b".repeat(64)), T0);
    assertEquals(Status.UNAUTHORIZED, failure.status());
    assertEquals("Token invalido ou expirado", failure.wireMessage());
    assertTrue(failure.getMessage().contains("nao abre nenhuma sessao"), failure.getMessage());
  }

  // Sessão vencida é apagada
  @Test
  void refusesAnExpiredSessionAndDropsIt() {
    ProtocolException failure = fails(withToken(TOKEN), T0.plus(Session.IDLE_TIMEOUT));

    assertEquals(Status.UNAUTHORIZED, failure.status());
    assertEquals("Token invalido ou expirado", failure.wireMessage());
    assertTrue(store.findSession(TOKEN).isEmpty());
  }
}
