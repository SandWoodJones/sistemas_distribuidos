package reservas.server;

// DDL do banco
final class Schema {
  private Schema() {
  }

  static final String[] STATEMENTS = {
      """
      CREATE TABLE IF NOT EXISTS users (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        name       TEXT NOT NULL UNIQUE,
        email      TEXT NOT NULL UNIQUE,
        password   TEXT NOT NULL,
        role       TEXT NOT NULL,
        created_at TEXT NOT NULL
      )
      """,
      // `user_id UNIQUE` é a regra "uma sessão por usuário"
      """
      CREATE TABLE IF NOT EXISTS sessions (
        token     TEXT PRIMARY KEY,
        user_id   INTEGER NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
        last_used TEXT NOT NULL
      )
      """
  };
}
