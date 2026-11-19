package reservas.client;

import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import reservas.protocol.ClassPeriod;
import reservas.protocol.Slot;

final class SlotGrid extends JPanel {
  private static final List<DayOfWeek> DAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
  private static final List<String> DAY_LABELS = List.of("Seg", "Ter", "Qua", "Qui", "Sex", "Sab");
  private static final Comparator<Slot> TABLE_ORDER = Comparator.comparing(Slot::day).thenComparing(Slot::period);

  private final Map<Slot, JCheckBox> boxes = new HashMap<>();

  SlotGrid() {
    super(new GridLayout(ClassPeriod.values().length + 1, DAYS.size() + 1));

    add(new JLabel());
    DAY_LABELS.forEach(day -> add(new JLabel(day, SwingConstants.CENTER)));
    for (ClassPeriod period : ClassPeriod.values()) {
      addRow(period);
    }
  }

  List<String> selected() {
    return boxes.entrySet().stream().filter(entry -> entry.getValue().isSelected()).map(Map.Entry::getKey)
        .sorted(TABLE_ORDER).map(Slot::wireName).toList();
  }

  void select(List<String> wireNames) {
    boxes.forEach((slot, box) -> box.setSelected(wireNames.contains(slot.wireName())));
  }

  private void addRow(ClassPeriod period) {
    JLabel name = new JLabel(period.wireName());
    name.setToolTipText(period.start() + "-" + period.end());
    add(name);

    for (DayOfWeek day : DAYS) {
      JCheckBox box = new JCheckBox();
      box.setHorizontalAlignment(SwingConstants.CENTER);
      boxes.put(new Slot(day, period), box);
      add(box);
    }
  }
}
