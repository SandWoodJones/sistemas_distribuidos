package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DateTextTest {
  @Test
  void movesADayAcrossMonthsAndYearsAndLeavesNonDatesAlone() {
    String[][] cases = {
        { "2026-09-15", "1", "2026-09-16" },
        { "2026-12-31", "1", "2027-01-01" },
        { "2026-03-01", "-1", "2026-02-28" },
        { "", "1", "" },
        { "2026-02-30", "1", "2026-02-30" },
        { "amanha", "-1", "amanha" } };

    for (String[] c : cases) {
      assertEquals(c[2], DateText.shift(c[0], Integer.parseInt(c[1])), c[0] + " " + c[1]);
    }
  }
}
