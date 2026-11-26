package reservas.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Aceita conexões e entrega cada uma a uma thread
public final class ServerSocketLoop implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(ServerSocketLoop.class);

  private final ServerSocket socket;
  private final Dispatcher dispatcher;
  private final ExecutorService connections = Executors.newCachedThreadPool();
  private final AtomicLong nextId = new AtomicLong(1);
  private final AtomicInteger open = new AtomicInteger();

  private ServerSocketLoop(ServerSocket socket, Dispatcher dispatcher) {
    this.socket = socket;
    this.dispatcher = dispatcher;
  }

  // Porta 0 pede uma porta livre ao sistema e `port()` diz qual foi dada
  public static ServerSocketLoop bind(int port, Dispatcher dispatcher) throws IOException {
    return new ServerSocketLoop(new ServerSocket(port), dispatcher);
  }

  public int port() {
    return socket.getLocalPort();
  }

  public void acceptForever() {
    log.info("servidor escutando na porta {}", port());

    while (!socket.isClosed()) {
      try {
        Socket client = socket.accept();
        Connection connection = new Connection(client, dispatcher, "c" + nextId.getAndIncrement());
        connections.execute(() -> counted(connection));
      } catch (IOException e) {
        if (!socket.isClosed()) {
          log.warn("falha aceitando conexao: {}", e.getMessage(), e);
        }
      }
    }

    log.info("servidor parou de escutar na porta {}", port());
  }

  // Conexões que ainda não terminaram, para a janela do servidor
  public int openConnections() {
    return open.get();
  }

  private void counted(Connection connection) {
    open.incrementAndGet();
    try {
      connection.run();
    } finally {
      open.decrementAndGet();
    }
  }

  @Override
  public void close() {
    try {
      socket.close();
    } catch (IOException e) {
      log.warn("falha fechando o socket de escuta: {}", e.getMessage(), e);
    }

    connections.shutdown();
  }
}
