package reservas.protocol;

import java.time.LocalDate;
import java.time.LocalTime;

// Dia e faixa de horário de uma consulta ou reserva, com o fim depois do início
public record TimeWindow(LocalDate date, LocalTime start, LocalTime end) {
  public TimeWindow {
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("fim " + end + " nao vem depois do inicio " + start);
    }
  }
}
