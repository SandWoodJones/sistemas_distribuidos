package reservas.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Optional;

import reservas.protocol.Codec;
import reservas.protocol.ProtocolException;

final class Database implements AutoCloseable {
  private final String url;
  private final Connection connection;

  private Database(String url, Connection connection) {
    this.url = url;
    this.connection = connection;
  }

  static Database open(String url) {
    try {
      Database db = new Database(url, DriverManager.getConnection(url));
      db.execute("PRAGMA foreign_keys = ON");
      return db;
    } catch (SQLException e) {
      throw failure("abrindo " + url, new Object[0], e);
    }
  }

  void execute(String sql) {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw failure(sql, new Object[0], e);
    }
  }

  <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... values) {
    try (PreparedStatement statement = prepare(sql, values); ResultSet rows = statement.executeQuery()) {
      return rows.next() ? Optional.of(mapper.map(rows)) : Optional.empty();
    } catch (SQLException e) {
      throw failure(sql, values, e);
    }
  }

  int queryInt(String sql, Object... values) {
    try (PreparedStatement statement = prepare(sql, values); ResultSet rows = statement.executeQuery()) {
      rows.next();
      return rows.getInt(1);
    } catch (SQLException e) {
      throw failure(sql, values, e);
    }
  }

  // Devolve a chave gerada por `AUTOINCREMENT`
  long insert(String sql, Object... values) {
    try (PreparedStatement statement = prepare(sql, values, Statement.RETURN_GENERATED_KEYS)) {
      statement.executeUpdate();
      try (ResultSet keys = statement.getGeneratedKeys()) {
        keys.next();
        return keys.getLong(1);
      }
    } catch (SQLException e) {
      throw failure(sql, values, e);
    }
  }

  // Devolve quantas linhas mudaram
  int update(String sql, Object... values) {
    try (PreparedStatement statement = prepare(sql, values)) {
      return statement.executeUpdate();
    } catch (SQLException e) {
      throw failure(sql, values, e);
    }
  }

  @Override
  public void close() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw failure("fechando " + url, new Object[0], e);
    }
  }

  private PreparedStatement prepare(String sql, Object[] values, int generatedKeys) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql, generatedKeys);
    for (int i = 0; i < values.length; i++) {
      statement.setObject(i + 1, values[i]);
    }

    return statement;
  }

  private PreparedStatement prepare(String sql, Object[] values) throws SQLException {
    return prepare(sql, values, Statement.NO_GENERATED_KEYS);
  }

  private static ProtocolException failure(String sql, Object[] values, SQLException cause) {
    return ProtocolException.internal(
        Codec.snippet(sql.strip()) + " " + Arrays.toString(values) + "; SQLException(errorCode=" + cause.getErrorCode()
            + ", sqlState=" + cause.getSQLState() + "): " + cause.getMessage(),
        cause);
  }

  @FunctionalInterface
  interface RowMapper<T> {
    T map(ResultSet row) throws SQLException;
  }
}
