package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import reservas.protocol.Status;

@Timeout(20)
class StoreConcurrencyTest {
  private static final Instant T0 = Instant.parse("2026-09-09T17:32:10Z");
  private static final int THREADS = 20;

  private final SqliteStore store = SqliteStore.openInMemory();
  private final ServerContext context = new ServerContext(store, Clock.fixed(T0, ZoneOffset.UTC), () -> "a".repeat(64));
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
}
