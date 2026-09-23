package reservas.protocol;

import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// Leitura validada de uma requisição
public final class Request {
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

  private JsonElement mustExist(String field) {
    JsonElement element = json.get(field);
    if (element == null) {
      throw invalid(field, "campo obrigatorio ausente");
    }

    return element;
  }

  private String asString(String field, JsonElement element) {
    if (element.isJsonNull()) {
      throw invalid(field, "valor `null`; use \"\" para omitir");
    }
    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
      throw invalid(field, "esperado string, veio " + element);
    }

    return element.getAsString();
  }

  // `badRequest` é o que vai para rede, o resto é log
  private ProtocolException invalid(String field, String reason) {
    return ProtocolException.badRequest(badRequest,
        Codec.diagnosis("campo '" + field + "': " + reason, json.toString()));
  }
}
