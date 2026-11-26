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

  // Sempre o menor id primeiro: duas trocas em sentidos opostos não travam uma na outra
  <T> T holdingBoth(long roomId, long otherRoomId, Supplier<T> work) {
    if (roomId == otherRoomId) {
      return holding(roomId, work);
    }

    return holding(Math.min(roomId, otherRoomId), () -> holding(Math.max(roomId, otherRoomId), work));
  }
}
