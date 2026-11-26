package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.T0;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Formats;
import reservas.protocol.Role;
import reservas.protocol.TestJson;

class BootstrapTest {
  @Test
  void seedsOneAdminThatLogsInThroughTheProtocol() {
    SqliteStore store = SqliteStore.openInMemory();

    assertEquals(Role.ADMIN, Bootstrap.seedAdmin(store, T0).orElseThrow().role());
    assertTrue(Bootstrap.seedAdmin(store, T0).isEmpty());
    assertEquals(1, store.countAdmins());

    JsonObject response = AuthHandlers.login(
        TestJson.object("{'op':'login','email':'admin@admin.com','password':'admin'}"), Fixtures.context(store));
    assertStatus("200", response);
    assertEquals("admin", response.get(Fields.ROLE).getAsString());
  }

  // Cliente fechado sem logout não prende a conta depois que o servidor reinicia
  @Test
  void endsThePreviousRunsSessionsSoTheNextLoginWorks() {
    SqliteStore store = SqliteStore.openInMemory();
    Bootstrap.seedAdmin(store, T0);
    JsonObject login = TestJson.object("{'op':'login','email':'admin@admin.com','password':'admin'}");
    assertStatus("200", AuthHandlers.login(login, Fixtures.context(store)));

    assertEquals(1, Bootstrap.endPreviousSessions(store));
    assertStatus("200", AuthHandlers.login(login, Fixtures.context(store)));
  }

  // Pré-cadastro do professor: 6 laboratórios com todos os horários e 33 usuários, só num banco novo
  @Test
  void seedsTheDemoLabsAndUsersOnlyOnAFreshDatabase() {
    SqliteStore store = SqliteStore.openInMemory();
    Bootstrap.seedAdmin(store, T0);

    assertTrue(Bootstrap.seedDemo(store, T0));
    List<Room> labs = store.listRooms(1);
    assertEquals(6, labs.size());
    assertEquals("Laboratorio 1", labs.get(0).name());
    labs.forEach(lab -> assertEquals(102, lab.slots().size(), lab.name()));

    List<User> users = store.listUsers();
    assertEquals(34, users.size());
    users.forEach(user -> assertTrue(Formats.matches(Formats.USER, user.name()), user.name()));
    assertEquals("usera", Bootstrap.demoUserName(0));
    assertEquals("userag", Bootstrap.demoUserName(32));

    JsonObject login = AuthHandlers.login(
        TestJson.object("{'op':'login','email':'userag@email.com','password':'senha123'}"), Fixtures.context(store));
    assertStatus("200", login);

    assertFalse(Bootstrap.seedDemo(store, T0));
    assertEquals(6, store.listRooms(1).size());
  }

  @Test
  void leavesADatabaseInUseWithoutTheDemo() {
    SqliteStore store = SqliteStore.openInMemory();
    Bootstrap.seedAdmin(store, T0);
    store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();

    assertFalse(Bootstrap.seedDemo(store, T0));
    assertTrue(store.listRooms(1).isEmpty());
  }

  @Test
  void leavesAnAccountInTheWayAlone() {
    SqliteStore store = SqliteStore.openInMemory();
    store.createUser("admin", "outro@email.com", "senha123", Role.USER, T0).orElseThrow();

    assertTrue(Bootstrap.seedAdmin(store, T0).isEmpty());
    assertEquals(0, store.countAdmins());
    assertEquals(Role.USER, store.findUser("admin").orElseThrow().role());
  }
}
