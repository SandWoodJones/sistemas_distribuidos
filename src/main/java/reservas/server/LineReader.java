package reservas.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import reservas.protocol.Codec;
import reservas.protocol.ProtocolException;

// Uma mensagem por linha, cortada no limite de bytes antes de interpretar
final class LineReader {
  private final InputStream bytes;

  LineReader(InputStream bytes) {
    this.bytes = bytes;
  }

  // Vazio quando o cliente fecha a conexão
  Optional<String> readLine() throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();

    for (int read = bytes.read(); read != -1; read = bytes.read()) {
      line.write(read);
      if (read == '\n') {
        return Optional.of(text(line));
      }

      // Exatamente no limite ainda vale
      if (line.size() == Codec.MAX_MESSAGE_BYTES) {
        throw tooLarge(line, discardToEndOfLine());
      }
    }

    return line.size() == 0 ? Optional.empty() : Optional.of(text(line));
  }

  private long discardToEndOfLine() throws IOException {
    long discarded = 0;
    for (int read = bytes.read(); read != -1 && read != '\n'; read = bytes.read()) {
      discarded++;
    }

    return discarded;
  }

  private static String text(ByteArrayOutputStream line) {
    return line.toString(StandardCharsets.UTF_8);
  }

  private static ProtocolException tooLarge(ByteArrayOutputStream line, long discarded) {
    return ProtocolException.badRequest(Codec.MESSAGE_TOO_LARGE,
        Codec.diagnosis(
            "linha passou de " + Codec.MAX_MESSAGE_BYTES + " bytes, mais " + discarded + " descartados ate o `\\n`",
            text(line)));
  }
}
