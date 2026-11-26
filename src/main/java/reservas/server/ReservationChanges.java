package reservas.server;

import java.time.LocalDate;
import java.time.LocalTime;

import reservas.protocol.Messages;
import reservas.protocol.ProtocolException;

// Alteração de reserva já validada; `null` é manter
public record ReservationChanges(Long roomId, LocalDate date, LocalTime start, LocalTime end, String topic,
    Integer participants) {
  public ReservationChanges {
    if (roomId == null && date == null && start == null && end == null && topic == null && participants == null) {
      throw ProtocolException.badRequest(Messages.NOTHING_TO_UPDATE, "todos os campos vazios ou ausentes");
    }
  }

  // Sala, dia ou horário: o que a sala de destino precisa aceitar de novo
  public boolean moves() {
    return roomId != null || date != null || start != null || end != null;
  }

  // Início que conta como novo para a regra do passado
  public boolean movesStart() {
    return date != null || start != null;
  }
}
