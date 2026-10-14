package reservas.client;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import reservas.protocol.Codec;
import reservas.protocol.Fields;
import reservas.protocol.LineReader;

// Uma conexão com o servidor
public final class ProtocolClient implements AutoCloseable {
  private final String host;
  private final int port;
  private final int timeoutMillis;
  private final Consumer<String> traffic;

  private Socket socket;
  private LineReader lines;
  private Writer out;
  private boolean used;

  private ProtocolClient(String host, int port, int timeoutMillis, Consumer<String> traffic) throws IOException {
    this.host = host;
    this.port = port;
    this.timeoutMillis = timeoutMillis;
    this.traffic = traffic;
    open();
  }

  // `traffic` recebe os bytes crus dos dois lados, é exibido pela janela
  public static ProtocolClient connect(String host, int port, int timeoutMillis, Consumer<String> traffic)
      throws IOException {
    return new ProtocolClient(host, port, timeoutMillis, traffic);
  }

  public JsonObject ask(JsonObject request) throws IOException {
    try {
      return exchange(request);
    } catch (SocketTimeoutException waiting) {
      // servidor ainda pode responder
      throw waiting;
    } catch (IOException dropped) {
      if (!used) {
        throw dropped;
      }

      return resend(request, dropped);
    }
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }

  private JsonObject exchange(JsonObject request) throws IOException {
    String line = Codec.encode(request);
    traffic.accept("-> " + line.strip());
    out.write(line);
    out.flush();

    String answer = lines.readLine().orElseThrow(() -> closedWithoutAnswering(request));
    used = true;
    traffic.accept("<- " + answer.strip());
    return Codec.decode(answer);
  }

  private JsonObject resend(JsonObject request, IOException dropped) throws IOException {
    traffic.accept("** conexao perdida (" + dropped.getMessage() + "), reconectando e reenviando");
    closeBroken();

    try {
      open();
      return exchange(request);
    } catch (IOException again) {
      again.addSuppressed(dropped);
      throw again;
    }
  }

  private void open() throws IOException {
    Socket fresh = new Socket();
    fresh.connect(new InetSocketAddress(host, port), timeoutMillis);
    fresh.setSoTimeout(timeoutMillis);

    socket = fresh;
    lines = new LineReader(new BufferedInputStream(fresh.getInputStream()));
    out = new OutputStreamWriter(fresh.getOutputStream(), StandardCharsets.UTF_8);
    used = false;
  }

  private void closeBroken() {
    try {
      socket.close();
    } catch (IOException failed) {
      traffic.accept("** falha ao fechar o socket anterior: " + failed);
    }
  }

  // O servidor nunca deve fechar sem responder uma requisição recebida por
  // inteiro
  private static IOException closedWithoutAnswering(JsonObject request) {
    return new IOException("servidor fechou a conexao sem responder a " + request.get(Fields.OP));
  }
}
