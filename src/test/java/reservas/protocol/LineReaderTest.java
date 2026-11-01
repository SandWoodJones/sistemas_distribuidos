package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertFails;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class LineReaderTest {
  private static final String LOGIN = "{\"op\":\"login\"}";

  private static LineReader reader(String text) {
    return new LineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void limitIs8192BytesIncludingTheNewline() throws IOException {
    String atLimit = "a".repeat(Codec.MAX_MESSAGE_BYTES - 1) + "\n";
    assertEquals(Optional.of(atLimit), reader(atLimit).readLine());

    assertFails(Status.BAD_REQUEST, "Mensagem excede o tamanho maximo",
        () -> reader("a".repeat(Codec.MAX_MESSAGE_BYTES) + "\n").readLine());

    assertEquals(Optional.of("é".repeat(4095) + "\n"), reader("é".repeat(4095) + "\n").readLine());
    assertThrows(ProtocolException.class, () -> reader("é".repeat(4096) + "\n").readLine());
  }

  @Test
  void resyncsOnTheNextLineAfterAnOversizedOne() throws IOException {
    String oversized = "{\"op\":\"login\",\"password\":\"" + "x".repeat(9000) + "\"}";
    LineReader lines = reader(oversized + "\n" + LOGIN + "\n");

    ProtocolException failure = assertThrows(ProtocolException.class, lines::readLine);
    assertTrue(failure.getMessage().contains("descartados"), failure.getMessage());
    assertTrue(failure.getMessage().contains("bruto={\"op\":\"login\""), failure.getMessage());
    assertEquals(Optional.of(LOGIN + "\n"), lines.readLine());
  }

  @Test
  void returnsATrailingLineWithoutTerminatorThenEmpty() throws IOException {
    LineReader lines = reader(LOGIN);

    assertEquals(Optional.of(LOGIN), lines.readLine());
    assertEquals(Optional.empty(), lines.readLine());
  }
}
