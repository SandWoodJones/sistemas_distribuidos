package reservas.client;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import reservas.protocol.Codec;
import reservas.protocol.Fields;
import reservas.protocol.LineReader;

// Uma conexão com o servidor
public final class ProtocolClient implements AutoCloseable {
  private final Socket socket;
  private final LineReader lines;
  private final Writer out;
  private final Consumer<String> traffic;

  private ProtocolClient(Socket socket, Consumer<String> traffic) throws IOException {
    this.socket = socket;
    this.traffic = traffic;
    this.lines = new LineReader(new BufferedInputStream(socket.getInputStream()));
    this.out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
  }

  // `traffic` recebe os bytes crus dos dois lados, é exibido pela janela
  public static ProtocolClient connect(String host, int port, int timeoutMillis, Consumer<String> traffic)
      throws IOException {
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), timeoutMillis);
    socket.setSoTimeout(timeoutMillis);

    return new ProtocolClient(socket, traffic);
  }

  public JsonObject ask(JsonObject request) throws IOException {
    String line = Codec.encode(request);
    traffic.accept("-> " + line.strip());
    out.write(line);
    out.flush();

    String answer = lines.readLine().orElseThrow(() -> closedWithoutAnswering(request));
    traffic.accept("<- " + answer.strip());
    return Codec.decode(answer);
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }

  // O servidor nunca deve fechar sem responder uma requisição recebida por
  // inteiro
  private static IOException closedWithoutAnswering(JsonObject request) {
    return new IOException("servidor fechou a conexao sem responder a " + request.get(Fields.OP));
  }
}
