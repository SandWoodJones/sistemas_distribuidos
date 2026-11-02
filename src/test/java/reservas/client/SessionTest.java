package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.T0;
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
import reservas.server.Bootstrap;
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

  @Test
  void sendsTheAdminOpsWithTheAdminToken() throws IOException {
    session.register(EMAIL, "joao", "senha123");
    Bootstrap.seedAdmin(server.store(), T0);
    session.login(Bootstrap.ADMIN_EMAIL, Bootstrap.ADMIN_PASSWORD);

    JsonObject users = session.adminListUsers();
    assertStatus("200", users);
    assertEquals("2", users.get(Fields.COUNT).getAsString());

    assertStatus("400", session.adminUpdateUser("joao", "", "", ""));
    assertStatus("200", session.adminUpdateUser("joao", "joaosilva", "", "admin"));

    JsonObject promoted = session.adminReadUser("joaosilva");
    assertStatus("200", promoted);
    assertEquals("admin", promoted.get(Fields.ROLE).getAsString());

    assertStatus("200", session.adminDeleteUser("joaosilva"));
    assertStatus("404", session.adminReadUser("joaosilva"));
  }

  @Test
  void sendsTheRoomOpsAndLeavesBlankFiltersOut() throws IOException {
    Bootstrap.seedAdmin(server.store(), T0);
    session.login(Bootstrap.ADMIN_EMAIL, Bootstrap.ADMIN_PASSWORD);

    RoomFields b12 = new RoomFields("Sala B12", "12", "Bloco B", List.of("projetor", "tv"));
    JsonObject created = session.createRoom(b12);
    assertStatus("201", created);
    String id = created.get(Fields.ROOM_ID).getAsString();

    JsonObject all = session.listRooms("", "", "", "");
    assertEquals("1", all.get(Fields.COUNT).getAsString());
    assertFalse(all.getAsJsonArray(Fields.ROOMS).get(0).getAsJsonObject().has(Fields.AVAILABLE));
    assertTrue(traffic.stream().noneMatch(line -> line.contains("\"date\"")), traffic.toString());
    assertEquals("0", session.listRooms("2026-09-15", "14:00", "16:00", "13").get(Fields.COUNT).getAsString());

    assertEquals(b12, RoomFields.from(session.readRoom(id)));

    RoomFields renamed = new RoomFields("Sala B13", "16", "Bloco B", List.of());
    assertStatus("200", session.updateRoom(id, renamed, "inactive"));
    JsonObject read = session.readRoom(id);
    assertEquals(renamed, RoomFields.from(read));
    assertEquals("inactive", read.get(Fields.ROOM_STATUS).getAsString());

    assertStatus("200", session.deleteRoom(id));
    assertStatus("404", session.readRoom(id));
  }
}
