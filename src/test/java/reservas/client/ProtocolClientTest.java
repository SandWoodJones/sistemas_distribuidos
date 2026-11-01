package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.REGISTER_JOAO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Fields;
import reservas.server.TestServer;

@Timeout(10)
class ProtocolClientTest {
  private final List<String> traffic = new ArrayList<>();

  @Test
  void answersARequestAndRecordsBothDirections() throws IOException {
    try (TestServer server = TestServer.start();
        ProtocolClient client = TestClients.connect(server.port(), traffic::add)) {
      JsonObject response = client.ask(TestJson.object(REGISTER_JOAO));

      assertEquals("register_response", response.get(Fields.OP).getAsString());
      assertStatus("201", response);
    }

    assertEquals(2, traffic.size(), traffic.toString());
    assertEquals("-> " + TestJson.json(REGISTER_JOAO), traffic.get(0));
    assertTrue(traffic.get(1).startsWith("<- {\"op\":\"register_response\""), traffic.get(1));
  }

  @Test
  void reportsAServerThatClosesWithoutAnswering() throws IOException, InterruptedException {
    try (ServerSocket rude = new ServerSocket(0)) {
      Thread closing = new Thread(() -> {
        try (Socket accepted = rude.accept()) {
          accepted.getInputStream().read();
        } catch (IOException ignored) {

        }
      });

      closing.start();

      try (ProtocolClient client = TestClients.connect(rude.getLocalPort(), traffic::add)) {
        IOException failure = assertThrows(IOException.class, () -> client.ask(TestJson.object(REGISTER_JOAO)));
        assertTrue(failure.getMessage().contains("sem responder"), failure.getMessage());
      }

      closing.join(2000);
    }
  }
}
