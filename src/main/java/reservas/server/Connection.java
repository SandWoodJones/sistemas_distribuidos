package reservas.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.google.gson.JsonObject;

import reservas.protocol.Codec;
import reservas.protocol.ProtocolException;
import reservas.protocol.Responses;
import reservas.protocol.Status;

// Uma conexão de cliente
final class Connection implements Runnable {
  // Fecha o socket sem invalidar o token
  static final int IDLE_TIMEOUT_MILLIS = 300_000;

  private static final Logger log = LoggerFactory.getLogger(Connection.class);

  private final Socket socket;
  private final Dispatcher dispatcher;
  private final String id;

  Connection(Socket socket, Dispatcher dispatcher, String id) {
    this.socket = socket;
    this.dispatcher = dispatcher;
    this.id = id;
  }

  @Override
  public void run() {
    MDC.put("conn", id);
    MDC.put("peer", String.valueOf(socket.getRemoteSocketAddress()));

    try (Socket open = socket) {
      open.setSoTimeout(IDLE_TIMEOUT_MILLIS);
      log.info("conexao aberta");
      serve(open);
    } catch (SocketTimeoutException e) {
      log.info("conexao fechada por {} ms sem trafego, o token continua valido", IDLE_TIMEOUT_MILLIS);
    } catch (IOException e) {
      log.warn("conexao encerrada por falha de socket: {}", e.getMessage(), e);
    } finally {
      MDC.clear();
    }
  }

  private void serve(Socket open) throws IOException {
    LineReader lines = new LineReader(new BufferedInputStream(open.getInputStream()));
    Writer out = new OutputStreamWriter(open.getOutputStream(), StandardCharsets.UTF_8);

    while (exchange(lines, out)) {
      // ...
    }
  }

  // `false` quando o cliente fecha
  private boolean exchange(LineReader lines, Writer out) throws IOException {
    JsonObject response;

    try {
      Optional<String> line = lines.readLine();
      if (line.isEmpty()) {
        log.info("cliente fechou a conexao");
        return false;
      }

      log.info("recebido {}", line.get().strip());
      response = dispatcher.handle(Codec.decode(line.get()));
    } catch (ProtocolException e) {
      // Linha acima do limite ou JSON invalido
      response = report(e);
    } catch (RuntimeException e) {
      log.error("falha inesperada tratando a linha", e);
      response = Responses.error(Status.INTERNAL_SERVER_ERROR, Codec.INTERNAL_ERROR);
    }

    send(out, response);
    return true;
  }

  private static void send(Writer out, JsonObject response) throws IOException {
    String line = encode(response);
    log.info("enviado {}", line.strip());
    out.write(line);
    out.flush();
  }

  private static String encode(JsonObject response) {
    try {
      return Codec.encode(response);
    } catch (ProtocolException e) {
      log.error(e.getMessage(), e);
      return Codec.encode(Responses.error(Status.INTERNAL_SERVER_ERROR, Codec.INTERNAL_ERROR));
    }
  }

  private static JsonObject report(ProtocolException failure) {
    log.warn(failure.getMessage(), failure);
    return Responses.error(failure);
  }
}
