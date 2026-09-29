package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.Status;

class AccountHandlersTest {
  private static final Instant T0 = Instant.parse("2026-09-09T17:32:10Z");
  private static final String TOKEN = "a".repeat(64);
  private static final String ADMIN_TOKEN = "b".repeat(64);

  private static final String UPDATE = "{\"op\":\"update_user\",\"token\":\"" + TOKEN + "\"";

  private static final String DELETE = "{\"op\":\"delete_user\",\"token\":\"" + TOKEN + "\"";

  private final SqliteStore store = SqliteStore.openInMemory();
  private final Clock clock = Clock.fixed(T0, ZoneId.of("America/Sao_Paulo"));
  private final TokenGenerator tokens = () -> TOKEN;
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

  private JsonObject readUser(String token) {
    return readUser(token, clock);
  }

  private JsonObject readUser(String token, Clock at) {
    return AccountHandlers.readUser(
        JsonParser.parseString("{\"op\":\"read_user\",\"token\":\"" + token + "\"}").getAsJsonObject(), store, at,
        tokens);
  }

  private ProtocolException readUserFails(String token) {
    return assertThrows(ProtocolException.class, () -> readUser(token));
  }

  private JsonObject updateUser(String body) {
    return AccountHandlers.updateUser(JsonParser.parseString(body).getAsJsonObject(), store, clock, tokens);
  }

  private ProtocolException updateUserFails(String body) {
    return assertThrows(ProtocolException.class, () -> updateUser(body));
  }

  private JsonObject deleteUser(String body) {
    return AccountHandlers.deleteUser(JsonParser.parseString(body).getAsJsonObject(), store, clock, tokens);
  }

  private ProtocolException deleteUserFails(String body) {
    return assertThrows(ProtocolException.class, () -> deleteUser(body));
  }

  @Test
  void ansers200WithTheWholeAccount() {
    JsonObject response = readUser(TOKEN);

    assertEquals(
        List.of(Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.USER, Fields.EMAIL, Fields.ROLE, Fields.CREATED_AT),
        List.copyOf(response.keySet()));
    assertEquals("read_user_response", response.get(Fields.OP).getAsString());
    assertEquals("200", response.get(Fields.STATUS).getAsString());
    assertEquals("Consulta realizada com sucesso", response.get(Fields.MESSAGE).getAsString());
    assertEquals("joao", response.get(Fields.USER).getAsString());
    assertEquals("user", response.get(Fields.ROLE).getAsString());
  }

  // O banco guarda UTC, a rede recebe no fuso do `Clock`
  @Test
  void formatsCreatedAtInTheZoneOfTheClock() {
    assertEquals("2026-09-09 14:32:10", readUser(TOKEN).get(Fields.CREATED_AT).getAsString());
    assertEquals("2026-09-09 17:32:10",
        readUser(TOKEN, Clock.fixed(T0, ZoneOffset.UTC)).get(Fields.CREATED_AT).getAsString());
  }

  @Test
  void answersTheRoleStoredInTheAccount() {
    User maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createSession(ADMIN_TOKEN, maria.id(), T0).orElseThrow();

    assertEquals("admin", readUser(ADMIN_TOKEN).get(Fields.ROLE).getAsString());
  }

  @Test
  void refusesATokenWithoutASession() {
    ProtocolException failure = readUserFails("c".repeat(64));
    assertEquals(Status.UNAUTHORIZED, failure.status());
    assertEquals("Token invalido ou expirado", failure.wireMessage());
  }

  @Test
  void carriesTheReadUserTextOnAMalformedToken() {
    ProtocolException failure = readUserFails("abc");
    assertEquals(Status.BAD_REQUEST, failure.status());
    assertEquals("Token em formato invalido", failure.wireMessage());
  }

  // Trocar o proprio `user` não derruba a sessão
  @Test
  void changesTheUserAndKeepsTheToken() {
    JsonObject response = updateUser(UPDATE + ",\"user\":\"joaosilva\"}");

    assertEquals(List.of(Fields.OP, Fields.STATUS, Fields.MESSAGE), List.copyOf(response.keySet()));
    assertEquals("update_user_response", response.get(Fields.OP).getAsString());
    assertEquals("200", response.get(Fields.STATUS).getAsString());
    assertEquals("Dados atualizados com sucesso", response.get(Fields.MESSAGE).getAsString());
    assertEquals("joaosilva", store.findUserById(joao.id()).orElseThrow().name());
    assertEquals(joao.id(), store.findSession(TOKEN).orElseThrow().userId());
  }

  @Test
  void changesThePassword() {
    updateUser(UPDATE + ",\"password\":\"novasenha1\"}");

    assertEquals("novasenha1", store.findUserById(joao.id()).orElseThrow().password());
  }

  @Test
  void treatsEmptyAsNoChange() {
    updateUser(UPDATE + ",\"user\":\"\",\"password\":\"\"}");

    assertEquals(joao, store.findUserById(joao.id()).orElseThrow());
  }

  @Test
  void rejectsNullFields() {
    for (String body : new String[] { UPDATE + ",\"user\":null}", UPDATE + ",\"password\":null}" }) {
      ProtocolException failure = updateUserFails(body);
      assertEquals(Status.BAD_REQUEST, failure.status(), body);
      assertEquals("Dados em formato invalido", failure.wireMessage(), body);
    }
  }

  @Test
  void rejectsTheEmailKeyEvenWhenEmpty() {
    for (String body : new String[] { UPDATE + ",\"email\":\"outro@email.com\"}", UPDATE + ",\"email\":\"\"}",
        UPDATE + ",\"email\":null}" }) {
      ProtocolException failure = updateUserFails(body);
      assertEquals(Status.BAD_REQUEST, failure.status(), body);
      assertEquals("Dados em formato invalido", failure.wireMessage(), body);
      assertTrue(failure.getMessage().contains("'email'"), failure.getMessage());
    }
  }

  @Test
  void rejectsValuesOutsideTheFormat() {
    for (String body : new String[] { UPDATE + ",\"user\":\"joao123\"}", UPDATE + ",\"user\":\"JOAO\"}",
        UPDATE + ",\"password\":\"senha 123\"}" }) {
      assertEquals(Status.BAD_REQUEST, updateUserFails(body).status(), body);
    }
  }

  @Test
  void rejectsAUserAlreadyTaken() {
    store.createUser("maria", "maria@email.com", "senha123", Role.USER, T0).orElseThrow();

    ProtocolException failure = updateUserFails(UPDATE + ",\"user\":\"maria\"}");
    assertEquals(Status.CONFLICT, failure.status());
    assertEquals("Usuario ja esta em uso", failure.wireMessage());
  }

  // Reenviar o proprio nome nao colide consigo mesmo
  @Test
  void acceptsTheOwnUserUnchanged() {
    assertEquals("200", updateUser(UPDATE + ",\"user\":\"joao\"}").get(Fields.STATUS).getAsString());
  }

  @Test
  void refusesAnUpdateWithATokenWithoutASession() {
    ProtocolException failure = assertThrows(ProtocolException.class,
        () -> updateUser("{\"op\":\"update_user\",\"token\":\"" + "c".repeat(64) + "\",\"user\":\"joaosilva\"}"));

    assertEquals(Status.UNAUTHORIZED, failure.status());
    assertEquals("Token invalido ou expirado", failure.wireMessage());
  }

  // A sessao cai com o cadastro por `ON DELETE CASCADE`
  @Test
  void removesTheAccountAndTheSession() {
    JsonObject response = deleteUser(DELETE + ",\"password\":\"senha123\"}");

    assertEquals(List.of(Fields.OP, Fields.STATUS, Fields.MESSAGE), List.copyOf(response.keySet()));
    assertEquals("delete_user_response", response.get(Fields.OP).getAsString());
    assertEquals("200", response.get(Fields.STATUS).getAsString());
    assertEquals("Usuario removido com sucesso", response.get(Fields.MESSAGE).getAsString());
    assertTrue(store.findUserById(joao.id()).isEmpty());
    assertTrue(store.findSession(TOKEN).isEmpty());
  }

  // Senha errada é 401, e o cadastro fica
  @Test
  void refusesTheWrongPasswordAndKeepsTheAccount() {
    ProtocolException failure = deleteUserFails(DELETE + ",\"password\":\"errada\"}");

    assertEquals(Status.UNAUTHORIZED, failure.status());
    assertEquals("Token invalido ou expirado", failure.wireMessage());
    assertTrue(failure.getMessage().contains("senha incorreta"), failure.getMessage());
    assertTrue(store.findUserById(joao.id()).isPresent());
  }

  @Test
  void rejectsAnAbsentOrMalformedPassword() {
    for (String body : new String[] { DELETE + "}", DELETE + ",\"password\":\"\"}",
        DELETE + ",\"password\":\"senha 123\"}", DELETE + ",\"password\":null}" }) {
      ProtocolException failure = deleteUserFails(body);
      assertEquals(Status.BAD_REQUEST, failure.status(), body);
      assertEquals("Senha em formato invalido", failure.wireMessage(), body);
    }
  }

  // O ultimo admin não sai
  @Test
  void refusesToRemoveTheLastAdmin() {
    User maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createSession(ADMIN_TOKEN, maria.id(), T0).orElseThrow();

    ProtocolException failure = assertThrows(ProtocolException.class,
        () -> deleteUser("{\"op\":\"delete_user\",\"token\":\"" + ADMIN_TOKEN + "\",\"password\":\"senha123\"}"));
    assertEquals(Status.FORBIDDEN, failure.status());
    assertEquals("Nao e possivel remover o ultimo administrador", failure.wireMessage());
    assertTrue(store.findUserById(maria.id()).isPresent());
  }

  @Test
  void removesAnAdminWhileAnotherRemains() {
    User maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createUser("ana", "ana@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createSession(ADMIN_TOKEN, maria.id(), T0).orElseThrow();

    assertEquals("200",
        deleteUser("{\"op\":\"delete_user\",\"token\":\"" + ADMIN_TOKEN + "\",\"password\":\"senha123\"}")
            .get(Fields.STATUS).getAsString());
    assertTrue(store.findUserById(maria.id()).isEmpty());
  }
}
