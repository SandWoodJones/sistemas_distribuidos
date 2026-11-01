package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.function.Executable;

import com.google.gson.JsonObject;

public final class ProtocolAssertions {
  private ProtocolAssertions() {
  }

  public static ProtocolException assertFails(Status status, String wireMessage, Executable call) {
    ProtocolException failure = assertThrows(ProtocolException.class, call);
    assertEquals(status, failure.status(), failure.getMessage());
    assertEquals(wireMessage, failure.wireMessage(), failure.getMessage());
    return failure;
  }

  public static void assertStatus(String status, JsonObject response) {
    assertEquals(status, response.get(Fields.STATUS).getAsString(), response.toString());
  }

  public static void assertResponse(String op, String status, String message, JsonObject response) {
    assertEquals(op, response.get(Fields.OP).getAsString(), response.toString());
    assertStatus(status, response);
    assertEquals(message, response.get(Fields.MESSAGE).getAsString(), response.toString());
  }

  public static void assertKeys(JsonObject response, String... keys) {
    assertEquals(List.of(keys), List.copyOf(response.keySet()), response.toString());
  }
}
