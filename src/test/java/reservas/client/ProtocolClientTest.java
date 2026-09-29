package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import reservas.protocol.Fields;
import reservas.server.AccountHandlers;
import reservas.server.AuthHandlers;
import reservas.server.Dispatcher;
import reservas.server.RandomTokenGenerator;
import reservas.server.ServerSocketLoop;
import reservas.server.SqliteStore;

@Timeout(10)
class ProtocolClientTest {
  private static final Instant T0 = Instant.parse("2026-09-09T17:32:10Z");
  private static final String REGISTER = "{\"op\":\"register\",\"email\":\"joao.silva@email.com\",\"user\":\"joao\",\"password\":\"senha123\"}";

  private final List<String> traffic = new ArrayList<>();

  private SqliteStore store;
  private ServerSocketLoop server;
  private Thread accepting;

  @BeforeEach
  void startServer() throws IOException {
    store = SqliteStore.openInMemory();
    Dispatcher dispatcher = new Dispatcher(store, Clock.fixed(T0, ZoneId.of("America/Sao_Paulo")),
        new RandomTokenGenerator(), failure -> {
        });
    AuthHandlers.install(dispatcher);
    AccountHandlers.install(dispatcher);

    server = ServerSocketLoop.bind(0, dispatcher);
    accepting = new Thread(server::acceptForever, "accept-test");
    accepting.start();
  }

  @AfterEach
  void stopServer() throws InterruptedException {
    server.close();
    accepting.join(2000);
    store.close();
  }

  private ProtocolClient connect() throws IOException {
    return ProtocolClient.connect("localhost", server.port(), 2000, traffic::add);
  }

  private static JsonObject request(String json) {
    return JsonParser.parseString(json).getAsJsonObject();
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
          .ask(request("{\"op\":\"login\",\"email\":\"joao.silva@email.com\",\"password\":\"senha123\"}"));
      assertStatus("200", login);
      String token = login.get(Fields.TOKEN).getAsString();

      assertStatus("200", client.ask(request("{\"op\":\"read_user\",\"token\":\"" + token + "\"}")));
      assertStatus("200", client.ask(request("{\"op\":\"logout\",\"token\":\"" + token + "\"}")));
    }
  }

  @Test
  void recordsBothDirectionsOfTraffic() throws IOException {
    try (ProtocolClient client = connect()) {
      client.ask(request(REGISTER));
    }

    assertEquals(2, traffic.size(), traffic.toString());
    assertEquals("-> " + REGISTER, traffic.get(0));
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

      try (ProtocolClient client = ProtocolClient.connect("localhost", rude.getLocalPort(), 2000, traffic::add)) {
        IOException failure = assertThrows(IOException.class, () -> client.ask(request(REGISTER)));
        assertTrue(failure.getMessage().contains("sem responder"), failure.getMessage());
      }

      closing.join(2000);
    }
  }
}
