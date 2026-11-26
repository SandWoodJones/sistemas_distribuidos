package reservas.client;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// Data escrita num campo de texto
final class DateText {
  private DateText() {
  }

  // Campo em branco parte de hoje. Texto que não é data fica como está; se for enviado, o servidor responde 400
  static String shift(String text, int days, LocalDate today) {
    if (text.isBlank()) {
      return today.plusDays(days).toString();
    }

    try {
      return LocalDate.parse(text).plusDays(days).toString();
    } catch (DateTimeParseException e) {
      return text;
    }
  }
}
