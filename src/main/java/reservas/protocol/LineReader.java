package reservas.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// Uma mensagem por linha, cortada no limite de bytes antes de interpretar
public final class LineReader {
  private static final int NO_LIMIT = Integer.MAX_VALUE;

  private final InputStream bytes;
  private final int maxBytes;

  private LineReader(InputStream bytes, int maxBytes) {
    this.bytes = bytes;
    this.maxBytes = maxBytes;
  }

  public static LineReader requests(InputStream bytes) {
    return new LineReader(bytes, Codec.MAX_REQUEST_BYTES);
  }

  public static LineReader responses(InputStream bytes) {
    return new LineReader(bytes, NO_LIMIT);
  }

  // Vazio quando o cliente fecha a conexão
  public Optional<String> readLine() throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();

    for (int read = bytes.read(); read != -1; read = bytes.read()) {
      line.write(read);
      if (read == '\n') {
        return Optional.of(text(line));
      }

      // Exatamente no limite ainda vale
      if (line.size() == maxBytes) {
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

  private ProtocolException tooLarge(ByteArrayOutputStream line, long discarded) {
    return ProtocolException.badRequest(Messages.MESSAGE_TOO_LARGE,
        Codec.diagnosis(
            "linha passou de " + maxBytes + " bytes, mais " + discarded + " descartados ate o `\\n`",
            text(line)));
  }
}
