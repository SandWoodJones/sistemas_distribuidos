package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

  private static String register(String user, String email) {
    return "{'op':'register','email':'" + email + "','user':'" + user + "','password':'senha123'}";
  }

  private static void assertStatus(String status, JsonObject response) {
    assertEquals(status, response.get(Fields.STATUS).getAsString(), response.toString());
  }

  // A conexão fica aberta entre requisições
  @Test
  void answersEveryRequestOnTheSameConnection() throws IOException {
    try (Client client = connect()) {
      assertStatus("201", client.ask(register("joao", "joao.silva@email.com")));

      JsonObject login = client.ask("{'op':'login','email':'joao.silva@email.com','password':'senha123'}");
      assertStatus("200", login);
      String token = login.get(Fields.TOKEN).getAsString();

      assertStatus("200", client.ask("{'op':'read_user','token':'" + token + "'}"));
      assertStatus("200", client.ask("{'op':'logout','token':'" + token + "'}"));
    }
  }

  // O resto da linha grande nao vira a mensagem seguinte
  @Test
  void answersAnOversizedLineAndKeepsGoing() throws IOException {
    try (Client client = connect()) {
      JsonObject rejected = client.ask("{'op':'login','password':'" + "x".repeat(9000) + "'}");
      assertEquals("error", rejected.get(Fields.OP).getAsString());
      assertStatus("400", rejected);
      assertEquals("Mensagem excede o tamanho maximo", rejected.get(Fields.MESSAGE).getAsString());

      assertStatus("201", client.ask(register("joao", "joao.silva@email.com")));
    }
  }

  @Test
  void answersMalformedJsonWithoutClosing() throws IOException {
    try (Client client = connect()) {
      JsonObject rejected = client.ask("{'op':'login',");
      assertEquals("error", rejected.get(Fields.OP).getAsString());
      assertStatus("400", rejected);
      assertEquals("Requisicao invalida", rejected.get(Fields.MESSAGE).getAsString());

      assertStatus("201", client.ask(register("joao", "joao.silva@email.com")));
    }
  }

  // Uma thread por conexao, as duas atendidas ao mesmo tempo
  @Test
  void servesTwoClientsAtOnce() throws IOException {
    try (Client first = connect(); Client second = connect()) {
      assertStatus("201", first.ask(register("joao", "joao.silva@email.com")));
      assertStatus("201", second.ask(register("maria", "maria@email.com")));

      String one = first.ask("{'op':'login','email':'joao.silva@email.com','password':'senha123'}")
          .get(Fields.TOKEN).getAsString();
      String two = second.ask("{'op':'login','email':'maria@email.com','password':'senha123'}")
          .get(Fields.TOKEN).getAsString();

      assertNotEquals(one, two);
      assertStatus("200", first.ask("{'op':'read_user','token':'" + one + "'}"));
      assertStatus("200", second.ask("{'op':'read_user','token':'" + two + "'}"));
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
