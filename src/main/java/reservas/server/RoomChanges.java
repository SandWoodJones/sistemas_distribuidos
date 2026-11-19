package reservas.server;

import java.util.List;

import reservas.protocol.Messages;
import reservas.protocol.ProtocolException;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;

// Alteração de sala já validada
public record RoomChanges(String name, Integer capacity, String location, List<String> resources, RoomStatus status,
    List<Slot> slots) {
  public RoomChanges {
    if (name == null && capacity == null && location == null && resources == null && status == null && slots == null) {
      throw ProtocolException.badRequest(Messages.NOTHING_TO_UPDATE, "todos os campos vazios, ausentes ou `[]`");
    }
  }
}
