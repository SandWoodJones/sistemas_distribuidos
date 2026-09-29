package reservas.server;

import com.google.gson.JsonObject;

// `op` como função honesta. Recebe JSON e não `Request`
@FunctionalInterface
public interface Handler {
  JsonObject handle(JsonObject request, ServerContext context);
}
