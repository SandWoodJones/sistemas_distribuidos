package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import reservas.protocol.TestJson;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.Slot;
import reservas.protocol.TimeWindow;
import reservas.protocol.Status;

@Timeout(20)
class StoreConcurrencyTest {
  private static final int THREADS = 20;

  private final SqliteStore store = SqliteStore.openInMemory();
  private final ServerContext context = Fixtures.context(store);
  private final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

  @AfterEach
  void closeStore() {
    store.close();
  }

  private static String name(int i) {
    return "user" + (char) ('a' + i);
  }

  private void register(String user) {
    AuthHandlers.register(TestJson.object("{'op':'register','email':'" + user
        + "@email.com','user':'" + user + "','password':'senha123'}"), context);
  }

  // Todas as threads largam juntas
  private void race(List<Runnable> work) throws InterruptedException {
    CountDownLatch start = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<>();

    for (Runnable task : work) {
      Thread thread = new Thread(() -> {
        try {
          start.await();
          task.run();
        } catch (Throwable e) {
          failures.add(e);
        }
      });
      threads.add(thread);
      thread.start();
    }

    start.countDown();
    for (Thread thread : threads) {
      thread.join(10_000);
    }
  }

  // 20 threads gravando e lendo a mesma conexão JDBC
  @Test
  void registersEveryUserUnderConcurrentLoad() throws InterruptedException {
    List<Runnable> work = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      String user = name(i);
      work.add(() -> {
        register(user);
        store.findUser(user).orElseThrow();
        store.countAdmins();
      });
    }

    race(work);

    assertTrue(failures.isEmpty(), failures.toString());
    for (int i = 0; i < THREADS; i++) {
      assertTrue(store.findUser(name(i)).isPresent(), name(i) + " nao foi gravado");
    }
  }

  // O mesmo `user` em vinte threads da um 201 e dezenove 409
  @Test
  void letsExactlyOneRegistrationWinTheSameUser() throws InterruptedException {
    AtomicInteger created = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    List<Runnable> work = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      work.add(() -> {
        try {
          register("joao");
          created.incrementAndGet();
        } catch (ProtocolException e) {
          if (e.status() != Status.CONFLICT) {
            throw e;
          }

          conflicts.incrementAndGet();
        }
      });
    }

    race(work);

    assertTrue(failures.isEmpty(), failures.toString());
    assertEquals(1, created.get());
    assertEquals(THREADS - 1, conflicts.get());
  }

  // Vinte usuários reservando a mesma sala e horário: um 201 e dezenove 409
  @Test
  void letsExactlyOneReservationWinTheSameSlot() throws InterruptedException {
    Room room = store.createRoom("Sala B12", 12, "Bloco B", List.of(), List.of(Slot.fromWire("3T1").orElseThrow()))
        .orElseThrow();
    AtomicInteger created = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    List<Runnable> work = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      String token = String.format("%064x", i);
      User user = store.createUser(name(i), name(i) + "@email.com", "senha123", Role.USER, Fixtures.T0)
          .orElseThrow();
      store.createSession(token, user.id(), Fixtures.T0).orElseThrow();

      work.add(() -> {
        try {
          ReservationHandlers.createReservation(TestJson.object("{'op':'create_reservation','token':'" + token
              + "','room_id':'" + room.id() + "','date':'2026-09-15','start_time':'13:00','end_time':'13:50',"
              + "'topic':'Aula','participants':'6'}"), context);
          created.incrementAndGet();
        } catch (ProtocolException e) {
          if (e.status() != Status.CONFLICT) {
            throw e;
          }

          conflicts.incrementAndGet();
        }
      });
    }

    race(work);

    assertTrue(failures.isEmpty(), failures.toString());
    assertEquals(1, created.get());
    assertEquals(THREADS - 1, conflicts.get());
    assertEquals(1, store.listReservationsOn(room.id(), LocalDate.parse("2026-09-15")).size());
  }

  // Trocas de sala em sentidos opostos (A para B e B para A) ao mesmo tempo: com os locks em ordem de id, ninguém
  // trava; o @Timeout derruba o teste se travar
  @Test
  void opposingRoomSwapsDoNotDeadlock() throws InterruptedException {
    List<Slot> tuesday = List.of(Slot.fromWire("3T1").orElseThrow(), Slot.fromWire("3T2").orElseThrow());
    long roomA = store.createRoom("Sala A", 12, "Bloco A", List.of(), tuesday).orElseThrow().id();
    long roomB = store.createRoom("Sala B", 12, "Bloco B", List.of(), tuesday).orElseThrow().id();

    List<Runnable> work = new ArrayList<>();
    String[][] swaps = { { "13:00", "13:50" }, { "13:50", "14:40" } };
    for (int i = 0; i < swaps.length; i++) {
      String token = String.format("%064x", i);
      User user = store.createUser(name(i), name(i) + "@email.com", "senha123", Role.USER, Fixtures.T0)
          .orElseThrow();
      store.createSession(token, user.id(), Fixtures.T0).orElseThrow();
      long home = i == 0 ? roomA : roomB;
      long away = i == 0 ? roomB : roomA;
      TimeWindow window = new TimeWindow(LocalDate.parse("2026-09-15"), LocalTime.parse(swaps[i][0]),
          LocalTime.parse(swaps[i][1]));
      long id = store.createReservation(new Booking(home, window, "Aula", 6), user.id(), Fixtures.T0).id();

      work.add(() -> {
        for (int round = 0; round < 50; round++) {
          move(token, id, round % 2 == 0 ? away : home);
        }
      });
    }

    race(work);

    assertTrue(failures.isEmpty(), failures.toString());
    assertEquals(2, store.listReservations(new ReservationFilter(null, LocalDate.parse("2026-09-15"), null)).size());
  }

  private void move(String token, long reservationId, long roomId) {
    ReservationHandlers.updateReservation(TestJson.object("{'op':'update_reservation','token':'" + token
        + "','reservation_id':'" + reservationId + "','room_id':'" + roomId
        + "','start_time':'','end_time':'','topic':'','participants':''}"), context);
  }
}
