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

  private Codec() {
  }

  // Serializa em uma única linha terminada com `\n`
  public static String encode(JsonObject message) {
    String line = message + "\n";

    if (sizeInBytes(line) > MAX_MESSAGE_BYTES) {
      throw new ProtocolException(Status.INTERNAL_SERVER_ERROR, MESSAGE_TOO_LARGE, "resposta gerada com "
          + sizeInBytes(line) + " bytes, limite " + MAX_MESSAGE_BYTES + "; inicio=" + snippet(line));
    }

    return line;
  }

  // Interpreta uma linha recebida. O `\n` final pode estar presente ou já ter
  // sido removido
  public static JsonObject decode(String line) {
    if (sizeInBytes(line) > MAX_MESSAGE_BYTES) {
      throw new ProtocolException(Status.BAD_REQUEST, MESSAGE_TOO_LARGE,
          "recebidos " + sizeInBytes(line) + " bytes, limite " + MAX_MESSAGE_BYTES + "; inicio=" + snippet(line));
    }

    try (JsonReader reader = new JsonReader(new StringReader(line))) {
      // Modo estrito recusa chaves sem aspas e aspas simples, assim os testes pegam
      // erros do serializador em vez de aceitar em silêncio
      reader.setStrictness(Strictness.STRICT);
      JsonElement parsed = JsonParser.parseReader(reader);

      if (!parsed.isJsonObject()) {
        throw new ProtocolException(Status.BAD_REQUEST, INVALID_REQUEST,
            "esperado objeto JSON, veio " + typeOf(parsed) + "; bruto=" + snippet(line));
      }
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        throw new ProtocolException(Status.BAD_REQUEST, INVALID_REQUEST,
            "conteudo extra depois do objeto; bruto=" + snippet(line));
      }

      return parsed.getAsJsonObject();
    } catch (JsonParseException | IOException | IllegalStateException e) {
      throw new ProtocolException(Status.BAD_REQUEST, INVALID_REQUEST,
          e.getClass().getSimpleName() + ": " + e.getMessage() + "; bruto=" + snippet(line), e);
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

  static String snippet(String line) {
    String visible = line.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    return visible.length() <= RAW_SNIPPET_LIMIT ? visible
        : visible.substring(0, RAW_SNIPPET_LIMIT) + "...(+" + (visible.length() - RAW_SNIPPET_LIMIT) + " chars)";
  }
}
