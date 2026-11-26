package reservas.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.gson.JsonParser;

import reservas.protocol.Codec;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;
import reservas.protocol.StringArrays;
import reservas.protocol.TimeWindow;

// Persistência, toda operação que pode conflitar decide o conflito aqui e devolve `Optional` vazio; quem chama traduz para 409.
// Guarda uma conexão pela vida inteira
public final class SqliteStore implements AutoCloseable {
  private final Database database;
  private final RoomLocks roomLocks = new RoomLocks();

  private SqliteStore(Database database) {
    this.database = database;
    for (String statement : Schema.STATEMENTS) {
      database.execute(statement);
    }
  }

  public static SqliteStore openFile(String path) {
    return new SqliteStore(Database.open("jdbc:sqlite:" + path));
  }

  // Banco descartável, morre com o `close`
  public static SqliteStore openInMemory() {
    return new SqliteStore(Database.open("jdbc:sqlite::memory:"));
  }

  public Optional<User> findUser(String name) {
    return database.queryOne("SELECT * FROM users WHERE name = ?", SqliteStore::toUser, name);
  }

  public Optional<User> findUserByEmail(String email) {
    return database.queryOne("SELECT * FROM users WHERE email = ?", SqliteStore::toUser, email);
  }

  public Optional<User> findUserById(long id) {
    return database.queryOne("SELECT * FROM users WHERE id = ?", SqliteStore::toUser, id);
  }

  public List<User> listUsers() {
    return database.queryList("SELECT * FROM users ORDER BY name", SqliteStore::toUser);
  }

  // Vazio se `name` ou `email` já estão cadastrados
  public synchronized Optional<User> createUser(String name, String email, String password, Role role,
      Instant createdAt) {
    if (findUser(name).isPresent() || findUserByEmail(email).isPresent()) {
      return Optional.empty();
    }

    long id = database.insert("INSERT INTO users (name, email, password, role, created_at) VALUES (?, ?, ?, ?, ?)",
        name, email, password, role.wireName(), createdAt.toString());
    return Optional.of(new User(id, name, email, password, role, createdAt));
  }

  // Vazio se o novo `name` já pertence a outro usuário
  public synchronized UpdateOutcome updateUser(long id, UserChanges changes) {
    User current = findUserById(id)
        .orElseThrow(() -> new IllegalStateException("atualizacao de usuario inexistente, id=" + id));
    User updated = current.with(changes);

    if (demotesLastAdmin(current, updated)) {
      return UpdateOutcome.LAST_ADMIN;
    }
    if (nameTakenByAnother(updated)) {
      return UpdateOutcome.NAME_TAKEN;
    }

    database.update("UPDATE users SET name = ?, password = ?, role = ? WHERE id = ?", updated.name(),
        updated.password(),
        updated.role().wireName(), id);

    return UpdateOutcome.UPDATED;
  }

  private boolean demotesLastAdmin(User current, User updated) {
    return current.role() == Role.ADMIN && updated.role() != Role.ADMIN && countAdmins() == 1;
  }

  private boolean nameTakenByAnother(User user) {
    Optional<User> sameName = findUser(user.name());
    return sameName.isPresent() && sameName.get().id() != user.id();
  }

  // A sessão e as reservas caem junto por `ON DELETE CASCADE`
  public void deleteUser(long id) {
    database.update("DELETE FROM users WHERE id = ?", id);
  }

  // `false` se `id` é o único admin
  public synchronized boolean deleteUserUnlessLastAdmin(long id) {
    if (isLastAdmin(id)) {
      return false;
    }

    deleteUser(id);
    return true;
  }

  private boolean isLastAdmin(long id) {
    boolean admin = findUserById(id).map(user -> user.role() == Role.ADMIN).orElse(false);
    return admin && countAdmins() == 1;
  }

  public int countAdmins() {
    return database.queryInt("SELECT COUNT(*) FROM users WHERE role = ?", Role.ADMIN.wireName());
  }

  public Optional<Session> findSession(String token) {
    return database.queryOne("SELECT * FROM sessions WHERE token = ?", SqliteStore::toSession, token);
  }

  public Optional<Session> findSessionOfUser(long userId) {
    return database.queryOne("SELECT * FROM sessions WHERE user_id = ?", SqliteStore::toSession, userId);
  }

  // Vazio se o usuário já possui sessão viva
  public synchronized Optional<Session> createSession(String token, long userId, Instant now) {
    Optional<Session> existing = findSessionOfUser(userId);
    if (existing.isPresent()) {
      if (!existing.get().isExpiredAt(now)) {
        return Optional.empty();
      }

      deleteSession(existing.get().token());
    }

    database.update("INSERT INTO sessions (token, user_id, last_used) VALUES (?, ?, ?)", token, userId, now.toString());
    return Optional.of(new Session(token, userId, now));
  }

  // Sessões que ainda não expiraram, por nome
  public List<LoggedInUser> listLoggedIn(Instant now) {
    return database.queryList("SELECT users.name, users.role, sessions.* FROM sessions "
        + "JOIN users ON users.id = sessions.user_id ORDER BY users.name", SqliteStore::toLoggedIn).stream()
        .filter(user -> !user.session().isExpiredAt(now)).toList();
  }

  public void deleteSession(String token) {
    database.update("DELETE FROM sessions WHERE token = ?", token);
  }

  // Devolve quantas sessões havia
  public int deleteAllSessions() {
    return database.update("DELETE FROM sessions");
  }

  // Renova o timeout de uma sessão
  public void touchSession(String token, Instant now) {
    database.update("UPDATE sessions SET last_used = ? WHERE token = ?", now.toString(), token);
  }

  public Optional<Room> findRoom(long id) {
    return database.queryOne("SELECT * FROM rooms WHERE id = ?", SqliteStore::toRoom, id);
  }

  public Optional<Room> findRoomByName(String name) {
    return database.queryOne("SELECT * FROM rooms WHERE name = ?", SqliteStore::toRoom, name);
  }

  public List<Room> listRooms(int minCapacity) {
    return database.queryList("SELECT * FROM rooms WHERE capacity >= ? ORDER BY id", SqliteStore::toRoom, minCapacity);
  }

  // Vazio se `name` já está cadastrado
  public synchronized Optional<Room> createRoom(String name, int capacity, String location, List<String> resources,
      List<Slot> slots) {
    if (findRoomByName(name).isPresent()) {
      return Optional.empty();
    }

    long id = database.insert(
        "INSERT INTO rooms (name, capacity, location, resources, status, slots) VALUES (?, ?, ?, ?, ?, ?)",
        name, capacity, location, toJson(resources), RoomStatus.ACTIVE.wireName(), slotsToJson(slots));
    return Optional.of(new Room(id, name, capacity, location, resources, RoomStatus.ACTIVE, slots));
  }

  // `false` se o novo `name` já pertence a outra sala
  public synchronized boolean updateRoom(long id, RoomChanges changes) {
    Room updated = findRoom(id)
        .orElseThrow(() -> new IllegalStateException("atualizacao de sala inexistente, id=" + id)).with(changes);

    Optional<Room> sameName = findRoomByName(updated.name());
    if (sameName.isPresent() && sameName.get().id() != id) {
      return false;
    }

    database.update(
        "UPDATE rooms SET name = ?, capacity = ?, location = ?, resources = ?, status = ?, slots = ? WHERE id = ?",
        updated.name(), updated.capacity(), updated.location(), toJson(updated.resources()),
        updated.status().wireName(), slotsToJson(updated.slots()), id);
    return true;
  }

  public void deleteRoom(long id) {
    database.update("DELETE FROM rooms WHERE id = ?", id);
  }

  // Checar e gravar na mesma sala
  public <T> T withRoomLock(long roomId, Supplier<T> work) {
    return roomLocks.holding(roomId, work);
  }

  // Troca de sala: as duas salas travadas, em ordem crescente de id
  public <T> T withRoomLocks(long roomId, long otherRoomId, Supplier<T> work) {
    return roomLocks.holdingBoth(roomId, otherRoomId, work);
  }

  public Optional<Reservation> findReservation(long id) {
    return database.queryOne("SELECT * FROM reservations WHERE id = ?", SqliteStore::toReservation, id);
  }

  public List<Reservation> listReservationsOn(long roomId, LocalDate date) {
    return database.queryList("SELECT * FROM reservations WHERE room_id = ? AND date = ? ORDER BY start_time",
        SqliteStore::toReservation, roomId, date.toString());
  }

  public List<Reservation> listReservations(ReservationFilter filter) {
    return database.queryList("SELECT * FROM reservations WHERE (?1 IS NULL OR user_id = ?1) "
        + "AND (?2 IS NULL OR date = ?2) AND (?3 IS NULL OR room_id = ?3) ORDER BY date, start_time, id",
        SqliteStore::toReservation, filter.userId(), Objects.toString(filter.date(), null), filter.roomId());
  }

  public Reservation createReservation(Booking booking, long userId, Instant createdAt) {
    TimeWindow window = booking.window();
    long id = database.insert(
        "INSERT INTO reservations (room_id, user_id, date, start_time, end_time, topic, participants, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        booking.roomId(), userId, window.date().toString(), window.start().toString(), window.end().toString(),
        booking.topic(), booking.participants(), createdAt.toString());
    return new Reservation(id, booking.roomId(), userId, window, booking.topic(), booking.participants(), createdAt);
  }

  public void updateReservation(Reservation reservation) {
    TimeWindow window = reservation.window();
    database.update("UPDATE reservations SET room_id = ?, date = ?, start_time = ?, end_time = ?, topic = ?, "
        + "participants = ? WHERE id = ?", reservation.roomId(), window.date().toString(), window.start().toString(),
        window.end().toString(), reservation.topic(), reservation.participants(), reservation.id());
  }

  public void deleteReservation(long id) {
    database.update("DELETE FROM reservations WHERE id = ?", id);
  }

  @Override
  public synchronized void close() {
    database.close();
  }

  private static User toUser(ResultSet row) throws SQLException {
    String role = row.getString("role");
    return new User(row.getLong("id"), row.getString("name"), row.getString("email"), row.getString("password"),
        Role.fromWire(role).orElseThrow(() -> corrupt("users.role", role)), Instant.parse(row.getString("created_at")));
  }

  private static Session toSession(ResultSet row) throws SQLException {
    return new Session(row.getString("token"), row.getLong("user_id"), Instant.parse(row.getString("last_used")));
  }

  private static LoggedInUser toLoggedIn(ResultSet row) throws SQLException {
    String role = row.getString("role");
    return new LoggedInUser(row.getString("name"), Role.fromWire(role).orElseThrow(() -> corrupt("users.role", role)),
        toSession(row));
  }

  private static Room toRoom(ResultSet row) throws SQLException {
    String status = row.getString("status");
    return new Room(row.getLong("id"), row.getString("name"), row.getInt("capacity"), row.getString("location"),
        fromJson(row.getString("resources")),
        RoomStatus.fromWire(status).orElseThrow(() -> corrupt("rooms.status", status)),
        slotsFromJson(row.getString("slots")));
  }

  private static Reservation toReservation(ResultSet row) throws SQLException {
    TimeWindow window = new TimeWindow(LocalDate.parse(row.getString("date")),
        LocalTime.parse(row.getString("start_time")), LocalTime.parse(row.getString("end_time")));
    return new Reservation(row.getLong("id"), row.getLong("room_id"), row.getLong("user_id"), window,
        row.getString("topic"), row.getInt("participants"), Instant.parse(row.getString("created_at")));
  }

  private static String slotsToJson(List<Slot> slots) {
    return toJson(slots.stream().map(Slot::wireName).toList());
  }

  private static String toJson(List<String> values) {
    return StringArrays.toJson(values).toString();
  }

  private static List<Slot> slotsFromJson(String text) {
    return fromJson(text).stream()
        .map(wireName -> Slot.fromWire(wireName).orElseThrow(() -> corrupt("rooms.slots", wireName))).toList();
  }

  private static List<String> fromJson(String text) {
    return StringArrays.fromJson(JsonParser.parseString(text).getAsJsonArray());
  }

  private static ProtocolException corrupt(String column, String value) {
    return ProtocolException
        .internal("valor invalido gravado em " + column + ": " + Codec.snippet(String.valueOf(value)));
  }
}
