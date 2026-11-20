package reservas.server;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Formats;
import reservas.protocol.Messages;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;
import reservas.protocol.Responses;
import reservas.protocol.Role;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;
import reservas.protocol.Status;
import reservas.protocol.TimeWindow;

public final class ReservationHandlers {
  static final String RESERVATION_BAD_REQUEST = "Dados da reserva em formato invalido";
  static final String RESERVATION_NOT_FOUND = "Reserva nao encontrada";
  static final String ROOM_UNAVAILABLE = "Sala indisponivel para reserva";
  static final String OUTSIDE_HOURS = "Horario fora do funcionamento da sala";
  static final String ROOM_TAKEN = "Sala ja reservada no periodo solicitado";
  static final String ALREADY_OVER = "Reserva ja encerrada";

  private ReservationHandlers() {
  }

  public static void install(Dispatcher dispatcher) {
    dispatcher.register(Op.CREATE_RESERVATION, ReservationHandlers::createReservation);
    dispatcher.register(Op.READ_RESERVATION, ReservationHandlers::readReservation);
    dispatcher.register(Op.DELETE_RESERVATION, ReservationHandlers::deleteReservation);
  }

  static JsonObject createReservation(JsonObject json, ServerContext context) {
    Request request = new Request(json, RESERVATION_BAD_REQUEST);
    Booking booking = booking(request, LocalDateTime.now(context.clock()));

    User user = Caller.authenticate(request, context).user();
    SqliteStore store = context.store();
    Instant createdAt = context.clock().instant();
    Reservation reservation = store.withRoomLock(booking.roomId(), () -> book(store, user, booking, createdAt));

    JsonObject response = Responses.of(Op.CREATE_RESERVATION, Status.CREATED, "Reserva realizada com sucesso");
    response.addProperty(Fields.RESERVATION_ID, Long.toString(reservation.id()));
    return response;
  }

  static JsonObject readReservation(JsonObject json, ServerContext context) {
    Request request = new Request(json, Messages.DATA_BAD_REQUEST);
    long id = reservationId(request);

    User user = Caller.authenticate(request, context).user();
    SqliteStore store = context.store();
    Reservation reservation = findReservation(store, id);
    requireOwnerOrAdmin(user, reservation);

    return Responses.found(Op.READ_RESERVATION, details(store, reservation, context.clock().getZone()));
  }

  static JsonObject deleteReservation(JsonObject json, ServerContext context) {
    Request request = new Request(json, Messages.DATA_BAD_REQUEST);
    long id = reservationId(request);

    User user = Caller.authenticate(request, context).user();
    SqliteStore store = context.store();
    Reservation reservation = findReservation(store, id);
    requireOwnerOrAdmin(user, reservation);
    requireActive(reservation, LocalDateTime.now(context.clock()));

    store.deleteReservation(id);
    return Responses.of(Op.DELETE_RESERVATION, Status.OK, "Reserva cancelada com sucesso");
  }

  private static Booking booking(Request request, LocalDateTime now) {
    long roomId = RoomHandlers.roomId(request);
    TimeWindow window = request.upcomingWindow(now);
    String topic = request.required(Fields.TOPIC, Formats.TOPIC);
    int participants = Integer.parseInt(request.required(Fields.PARTICIPANTS, Formats.CAPACITY));

    return new Booking(roomId, window, topic, participants);
  }

  private static Reservation book(SqliteStore store, User user, Booking booking, Instant createdAt) {
    Room room = RoomHandlers.findRoom(store, booking.roomId());
    requireFits(room, booking.participants());
    requireBookable(room, booking.window());
    requireFree(store.listReservationsOn(room.id(), booking.window().date()), booking.window());

    return store.createReservation(booking, user.id(), createdAt);
  }

  private static void requireFits(Room room, int participants) {
    if (participants > room.capacity()) {
      throw ProtocolException.badRequest(RESERVATION_BAD_REQUEST,
          "campo 'participants' " + participants + " acima da capacidade " + room.capacity() + " da sala " + room.id());
    }
  }

  private static void requireBookable(Room room, TimeWindow window) {
    if (room.status() == RoomStatus.INACTIVE) {
      throw ProtocolException.conflict(ROOM_UNAVAILABLE, "sala " + room.id() + " esta inativa");
    }
    if (!room.worksDuring(window)) {
      List<String> slots = room.slotsOn(window.date().getDayOfWeek()).stream().map(Slot::wireName).toList();
      throw ProtocolException.conflict(OUTSIDE_HOURS, "sala " + room.id() + " em " + window.date() + " so funciona em "
          + slots + ", pedido " + window.start() + "-" + window.end());
    }
  }

  private static void requireFree(List<Reservation> sameDay, TimeWindow window) {
    for (Reservation taken : sameDay) {
      if (taken.window().overlaps(window)) {
        throw ProtocolException.conflict(ROOM_TAKEN, "reserva " + taken.id() + " ja ocupa " + taken.window().start()
            + "-" + taken.window().end() + ", pedido " + window.start() + "-" + window.end());
      }
    }
  }

  private static void requireOwnerOrAdmin(User user, Reservation reservation) {
    if (reservation.userId() != user.id() && user.role() != Role.ADMIN) {
      throw ProtocolException.forbidden(Admin.FORBIDDEN,
          "reserva " + reservation.id() + " e de outro usuario e \"" + user.name() + "\" nao e admin");
    }
  }

  private static void requireActive(Reservation reservation, LocalDateTime now) {
    if (reservation.isOverAt(now)) {
      throw ProtocolException.conflict(ALREADY_OVER,
          "reserva " + reservation.id() + " terminou em " + reservation.window().endsAt() + ", agora e " + now);
    }
  }

  private static JsonObject details(SqliteStore store, Reservation reservation, ZoneId zone) {
    Room room = store.findRoom(reservation.roomId()).orElseThrow(() -> dangling(reservation));
    User owner = store.findUserById(reservation.userId()).orElseThrow(() -> dangling(reservation));
    return ReservationViews.details(reservation, room.name(), owner.name(), zone);
  }

  private static long reservationId(Request request) {
    return Long.parseLong(request.required(Fields.RESERVATION_ID, Formats.ID));
  }

  private static Reservation findReservation(SqliteStore store, long id) {
    return store.findReservation(id).orElseThrow(
        () -> ProtocolException.notFound(RESERVATION_NOT_FOUND, "reservation_id " + id + " nao cadastrado"));
  }

  private static ProtocolException dangling(Reservation reservation) {
    return ProtocolException.internal("reserva " + reservation.id() + " aponta para sala " + reservation.roomId()
        + " ou usuario " + reservation.userId() + " inexistente");
  }
}
