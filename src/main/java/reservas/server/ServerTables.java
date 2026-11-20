package reservas.server;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import reservas.protocol.Slot;
import reservas.server.ListTableModel.Column;

// Colunas das tabelas da janela do servidor: o que ele guarda, como guarda
final class ServerTables {
  private static final DateTimeFormatter LAST_USED = DateTimeFormatter.ofPattern("HH:mm:ss");

  private ServerTables() {
  }

  static ListTableModel<LoggedInUser> loggedIn(ZoneId zone) {
    return new ListTableModel<>(List.of(
        new Column<>("Usuario", LoggedInUser::name),
        new Column<>("Perfil", user -> user.role().wireName()),
        new Column<>("Ultima atividade", user -> LAST_USED.withZone(zone).format(user.session().lastUsed()))));
  }

  static ListTableModel<Room> rooms() {
    return new ListTableModel<>(List.of(
        new Column<>("ID", room -> Long.toString(room.id())),
        new Column<>("Nome", Room::name),
        new Column<>("Capacidade", room -> Integer.toString(room.capacity())),
        new Column<>("Local", Room::location),
        new Column<>("Recursos", room -> String.join(", ", room.resources())),
        new Column<>("Estado", room -> room.status().wireName()),
        new Column<>("Horarios", room -> String.join(", ", room.slots().stream().map(Slot::wireName).toList()))));
  }

  static ListTableModel<ReservationRow> reservations(ZoneId zone) {
    return new ListTableModel<>(List.of(
        new Column<>("ID", row -> Long.toString(row.reservation().id())),
        new Column<>("Sala", row -> row.reservation().roomId() + " - " + row.roomName()),
        new Column<>("Usuario", ReservationRow::userName),
        new Column<>("Data", row -> row.reservation().window().date().toString()),
        new Column<>("Inicio", row -> row.reservation().window().start().toString()),
        new Column<>("Fim", row -> row.reservation().window().end().toString()),
        new Column<>("Assunto", row -> row.reservation().topic()),
        new Column<>("Participantes", row -> Integer.toString(row.reservation().participants())),
        new Column<>("Criada em", row -> UserViews.CREATED_AT.withZone(zone).format(row.reservation().createdAt()))));
  }

  // Junta os nomes pelo id; sala ou usuário que sumiu no meio da leitura vira "?"
  static List<ReservationRow> reservationRows(List<Reservation> reservations, List<Room> rooms, List<User> users) {
    Map<Long, String> roomNames = rooms.stream().collect(Collectors.toMap(Room::id, Room::name));
    Map<Long, String> userNames = users.stream().collect(Collectors.toMap(User::id, User::name));
    return reservations.stream().map(reservation -> new ReservationRow(reservation,
        roomNames.getOrDefault(reservation.roomId(), "?"), userNames.getOrDefault(reservation.userId(), "?")))
        .toList();
  }
}
