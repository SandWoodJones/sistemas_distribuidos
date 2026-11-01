package reservas.server;

import static java.util.Objects.requireNonNullElse;

import java.time.Instant;

import reservas.protocol.Role;

// Um cadastro. `id` é interno e nunca trafega
public record User(long id, String name, String email, String password, Role role, Instant createdAt) {
  // Aplica uma alteração já validada
  public User with(UserChanges changes) {
    return new User(id, requireNonNullElse(changes.name(), name), email,
        requireNonNullElse(changes.password(), password), requireNonNullElse(changes.role(), role), createdAt);
  }
}
