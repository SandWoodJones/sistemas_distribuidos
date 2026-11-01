package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.T0_ON_THE_WIRE;

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

  @Test
  void keepsTheTokenAndRoleOnlyAfterAnAcceptedLogin() throws IOException {
    JsonObject anonymous = session.readUser();
    assertStatus("401", anonymous);
    assertEquals("Token invalido ou expirado", anonymous.get(Fields.MESSAGE).getAsString());

    assertStatus("201", session.register(EMAIL, "joao", "senha123"));
    assertStatus("401", session.login(EMAIL, "errada"));
    assertFalse(session.isLoggedIn());
    assertTrue(session.role().isEmpty());

    assertStatus("200", session.login(EMAIL, "senha123"));
    assertTrue(session.isLoggedIn());
    assertEquals("user", session.role().orElseThrow());
  }

  // Sem token o servidor responde 401
  @Test
  void sendsTheAccountOpsWithTheSessionToken() throws IOException {
    session.register(EMAIL, "joao", "senha123");
    session.login(EMAIL, "senha123");

    JsonObject account = session.readUser();
    assertStatus("200", account);
    assertEquals("joao", account.get(Fields.USER).getAsString());
    assertEquals(EMAIL, account.get(Fields.EMAIL).getAsString());
    assertEquals(T0_ON_THE_WIRE, account.get(Fields.CREATED_AT).getAsString());

    assertStatus("400", session.updateUser("", ""));
    assertStatus("200", session.updateUser("joaosilva", ""));
    assertEquals("joaosilva", session.readUser().get(Fields.USER).getAsString());
  }

  @Test
  void forgetsTheTokenWhenLogoutAnswers200Or401() throws IOException {
    session.register(EMAIL, "joao", "senha123");
    session.login(EMAIL, "senha123");

    assertStatus("200", session.logout());
    assertFalse(session.isLoggedIn());

    String token = session.login(EMAIL, "senha123").get(Fields.TOKEN).getAsString();
    server.store().deleteSession(token);

    assertStatus("401", session.logout());
    assertFalse(session.isLoggedIn());
  }

  @Test
  void forgetsTheTokenAfterDeletingTheAccount() throws IOException {
    session.register(EMAIL, "joao", "senha123");
    session.login(EMAIL, "senha123");

    assertStatus("200", session.deleteUser("senha123"));
    assertFalse(session.isLoggedIn());
    assertStatus("401", session.login(EMAIL, "senha123"));
  }
}
