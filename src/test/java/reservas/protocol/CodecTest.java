package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertFails;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class CodecTest {
  @Test
  void encodesOneLineThatDecodesBack() {
    JsonObject message = new JsonObject();
    message.addProperty(Fields.OP, "login_response");
    message.addProperty(Fields.MESSAGE, "linha1\nlinha2");

    String line = Codec.encode(message);
    assertEquals("{\"op\":\"login_response\",\"message\":\"linha1\\nlinha2\"}\n", line);
    assertEquals(message, Codec.decode(line));
  }

  @Test
  void rejectsAnythingButOneStrictJsonObject() {
    for (String line : new String[] { "", "   ", "{", "{\"op\":}", "nao e json", "[]", "\"login\"", "42", "null",
        "{\"op\":\"login\"}{\"op\":\"logout\"}", "{op:\"login\"}", "{'op':'login'}" }) {
      ProtocolException failure = assertFails(Status.BAD_REQUEST, "Requisicao invalida", () -> Codec.decode(line));
      assertEquals(Status.BAD_REQUEST, failure.status(), line);
      assertEquals("Requisicao invalida", failure.wireMessage(), line);
      assertTrue(failure.getMessage().contains("bruto=" + Codec.snippet(line)), failure.getMessage());
    }

    assertNotNull(assertThrows(ProtocolException.class, () -> Codec.decode("{op:\"login\"}")).getCause());
  }

  @Test
  void refusesToEncodeAnOversizedResponse() {
    JsonObject huge = new JsonObject();
    huge.addProperty(Fields.MESSAGE, "ç".repeat(4100));

    assertFails(Status.INTERNAL_SERVER_ERROR, "Erro interno do servidor", () -> Codec.encode(huge));
  }
}
