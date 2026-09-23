package reservas.protocol;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

// Converte uma mensagem entre JSON e a linha única que trafega no socket
public final class Codec {
  // Tamanho máximo de uma mensagem, já incluindo o `\n` final
  public static final int MAX_MESSAGE_BYTES = 8192;
  // Trecho da linha bruta que entra no diagnóstico
  private static final int RAW_SNIPPET_LIMIT = 200;

  public static final String INVALID_REQUEST = "Requisicao invalida";
  public static final String UNKNOWN_OPERATION = "Operacao desconhecida";
  public static final String MESSAGE_TOO_LARGE = "Mensagem excede o tamanho maximo";
  public static final String INTERNAL_ERROR = "Erro interno do servidor";

  private Codec() {
  }

  // Serializa em uma única linha terminada com `\n`
  public static String encode(JsonObject message) {
    String line = message + "\n";

    if (sizeInBytes(line) > MAX_MESSAGE_BYTES) {
      throw ProtocolException.internal("resposta gerada com " + sizeInBytes(line) + " bytes, limite " + MAX_MESSAGE_BYTES
          + "; inicio=" + snippet(line));
    }

    return line;
  }

  // Interpreta uma linha recebida. O `\n` final pode estar presente ou já ter
  // sido removido
  public static JsonObject decode(String line) {
    if (sizeInBytes(line) > MAX_MESSAGE_BYTES) {
      throw ProtocolException.badRequest(MESSAGE_TOO_LARGE,
          diagnosis("recebidos " + sizeInBytes(line) + " bytes, limite " + MAX_MESSAGE_BYTES, line));
    }

    try (JsonReader reader = new JsonReader(new StringReader(line))) {
      // Modo estrito recusa chaves sem aspas e aspas simples, assim os testes pegam
      // erros do serializador em vez de aceitar em silêncio
      reader.setStrictness(Strictness.STRICT);
      JsonElement parsed = JsonParser.parseReader(reader);

      if (!parsed.isJsonObject()) {
        throw ProtocolException.badRequest(INVALID_REQUEST,
            diagnosis("esperado objeto JSON, veio " + typeOf(parsed), line));
      }
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        throw ProtocolException.badRequest(INVALID_REQUEST, diagnosis("conteudo extra depois do objeto", line));
      }

      return parsed.getAsJsonObject();
    } catch (JsonParseException | IOException | IllegalStateException e) {
      throw ProtocolException.badRequest(INVALID_REQUEST,
          diagnosis(e.getClass().getSimpleName() + ": " + e.getMessage(), line), e);
    }
  }

  // Comprimento em bytes UTF-8
  public static int sizeInBytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }

  private static String typeOf(JsonElement element) {
    if (element.isJsonArray()) {
      return "array";
    }
    if (element.isJsonNull()) {
      return "null";
    }
    if (element.isJsonPrimitive()) {
      return "valor primitivo";
    }
    return "objeto";
  }

  public static String diagnosis(String detail, String line) {
    return detail + "; bruto=" + snippet(line);
  }

  public static String snippet(String line) {
    String visible = line.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    return visible.length() <= RAW_SNIPPET_LIMIT ? visible
        : visible.substring(0, RAW_SNIPPET_LIMIT) + "...(+" + (visible.length() - RAW_SNIPPET_LIMIT) + " chars)";
  }
}
