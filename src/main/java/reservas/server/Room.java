package reservas.server;

import static java.util.Objects.requireNonNullElse;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

import reservas.protocol.ClassPeriod;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;
import reservas.protocol.TimeWindow;

public record Room(long id, String name, int capacity, String location, List<String> resources, RoomStatus status,
    List<Slot> slots) {
  public Room {
    resources = List.copyOf(resources);
    slots = List.copyOf(slots);
  }

  // Aplica uma alteração já validada
  public Room with(RoomChanges changes) {
    return new Room(id, requireNonNullElse(changes.name(), name), requireNonNullElse(changes.capacity(), capacity),
        requireNonNullElse(changes.location(), location), requireNonNullElse(changes.resources(), resources),
        requireNonNullElse(changes.status(), status), requireNonNullElse(changes.slots(), slots));
  }

  public boolean worksDuring(TimeWindow window) {
    DayOfWeek day = window.date().getDayOfWeek();
    return day != DayOfWeek.SUNDAY && ClassPeriod.between(window.start(), window.end()).stream()
        .allMatch(period -> slots.contains(new Slot(day, period)));
  }

  // Ativa, funcionando em todas as aulas da janela e sem reserva sobreposta
  public boolean isFreeDuring(TimeWindow window, List<Reservation> sameDay) {
    return status == RoomStatus.ACTIVE && worksDuring(window)
        && sameDay.stream().noneMatch(reservation -> reservation.window().overlaps(window));
  }

  public List<Slot> slotsOn(DayOfWeek day) {
    return slots.stream().filter(slot -> slot.day() == day).sorted(Comparator.comparing(Slot::period)).toList();
  }
}
