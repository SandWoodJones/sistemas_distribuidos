package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertFails;
import static reservas.protocol.ProtocolAssertions.assertKeys;
import static reservas.protocol.ProtocolAssertions.assertResponse;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.LOGIN_JOAO;
import static reservas.server.Fixtures.REGISTER_JOAO;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.TOKEN;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.Status;
import reservas.protocol.TestJson;

class AuthHandlersTest {
  private final SqliteStore store = SqliteStore.openInMemory();
  private final ServerContext context = Fixtures.context(store);

  @AfterEach
  void closeStore() {
    store.close();
  }

  private JsonObject register(String json) {
    return AuthHandlers.register(TestJson.object(json), context);
  }

  private JsonObject login(String json) {
    return login(json, T0);
  }

  private JsonObject login(String json, Instant now) {
    return AuthHandlers.login(TestJson.object(json), Fixtures.context(store, now));
  }

  private JsonObject logout(String token) {
    return AuthHandlers.logout(TestJson.object("{'op':'logout','token':'" + token + "'}"), context);
  }

  @Test
  void registerCreatesAPlainUserWithoutSession() {
    JsonObject response = register("{'op':'register','email':'joao.silva@email.com','user':'joao',"
        + "'password':'senha123','role':'admin','apelido':'jo'}");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("register_response", "201", "Usuario cadastrado com sucesso", response);

    User created = store.findUser("joao").orElseThrow();
    assertEquals("joao.silva@email.com", created.email());
    assertEquals("senha123", created.password());
    assertEquals(Role.USER, created.role());
    assertEquals(T0, created.createdAt());
    assertTrue(store.findSessionOfUser(created.id()).isEmpty());
  }

  @Test
  void registerRejectsEachFieldOutsideTheFormat() {
    String[][] cases = {
        { "email", "{'op':'register','email':'nao-e-email','user':'joao','password':'senha123'}" },
        { "email", "{'op':'register','user':'joao','password':'senha123'}" },
        { "email", "{'op':'register','email':null,'user':'joao','password':'senha123'}" },
        { "user", "{'op':'register','email':'joao.silva@email.com','user':'joao123','password':'senha123'}" },
        { "user", "{'op':'register','email':'joao.silva@email.com','user':'JOAO','password':'senha123'}" },
        { "user", "{'op':'register','email':'joao.silva@email.com','user':'','password':'senha123'}" },
        { "user", "{'op':'register','email':'joao.silva@email.com','user':12,'password':'senha123'}" },
        { "password", "{'op':'register','email':'joao.silva@email.com','user':'joao','password':'senha 123'}" },
        { "password", "{'op':'register','email':'joao.silva@email.com','user':'joao'}" } };

    for (String[] c : cases) {
      ProtocolException failure = assertFails(Status.BAD_REQUEST, "Dados de cadastro em formato invalido",
          () -> register(c[1]));
      assertTrue(failure.getMessage().contains("campo '" + c[0] + "'"), failure.getMessage());
    }
  }

  @Test
  void registerRejectsATakenUserOrEmail() {
    register(REGISTER_JOAO);

    ProtocolException user = assertFails(Status.CONFLICT, "Usuario ou email ja cadastrado",
        () -> register("{'op':'register','email':'outro@email.com','user':'joao','password':'outrasenha'}"));
    ProtocolException email = assertFails(Status.CONFLICT, "Usuario ou email ja cadastrado",
        () -> register("{'op':'register','email':'joao.silva@email.com','user':'maria','password':'outrasenha'}"));

    assertTrue(user.getMessage().contains("user \"joao\""), user.getMessage());
    assertTrue(email.getMessage().contains("email \"joao.silva@email.com\""), email.getMessage());
  }

  @Test
  void loginAnswersTokenAndRoleAndOpensTheSession() {
    register(REGISTER_JOAO);

    JsonObject response = login(LOGIN_JOAO);
    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.TOKEN, Fields.ROLE);
    assertResponse("login_response", "200", "Login realizado com sucesso", response);
    assertEquals(TOKEN, response.get(Fields.TOKEN).getAsString());
    assertEquals("user", response.get(Fields.ROLE).getAsString());
  }

  @Test
  void loginChecksFormatThenCredentialsThenSession() {
    register(REGISTER_JOAO);
    login(LOGIN_JOAO);

    for (String json : new String[] { "{'op':'login','email':'nao-e-email','password':'senha123'}",
        "{'op':'login','email':'joao.silva@email.com','password':'senha 123'}",
        "{'op':'login','password':'senha123'}",
        "{'op':'login','email':'joao.silva@email.com','password':null}" }) {
      assertFails(Status.BAD_REQUEST, "Email ou senha em formato invalido", () -> login(json));
    }

    ProtocolException unknown = assertFails(Status.UNAUTHORIZED, "Email ou senha incorretos",
        () -> login("{'op':'login','email':'ninguem@email.com','password':'senha123'}"));
    assertTrue(unknown.getMessage().contains("nao cadastrado"), unknown.getMessage());

    ProtocolException wrong = assertFails(Status.UNAUTHORIZED, "Email ou senha incorretos",
        () -> login("{'op':'login','email':'joao.silva@email.com','password':'errada'}"));
    assertTrue(wrong.getMessage().contains("senha incorreta"), wrong.getMessage());

    assertFails(Status.CONFLICT, "Usuario ja possui sessao ativa", () -> login(LOGIN_JOAO));
  }

  @Test
  void loginReplacesAnExpiredSession() {
    register(REGISTER_JOAO);
    login(LOGIN_JOAO);

    Instant later = T0.plus(Session.IDLE_TIMEOUT);
    assertStatus("200", login(LOGIN_JOAO, later));
    assertEquals(later, store.findSession(TOKEN).orElseThrow().lastUsed());
  }

  @Test
  void logoutEndsTheSessionOnlyOnce() {
    register(REGISTER_JOAO);
    login(LOGIN_JOAO);

    JsonObject response = logout(TOKEN);
    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("logout_response", "200", "Logout realizado com sucesso", response);
    assertTrue(store.findSession(TOKEN).isEmpty());

    assertFails(Status.UNAUTHORIZED, "Token invalido ou expirado", () -> logout(TOKEN));
    assertStatus("200", login(LOGIN_JOAO));
  }
}
