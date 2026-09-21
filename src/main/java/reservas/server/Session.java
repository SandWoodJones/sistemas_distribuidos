package reservas.server;

import java.time.Duration;
import java.time.Instant;

// Sessão aberta por login. Um usuário tem no máximo uma
public record Session(String token, long userId, Instant lastUsed) {
  // Validade ociosa do token de 30 minutos sem uso
  public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

  // Exatamente no limite consta como expirado
  public boolean isExpiredAt(Instant now) {
    return !now.isBefore(lastUsed.plus(IDLE_TIMEOUT));
  }

  public Session usedAt(Instant now) {
    return new Session(token, userId, now);
  }
}
