package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class SlotGridTest {
  @Test
  void marksTheLoadedSlotsAndReadsThemBackByDayThenClass() {
    SlotGrid grid = new SlotGrid();
    assertEquals(List.of(), grid.selected());

    grid.select(List.of("5N1", "2T1", "2M1", "7M6"));
    assertEquals(List.of("2M1", "2T1", "5N1", "7M6"), grid.selected());

    grid.select(List.of("3T2"));
    assertEquals(List.of("3T2"), grid.selected());
  }
}
