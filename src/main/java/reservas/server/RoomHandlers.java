package reservas.server;

import java.util.List;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Formats;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;
import reservas.protocol.Responses;
import reservas.protocol.RoomStatus;
import reservas.protocol.Status;

public final class RoomHandlers {
  static final String ROOM_BAD_REQUEST = "Dados da sala em formato invalido";
  static final String FILTERS_BAD_REQUEST = "Filtros em formato invalido";
  static final String ID_BAD_REQUEST = "Dados em formato invalido";
  static final String ROOM_NOT_FOUND = "Sala nao encontrada";
  static final String NAME_IN_USE = "Sala ja cadastrada";

  private static final int ANY_CAPACITY = 1;

  private RoomHandlers() {
  }

  public static void install(Dispatcher dispatcher) {
    dispatcher.register(Op.CREATE_ROOM, RoomHandlers::createRoom);
    dispatcher.register(Op.LIST_ROOMS, RoomHandlers::listRooms);
    dispatcher.register(Op.READ_ROOM, RoomHandlers::readRoom);
    dispatcher.register(Op.UPDATE_ROOM, RoomHandlers::updateRoom);
    dispatcher.register(Op.DELETE_ROOM, RoomHandlers::deleteRoom);
  }

  static JsonObject createRoom(JsonObject json, ServerContext context) {
    Request request = new Request(json, ROOM_BAD_REQUEST);
    String name = request.required(Fields.NAME, Formats.ROOM_NAME);
    int capacity = Integer.parseInt(request.required(Fields.CAPACITY, Formats.CAPACITY));
    String location = request.required(Fields.LOCATION, Formats.LOCATION);
    List<String> resources = request.requiredStrings(Fields.RESOURCES);

    Caller.authenticate(request, context).requireAdmin();
    Room room = context.store().createRoom(name, capacity, location, resources).orElseThrow(() -> nameInUse(name));

    JsonObject response = Responses.of(Op.CREATE_ROOM, Status.CREATED, "Sala cadastrada com sucesso");
    response.addProperty(Fields.ROOM_ID, Long.toString(room.id()));
    return response;
  }

  static JsonObject listRooms(JsonObject json, ServerContext context) {
    Request request = new Request(json, FILTERS_BAD_REQUEST);
    request.optional(Fields.DATE, Formats.DATE);
    request.optional(Fields.START_TIME, Formats.TIME);
    request.optional(Fields.END_TIME, Formats.TIME);
    boolean withSlot = request.allOrNone(Fields.DATE, Fields.START_TIME, Fields.END_TIME);
    int minCapacity = request.optional(Fields.MIN_CAPACITY, Formats.CAPACITY).map(Integer::parseInt)
        .orElse(ANY_CAPACITY);

    Caller.authenticate(request, context);
    List<JsonObject> rooms = context.store().listRooms(minCapacity).stream()
        .map(room -> withSlot ? withAvailability(room) : RoomViews.details(room)).toList();

    return Responses.listing(Op.LIST_ROOMS, Fields.ROOMS, rooms);
  }

  static JsonObject readRoom(JsonObject json, ServerContext context) {
    Request request = new Request(json, ID_BAD_REQUEST);
    long id = roomId(request);

    Caller.authenticate(request, context);
    Room room = findRoom(context.store(), id);

    return Responses.found(Op.READ_ROOM, RoomViews.details(room));
  }

  static JsonObject updateRoom(JsonObject json, ServerContext context) {
    Request request = new Request(json, ROOM_BAD_REQUEST);
    long id = roomId(request);
    RoomChanges changes = changes(request);

    Caller.authenticate(request, context).requireAdmin();

    SqliteStore store = context.store();
    findRoom(store, id);
    if (!store.updateRoom(id, changes)) {
      throw nameInUse(changes.name());
    }

    return Responses.of(Op.UPDATE_ROOM, Status.OK, "Sala atualizada com sucesso");
  }

  // Sala com reservas futuras é 409
  static JsonObject deleteRoom(JsonObject json, ServerContext context) {
    Request request = new Request(json, ID_BAD_REQUEST);
    long id = roomId(request);

    Caller.authenticate(request, context).requireAdmin();

    SqliteStore store = context.store();
    findRoom(store, id);
    store.deleteRoom(id);
    return Responses.of(Op.DELETE_ROOM, Status.OK, "Sala removida com sucesso");
  }

  private static JsonObject withAvailability(Room room) {
    JsonObject view = RoomViews.details(room);
    view.addProperty(Fields.AVAILABLE, Boolean.toString(room.status() == RoomStatus.ACTIVE));
    return view;
  }

  private static RoomChanges changes(Request request) {
    String name = request.optional(Fields.NAME, Formats.ROOM_NAME).orElse(null);
    Integer capacity = request.optional(Fields.CAPACITY, Formats.CAPACITY).map(Integer::valueOf).orElse(null);
    String location = request.optional(Fields.LOCATION, Formats.LOCATION).orElse(null);
    List<String> resources = request.optionalStrings(Fields.RESOURCES).orElse(null);
    RoomStatus status = request.optional(Fields.ROOM_STATUS, Formats.ROOM_STATUS).flatMap(RoomStatus::fromWire)
        .orElse(null);

    return new RoomChanges(name, capacity, location, resources, status);
  }

  private static long roomId(Request request) {
    return Long.parseLong(request.required(Fields.ROOM_ID, Formats.ID));
  }

  private static Room findRoom(SqliteStore store, long id) {
    return store.findRoom(id)
        .orElseThrow(() -> ProtocolException.notFound(ROOM_NOT_FOUND, "room_id " + id + " nao cadastrado"));
  }

  private static ProtocolException nameInUse(String name) {
    return ProtocolException.conflict(NAME_IN_USE, "name \"" + name + "\" ja pertence a outra sala");
  }
}
