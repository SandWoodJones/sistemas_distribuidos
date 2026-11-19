package reservas.protocol;

import java.time.LocalTime;
import java.util.Optional;

// Tabela de aulas da UTFPR.
public enum ClassPeriod implements WireValue {
  M1("07:30", "08:20"),
  M2("08:20", "09:10"),
  M3("09:10", "10:00"),
  M4("10:20", "11:10"),
  M5("11:10", "12:00"),
  M6("12:00", "12:50"),
  T1("13:00", "13:50"),
  T2("13:50", "14:40"),
  T3("14:40", "15:30"),
  T4("15:50", "16:40"),
  T5("16:40", "17:30"),
  T6("17:30", "18:20"),
  N1("18:40", "19:30"),
  N2("19:30", "20:20"),
  N3("20:20", "21:10"),
  N4("21:20", "22:10"),
  N5("22:10", "23:00");

  private final LocalTime start;
  private final LocalTime end;

  ClassPeriod(String start, String end) {
    this.start = LocalTime.parse(start);
    this.end = LocalTime.parse(end);
  }

  @Override
  public String wireName() {
    return name();
  }

  public LocalTime start() {
    return start;
  }

  public LocalTime end() {
    return end;
  }

  public static Optional<ClassPeriod> fromWire(String wireName) {
    return WireValues.fromWire(values(), wireName);
  }
}
