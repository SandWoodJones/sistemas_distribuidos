package reservas.server;

import java.time.Instant;

import reservas.protocol.Role;

// Um cadastro. `id` é interno e nunca trafega
public record User(long id, String name, String email, String password, Role role, Instant createdAt) {
  // Aplica uma atualização já validada. `null` significa "não alterar".
  // `email` e `role` não mudam por `update_user`
  public User with(String newName, String newPassword) {
    return new User(id, newName == null ? name : newName, email, newPassword == null ? password : newPassword, role,
        createdAt);
  }
}
