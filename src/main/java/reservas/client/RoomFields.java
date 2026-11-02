package reservas.client;

import java.util.List;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.StringArrays;

public record RoomFields(String name, String capacity, String location, List<String> resources) {
  public RoomFields {
    resources = List.copyOf(resources);
  }

  public static RoomFields from(JsonObject room) {
    return new RoomFields(room.get(Fields.NAME).getAsString(), room.get(Fields.CAPACITY).getAsString(),
        room.get(Fields.LOCATION).getAsString(), StringArrays.fromJson(room.getAsJsonArray(Fields.RESOURCES)));
  }
}
