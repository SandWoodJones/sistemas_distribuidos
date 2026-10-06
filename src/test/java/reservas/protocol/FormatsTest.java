package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class FormatsTest {
  private static void accepts(Pattern format, String... values) {
    for (String value : values) {
      assertTrue(Formats.matches(format, value), "deveria aceitar: " + value);
    }
  }

  private static void rejects(Pattern format, String... values) {
    for (String value : values) {
      assertFalse(Formats.matches(format, value), "deveria recusar: " + value);
    }
  }

  @Test
  void op() {
    accepts(Formats.OP, "login", "login_response", "admin_list_users", "log");
    rejects(Formats.OP, "Login", "ab", "login1", "", "a".repeat(31));
  }

  @Test
  void status() {
    accepts(Formats.STATUS, "200", "201", "409", "500");
    rejects(Formats.STATUS, "20", "2000", "abc", "");
  }

  @Test
  void token() {
    accepts(Formats.TOKEN, "c0fc3c713f09a43384ac08f7d91fca430dcbc6466fff9284ce4571bdc2c8f9f9", "a".repeat(64));
    rejects(Formats.TOKEN, "A".repeat(64), "g".repeat(64), "a".repeat(63), "a".repeat(65), "");
  }

  // Requisitos.pdf permite dígitos, a planilha v2.0 não
  @Test
  void user() {
    accepts(Formats.USER, "joao", "maria", "a", "a".repeat(30));
    rejects(Formats.USER, "joao123", "Joao", "jo ao", "joão", "", "a".repeat(31));
  }

  @Test
  void password() {
    accepts(Formats.PASSWORD, "senha123", "Senha", "a".repeat(20));
    rejects(Formats.PASSWORD, "senha 123", "senha@", "senhã", "", "a".repeat(21));
  }

  @Test
  void email() {
    accepts(Formats.EMAIL, "joao.silva@email.com", "admin@utfpr.edu.br", "a@b.c",
        "joao@alunos.utfpr.edu.br", "a@b.c.d.e.f");
    rejects(Formats.EMAIL, "Joao@email.com", "joao@email", "joao silva@email.com", "@email.com",
        "a@b.c.d.e.f.g", "");
  }

  @Test
  void role() {
    accepts(Formats.ROLE, "user", "admin");
    rejects(Formats.ROLE, "Admin", "adm", "useradmin", "");
  }

  @Test
  void id() {
    accepts(Formats.ID, "1", "27", "0123456789");
    rejects(Formats.ID, "01234567890", "1a", "-1", "");
  }

  @Test
  void roomName() {
    accepts(Formats.ROOM_NAME, "Sala de Estudo B12", "A-1");
    rejects(Formats.ROOM_NAME, "Sala #1", "Salão", "", "a".repeat(31));
  }

  @Test
  void capacity() {
    accepts(Formats.CAPACITY, "1", "6", "12", "9999");
    rejects(Formats.CAPACITY, "0", "012", "10000", "abc", "");
  }

  @Test
  void location() {
    accepts(Formats.LOCATION, "Bloco B - 1 andar", "Sala 3, andar 2.");
    rejects(Formats.LOCATION, "Bloco #B", "Blocô", "", "a".repeat(61));
  }

  @Test
  void roomStatus() {
    accepts(Formats.ROOM_STATUS, "active", "inactive");
    rejects(Formats.ROOM_STATUS, "Active", "ativo", "");
  }

  @Test
  void date() {
    accepts(Formats.DATE, "2026-09-15", "9999-99-99");
    rejects(Formats.DATE, "2026-9-15", "15/09/2026", "2026-09-15 14:00", "");
  }

  @Test
  void time() {
    accepts(Formats.TIME, "14:00", "00:00", "23:59");
    rejects(Formats.TIME, "24:00", "14:60", "2:00", "14:0", "1400", "");
  }

  @Test
  void topic() {
    accepts(Formats.TOPIC, "Reuniao de projeto");
    rejects(Formats.TOPIC, "Reunião de projeto", "Reuniao #1", "", "a".repeat(61));
  }

  @Test
  void available() {
    accepts(Formats.AVAILABLE, "true", "false");
    rejects(Formats.AVAILABLE, "True", "1", "");
  }

  @Test
  void createdAt() {
    accepts(Formats.CREATED_AT, "2026-09-09 14:32:10");
    rejects(Formats.CREATED_AT, "2026-09-09T14:32:10", "2026-09-09 14:32", "2026-09-09", "");
  }

  @Test
  void scope() {
    accepts(Formats.SCOPE, "mine", "all");
    rejects(Formats.SCOPE, "Mine", "todos", "");
  }

  // Valor nulo é sempre 400
  @Test
  void nullIsNeverValid() {
    rejects(Formats.USER, (String) null);
    rejects(Formats.TOKEN, (String) null);
    rejects(Formats.DATE, (String) null);
  }
}
