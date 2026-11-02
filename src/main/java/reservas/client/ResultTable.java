package reservas.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.StringArrays;

// Resposta como tabela
final class ResultTable {
  private static final List<String> LISTINGS = List.of(Fields.USERS, Fields.ROOMS, Fields.RESERVATIONS);
  private static final Set<String> ENVELOPE = Set.of(Fields.OP, Fields.STATUS, Fields.MESSAGE);

  private ResultTable() {
  }

  static TableModel of(JsonObject response) {
    List<JsonObject> rows = rows(response);
    List<String> columns = columns(rows);

    DefaultTableModel model = new DefaultTableModel(columns.toArray(), 0) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };
    rows.forEach(row -> model.addRow(columns.stream().map(key -> cell(row.get(key))).toArray()));
    return model;
  }

  private static List<JsonObject> rows(JsonObject response) {
    for (String listing : LISTINGS) {
      if (response.has(listing)) {
        List<JsonObject> items = new ArrayList<>();
        response.getAsJsonArray(listing).forEach(item -> items.add(item.getAsJsonObject()));
        return items;
      }
    }

    JsonObject single = new JsonObject();
    response.entrySet().stream().filter(field -> !ENVELOPE.contains(field.getKey()))
        .forEach(field -> single.add(field.getKey(), field.getValue()));
    return single.isEmpty() ? List.of() : List.of(single);
  }

  // Colunas na ordem em que aparecem
  private static List<String> columns(List<JsonObject> rows) {
    Set<String> keys = new LinkedHashSet<>();
    rows.forEach(row -> keys.addAll(row.keySet()));
    return List.copyOf(keys);
  }

  private static String cell(JsonElement value) {
    if (value == null || value.isJsonNull()) {
      return "";
    }
    if (value.isJsonArray()) {
      return ListText.format(StringArrays.fromJson(value.getAsJsonArray()));
    }

    return value.getAsString();
  }
}
