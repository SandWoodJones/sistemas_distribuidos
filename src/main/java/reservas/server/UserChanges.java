package reservas.server;

import reservas.protocol.Messages;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;

// Alteração de cadastro já validada
public record UserChanges(String name, String password, Role role) {
  public UserChanges {
    if (name == null && password == null && role == null) {
      throw ProtocolException.badRequest(Messages.NOTHING_TO_UPDATE,
          "todos os campos vazios ou ausentes: nada para alterar");
    }
  }
}
