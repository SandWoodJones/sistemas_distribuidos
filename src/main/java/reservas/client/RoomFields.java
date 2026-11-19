package reservas.client;

import java.util.List;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.StringArrays;

public record RoomFields(String name, String capacity, String location, List<String> resources, List<String> slots) {
  public RoomFields {
    resources = List.copyOf(resources);
    slots = List.copyOf(slots);
  }

  public static RoomFields from(JsonObject room) {
    return new RoomFields(room.get(Fields.NAME).getAsString(), room.get(Fields.CAPACITY).getAsString(),
        room.get(Fields.LOCATION).getAsString(), StringArrays.fromJson(room.getAsJsonArray(Fields.RESOURCES)),
        StringArrays.fromJson(room.getAsJsonArray(Fields.SLOTS)));
  }
}
