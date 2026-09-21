package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import reservas.protocol.Formats;
import reservas.protocol.Role;

class BootstrapTest {
  private static final Instant T0 = Instant.parse("2026-09-09T14:32:10Z");

  private final SqliteStore store = SqliteStore.openInMemory();

  @AfterEach
  void closeStore() {
    store.close();
  }

  // O admin semeado tem de conseguir fazer `login` pelo protocolo normal
  @Test
  void seededCredentialsPassTheProtocolRegexes() {
    assertTrue(Formats.matches(Formats.USER, Bootstrap.ADMIN_USER));
    assertTrue(Formats.matches(Formats.EMAIL, Bootstrap.ADMIN_EMAIL));
    assertTrue(Formats.matches(Formats.PASSWORD, Bootstrap.ADMIN_PASSWORD));
  }

  @Test
  void seedsOneAdmin() {
    User admin = Bootstrap.seedAdmin(store, T0).orElseThrow();
    assertEquals(Role.ADMIN, admin.role());
    assertEquals(Bootstrap.ADMIN_EMAIL, admin.email());
    assertEquals(1, store.countAdmins());
  }

  @Test
  void seedingIsIdempotent() {
    Bootstrap.seedAdmin(store, T0);
    assertTrue(Bootstrap.seedAdmin(store, T0).isEmpty());
    assertEquals(1, store.countAdmins());
  }
}
