package reservas.server;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import reservas.protocol.Codec;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;

// Persistência, toda operação que pode conflitar decide o conflito aqui e devolve `Optional` vazio; quem chama traduz para 409.
// Guarda uma conexão pela vida inteira
public final class SqliteStore implements AutoCloseable {
  private final Database database;

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
  public synchronized Optional<User> updateUser(User user) {
    Optional<User> sameName = findUser(user.name());
    if (sameName.isPresent() && sameName.get().id() != user.id()) {
      return Optional.empty();
    }

    int changed = database.update("UPDATE users SET name = ?, password = ? WHERE id = ?", user.name(), user.password(),
        user.id());
    if (changed == 0) {
      throw new IllegalStateException("atualizacao de usuario inexistente, id=" + user.id());
    }

    return Optional.of(user);
  }

  // A sessão cai junto por `ON DELETE CASCADE`
  public void deleteUser(long id) {
    database.update("DELETE FROM users WHERE id = ?", id);
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

  public void deleteSession(String token) {
    database.update("DELETE FROM sessions WHERE token = ?", token);
  }

  // Renova o timeout de uma sessão
  public void touchSession(String token, Instant now) {
    database.update("UPDATE sessions SET last_used = ? WHERE token = ?", now.toString(), token);
  }

  @Override
  public synchronized void close() {
    database.close();
  }

  private static User toUser(ResultSet row) throws SQLException {
    String role = row.getString("role");
    return new User(row.getLong("id"), row.getString("name"), row.getString("email"), row.getString("password"),
        Role.fromWire(role).orElseThrow(() -> corrupt("role", role)), Instant.parse(row.getString("created_at")));
  }

  private static Session toSession(ResultSet row) throws SQLException {
    return new Session(row.getString("token"), row.getLong("user_id"), Instant.parse(row.getString("last_used")));
  }

  private static ProtocolException corrupt(String column, String value) {
    return ProtocolException
        .internal("valor invalido gravado em users." + column + ": " + Codec.snippet(String.valueOf(value)));
  }
}
