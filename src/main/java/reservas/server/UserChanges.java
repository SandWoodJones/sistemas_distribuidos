package reservas.server;

import reservas.protocol.ProtocolException;
import reservas.protocol.Role;

// Alteração de cadastro já validada
public record UserChanges(String name, String password, Role role) {
  static final String NOTHING_TO_UPDATE = "Nenhum dado para atualizar";

  public UserChanges {
    if (name == null && password == null && role == null) {
      throw ProtocolException.badRequest(NOTHING_TO_UPDATE, "todos os campos vazios ou ausentes: nada para alterar");
    }
  }
}
