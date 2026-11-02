package reservas.server;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.StringArrays;

final class RoomViews {
  private RoomViews() {
  }

  static JsonObject details(Room room) {
    JsonObject view = new JsonObject();
    view.addProperty(Fields.ROOM_ID, Long.toString(room.id()));
    view.addProperty(Fields.NAME, room.name());
    view.addProperty(Fields.CAPACITY, Integer.toString(room.capacity()));
    view.addProperty(Fields.LOCATION, room.location());
    view.add(Fields.RESOURCES, StringArrays.toJson(room.resources()));
    view.addProperty(Fields.ROOM_STATUS, room.status().wireName());
    return view;
  }
}
