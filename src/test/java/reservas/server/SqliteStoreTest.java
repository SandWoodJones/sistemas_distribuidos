package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.TOKEN;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reservas.protocol.Role;

class SqliteStoreTest {
  @Test
  void aFileDatabaseSurvivesReopening(@TempDir Path dir) {
    String file = dir.resolve("reservas.db").toString();

    try (SqliteStore first = SqliteStore.openFile(file)) {
      User joao = first.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
      first.createSession(TOKEN, joao.id(), T0).orElseThrow();
    }

    try (SqliteStore second = SqliteStore.openFile(file)) {
      User joao = second.findUser("joao").orElseThrow();
      assertEquals(T0, joao.createdAt());
      assertEquals(Role.USER, joao.role());
      assertEquals(joao.id(), second.findSession(TOKEN).orElseThrow().userId());
    }
  }
}
