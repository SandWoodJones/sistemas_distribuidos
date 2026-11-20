package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.T0_ON_THE_WIRE;
import static reservas.server.Fixtures.TOKEN;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import reservas.protocol.Role;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;
import reservas.protocol.TimeWindow;

class ServerTablesTest {
  private static List<Object> row(ListTableModel<?> model, int row) {
    return IntStream.range(0, model.getColumnCount()).mapToObj(column -> model.getValueAt(row, column)).toList();
  }

  @Test
  void loggedInShowsNameRoleAndLastUseInTheServerZone() {
    ListTableModel<LoggedInUser> model = ServerTables.loggedIn(ServerApp.ZONE);
    model.show(List.of(new LoggedInUser("maria", Role.ADMIN, new Session(TOKEN, 1, T0))));

    assertEquals(List.of("maria", "admin", "14:32:10"), row(model, 0));

    model.show(List.of());
    assertEquals(0, model.getRowCount());
  }

  @Test
  void roomsShowEveryStoredField() {
    ListTableModel<Room> model = ServerTables.rooms();
    model.show(List.of(new Room(7, "Sala B12", 12, "Bloco B", List.of("projetor", "tv"), RoomStatus.INACTIVE,
        List.of(Slot.fromWire("3T1").orElseThrow(), Slot.fromWire("3T2").orElseThrow()))));

    assertEquals(List.of("ID", "Nome", "Capacidade", "Local", "Recursos", "Estado", "Horarios"),
        IntStream.range(0, model.getColumnCount()).mapToObj(model::getColumnName).toList());
    assertEquals(List.of("7", "Sala B12", "12", "Bloco B", "projetor, tv", "inactive", "3T1, 3T2"), row(model, 0));
  }

  @Test
  void reservationsShowTheRoomAndOwnerByName() {
    Room b12 = new Room(7, "Sala B12", 12, "Bloco B", List.of(), RoomStatus.ACTIVE, List.of());
    User joao = new User(3, "joao", "joao.silva@email.com", "senha123", Role.USER, T0);
    TimeWindow window = new TimeWindow(LocalDate.parse("2026-09-15"), LocalTime.parse("13:00"),
        LocalTime.parse("14:40"));
    Reservation reservation = new Reservation(27, 7, 3, window, "Reuniao de projeto", 6, T0);

    ListTableModel<ReservationRow> model = ServerTables.reservations(ServerApp.ZONE);
    model.show(ServerTables.reservationRows(List.of(reservation), List.of(b12), List.of(joao)));

    assertEquals(List.of("27", "7 - Sala B12", "joao", "2026-09-15", "13:00", "14:40", "Reuniao de projeto", "6",
        T0_ON_THE_WIRE), row(model, 0));
  }
}
