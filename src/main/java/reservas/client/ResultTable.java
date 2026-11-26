package reservas.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reservas.protocol.Fields;

// Resposta como tabela
final class ResultTable {
  private static final List<String> LISTINGS = List.of(Fields.USERS, Fields.ROOMS, Fields.RESERVATIONS, Fields.AGENDA);
  private static final Set<String> ENVELOPE = Set.of(Fields.OP, Fields.STATUS, Fields.MESSAGE);
  private static final Map<String, String> HEADERS = Map.ofEntries(Map.entry(Fields.ROOM_ID, "Sala"),
      Map.entry(Fields.RESERVATION_ID, "Reserva"), Map.entry(Fields.NAME, "Nome"),
      Map.entry(Fields.CAPACITY, "Capacidade"), Map.entry(Fields.LOCATION, "Local"),
      Map.entry(Fields.RESOURCES, "Recursos"), Map.entry(Fields.ROOM_STATUS, "Estado"),
      Map.entry(Fields.SLOTS, "Horarios"), Map.entry(Fields.USER, "Usuario"), Map.entry(Fields.EMAIL, "Email"),
      Map.entry(Fields.ROLE, "Perfil"), Map.entry(Fields.CREATED_AT, "Criado em"), Map.entry(Fields.DATE, "Data"),
      Map.entry(Fields.START_TIME, "Inicio"), Map.entry(Fields.END_TIME, "Fim"), Map.entry(Fields.TOPIC, "Assunto"),
      Map.entry(Fields.PARTICIPANTS, "Participantes"), Map.entry(Fields.AVAILABLE, "Disponivel"),
      Map.entry(Fields.SLOT, "Aula"), Map.entry(Fields.SLOT_STATUS, "Situacao"), Map.entry(Fields.AGENDA, "Agenda"),
      Map.entry(Fields.TOKEN, "Token"));

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

  // Cabeçalho para quem lê; a coluna do modelo continua com a chave do protocolo
  static String header(String key) {
    return HEADERS.getOrDefault(key, key);
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
      List<String> items = new ArrayList<>();
      value.getAsJsonArray().forEach(item -> items.add(cell(item)));
      return ListText.format(items);
    }
    if (value.isJsonObject()) {
      return value.toString();
    }

    return value.getAsString();
  }
}
