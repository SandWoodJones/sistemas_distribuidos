package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.Status;

class AuthHandlersTest {
  private static final Instant T0 = Instant.parse("2026-09-09T14:32:10Z");
  private static final String VALID = "{'op':'register','email':'joao.silva@email.com','user':'joao','password':'senha123'}";
  private final SqliteStore store = SqliteStore.openInMemory();
  private final TokenGenerator tokens = () -> "a".repeat(64);
  private final ServerContext context = new ServerContext(store, Clock.fixed(T0, ZoneOffset.UTC), tokens);

  private static final String LOGIN = "{'op':'login','email':'joao.silva@email.com','password':'senha123'}";
  private static final String TOKEN = "a".repeat(64);

  @AfterEach
  void closeStore() {
    store.close();
  }

  private JsonObject register(String json) {
    return AuthHandlers.register(TestJson.object(json), context);
  }

  private ProtocolException registerFails(String json) {
    return assertThrows(ProtocolException.class, () -> register(json));
  }

  private JsonObject login(String json) {
    return login(json, context.clock());
  }

  private JsonObject login(String json, Clock at) {
    return AuthHandlers.login(TestJson.object(json), new ServerContext(store, at, tokens));
  }

  private ProtocolException loginFails(String json) {
    return assertThrows(ProtocolException.class, () -> login(json));
  }

  private JsonObject logout(String token) {
    return AuthHandlers.logout(
        TestJson.object("{'op':'logout','token':'" + token + "'}"), context);
  }

  @Test
  void answers201WithTheEnvelopeAndNothingElse() {
    JsonObject response = register(VALID);
    assertEquals(List.of(Fields.OP, Fields.STATUS, Fields.MESSAGE), List.copyOf(response.keySet()));
    assertEquals("register_response", response.get(Fields.OP).getAsString());
    assertEquals("201", response.get(Fields.STATUS).getAsString());
    assertEquals("Usuario cadastrado com sucesso", response.get(Fields.MESSAGE).getAsString());
  }

  @Test
  void storesTheAccountAsUserAndWithoutSession() {
    register(VALID);

    User created = store.findUser("joao").orElseThrow();
    assertEquals("joao.silva@email.com", created.email());
    assertEquals("senha123", created.password());
    assertEquals(Role.USER, created.role());
    assertEquals(T0, created.createdAt());
    assertTrue(store.findSessionOfUser(created.id()).isEmpty());
  }

  @Test
  void rejectsValuesOutsideTheFormat() {
    for (String json : new String[] {
        "{'op':'register','email':'nao-e-email','user':'joao','password':'senha123'}",
        "{'op':'register','email':'joao.silva@email.com','user':'joao123','password':'senha123'}",
        "{'op':'register','email':'joao.silva@email.com','user':'JOAO','password':'senha123'}",
        "{'op':'register','email':'joao.silva@email.com','user':'joao','password':'senha 123'}" }) {
      ProtocolException failure = registerFails(json);
      assertEquals(Status.BAD_REQUEST, failure.status(), json);
      assertEquals("Dados de cadastro em formato invalido", failure.wireMessage(), json);
    }
  }

  @Test
  void rejectsAbsentNullAndEmptyFields() {
    for (String json : new String[] { "{'op':'register','user':'joao','password':'senha123'}",
        "{'op':'register','email':'joao.silva@email.com','password':'senha123'}",
        "{'op':'register','email':'joao.silva@email.com','user':'joao'}",
        "{'op':'register','email':null,'user':'joao','password':'senha123'}",
        "{'op':'register','email':'joao.silva@email.com','user':'','password':'senha123'}" }) {
      assertEquals(Status.BAD_REQUEST, registerFails(json).status(), json);
    }
  }

  @Test
  void rejectsADuplicateUser() {
    register(VALID);

    ProtocolException failure = registerFails(
        "{'op':'register','email':'outro@email.com','user':'joao','password':'outrasenha'}");
    assertEquals(Status.CONFLICT, failure.status());
    assertEquals("Usuario ou email ja cadastrado", failure.wireMessage());
    assertTrue(failure.getMessage().contains("user \"joao\""), failure.getMessage());
  }

  @Test
  void rejectsADuplicateEmail() {
    register(VALID);

    ProtocolException failure = registerFails(
        "{'op':'register','email':'joao.silva@email.com','user':'maria','password':'outrasenha'}");
    assertEquals(Status.CONFLICT, failure.status());
    assertEquals("Usuario ou email ja cadastrado", failure.wireMessage());
    assertTrue(failure.getMessage().contains("email \"joao.silva@email.com\""), failure.getMessage());
  }

  // `role` enviado no cadastro é campo desconhecido
  @Test
  void ignoresUnknownFields() {
    JsonObject response = register("{'op':'register','email':'joao.silva@email.com','user':'joao',"
        + "'password':'senha123','role':'admin','apelido':'jo'}");

    assertEquals("201", response.get(Fields.STATUS).getAsString());
    assertEquals(Role.USER, store.findUser("joao").orElseThrow().role());
  }

  @Test
  void answers200WithTokenAndRole() {
    register(VALID);

    JsonObject response = login(LOGIN);
    assertEquals(List.of(Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.TOKEN, Fields.ROLE),
        List.copyOf(response.keySet()));
    assertEquals("login_response", response.get(Fields.OP).getAsString());
    assertEquals("200", response.get(Fields.STATUS).getAsString());
    assertEquals("Login realizado com sucesso", response.get(Fields.MESSAGE).getAsString());
    assertEquals(TOKEN, response.get(Fields.TOKEN).getAsString());
    assertEquals("user", response.get(Fields.ROLE).getAsString());
  }

  @Test
  void opensTheSession() {
    register(VALID);
    login(LOGIN);

    Session session = store.findSession(TOKEN).orElseThrow();
    assertEquals(store.findUser("joao").orElseThrow().id(), session.userId());
    assertEquals(T0, session.lastUsed());
  }

  @Test
  void answersTheAdminRoleForTheSeededAdmin() {
    Bootstrap.seedAdmin(store, T0);

    JsonObject response = login("{'op':'login','email':'" + Bootstrap.ADMIN_EMAIL + "','password':'"
        + Bootstrap.ADMIN_PASSWORD + "'}");
    assertEquals("admin", response.get(Fields.ROLE).getAsString());
  }

  @Test
  void givesTheSame401ForUnknownEmailAndWrongPassword() {
    register(VALID);

    ProtocolException unknown = loginFails(
        "{'op':'login','email':'ninguem@email.com','password':'senha123'}");
    ProtocolException wrong = loginFails(
        "{'op':'login','email':'joao.silva@email.com','password':'errada'}");

    assertEquals(Status.UNAUTHORIZED, unknown.status());
    assertEquals(Status.UNAUTHORIZED, wrong.status());
    assertEquals("Email ou senha incorretos", unknown.wireMessage());
    assertEquals("Email ou senha incorretos", wrong.wireMessage());
    assertTrue(unknown.getMessage().contains("nao cadastrado"), unknown.getMessage());
    assertTrue(wrong.getMessage().contains("senha incorreta"), wrong.getMessage());
  }

  @Test
  void rejectsCredentialsOutsideTheFormat() {
    for (String json : new String[] { "{'op':'login','email':'nao-e-email','password':'senha123'}",
        "{'op':'login','email':'joao.silva@email.com','password':'senha 123'}",
        "{'op':'login','password':'senha123'}",
        "{'op':'login','email':'joao.silva@email.com','password':null}" }) {
      ProtocolException failure = loginFails(json);
      assertEquals(Status.BAD_REQUEST, failure.status(), json);
      assertEquals("Email ou senha em formato invalido", failure.wireMessage(), json);
    }
  }

  @Test
  void refusesASecondLoginWhileTheSessionIsAlive() {
    register(VALID);
    login(LOGIN);

    ProtocolException failure = loginFails(LOGIN);
    assertEquals(Status.CONFLICT, failure.status());
    assertEquals("Usuario ja possui sessao ativa", failure.wireMessage());
  }

  // A sessao vencida nao e 409, o login novo toma o lugar dela
  @Test
  void letsTheUserInAgainAfterTheSessionExpires() {
    register(VALID);
    login(LOGIN);

    Instant later = T0.plus(Session.IDLE_TIMEOUT);
    JsonObject response = login(LOGIN, Clock.fixed(later, ZoneOffset.UTC));
    assertEquals("200", response.get(Fields.STATUS).getAsString());
    assertEquals(later, store.findSession(TOKEN).orElseThrow().lastUsed());
  }

  @Test
  void answers200AndInvalidatesTheToken() {
    register(VALID);
    login(LOGIN);

    JsonObject response = logout(TOKEN);
    assertEquals(List.of(Fields.OP, Fields.STATUS, Fields.MESSAGE), List.copyOf(response.keySet()));
    assertEquals("logout_response", response.get(Fields.OP).getAsString());
    assertEquals("200", response.get(Fields.STATUS).getAsString());
    assertEquals("Logout realizado com sucesso", response.get(Fields.MESSAGE).getAsString());
    assertTrue(store.findSession(TOKEN).isEmpty());
  }

  @Test
  void refusesToLogoutTwice() {
    register(VALID);
    login(LOGIN);
    logout(TOKEN);

    ProtocolException failure = assertThrows(ProtocolException.class, () -> logout(TOKEN));
    assertEquals(Status.UNAUTHORIZED, failure.status());
    assertEquals("Token invalido ou expirado", failure.wireMessage());
  }

  @Test
  void letsTheUserLogInAgainAfterLogout() {
    register(VALID);
    login(LOGIN);
    logout(TOKEN);

    assertEquals("200", login(LOGIN).get(Fields.STATUS).getAsString());
  }

  @Test
  void carriesTheLogoutTextOnAMalformedToken() {
    ProtocolException failure = assertThrows(ProtocolException.class, () -> logout("abc"));
    assertEquals(Status.BAD_REQUEST, failure.status());
    assertEquals("Token em formato invalido", failure.wireMessage());
  }
}
