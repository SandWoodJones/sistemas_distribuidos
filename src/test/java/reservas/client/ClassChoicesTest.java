package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ClassChoicesTest {
  private static final LocalDateTime AT_14_32 = LocalDateTime.parse("2026-09-09T14:32:10");

  @Test
  void todayOffersOnlyTheClassesThatHaveNotStarted() {
    assertEquals(List.of("14:40", "15:50", "16:40", "17:30", "18:40", "19:30", "20:20", "21:20", "22:10"),
        ClassChoices.starts("2026-09-09", AT_14_32));
    assertEquals(17, ClassChoices.starts("2026-09-10", AT_14_32).size());
    assertEquals(17, ClassChoices.starts("", AT_14_32).size());
  }

  @Test
  void endsComeAfterTheChosenStart() {
    assertEquals(List.of("22:10", "23:00"), ClassChoices.endsAfter("21:20"));
    assertEquals(17, ClassChoices.endsAfter("").size());
  }
}
