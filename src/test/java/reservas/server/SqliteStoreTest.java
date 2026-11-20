package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static reservas.server.Fixtures.OTHER_TOKEN;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.TOKEN;

import java.nio.file.Path;
import java.util.List;

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

  // A janela do servidor mostra só quem tem sessão viva
  @Test
  void listsTheUsersWhoseSessionIsAlive() {
    try (SqliteStore store = SqliteStore.openInMemory()) {
      User joao = store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
      User maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
      User ana = store.createUser("ana", "ana@email.com", "senha123", Role.USER, T0).orElseThrow();
      store.createSession(TOKEN, joao.id(), T0).orElseThrow();
      store.createSession(OTHER_TOKEN, maria.id(), T0).orElseThrow();
      store.createSession("c".repeat(64), ana.id(), T0.minus(Session.IDLE_TIMEOUT)).orElseThrow();

      assertEquals(List.of("joao:user", "maria:admin"), names(store.listLoggedIn(T0)));

      store.deleteSession(TOKEN);
      assertEquals(List.of("maria:admin"), names(store.listLoggedIn(T0)));
    }
  }

  private static List<String> names(List<LoggedInUser> users) {
    return users.stream().map(user -> user.name() + ":" + user.role().wireName()).toList();
  }
}
