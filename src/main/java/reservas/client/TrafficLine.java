package reservas.client;

import java.util.regex.Pattern;

// Como pintar uma linha do painel de mensagens
enum TrafficLine {
  SENT, RECEIVED_OK, RECEIVED_ERROR, NOTE;

  private static final Pattern ERROR_STATUS = Pattern.compile("\"status\"\\s*:\\s*\"[45][0-9]{2}\"");

  static TrafficLine of(String line) {
    if (line.startsWith("-> ")) {
      return SENT;
    }
    if (line.startsWith("<- ")) {
      return ERROR_STATUS.matcher(line).find() ? RECEIVED_ERROR : RECEIVED_OK;
    }

    return NOTE;
  }
}
