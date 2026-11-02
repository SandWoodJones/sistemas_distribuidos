package reservas.client;

import java.io.IOException;
import java.util.Optional;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Op;
import reservas.protocol.Status;
import reservas.protocol.StringArrays;

// Sessão do lado do cliente. Token vive em memória
public final class Session implements AutoCloseable {
  private final ProtocolClient client;

  private String token;
  private String role;

  public Session(ProtocolClient client) {
    this.client = client;
  }

  public boolean isLoggedIn() {
    return token != null;
  }

  public Optional<String> role() {
    return Optional.ofNullable(role);
  }

  public JsonObject register(String email, String user, String password) throws IOException {
    JsonObject request = request(Op.REGISTER);
    request.addProperty(Fields.EMAIL, email);
    request.addProperty(Fields.USER, user);
    request.addProperty(Fields.PASSWORD, password);

    return client.ask(request);
  }

  public JsonObject login(String email, String password) throws IOException {
    JsonObject request = request(Op.LOGIN);
    request.addProperty(Fields.EMAIL, email);
    request.addProperty(Fields.PASSWORD, password);

    JsonObject response = client.ask(request);
    if (succeeded(response)) {
      token = response.get(Fields.TOKEN).getAsString();
      role = response.get(Fields.ROLE).getAsString();
    }

    return response;
  }

  public JsonObject logout() throws IOException {
    JsonObject response = client.ask(authenticated(Op.LOGOUT));
    if (succeeded(response) || tokenAlreadyDead(response)) {
      forget();
    }

    return response;
  }

  public JsonObject readUser() throws IOException {
    return client.ask(authenticated(Op.READ_USER));
  }

  public JsonObject updateUser(String user, String password) throws IOException {
    JsonObject request = authenticated(Op.UPDATE_USER);
    request.addProperty(Fields.USER, user);
    request.addProperty(Fields.PASSWORD, password);

    return client.ask(request);
  }

  public JsonObject deleteUser(String password) throws IOException {
    JsonObject request = authenticated(Op.DELETE_USER);
    request.addProperty(Fields.PASSWORD, password);

    JsonObject response = client.ask(request);
    if (succeeded(response)) {
      forget();
    }

    return response;
  }

  public JsonObject adminListUsers() throws IOException {
    return client.ask(authenticated(Op.ADMIN_LIST_USERS));
  }

  public JsonObject adminReadUser(String target) throws IOException {
    return client.ask(targeting(Op.ADMIN_READ_USER, target));
  }

  public JsonObject adminUpdateUser(String target, String user, String password, String role) throws IOException {
    JsonObject request = targeting(Op.ADMIN_UPDATE_USER, target);
    request.addProperty(Fields.USER, user);
    request.addProperty(Fields.PASSWORD, password);
    request.addProperty(Fields.ROLE, role);

    return client.ask(request);
  }

  public JsonObject adminDeleteUser(String target) throws IOException {
    return client.ask(targeting(Op.ADMIN_DELETE_USER, target));
  }

  public JsonObject createRoom(RoomFields room) throws IOException {
    JsonObject request = authenticated(Op.CREATE_ROOM);
    addRoom(request, room);

    return client.ask(request);
  }

  public JsonObject listRooms(String date, String startTime, String endTime, String minCapacity) throws IOException {
    JsonObject request = authenticated(Op.LIST_ROOMS);
    addUnlessBlank(request, Fields.DATE, date);
    addUnlessBlank(request, Fields.START_TIME, startTime);
    addUnlessBlank(request, Fields.END_TIME, endTime);
    addUnlessBlank(request, Fields.MIN_CAPACITY, minCapacity);

    return client.ask(request);
  }

  public JsonObject readRoom(String roomId) throws IOException {
    return client.ask(onRoom(Op.READ_ROOM, roomId));
  }

  public JsonObject updateRoom(String roomId, RoomFields room, String roomStatus) throws IOException {
    JsonObject request = onRoom(Op.UPDATE_ROOM, roomId);
    addRoom(request, room);
    request.addProperty(Fields.ROOM_STATUS, roomStatus);

    return client.ask(request);
  }

  public JsonObject deleteRoom(String roomId) throws IOException {
    return client.ask(onRoom(Op.DELETE_ROOM, roomId));
  }

  @Override
  public void close() throws IOException {
    forget();
    client.close();
  }

  private void forget() {
    token = null;
    role = null;
  }

  private JsonObject request(Op op) {
    JsonObject request = new JsonObject();
    request.addProperty(Fields.OP, op.wireName());
    return request;
  }

  // Token ausente vai como `""`
  private JsonObject authenticated(Op op) {
    JsonObject request = request(op);
    request.addProperty(Fields.TOKEN, token == null ? "" : token);
    return request;
  }

  private JsonObject targeting(Op op, String target) {
    JsonObject request = authenticated(op);
    request.addProperty(Fields.TARGET_USER, target);
    return request;
  }

  private JsonObject onRoom(Op op, String roomId) {
    JsonObject request = authenticated(op);
    request.addProperty(Fields.ROOM_ID, roomId);
    return request;
  }

  private static void addRoom(JsonObject request, RoomFields room) {
    request.addProperty(Fields.NAME, room.name());
    request.addProperty(Fields.CAPACITY, room.capacity());
    request.addProperty(Fields.LOCATION, room.location());
    request.add(Fields.RESOURCES, StringArrays.toJson(room.resources()));
  }

  private static void addUnlessBlank(JsonObject request, String field, String value) {
    if (!value.isBlank()) {
      request.addProperty(field, value);
    }
  }

  private static boolean succeeded(JsonObject response) {
    return hasStatus(response, Status.OK);
  }

  private static boolean tokenAlreadyDead(JsonObject response) {
    return hasStatus(response, Status.UNAUTHORIZED);
  }

  private static boolean hasStatus(JsonObject response, Status status) {
    return status.code().equals(response.get(Fields.STATUS).getAsString());
  }
}
