package reservas.server;

import java.time.Instant;

import reservas.protocol.Role;

// Um cadastro. `id` é interno e nunca trafega
public record User(long id, String name, String email, String password, Role role, Instant createdAt) {
  // Aplica uma alteração já validada
  public User with(UserChanges changes) {
    return new User(id, keep(changes.name(), name), email, keep(changes.password(), password),
        keep(changes.role(), role), createdAt);
  }

  private static <T> T keep(T change, T current) {
    return change == null ? current : change;
  }
}
