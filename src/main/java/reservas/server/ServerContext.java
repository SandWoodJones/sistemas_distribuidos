package reservas.server;

import java.time.Clock;

// Tudo que um handler precisa do servidor
public record ServerContext(SqliteStore store, Clock clock, TokenGenerator tokens) {}
