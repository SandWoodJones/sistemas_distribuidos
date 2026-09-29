package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.server.TestServer;

@Timeout(10)
class SessionTest {
  private static final String EMAIL = "joao.silva@email.com";

  private TestServer server;
  private final List<String> traffic = new ArrayList<>();

  private Session session;

  @BeforeEach
  void startServer() throws IOException {
    server = TestServer.start();
    session = new Session(TestClients.connect(server.port(), traffic::add));
  }

  @AfterEach
  void stopServer() throws IOException {
    session.close();
    server.close();
  }

  private static void assertStatus(String status, JsonObject response) {
    assertEquals(status, response.get(Fields.STATUS).getAsString(), response.toString());
  }

  private JsonObject signUpAndIn() throws IOException {
    assertStatus("201", session.register(EMAIL, "joao", "senha123"));
    return session.login(EMAIL, "senha123");
  }

  @Test
  void keepsTheTokenAndRoleAfterLogin() throws IOException {
    assertFalse(session.isLoggedIn());

    assertStatus("200", signUpAndIn());
    assertTrue(session.isLoggedIn());
    assertEquals("user", session.role().orElseThrow());
  }

  // Login recusado não abre sessão
  @Test
  void staysLoggedOutWhenLoginFails() throws IOException {
    assertStatus("201", session.register(EMAIL, "joao", "senha123"));

    assertStatus("401", session.login(EMAIL, "errada"));
    assertFalse(session.isLoggedIn());
    assertTrue(session.role().isEmpty());
  }

  // Sem token o servidor responde 401
  @Test
  void sendsAnEmptyTokenBeforeLogin() throws IOException {
    JsonObject response = session.readUser();

    assertStatus("401", response);
    assertEquals("Token invalido ou expirado", response.get(Fields.MESSAGE).getAsString());
  }

  @Test
  void readsTheWholeAccount() throws IOException {
    signUpAndIn();

    JsonObject response = session.readUser();
    assertStatus("200", response);
    assertEquals("joao", response.get(Fields.USER).getAsString());
    assertEquals(EMAIL, response.get(Fields.EMAIL).getAsString());
    assertEquals("user", response.get(Fields.ROLE).getAsString());
    assertEquals("2026-09-09 14:32:10", response.get(Fields.CREATED_AT).getAsString());
  }

  @Test
  void treatsEmptyUpdateFieldsAsNoChange() throws IOException {
    signUpAndIn();

    assertStatus("200", session.updateUser("", ""));
    assertEquals("joao", session.readUser().get(Fields.USER).getAsString());

    assertStatus("200", session.updateUser("joaosilva", ""));
    assertEquals("joaosilva", session.readUser().get(Fields.USER).getAsString());
  }

  @Test
  void forgetsTheTokenOnLogout() throws IOException {
    signUpAndIn();

    assertStatus("200", session.logout());
    assertFalse(session.isLoggedIn());
    assertStatus("401", session.readUser());
  }

  @Test
  void forgetsTheTokenAfterDeletingTheAccount() throws IOException {
    signUpAndIn();

    assertStatus("200", session.deleteUser("senha123"));
    assertFalse(session.isLoggedIn());
    assertStatus("401", session.login(EMAIL, "senha123"));
  }
}
