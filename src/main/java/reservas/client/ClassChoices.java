package reservas.client;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import reservas.protocol.ClassPeriod;
import reservas.protocol.Formats;

// Horários de aula oferecidos nas listas de início e fim
final class ClassChoices {
  private ClassChoices() {
  }

  // Com a data de hoje, só as aulas que ainda não começaram: o servidor recusa as outras (regra 9.3)
  static List<String> starts(String date, LocalDateTime now) {
    boolean today = date.strip().equals(now.toLocalDate().toString());
    return Arrays.stream(ClassPeriod.values()).map(ClassPeriod::start)
        .filter(start -> !today || start.isAfter(now.toLocalTime())).map(LocalTime::toString).toList();
  }

  // Só fins depois do início escolhido; início em branco mostra todos
  static List<String> endsAfter(String start) {
    boolean chosen = Formats.matches(Formats.START_TIME, start);
    return Arrays.stream(ClassPeriod.values()).map(ClassPeriod::end)
        .filter(end -> !chosen || end.isAfter(LocalTime.parse(start))).map(LocalTime::toString).toList();
  }
}
