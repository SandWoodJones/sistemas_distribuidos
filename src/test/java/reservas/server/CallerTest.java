package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertFails;
import static reservas.server.Fixtures.OTHER_TOKEN;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.TOKEN;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reservas.protocol.TestJson;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;
import reservas.protocol.Role;
import reservas.protocol.Status;

class CallerTest {
  private static final String BAD_REQUEST = "Token em formato invalido";
  private static final String UNAUTHORIZED = "Token invalido ou expirado";

  private final SqliteStore store = SqliteStore.openInMemory();
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
    return Caller.authenticate(new Request(TestJson.object(json), BAD_REQUEST),
        Fixtures.context(store, now));
  }

  private void assertRejected(Status status, String wireMessage, String diagnosis, String json, Instant now) {
    ProtocolException failure = assertFails(status, wireMessage, () -> authenticate(json, now));
    assertTrue(failure.getMessage().contains(diagnosis), failure.getMessage());
  }

  private static String withToken(String token) {
    return "{'op':'read_user','token':'" + token + "'}";
  }

  @Test
  void rejectsATokenThatOpensNoSession() {
    assertRejected(Status.UNAUTHORIZED, UNAUTHORIZED, "ausente ou vazio", "{'op':'read_user'}", T0);
    assertRejected(Status.UNAUTHORIZED, UNAUTHORIZED, "ausente ou vazio", withToken(""), T0);

    for (String json : new String[] { withToken("abc"), withToken("A".repeat(64)), withToken("g".repeat(64)),
        "{'op':'read_user','token':null}" }) {
      assertRejected(Status.BAD_REQUEST, BAD_REQUEST, "'token'", json, T0);
    }

    assertRejected(Status.UNAUTHORIZED, UNAUTHORIZED, "nao abre nenhuma sessao", withToken(OTHER_TOKEN), T0);
  }

  @Test
  void idleTimeoutCountsFromTheLastUse() {
    Instant almost = T0.plus(Session.IDLE_TIMEOUT).minusSeconds(1);
    Instant almostAgain = almost.plus(Session.IDLE_TIMEOUT).minusSeconds(1);

    assertEquals(joao, authenticate(withToken(TOKEN), almost).user());
    assertEquals(joao, authenticate(withToken(TOKEN), almostAgain).user());

    assertRejected(Status.UNAUTHORIZED, UNAUTHORIZED, "parada desde", withToken(TOKEN),
        almostAgain.plus(Session.IDLE_TIMEOUT));
    assertTrue(store.findSession(TOKEN).isEmpty());
  }
}
