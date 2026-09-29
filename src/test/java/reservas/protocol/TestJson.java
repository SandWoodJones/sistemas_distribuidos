package reservas.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// Aspas simples viram duplas, para os testes escreverem mensagem sem escapar tudo.
// `CodecTest` e `LineReaderTest` nao usam isto: la a aspa crua e o objeto do teste
public final class TestJson {
  private TestJson() {
  }

  public static String json(String body) {
    return body.replace('\'', '"');
  }

  public static JsonObject object(String body) {
    return JsonParser.parseString(json(body)).getAsJsonObject();
  }
}
