package reservas.server;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;

// Campos de um usuário nas respostas
final class UserViews {
  private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private UserViews() {

  }

  static JsonObject summary(User user) {
    JsonObject view = new JsonObject();

    view.addProperty(Fields.USER, user.name());
    view.addProperty(Fields.EMAIL, user.email());
    view.addProperty(Fields.ROLE, user.role().wireName());

    return view;
  }

  static void addDetails(JsonObject response, User user, ZoneId zone) {
    summary(user).entrySet().forEach(field -> response.add(field.getKey(), field.getValue()));
    response.addProperty(Fields.CREATED_AT, CREATED_AT.withZone(zone).format(user.createdAt()));
  }
}
