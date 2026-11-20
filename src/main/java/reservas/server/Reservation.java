package reservas.server;

import java.time.Instant;
import java.time.LocalDateTime;

import reservas.protocol.TimeWindow;

public record Reservation(long id, long roomId, long userId, TimeWindow window, String topic, int participants,
    Instant createdAt) {
  public boolean isOverAt(LocalDateTime now) {
    return !window.endsAt().isAfter(now);
  }
}
