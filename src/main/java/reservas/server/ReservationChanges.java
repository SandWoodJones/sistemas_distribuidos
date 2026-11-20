package reservas.server;

import java.time.LocalTime;

import reservas.protocol.Messages;
import reservas.protocol.ProtocolException;

// Alteração de reserva já validada; `null` é manter
public record ReservationChanges(LocalTime start, LocalTime end, String topic, Integer participants) {
  public ReservationChanges {
    if (start == null && end == null && topic == null && participants == null) {
      throw ProtocolException.badRequest(Messages.NOTHING_TO_UPDATE, "todos os campos vazios ou ausentes");
    }
  }

  public boolean movesTime() {
    return start != null || end != null;
  }
}
