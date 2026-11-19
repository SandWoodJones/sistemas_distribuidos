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
    String local94 = "a".repeat(94);
    accepts(Formats.EMAIL, local94 + "@b.com", "joao.silva@email.com", "admin@utfpr.edu.br", "a@b.co",
        "joao@alunos.utfpr.edu.br", "joao@meu-provedor.com.br", "joao_silva@email.com",
        "joao-silva@email.com");
    rejects(Formats.EMAIL, local94 + "a@b.com", "Joao@email.com", "joao@email", "joao silva@email.com", "@email.com",
        "a@b.c", "a@b.c.d.e.f", "");
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
  void resource() {
    accepts(Formats.RESOURCE, "projetor", "Quadro branco", "TV-55", "0");
    rejects(Formats.RESOURCE, "projetor/tv", "Televisão", "", "a".repeat(31));
  }

  @Test
  void slot() {
    accepts(Formats.SLOT, "2M1", "4T3", "7M6", "6N5");
    rejects(Formats.SLOT, "1M1", "8M1", "2N6", "2M7", "2m1", "2M", "M1", "2M10", "");
  }

  @Test
  void date() {
    accepts(Formats.DATE, "2026-09-15", "2026-01-01", "2026-12-31", "2026-02-30");
    rejects(Formats.DATE, "2026-13-01", "2026-00-10", "2026-09-00", "2026-09-32", "2026-9-15", "15/09/2026",
        "2026-09-15 14:00", "");
  }

  @Test
  void classBoundaries() {
    accepts(Formats.START_TIME, "07:30", "08:20", "09:10", "10:20", "11:10", "12:00", "13:00", "13:50", "14:40",
        "15:50", "16:40", "17:30", "18:40", "19:30", "20:20", "21:20", "22:10");
    rejects(Formats.START_TIME, "08:00", "10:00", "23:00", "7:30", "07:30:99", "");
    accepts(Formats.END_TIME, "08:20", "09:10", "10:00", "11:10", "12:00", "12:50", "13:50", "14:40", "15:30", "16:40",
        "17:30", "18:20", "19:30", "20:20", "21:10", "22:10", "23:00");
    rejects(Formats.END_TIME, "07:30", "10:20", "13:00", "18:40", "");
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
