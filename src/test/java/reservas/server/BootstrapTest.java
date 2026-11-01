package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.T0;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
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
}
