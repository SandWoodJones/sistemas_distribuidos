package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Fields;
import reservas.server.TestServer;

@Timeout(10)
class ProtocolClientTest {
  private static final String REGISTER = "{'op':'register','email':'joao.silva@email.com','user':'joao','password':'senha123'}";

  private TestServer server;
  private final List<String> traffic = new ArrayList<>();

  @BeforeEach
  void startServer() throws IOException {
    server = TestServer.start();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private ProtocolClient connect() throws IOException {
    return TestClients.connect(server.port(), traffic::add);
  }

  private static JsonObject request(String json) {
    return TestJson.object(json);
  }

  private static void assertStatus(String status, JsonObject response) {
    assertEquals(status, response.get(Fields.STATUS).getAsString(), response.toString());
  }

  @Test
  void roundTripsARequest() throws IOException {
    try (ProtocolClient client = connect()) {
      JsonObject response = client.ask(request(REGISTER));

      assertEquals("register_response", response.get(Fields.OP).getAsString());
      assertStatus("201", response);
    }
  }

  // A conexao serve a sessao inteira
  @Test
  void carriesAWholeSessionOnOneConnection() throws IOException {
    try (ProtocolClient client = connect()) {
      assertStatus("201", client.ask(request(REGISTER)));

      JsonObject login = client
          .ask(request("{'op':'login','email':'joao.silva@email.com','password':'senha123'}"));
      assertStatus("200", login);
      String token = login.get(Fields.TOKEN).getAsString();

      assertStatus("200", client.ask(request("{'op':'read_user','token':'" + token + "'}")));
      assertStatus("200", client.ask(request("{'op':'logout','token':'" + token + "'}")));
    }
  }

  @Test
  void recordsBothDirectionsOfTraffic() throws IOException {
    try (ProtocolClient client = connect()) {
      client.ask(request(REGISTER));
    }

    assertEquals(2, traffic.size(), traffic.toString());
    assertEquals("-> " + TestJson.json(REGISTER), traffic.get(0));
    assertTrue(traffic.get(1).startsWith("<- {\"op\":\"register_response\""), traffic.get(1));
  }

  @Test
  void reportsAServerThatClosesWithoutAnswering() throws IOException, InterruptedException {
    try (ServerSocket rude = new ServerSocket(0)) {
      Thread closing = new Thread(() -> {
        try (Socket accepted = rude.accept()) {
          accepted.getInputStream().read();
        } catch (IOException ignored) {
          // Ignorado
        }
      });

      closing.start();

      try (ProtocolClient client = TestClients.connect(rude.getLocalPort(), traffic::add)) {
        IOException failure = assertThrows(IOException.class, () -> client.ask(request(REGISTER)));
        assertTrue(failure.getMessage().contains("sem responder"), failure.getMessage());
      }

      closing.join(2000);
    }
  }
}
