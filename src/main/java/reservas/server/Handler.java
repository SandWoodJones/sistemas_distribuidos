package reservas.server;

import java.time.Clock;

import com.google.gson.JsonObject;

// `op` como função honesta. Recebe JSON e não `Request`
@FunctionalInterface
public interface Handler {
  JsonObject handle(JsonObject request, SqliteStore store, Clock clock, TokenGenerator tokens);
}
