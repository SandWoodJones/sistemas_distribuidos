package reservas.server;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    dispatcher.register(Op.CHECK_AVAILABILITY, ReservationHandlers::checkAvailability);
    dispatcher.register(Op.CREATE_RESERVATION, ReservationHandlers::createReservation);
    dispatcher.register(Op.LIST_RESERVATIONS, ReservationHandlers::listReservations);
    dispatcher.register(Op.READ_RESERVATION, ReservationHandlers::readReservation);
    dispatcher.register(Op.UPDATE_RESERVATION, ReservationHandlers::updateReservation);
    dispatcher.register(Op.DELETE_RESERVATION, ReservationHandlers::deleteReservation);
  }

  static JsonObject checkAvailability(JsonObject json, ServerContext context) {
    Request request = new Request(json, Messages.DATA_BAD_REQUEST);
    long roomId = RoomHandlers.roomId(request);
    TimeWindow window = request.window();

    Caller.authenticate(request, context);
    SqliteStore store = context.store();
    Room room = RoomHandlers.findRoom(store, roomId);

    boolean available = RoomHandlers.isFree(store, room, window);
    return Responses.found(Op.CHECK_AVAILABILITY, ReservationViews.availability(room.id(), window, available));
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

  // Sem `scope` vale "mine"; "all" só para admin
  static JsonObject listReservations(JsonObject json, ServerContext context) {
    Request request = new Request(json, RoomHandlers.FILTERS_BAD_REQUEST);
    boolean all = request.filter(Fields.SCOPE, Formats.SCOPE).map(Fields.SCOPE_ALL::equals).orElse(false);
    LocalDate date = request.dateFilter().orElse(null);
    Long roomId = request.filter(Fields.ROOM_ID, Formats.ID).map(Long::valueOf).orElse(null);

    Caller caller = Caller.authenticate(request, context);
    if (all) {
      caller.requireAdmin();
    }

    SqliteStore store = context.store();
    ReservationFilter filter = new ReservationFilter(all ? null : caller.user().id(), date, roomId);
    List<JsonObject> reservations = store.listReservations(filter).stream()
        .map(reservation -> summary(store, reservation)).toList();

    return Responses.listing(Op.LIST_RESERVATIONS, Fields.RESERVATIONS, reservations);
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

  static JsonObject updateReservation(JsonObject json, ServerContext context) {
    Request request = new Request(json, RESERVATION_BAD_REQUEST);
    long id = reservationId(request);
    ReservationChanges changes = changes(request);

    User user = Caller.authenticate(request, context).user();
    SqliteStore store = context.store();
    long roomId = findReservation(store, id).roomId();
    LocalDateTime now = LocalDateTime.now(context.clock());
    store.withRoomLock(roomId, () -> reschedule(store, user, id, changes, now));

    return Responses.of(Op.UPDATE_RESERVATION, Status.OK, "Reserva atualizada com sucesso");
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

  // `room_id` e `date` não mudam; para trocar, cancela-se e cria-se outra
  private static ReservationChanges changes(Request request) {
    request.mustBeAbsent(Fields.ROOM_ID);
    request.mustBeAbsent(Fields.DATE);
    LocalTime start = request.optional(Fields.START_TIME, Formats.START_TIME).map(LocalTime::parse).orElse(null);
    LocalTime end = request.optional(Fields.END_TIME, Formats.END_TIME).map(LocalTime::parse).orElse(null);
    String topic = request.optional(Fields.TOPIC, Formats.TOPIC).orElse(null);
    Integer participants = request.optional(Fields.PARTICIPANTS, Formats.CAPACITY).map(Integer::valueOf).orElse(null);

    return new ReservationChanges(start, end, topic, participants);
  }

  // Roda sob o lock da sala da reserva
  private static Reservation reschedule(SqliteStore store, User user, long id, ReservationChanges changes,
      LocalDateTime now) {
    Reservation current = findReservation(store, id);
    requireOwner(user, current);
    requireActive(current, now);

    Room room = room(store, current);
    Reservation updated = changed(current, changes, now);
    requireFits(room, updated.participants());
    if (changes.movesTime()) {
      requireBookable(room, updated.window());
      requireFree(othersOn(store, current), updated.window());
    }

    store.updateReservation(updated);
    return updated;
  }

  // Horário resultante com fim antes do início, ou início novo que já passou
  private static Reservation changed(Reservation current, ReservationChanges changes, LocalDateTime now) {
    Reservation updated;
    try {
      updated = current.with(changes);
    } catch (IllegalArgumentException e) {
      throw ProtocolException.badRequest(RESERVATION_BAD_REQUEST, "campo 'end_time': " + e.getMessage(), e);
    }

    if (changes.start() != null && !updated.window().startsAt().isAfter(now)) {
      throw ProtocolException.badRequest(RESERVATION_BAD_REQUEST,
          "campo 'start_time': inicio " + updated.window().startsAt() + " ja passou, agora e " + now);
    }

    return updated;
  }

  private static List<Reservation> othersOn(SqliteStore store, Reservation reservation) {
    return store.listReservationsOn(reservation.roomId(), reservation.window().date()).stream()
        .filter(other -> other.id() != reservation.id()).toList();
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
    if (!reservation.isOpenTo(user)) {
      throw ProtocolException.forbidden(Admin.FORBIDDEN,
          "reserva " + reservation.id() + " e de outro usuario e \"" + user.name() + "\" nao e admin");
    }
  }

  // Nem o admin altera reserva de outro
  private static void requireOwner(User user, Reservation reservation) {
    if (reservation.userId() != user.id()) {
      throw ProtocolException.forbidden(Admin.FORBIDDEN,
          "reserva " + reservation.id() + " e de outro usuario; so o dono altera, mesmo sendo admin");
    }
  }

  private static void requireActive(Reservation reservation, LocalDateTime now) {
    if (reservation.isOverAt(now)) {
      throw ProtocolException.conflict(ALREADY_OVER,
          "reserva " + reservation.id() + " terminou em " + reservation.window().endsAt() + ", agora e " + now);
    }
  }

  private static JsonObject summary(SqliteStore store, Reservation reservation) {
    return ReservationViews.summary(reservation, room(store, reservation).name(), owner(store, reservation).name());
  }

  private static JsonObject details(SqliteStore store, Reservation reservation, ZoneId zone) {
    return ReservationViews.details(reservation, room(store, reservation).name(), owner(store, reservation).name(),
        zone);
  }

  private static Room room(SqliteStore store, Reservation reservation) {
    return store.findRoom(reservation.roomId()).orElseThrow(() -> dangling(reservation));
  }

  static User owner(SqliteStore store, Reservation reservation) {
    return store.findUserById(reservation.userId()).orElseThrow(() -> dangling(reservation));
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
