package reservas.server;

import reservas.protocol.Role;

// Linha da lista de usuários logados na janela do servidor
public record LoggedInUser(String name, Role role, Session session) {}
