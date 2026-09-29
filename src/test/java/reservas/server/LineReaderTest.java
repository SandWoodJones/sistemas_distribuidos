package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import reservas.protocol.Codec;
import reservas.protocol.ProtocolException;
import reservas.protocol.Status;

class LineReaderTest {
  private static final String LOGIN = "{\"op\":\"login\"}";

  private static LineReader reader(String text) {
    return new LineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void readsOneLineWithItsTerminator() throws IOException {
    assertEquals(Optional.of(LOGIN + "\n"), reader(LOGIN + "\n").readLine());
  }

  @Test
  void readsSeveralLinesInSequence() throws IOException {
    LineReader lines = reader(LOGIN + "\n{\"op\":\"logout\"}\n");

    assertEquals(Optional.of(LOGIN + "\n"), lines.readLine());
    assertEquals(Optional.of("{\"op\":\"logout\"}\n"), lines.readLine());
    assertEquals(Optional.empty(), lines.readLine());
  }

  @Test
  void answersEmptyWhenTheClientCloses() throws IOException {
    assertEquals(Optional.empty(), reader("").readLine());
  }

  @Test
  void returnsATrailingLineWithoutTerminator() throws IOException {
    assertEquals(Optional.of(LOGIN), reader(LOGIN).readLine());
  }

  @Test
  void acceptsALineExactlyAtTheLimit() throws IOException {
    String line = "a".repeat(Codec.MAX_MESSAGE_BYTES - 1) + "\n";

    assertEquals(Codec.MAX_MESSAGE_BYTES, Codec.sizeInBytes(line));
    assertEquals(Optional.of(line), reader(line).readLine());
  }

  @Test
  void rejectsALineOneByteOverTheLimit() {
    ProtocolException failure = assertThrows(ProtocolException.class,
        () -> reader("a".repeat(Codec.MAX_MESSAGE_BYTES) + "\n").readLine());

    assertEquals(Status.BAD_REQUEST, failure.status());
    assertEquals("Mensagem excede o tamanho maximo", failure.wireMessage());
  }

  @Test
  void countsBytesAndNotCharacters() throws IOException {
    assertEquals(Optional.of("é".repeat(4095) + "\n"), reader("é".repeat(4095) + "\n").readLine());
    assertThrows(ProtocolException.class, () -> reader("é".repeat(4096) + "\n").readLine());
  }

  // O resto de uma linha grande não vira a mensagem seguinte
  @Test
  void resyncsOnTheNextLineAfterAnOversizedOne() throws IOException {
    LineReader lines = reader("a".repeat(9000) + "\n" + LOGIN + "\n");

    ProtocolException failure = assertThrows(ProtocolException.class, lines::readLine);
    assertTrue(failure.getMessage().contains("descartados"), failure.getMessage());
    assertEquals(Optional.of(LOGIN + "\n"), lines.readLine());
  }

  @Test
  void diagnosisQuotesTheStartOfTheLine() {
    ProtocolException failure = assertThrows(ProtocolException.class,
        () -> reader("{\"op\":\"login\",\"password\":\"" + "x".repeat(9000) + "\"}\n").readLine());

    assertTrue(failure.getMessage().contains("bruto={\"op\":\"login\""), failure.getMessage());
  }
}
