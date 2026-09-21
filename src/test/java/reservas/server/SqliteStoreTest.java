package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reservas.protocol.Role;

class SqliteStoreTest {
  private static final Instant T0 = Instant.parse("2026-09-09T14:32:10Z");
  private static final Instant ALMOST = T0.plus(Session.IDLE_TIMEOUT).minusSeconds(1);
  private static final Instant LIMIT = T0.plus(Session.IDLE_TIMEOUT);
  private static final String TOKEN_A = "a".repeat(64);
  private static final String TOKEN_B = "b".repeat(64);

  private final SqliteStore store = SqliteStore.openInMemory();

  @AfterEach
  void closeStore() {
    store.close();
  }

  private User joao() {
    return store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
  }

  private User maria() {
    return store.createUser("maria", "maria@email.com", "senha456", Role.ADMIN, T0).orElseThrow();
  }

  @Test
  void findsUserByNameAndByEmail() {
    joao();
    assertTrue(store.findUser("joao").isPresent());
    assertTrue(store.findUserByEmail("joao.silva@email.com").isPresent());
    assertTrue(store.findUser("maria").isEmpty());
    assertTrue(store.findUserByEmail("maria@email.com").isEmpty());
  }

  // Os dois casos respondem com 409, mas precisam ser detectados separadamente
  @Test
  void rejectsDuplicateNameOrEmail() {
    joao();
    assertTrue(store.createUser("joao", "outro@email.com", "x", Role.USER, T0).isEmpty());
    assertTrue(store.createUser("outro", "joao.silva@email.com", "x", Role.USER, T0).isEmpty());
  }

  @Test
  void rejectedCreateDoesNotConsumeAnId() {
    assertEquals(1, joao().id());
    store.createUser("joao", "outro@email.com", "x", Role.USER, T0);
    assertEquals(2, maria().id());
  }

  @Test
  void countsAdmins() {
    joao();
    assertEquals(0, store.countAdmins());
    maria();
    assertEquals(1, store.countAdmins());
  }

  // Trocar o próprio `user` mantém `id`, `email` e data de cadastro
  @Test
  void renameKeepsTheRestOfTheRecord() {
    User joao = joao();
    assertTrue(store.updateUser(joao.with("joaosilva", null)).isPresent());

    User renamed = store.findUser("joaosilva").orElseThrow();
    assertEquals(joao.id(), renamed.id());
    assertEquals("joao.silva@email.com", renamed.email());
    assertEquals(T0, renamed.createdAt());
    assertTrue(store.findUser("joao").isEmpty());
  }

  @Test
  void nullFieldKeepsTheCurrentValue() {
    User joao = joao();
    store.updateUser(joao.with("joaosilva", null));
    assertEquals("senha123", store.findUser("joaosilva").orElseThrow().password());
  }

  @Test
  void renamingToAnotherUsersNameIsAConflict() {
    User joao = joao();
    maria();
    assertTrue(store.updateUser(joao.with("maria", null)).isEmpty());
    assertEquals("joao", store.findUser("joao").orElseThrow().name());
  }

  @Test
  void keepingOwnNameIsNotAConflict() {
    User joao = joao();
    assertTrue(store.updateUser(joao.with("joao", "nova1")).isPresent());
    assertEquals("nova1", store.findUser("joao").orElseThrow().password());
  }

  @Test
  void updatingAnUnknownIdIsAProgrammingError() {
    User ghost = new User(999, "fantasma", "f@email.com", "x", Role.USER, T0);
    assertThrows(IllegalStateException.class, () -> store.updateUser(ghost));
  }

  @Test
  void oneSessionPerUser() {
    User joao = joao();
    assertTrue(store.createSession(TOKEN_A, joao.id(), T0).isPresent());
    assertTrue(store.createSession(TOKEN_B, joao.id(), T0).isEmpty());
    assertTrue(store.findSession(TOKEN_A).isPresent());
    assertTrue(store.findSession(TOKEN_B).isEmpty());
  }

  @Test
  void expiryIsThirtyIdleMinutes() {
    Session session = new Session(TOKEN_A, 1, T0);
    assertFalse(session.isExpiredAt(ALMOST));
    assertTrue(session.isExpiredAt(LIMIT));
  }

  @Test
  void touchRenewsTheCountdown() {
    User joao = joao();
    store.createSession(TOKEN_A, joao.id(), T0);
    store.touchSession(TOKEN_A, ALMOST);
    assertFalse(store.findSession(TOKEN_A).orElseThrow().isExpiredAt(LIMIT));
  }

  @Test
  void touchingAnUnknownTokenIsIgnored() {
    store.touchSession(TOKEN_A, T0);
    assertTrue(store.findSession(TOKEN_A).isEmpty());
  }

  // Sessão vencida não é 409, o login novo toma o lugar dela
  @Test
  void expiredSessionIsReplaced() {
    User joao = joao();
    store.createSession(TOKEN_A, joao.id(), T0);
    assertTrue(store.createSession(TOKEN_B, joao.id(), LIMIT).isPresent());
    assertTrue(store.findSession(TOKEN_A).isEmpty());
    assertEquals(TOKEN_B, store.findSessionOfUser(joao.id()).orElseThrow().token());
  }

  // Quem derruba a sessão é `ON DELETE CASCADE`
  @Test
  void deletingAUserCascadesToTheirSession() {
    User maria = maria();
    store.createSession(TOKEN_A, maria.id(), T0);
    store.deleteUser(maria.id());
    assertTrue(store.findUser("maria").isEmpty());
    assertTrue(store.findSession(TOKEN_A).isEmpty());
    assertEquals(0, store.countAdmins());
  }

  @Test
  void eachInMemoryDatabaseIsIndependent() {
    joao();
    try (SqliteStore other = SqliteStore.openInMemory()) {
      assertTrue(other.findUser("joao").isEmpty());
    }
  }

  @Test
  void aFileDatabaseSurvivesReopening(@TempDir Path dir) {
    Path file = dir.resolve("reservas.db");

    try (SqliteStore first = SqliteStore.openFile(file.toString())) {
      User joao = first.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
      first.createSession(TOKEN_A, joao.id(), T0);
    }

    try (SqliteStore second = SqliteStore.openFile(file.toString())) {
      User joao = second.findUser("joao").orElseThrow();
      assertEquals(T0, joao.createdAt());
      assertEquals(Role.USER, joao.role());
    }
  }
}
