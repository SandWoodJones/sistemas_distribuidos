package reservas.server;

import java.time.Instant;

import reservas.protocol.Fields;
import reservas.protocol.Formats;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;

// Autor da requisição, identificado pelo token
public record Caller(User user, Session session) {
  static final String UNAUTHORIZED = "Token invalido ou expirado";

  // Token ausente é 401, presente e fora do formato é 400
  static Caller authenticate(Request request, ServerContext context) {
    String token = request.optional(Fields.TOKEN, Formats.TOKEN)
        .orElseThrow(() -> ProtocolException.unauthorized(UNAUTHORIZED, "token ausente ou vazio"));

    SqliteStore store = context.store();
    Instant now = context.clock().instant();
    Session session = store.findSession(token)
        .orElseThrow(() -> ProtocolException.unauthorized(UNAUTHORIZED, "token " + token + " nao abre nenhuma sessao"));

    if (session.isExpiredAt(now)) {
      store.deleteSession(token);
      throw ProtocolException.unauthorized(UNAUTHORIZED,
          "sessao do token " + token + " parada desde " + session.lastUsed() + ", limite " + Session.IDLE_TIMEOUT);
    }

    store.touchSession(token, now);
    return new Caller(owner(store, session), session.usedAt(now));
  }

  private static User owner(SqliteStore store, Session session) {
    return store.findUserById(session.userId()).orElseThrow(() -> ProtocolException
        .internal("sessao " + session.token() + " aponta para usuario inexistente, id=" + session.userId()));
  }
}
