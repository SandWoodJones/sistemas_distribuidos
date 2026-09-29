package reservas.server;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reservas.protocol.ProtocolException;

// Ponto de entrada
public final class ServerApp {
  // Rede fala na hora local do servidor
  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  public static final int DEFAULT_PORT = 5000;

  private static final String DATABASE_FILE = "reservas.db";
  private static final Logger log = LoggerFactory.getLogger(ServerApp.class);

  private ServerApp() {
  }

  public static void main(String[] args) throws IOException {
    Clock clock = Clock.system(ZONE);

    try (SqliteStore store = SqliteStore.openFile(DATABASE_FILE)) {
      seedAdmin(store, clock.instant());

      try (ServerSocketLoop server = start(port(args), store, clock)) {
        server.acceptForever();
      }
    }
  }

  public static ServerSocketLoop start(int port, SqliteStore store, Clock clock) throws IOException {
    Dispatcher dispatcher = new Dispatcher(store, clock, new RandomTokenGenerator(), ServerApp::report);
    AuthHandlers.install(dispatcher);
    AccountHandlers.install(dispatcher);

    return ServerSocketLoop.bind(port, dispatcher);
  }

  private static void report(ProtocolException failure) {
    log.warn(failure.getMessage(), failure);
  }

  private static void seedAdmin(SqliteStore store, Instant now) {
    Bootstrap.seedAdmin(store, now)
        .ifPresent(admin -> log.info("administrador inicial criado: user {}, email {}", admin.name(), admin.email()));
  }

  private static int port(String[] args) {
    if (args.length == 0) {
      return DEFAULT_PORT;
    }

    try {
      return Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("porta invalida \"" + args[0] + "\", esperado numero de 1 a 65535", e);
    }
  }
}
