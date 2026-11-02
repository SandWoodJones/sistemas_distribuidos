package reservas.client;

import java.util.Arrays;
import java.util.List;

// Lista de strings escrita num campo de texto
final class ListText {
  private static final String SEPARATOR = ",";
  private static final String JOINER = ", ";

  private ListText() {
  }

  // Texto em branco vira lista vazia, itens vazios somem
  static List<String> parse(String text) {
    return Arrays.stream(text.split(SEPARATOR)).map(String::strip).filter(item -> !item.isEmpty()).toList();
  }

  static String format(List<String> items) {
    return String.join(JOINER, items);
  }
}
