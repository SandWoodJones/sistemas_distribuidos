package reservas.protocol;

import java.util.Optional;

// Perfil do usuário. Todo cadastro por `register` resulta em `USER`; `ADMIN` só acontece por `admin_update_user`
public enum Role implements WireValue {
  USER("user"),
  ADMIN("admin");

  private final String wireName;

  Role(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }

  public static Optional<Role> fromWire(String wireName) {
    return WireValues.fromWire(values(), wireName);
  }
}
