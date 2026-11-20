package reservas.server;

import static java.util.Objects.requireNonNullElse;

import java.time.Instant;
import java.time.LocalDateTime;

import reservas.protocol.Role;
import reservas.protocol.TimeWindow;

public record Reservation(long id, long roomId, long userId, TimeWindow window, String topic, int participants,
    Instant createdAt) {
  // Fim que não vem depois do início lança `IllegalArgumentException`
  public Reservation with(ReservationChanges changes) {
    TimeWindow moved = new TimeWindow(window.date(), requireNonNullElse(changes.start(), window.start()),
        requireNonNullElse(changes.end(), window.end()));
    return new Reservation(id, roomId, userId, moved, requireNonNullElse(changes.topic(), topic),
        requireNonNullElse(changes.participants(), participants), createdAt);
  }

  public boolean isOverAt(LocalDateTime now) {
    return !window.endsAt().isAfter(now);
  }

  // Dono ou admin
  public boolean isOpenTo(User user) {
    return userId == user.id() || user.role() == Role.ADMIN;
  }
}
