package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class CodecTest {
  private static JsonObject login() {
    JsonObject request = new JsonObject();
    request.addProperty(Fields.OP, Op.LOGIN.wireName());
    request.addProperty(Fields.EMAIL, "joao.silva@email.com");
    ;
    request.addProperty(Fields.PASSWORD, "senha123");
    return request;
  }

  @Test
  void encodesOneLineTerminatedByNewLine() {
    String line = Codec.encode(login());
    assertTrue(line.endsWith("\n"));
    assertEquals(1, line.chars().filter(c -> c == '\n').count(), "mensagem tem de ser uma linha");
    assertEquals("{\"op\":\"login\",\"email\":\"joao.silva@email.com\",\"password\":\"senha123\"}\n", line);
  }

  // Um `\n` dentro de um valor é escapado e não quebra nada
  @Test
  void escapesNewlineInsideValues() {
    JsonObject message = new JsonObject();
    message.addProperty(Fields.MESSAGE, "linha1\nlinha2");
    String line = Codec.encode(message);
    assertEquals(1, line.chars().filter(c -> c == '\n').count());
    assertEquals("linha1\nlinha2", Codec.decode(line).get(Fields.MESSAGE).getAsString());
  }

  @Test
  void decodesWithOrWithoutTrailingNewline() {
    assertEquals(login(), Codec.decode(Codec.encode(login())));
    assertEquals(login(), Codec.decode(login().toString()));
  }

  // campos desconhecidos são preservados
  @Test
  void keepsUnknownFields() {
    JsonObject decoded = Codec.decode("{\"op\":\"login\",\"campo_novo\":\"x\"}");
    assertEquals("x", decoded.get("campo_novo").getAsString());
  }

  @Test
  void rejectsMalformedJson() {
    for (String line : new String[] { "", "   ", "{", "{\"op\":}", "nao e json" }) {
      ProtocolException thrown = assertThrows(ProtocolException.class, () -> Codec.decode(line));
      assertEquals(Status.BAD_REQUEST, thrown.status());
      assertEquals(Messages.INVALID_REQUEST, thrown.wireMessage());
    }
  }

  @Test
  void rejectsJsonThatIsNotAnObject() {
    for (String line : new String[] { "[]", "\"login\"", "42", "null" }) {
      assertThrows(ProtocolException.class, () -> Codec.decode(line));
    }
  }

  @Test
  void rejectsTrailingContentAfterTheObject() {
    assertThrows(ProtocolException.class, () -> Codec.decode("{\"op\":\"login\"}{\"op\":\"logout\"}"));
  }

  // Chaves sem aspas ou com aspas simples não são JSON válido
  @Test
  void rejectsLenientJson() {
    assertThrows(ProtocolException.class, () -> Codec.decode("{op:\"login\"}"));
    assertThrows(ProtocolException.class, () -> Codec.decode("{'op':'login'}"));
  }

  @Test
  void acceptsExactlyTheSizeLimit() {
    String line = "{\"a\":\"" + "x".repeat(8184) + "\"}";
    assertEquals(Codec.MAX_MESSAGE_BYTES, Codec.sizeInBytes(line));
    assertEquals("x".repeat(8184), Codec.decode(line).get("a").getAsString());
  }

  @Test
  void rejectsOneByteOverTheLimit() {
    String line = "{\"a\":\"" + "x".repeat(8185) + "\"}";
    ProtocolException thrown = assertThrows(ProtocolException.class, () -> Codec.decode(line));
    assertEquals(Status.BAD_REQUEST, thrown.status());
    assertEquals(Messages.MESSAGE_TOO_LARGE, thrown.wireMessage());
  }

  @Test
  void encodingAnOversizedMessageIsAnInternalError() {
    JsonObject huge = new JsonObject();
    huge.addProperty(Fields.MESSAGE, "x".repeat(Codec.MAX_MESSAGE_BYTES));
    ProtocolException thrown = assertThrows(ProtocolException.class, () -> Codec.encode(huge));
    assertEquals(Status.INTERNAL_SERVER_ERROR, thrown.status());
  }

  // Limite é medido em bytes UTF-8, não em caracteres
  @Test
  void sizeIsCountedInUtf8Bytes() {
    assertEquals(4, "ação".length());
    assertEquals(6, Codec.sizeInBytes("ação"));
  }

  // O diagnóstico local tem de dizer o que quebrou
  @Test
  void diagnosisKeepsCauseAndRawLine() {
    ProtocolException thrown = assertThrows(ProtocolException.class, () -> Codec.decode("{op:\"login\"}"));
    assertEquals(Messages.INVALID_REQUEST, thrown.wireMessage());
    assertTrue(thrown.getMessage().contains("{op:"), "diagnostico deve trazer a linha bruta");
    assertNotNull(thrown.getCause());
  }
}
