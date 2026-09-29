package reservas.client;

import java.io.IOException;
import java.util.function.Consumer;

final class TestClients {
  private static final int TIMEOUT_MILLIS = 2000;

  private TestClients() {}

  static ProtocolClient connect(int port, Consumer<String> traffic) throws IOException {
    return ProtocolClient.connect("localhost", port, TIMEOUT_MILLIS, traffic);
  }
}
