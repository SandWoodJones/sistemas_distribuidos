package reservas.server;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import reservas.protocol.ClassPeriod;
import reservas.protocol.Role;
import reservas.protocol.Slot;

public final class Bootstrap {
  public static final String ADMIN_USER = "admin";
  public static final String ADMIN_EMAIL = "admin@admin.com";
  public static final String ADMIN_PASSWORD = "admin";

  // Pré-cadastro pedido pelo professor para os testes
  public static final int DEMO_LABS = 6;
  public static final int DEMO_USERS = 33;
  public static final String DEMO_PASSWORD = "senha123";
  static final int LAB_CAPACITY = 30;
  static final String LAB_LOCATION = "Bloco A";

  private Bootstrap() {
  }

  // Vazio se já existe algum administrador
  public static Optional<User> seedAdmin(SqliteStore store, Instant now) {
    if (store.countAdmins() > 0) {
      return Optional.empty();
    }

    return store.createUser(ADMIN_USER, ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN, now);
  }

  // Só num banco novo, sem nenhuma sala e sem usuário além do admin: um banco em uso não muda
  public static boolean seedDemo(SqliteStore store, Instant now) {
    if (!store.listRooms(1).isEmpty() || store.listUsers().size() > 1) {
      return false;
    }

    for (int i = 1; i <= DEMO_LABS; i++) {
      store.createRoom("Laboratorio " + i, LAB_CAPACITY, LAB_LOCATION, List.of(), everySlot());
    }
    for (int i = 0; i < DEMO_USERS; i++) {
      String name = demoUserName(i);
      store.createUser(name, name + "@email.com", DEMO_PASSWORD, Role.USER, now);
    }

    return true;
  }

  // usera a userz, depois useraa, userab...: `user` só aceita letras
  public static String demoUserName(int i) {
    String suffix = i < 26 ? String.valueOf((char) ('a' + i)) : "a" + (char) ('a' + i - 26);
    return "user" + suffix;
  }

  // Segunda a sábado, todas as aulas
  private static List<Slot> everySlot() {
    return Arrays.stream(DayOfWeek.values()).filter(day -> day != DayOfWeek.SUNDAY)
        .flatMap(day -> Arrays.stream(ClassPeriod.values()).map(period -> new Slot(day, period))).toList();
  }

  // Os tokens da execução anterior morreram com os clientes; sem isso, o próximo login dá 409 por até 30 min
  public static int endPreviousSessions(SqliteStore store) {
    return store.deleteAllSessions();
  }
}
