package reservas.server;

import java.io.IOException;

public final class TestServer implements AutoCloseable {
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
    ServerSocketLoop server = ServerApp.start(0, store, Fixtures.clockAt(Fixtures.T0));
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
