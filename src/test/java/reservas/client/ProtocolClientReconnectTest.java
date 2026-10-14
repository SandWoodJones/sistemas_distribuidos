package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;

@Timeout(10)
class ProtocolClientReconnectTest {
  private static final String ANSWER = "{\"op\":\"read_user_response\",\"status\":\"200\",\"message\":\"ok\"}\n";

  @Test
  void reconnectsAndResendsWHenTheServerClosedTheConnection() throws IOException {
    try (ServerSocket listening = new ServerSocket(0)) {

      Thread serving = new Thread(() -> answerThenClose(listening, 2), "fake-server");
      serving.setDaemon(true);
      serving.start();

      List<String> traffic = new ArrayList<>();
      try (ProtocolClient client = TestClients.connect(listening.getLocalPort(), traffic::add)) {
        assertEquals("200", status(client.ask(readUser())));
        assertEquals("200", status(client.ask(readUser())));
      }

      assertTrue(traffic.stream().anyMatch(line -> line.contains("reconectando")), traffic.toString());
    }
  }

  // Responde e fecha
  private static void answerThenClose(ServerSocket listening, int connections) {
    for (int i = 0; i < connections; i++) {
      try (Socket client = listening.accept()) {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        Writer out = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8);

        in.readLine();
        out.write(ANSWER);
        out.flush();
      } catch (IOException stop) {
        return;
      }
    }
  }

  private static JsonObject readUser() {
    JsonObject request = new JsonObject();
    request.addProperty(Fields.OP, "read_user");
    request.addProperty(Fields.TOKEN, "a".repeat(64));
    return request;
  }

  private static String status(JsonObject response) {
    return response.get(Fields.STATUS).getAsString();
  }
}
