package reservas.server;

import reservas.protocol.ProtocolException;
import reservas.protocol.Role;

public record Admin(User user) {
  static final String FORBIDDEN = "Permissao insuficiente";
  static final String LAST_ADMIN = "Nao e possivel remover o ultimo administrador";

  public Admin {
    if (user.role() != Role.ADMIN) {
      throw ProtocolException.forbidden(FORBIDDEN,
          "user \"" + user.name() + "\" tem role " + user.role().wireName() + ", a op exige admin");
    }
  }

  static ProtocolException lastAdmin(User user) {
    return ProtocolException.forbidden(LAST_ADMIN, "user \"" + user.name() + "\" e o unico admin");
  }
}
