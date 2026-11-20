package reservas.client;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// Data escrita num campo de texto
final class DateText {
  private DateText() {
  }

  // Texto que não é data fica como está; se for enviado, o servidor responde 400 e diz o campo
  static String shift(String text, int days) {
    try {
      return LocalDate.parse(text).plusDays(days).toString();
    } catch (DateTimeParseException e) {
      return text;
    }
  }
}
