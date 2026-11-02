package reservas.protocol;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;

// Array JSON de strings
public final class StringArrays {
  private StringArrays() {
  }

  public static JsonArray toJson(List<String> values) {
    JsonArray array = new JsonArray();
    values.forEach(array::add);
    return array;
  }

  public static List<String> fromJson(JsonArray array) {
    List<String> values = new ArrayList<>();
    array.forEach(item -> values.add(item.getAsString()));
    return values;
  }
}
