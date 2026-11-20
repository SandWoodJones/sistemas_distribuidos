package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.TOKEN;

import java.util.List;

import org.junit.jupiter.api.Test;

import reservas.protocol.Role;

class LoggedInTableModelTest {
  @Test
  void showsNameRoleAndLastUseInTheServerZone() {
    LoggedInTableModel model = new LoggedInTableModel(ServerApp.ZONE);
    model.show(List.of(new LoggedInUser("maria", Role.ADMIN, new Session(TOKEN, 1, T0))));

    assertEquals(1, model.getRowCount());
    assertEquals("maria", model.getValueAt(0, 0));
    assertEquals("admin", model.getValueAt(0, 1));
    assertEquals("14:32:10", model.getValueAt(0, 2));

    model.show(List.of());
    assertEquals(0, model.getRowCount());
  }
}
