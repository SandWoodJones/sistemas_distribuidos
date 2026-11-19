package reservas.protocol;

public enum SlotStatus implements WireValue {
  FREE("livre"),
  UNAVAILABLE("indisponivel");

  private final String wireName;

  SlotStatus(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
