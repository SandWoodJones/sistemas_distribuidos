package reservas.protocol;

import java.time.DayOfWeek;
import java.util.Optional;

// Horário de funcionamento no código da UTFPR
public record Slot(DayOfWeek day, ClassPeriod period) implements WireValue {
  public Slot {
    if (day == DayOfWeek.SUNDAY) {
      throw new IllegalArgumentException("horario " + period + " no domingo: dias vao de segunda (2) a sabado (7)");
    }
  }

  @Override
  public String wireName() {
    return (day.getValue() + 1) + period.wireName();
  }

  // Vazio quando fora de `Formats.SLOT`
  public static Optional<Slot> fromWire(String wireName) {
    if (!Formats.matches(Formats.SLOT, wireName)) {
      return Optional.empty();
    }

    DayOfWeek day = DayOfWeek.of(Character.getNumericValue(wireName.charAt(0)) - 1);
    return ClassPeriod.fromWire(wireName.substring(1)).map(period -> new Slot(day, period));
  }
}
