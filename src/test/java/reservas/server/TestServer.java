package reservas.server;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public final class TestServer implements AutoCloseable {
  public static final Instant T0 = Instant.parse("2026-09-09T17:32:10Z");

  private final SqliteStore store;
  private final ServerSocketLoop server;
  private final Thread accepting;

  private TestServer(SqliteStore store, ServerSocketLoop server, Thread accepting) {
    this.store = store;
    this.server = server;
    this.accepting = accepting;
  }

  public static TestServer start() throws IOException {
    SqliteStore store = SqliteStore.openInMemory();
    Dispatcher dispatcher = new Dispatcher(store, Clock.fixed(T0, ZoneId.of("America/Sao_Paulo")),
        new RandomTokenGenerator(), failure -> {
        });

    AuthHandlers.install(dispatcher);
    AccountHandlers.install(dispatcher);

    ServerSocketLoop server = ServerSocketLoop.bind(0, dispatcher);
    Thread accepting = new Thread(server::acceptForever, "accept-test");
    accepting.start();

    return new TestServer(store, server, accepting);
  }

  public int port() {
    return server.port();
  }

  public SqliteStore store() {
    return store;
  }

  @Override
  public void close() {
    server.close();

    try {
      accepting.join(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    store.close();
  }
}
