package reservas.server;

import java.io.IOException;
import java.net.BindException;
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

  public static void main(String[] args) {
    int port;

    try {
      port = port(args);
    } catch (IllegalArgumentException e) {
      fail(e.getMessage());
      return;
    }

    try {
      serve(port);
    } catch (BindException e) {
      fail("porta " + port + " ja esta em uso ou nao pode ser aberta: " + e.getMessage());
    } catch (IOException | RuntimeException e) {
      // Falha inesperada: stack trace fica no log
      log.error("servidor encerrado por falha inesperada", e);
      System.exit(1);
    }
  }

  private static void serve(int port) throws IOException {
    Clock clock = Clock.system(ZONE);

    try (SqliteStore store = SqliteStore.openFile(DATABASE_FILE)) {
      seedAdmin(store, clock.instant());

      try (ServerSocketLoop server = start(port, store, clock)) {
        server.acceptForever();
      }
    }
  }

  private static void fail(String reason) {
    log.error(reason);
    System.exit(1);
  }

  public static ServerSocketLoop start(int port, SqliteStore store, Clock clock) throws IOException {
    Dispatcher dispatcher = new Dispatcher(store, clock, new RandomTokenGenerator(), ServerApp::report);
    installHandlers(dispatcher);

    return ServerSocketLoop.bind(port, dispatcher);
  }

  static void installHandlers(Dispatcher dispatcher) {
    AuthHandlers.install(dispatcher);
    AccountHandlers.install(dispatcher);
    AdminHandlers.install(dispatcher);
    RoomHandlers.install(dispatcher);
  }

  private static void report(ProtocolException failure) {
    Diagnostics.report(log, failure);
  }

  private static void seedAdmin(SqliteStore store, Instant now) {
    Bootstrap.seedAdmin(store, now)
        .ifPresent(admin -> log.info("administrador inicial criado: user {}, email {}, senha {}", admin.name(),
            admin.email(), admin.password()));
  }

  private static int port(String[] args) {
    if (args.length == 0) {
      return DEFAULT_PORT;
    }

    int port;
    try {
      port = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      throw invalidPort(args[0]);
    }

    if (port < 1 || port > 65535) {
      throw invalidPort(args[0]);
    }

    return port;
  }

  private static IllegalArgumentException invalidPort(String given) {
    return new IllegalArgumentException("porta invalida \"" + given + "\", esperado numero de 1 a 65535");
  }
}
