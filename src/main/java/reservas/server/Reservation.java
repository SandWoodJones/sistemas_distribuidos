package reservas.server;

import java.time.Instant;
import java.time.LocalDateTime;

import reservas.protocol.Role;
import reservas.protocol.TimeWindow;

public record Reservation(long id, long roomId, long userId, TimeWindow window, String topic, int participants,
    Instant createdAt) {
  public boolean isOverAt(LocalDateTime now) {
    return !window.endsAt().isAfter(now);
  }

  // Dono ou admin
  public boolean isOpenTo(User user) {
    return userId == user.id() || user.role() == Role.ADMIN;
  }
}
