package reservas.server;

import reservas.protocol.ProtocolException;

public enum UpdateOutcome {
  UPDATED,
  NAME_TAKEN,
  LAST_ADMIN;

  static final String NAME_IN_USE = "Usuario ja esta em uso";

  void orThrow(User target, UserChanges changes) {
    switch (this) {
      case UPDATED -> {
      }
      case LAST_ADMIN -> throw Admin.lastAdmin(target);
      case NAME_TAKEN ->
        throw ProtocolException.conflict(NAME_IN_USE, "user \"" + changes.name() + "\" ja pertence a outro cadastro");
    }
  }
}
