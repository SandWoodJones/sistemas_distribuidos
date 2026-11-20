package reservas.server;

import java.time.Instant;
import java.util.Optional;

import reservas.protocol.Role;

public final class Bootstrap {
  public static final String ADMIN_USER = "admin";
  public static final String ADMIN_EMAIL = "admin@admin.com";
  public static final String ADMIN_PASSWORD = "admin";

  private Bootstrap() {
  }

  // Vazio se já existe algum administrador
  public static Optional<User> seedAdmin(SqliteStore store, Instant now) {
    if (store.countAdmins() > 0) {
      return Optional.empty();
    }

    return store.createUser(ADMIN_USER, ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN, now);
  }

  // Os tokens da execução anterior morreram com os clientes; sem isso, o próximo login dá 409 por até 30 min
  public static int endPreviousSessions(SqliteStore store) {
    return store.deleteAllSessions();
  }
}
