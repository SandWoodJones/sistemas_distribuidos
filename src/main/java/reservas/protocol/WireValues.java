package reservas.protocol;

import java.util.Optional;

public final class WireValues {
  private WireValues() {}

  public static<T extends WireValue> Optional<T> fromWire(T[] values, String wireName) {
    for (T value : values) {
      if (value.wireName().equals(wireName)) {
        return Optional.of(value);
      }
    }

    return Optional.empty();
  }
}
