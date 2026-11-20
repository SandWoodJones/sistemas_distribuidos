package reservas.protocol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// Leitura validada de uma requisição
public final class Request {
  private static final List<String> CLEAR_ALL = List.of(Fields.CLEAR_RESOURCES);

  private final JsonObject json;
  private final String badRequest;

  public Request(JsonObject json, String badRequest) {
    this.json = json;
    this.badRequest = badRequest;
  }

  // Ausente, `null`, não-string e fora de formato são 400
  public String required(String field, Pattern format) {
    return checked(field, asString(field, mustExist(field)), format);
  }

  // Presente em toda requisição menos `register` e `login`
  public String token() {
    return required(Fields.TOKEN, Formats.TOKEN);
  }

  // Campo opcional
  public Optional<String> optional(String field, Pattern format) {
    if (!json.has(field)) {
      return Optional.empty();
    }

    String value = asString(field, json.get(field));
    return value.isEmpty() ? Optional.empty() : Optional.of(checked(field, value, format));
  }

  // Filtro ausente é sem filtro
  public Optional<String> filter(String field, Pattern format) {
    return json.has(field) ? Optional.of(required(field, format)) : Optional.empty();
  }

  public Optional<LocalDate> dateFilter() {
    return filter(Fields.DATE, Formats.DATE).map(value -> calendarDate(Fields.DATE, value));
  }

  // `date`, `start_time` e `end_time` vêm todos ou nenhum
  public Optional<TimeWindow> windowFilter() {
    Optional<LocalDate> date = dateFilter();
    Optional<LocalTime> start = filter(Fields.START_TIME, Formats.START_TIME).map(LocalTime::parse);
    Optional<LocalTime> end = filter(Fields.END_TIME, Formats.END_TIME).map(LocalTime::parse);

    if (!allOrNone(Fields.DATE, Fields.START_TIME, Fields.END_TIME)) {
      return Optional.empty();
    }

    return Optional.of(window(date.orElseThrow(), start.orElseThrow(), end.orElseThrow()));
  }

  public TimeWindow window() {
    LocalDate date = calendarDate(Fields.DATE, required(Fields.DATE, Formats.DATE));
    LocalTime start = LocalTime.parse(required(Fields.START_TIME, Formats.START_TIME));
    LocalTime end = LocalTime.parse(required(Fields.END_TIME, Formats.END_TIME));
    return window(date, start, end);
  }

  public TimeWindow upcomingWindow(LocalDateTime now) {
    TimeWindow window = window();
    if (!window.startsAt().isAfter(now)) {
      throw invalid(Fields.START_TIME, "inicio " + window.startsAt() + " ja passou, agora e " + now);
    }

    return window;
  }

  public List<String> resources() {
    return withoutClearMark(requiredList(Fields.RESOURCES, Formats.RESOURCE));
  }

  // Ausente ou `[]` mantém, `["0"]` apaga todos.
  public Optional<List<String>> resourcesChange() {
    return listChange(Fields.RESOURCES, Formats.RESOURCE)
        .map(resources -> resources.equals(CLEAR_ALL) ? List.of() : withoutClearMark(resources));
  }

  public List<Slot> slots() {
    return slots(requiredList(Fields.SLOTS, Formats.SLOT));
  }

  public Optional<List<Slot>> slotsChange() {
    return listChange(Fields.SLOTS, Formats.SLOT).map(this::slots);
  }

  // Campos que só valem juntos. `true` se vieram todos, `false` se nenhum
  private boolean allOrNone(String... fields) {
    List<String> sent = Arrays.stream(fields).filter(this::isSent).toList();
    if (sent.isEmpty()) {
      return false;
    }

    for (String field : fields) {
      if (!sent.contains(field)) {
        throw invalid(field, "ausente, mas " + sent + " foi enviado e exige " + Arrays.toString(fields));
      }
    }

    return true;
  }

  // Campo conhecido que a operação não aceita
  public void mustBeAbsent(String field) {
    if (json.has(field)) {
      throw invalid(field, "campo nao pode ser enviado nesta operacao");
    }
  }

  // Formato esperado vai junto do valor recusado
  private String checked(String field, String value, Pattern format) {
    if (!Formats.matches(format, value)) {
      throw invalid(field, "esperado " + format.pattern() + ", veio \"" + Codec.snippet(value) + "\"");
    }

    return value;
  }

  private LocalDate calendarDate(String field, String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw invalid(field, "data inexistente no calendario, \"" + value + "\"", e);
    }
  }

  private TimeWindow window(LocalDate date, LocalTime start, LocalTime end) {
    try {
      return new TimeWindow(date, start, end);
    } catch (IllegalArgumentException e) {
      throw invalid(Fields.END_TIME, e.getMessage(), e);
    }
  }

  private JsonElement mustExist(String field) {
    JsonElement element = json.get(field);
    if (element == null) {
      throw invalid(field, "campo obrigatorio ausente");
    }

    return element;
  }

  private boolean isSent(String field) {
    JsonElement element = json.get(field);
    return element != null && !isEmptyString(element);
  }

  private static boolean isEmptyString(JsonElement element) {
    return isString(element) && element.getAsString().isEmpty();
  }

  private static boolean isString(JsonElement element) {
    return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
  }

  private String asString(String field, JsonElement element) {
    if (element.isJsonNull()) {
      throw invalid(field, "valor `null`; use \"\" para omitir");
    }
    if (!isString(element)) {
      throw invalid(field, "esperado string, veio " + element);
    }

    return element.getAsString();
  }

  private List<String> requiredList(String field, Pattern itemFormat) {
    List<String> items = asStrings(field, mustExist(field));
    items.forEach(item -> checked(field, item, itemFormat));
    return items;
  }

  private List<String> asStrings(String field, JsonElement element) {
    if (!element.isJsonArray()) {
      throw invalid(field, "esperado array de strings, veio " + element);
    }

    List<String> values = new ArrayList<>();
    for (JsonElement item : element.getAsJsonArray()) {
      if (!isString(item)) {
        throw invalid(field, "item do array nao e string: " + item);
      }
      values.add(item.getAsString());
    }

    return List.copyOf(values);
  }

  private Optional<List<String>> listChange(String field, Pattern itemFormat) {
    if (!json.has(field)) {
      return Optional.empty();
    }

    List<String> items = requiredList(field, itemFormat);
    return items.isEmpty() ? Optional.empty() : Optional.of(items);
  }

  private List<String> withoutClearMark(List<String> resources) {
    if (resources.contains(Fields.CLEAR_RESOURCES)) {
      throw invalid(Fields.RESOURCES, "\"0\" so vale sozinho, no update_room, para apagar todos: " + resources);
    }

    return resources;
  }

  private List<Slot> slots(List<String> wireNames) {
    if (wireNames.isEmpty()) {
      throw invalid(Fields.SLOTS, "a sala precisa funcionar em pelo menos um horario");
    }
    if (Set.copyOf(wireNames).size() != wireNames.size()) {
      throw invalid(Fields.SLOTS, "horario repetido em " + wireNames);
    }

    return wireNames.stream().map(wireName -> Slot.fromWire(wireName).orElseThrow()).toList();
  }

  // `badRequest` é o que vai para rede, o resto é log
  private ProtocolException invalid(String field, String reason) {
    return invalid(field, reason, null);
  }

  private ProtocolException invalid(String field, String reason, Throwable cause) {
    return ProtocolException.badRequest(badRequest,
        Codec.diagnosis("campo '" + field + "': " + reason, json.toString()), cause);
  }
}
