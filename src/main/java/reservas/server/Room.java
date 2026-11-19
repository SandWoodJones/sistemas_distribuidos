package reservas.server;

import static java.util.Objects.requireNonNullElse;

import java.util.List;

import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;

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
}
