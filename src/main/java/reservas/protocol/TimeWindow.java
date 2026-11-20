package reservas.protocol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Dia e faixa de horário de uma consulta ou reserva, com o fim depois do início
public record TimeWindow(LocalDate date, LocalTime start, LocalTime end) {
  public TimeWindow {
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("fim " + end + " nao vem depois do inicio " + start);
    }
  }

  public LocalDateTime startsAt() {
    return date.atTime(start);
  }

  public LocalDateTime endsAt() {
    return date.atTime(end);
  }

  public boolean overlaps(TimeWindow other) {
    return date.equals(other.date) && start.isBefore(other.end) && end.isAfter(other.start);
  }
}
