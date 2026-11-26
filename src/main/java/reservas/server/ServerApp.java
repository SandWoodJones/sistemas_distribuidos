package reservas.server;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.BindException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import javax.swing.SwingUtilities;

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

    if (GraphicsEnvironment.isHeadless()) {
      serveFromTerminal(port);
    } else {
      openWindow(port);
    }
  }

  // Sem tela: escuta na porta do argumento até o processo ser morto
  private static void serveFromTerminal(int port) {
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
      prepare(store, clock.instant());

      try (ServerSocketLoop server = start(port, store, clock)) {
        server.acceptForever();
      }
    }
  }

  // A janela escolhe a porta; o argumento só preenche o campo
  private static void openWindow(int port) {
    Clock clock = Clock.system(ZONE);
    SqliteStore store = SqliteStore.openFile(DATABASE_FILE);
    prepare(store, clock.instant());

    SwingUtilities.invokeLater(() -> new ServerWindow(store, clock, port).setVisible(true));
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
    ReservationHandlers.install(dispatcher);
  }

  private static void report(ProtocolException failure) {
    Diagnostics.report(log, failure);
  }

  private static void prepare(SqliteStore store, Instant now) {
    int ended = Bootstrap.endPreviousSessions(store);
    if (ended > 0) {
      log.info("{} sessao(oes) da execucao anterior encerrada(s)", ended);
    }

    seedAdmin(store, now);
    if (Bootstrap.seedDemo(store, now)) {
      log.info("pre-cadastro: {} laboratorios com todos os horarios e {} usuarios ({} a {}, senha {})",
          Bootstrap.DEMO_LABS, Bootstrap.DEMO_USERS, Bootstrap.demoUserName(0),
          Bootstrap.demoUserName(Bootstrap.DEMO_USERS - 1), Bootstrap.DEMO_PASSWORD);
    }
  }

  private static void seedAdmin(SqliteStore store, Instant now) {
    Bootstrap.seedAdmin(store, now)
        .ifPresent(admin -> log.info("administrador inicial criado: user {}, email {}, senha {}", admin.name(),
            admin.email(), admin.password()));

    if (store.countAdmins() == 0) {
      log.warn(
          "administrador inicial nao criado: ja existe conta comum com user {} ou email {}; o servidor esta sem administrador ate que {} seja apagado",
          Bootstrap.ADMIN_USER, Bootstrap.ADMIN_EMAIL, DATABASE_FILE);
    }
  }

  private static int port(String[] args) {
    return args.length == 0 ? DEFAULT_PORT : parsePort(args[0]);
  }

  static int parsePort(String text) {
    int port;
    try {
      port = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw invalidPort(text);
    }

    if (port < 1 || port > 65535) {
      throw invalidPort(text);
    }

    return port;
  }

  private static IllegalArgumentException invalidPort(String given) {
    return new IllegalArgumentException("porta invalida \"" + given + "\", esperado numero de 1 a 65535");
  }
}
