package reservas.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

final class RoomLocks {
  private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();

  <T> T holding(long roomId, Supplier<T> work) {
    synchronized (locks.computeIfAbsent(roomId, id -> new Object())) {
      return work.get();
    }
  }
}
