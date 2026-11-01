package reservas.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import reservas.protocol.Fields;

final class RoomViews {
  private RoomViews() {
  }

  static JsonObject details(Room room) {
    JsonArray resources = new JsonArray();
    room.resources().forEach(resources::add);

    JsonObject view = new JsonObject();
    view.addProperty(Fields.ROOM_ID, Long.toString(room.id()));
    view.addProperty(Fields.NAME, room.name());
    view.addProperty(Fields.CAPACITY, Integer.toString(room.capacity()));
    view.addProperty(Fields.LOCATION, room.location());
    view.add(Fields.RESOURCES, resources);
    view.addProperty(Fields.ROOM_STATUS, room.status().wireName());
    return view;
  }
}
