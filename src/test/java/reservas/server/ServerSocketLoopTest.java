package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static reservas.protocol.ProtocolAssertions.assertResponse;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.LOGIN_JOAO;
import static reservas.server.Fixtures.REGISTER_JOAO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Codec;
import reservas.protocol.Fields;

@Timeout(10)
class ServerSocketLoopTest {
  private TestServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = TestServer.start();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private Client connect() throws IOException {
    return new Client(server.port());
  }

  @Test
  void servesTwoClientsAtOnceEachOnItsOwnConnection() throws IOException {
    try (Client first = connect(); Client second = connect()) {
      assertStatus("201", first.ask(REGISTER_JOAO));
      assertStatus("201", second.ask(
          "{'op':'register','email':'maria@email.com','user':'maria','password':'senha123'}"));

      String one = first.ask(LOGIN_JOAO).get(Fields.TOKEN).getAsString();
      String two = second.ask("{'op':'login','email':'maria@email.com','password':'senha123'}")
          .get(Fields.TOKEN).getAsString();
      assertNotEquals(one, two);

      assertEquals("joao", first.ask("{'op':'read_user','token':'" + one + "'}").get(Fields.USER).getAsString());
      assertEquals("maria", second.ask("{'op':'read_user','token':'" + two + "'}").get(Fields.USER).getAsString());
      assertStatus("200", first.ask("{'op':'logout','token':'" + one + "'}"));
      assertStatus("200", second.ask("{'op':'logout','token':'" + two + "'}"));
    }
  }

  @Test
  void answersBadLinesAndKeepsTheConnection() throws IOException {
    try (Client client = connect()) {
      JsonObject oversized = client.ask("{'op':'login','password':'}" + "x".repeat(9000) + "'}");
      assertResponse("error", "400", "Mensagem excede o tamanho maximo", oversized);

      JsonObject malformed = client.ask("{'op':'login',");
      assertResponse("error", "400", "Requisicao invalida", malformed);

      assertStatus("201", client.ask(REGISTER_JOAO));
    }
  }

  // Um cliente TCP que fala uma linha e le uma linha
  private static final class Client implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;

    Client(int port) throws IOException {
      socket = new Socket("localhost", port);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    // A linha vai crua para o socket, entao a aspa simples vira dupla aqui
    JsonObject ask(String line) throws IOException {
      out.write(TestJson.json(line) + "\n");
      out.flush();
      return Codec.decode(in.readLine());
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }
}
