package reservas.protocol;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

// Construtores do envelope de resposta
public final class Responses {
  private static final String QUERY_OK = "Consulta realizada com sucesso";

  private Responses() {
  }

  public static JsonObject of(Op op, Status status, String message) {
    return envelope(op.responseName(), status, message);
  }

  public static JsonObject of(Op op, ProtocolException failure) {
    return of(op, failure.status(), failure.wireMessage());
  }

  public static JsonObject found(Op op, JsonObject record) {
    JsonObject response = of(op, Status.OK, QUERY_OK);
    record.entrySet().forEach(field -> response.add(field.getKey(), field.getValue()));
    return response;
  }

  public static JsonObject listing(Op op, String key, List<JsonObject> items) {
   JsonArray array = new JsonArray();
   items.forEach(array::add);

   JsonObject response = of(op, Status.OK, QUERY_OK);
   response.addProperty(Fields.COUNT, Integer.toString(items.size()));
   response.add(key, array);
   return response;
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
