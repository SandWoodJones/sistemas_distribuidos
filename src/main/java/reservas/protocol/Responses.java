package reservas.protocol;

import com.google.gson.JsonObject;

// Construtores do envelope de resposta
public final class Responses {
  private Responses() {
  }

  public static JsonObject of(Op op, Status status, String message) {
    return envelope(op.responseName(), status, message);
  }

  public static JsonObject of(Op op, ProtocolException failure) {
    return of(op, failure.status(), failure.wireMessage());
  }

  // O `op` `error` usado quando nenhum `op` de requisição é determinado
  public static JsonObject error(Status status, String message) {
    return envelope(Op.ERROR_OP, status, message);
  }

  public static JsonObject error(ProtocolException failure) {
    return error(failure.status(), failure.wireMessage());
  }

  private static JsonObject envelope(String op, Status status, String message) {
    JsonObject response = new JsonObject();
    response.addProperty(Fields.OP, op);
    response.addProperty(Fields.STATUS, status.code());
    response.addProperty(Fields.MESSAGE, message);
    return response;
  }
}
