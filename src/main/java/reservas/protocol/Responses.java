package reservas.protocol;

import com.google.gson.JsonObject;

// Construtores do envelope de resposta
public final class Responses {
  private Responses() {
  }

  public static JsonObject of(Op op, Status status, String message) {
    return envelope(op.responseName(), status, message);
  }

  // O `op` `error` usado quando nenhum `op` de requisição é determinado
  public static JsonObject error(Status status, String message) {
    return envelope(Op.ERROR_OP, status, message);
  }

  // A linha não gera um objeto JSON válido
  public static JsonObject invalidRequest() {
    return error(Status.BAD_REQUEST, Codec.INVALID_REQUEST);
  }

  // O `op` é bem formado, mas não nomeia operação implementada
  public static JsonObject unknownOperation() {
    return error(Status.BAD_REQUEST, Codec.UNKNOWN_OPERATION);
  }

  // A mensagem excedeu `Codec.MAX_MESSAGE_BYTES`
  public static JsonObject messageTooLarge() {
    return error(Status.BAD_REQUEST, Codec.MESSAGE_TOO_LARGE);
  }

  private static JsonObject envelope(String op, Status status, String message) {
    JsonObject response = new JsonObject();
    response.addProperty(Fields.OP, op);
    response.addProperty(Fields.STATUS, status.code());
    response.addProperty(Fields.MESSAGE, message);
    return response;
  }
}
