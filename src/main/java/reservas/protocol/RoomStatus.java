package reservas.protocol;

import java.util.Optional;

// Estado da sala
public enum RoomStatus implements WireValue {
  ACTIVE("active"),
  INACTIVE("inactive");

  private final String wireName;

  RoomStatus(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }

  public static Optional<RoomStatus> fromWire(String wireName) {
    return WireValues.fromWire(values(), wireName);
  }
}
