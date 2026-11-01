package reservas.protocol;

import java.util.Optional;

// Os nomes das constantes são os nomes do protocolo em maiúsculas: `REGISTER` trafega como `register`.
// O `op` de uma resposta é sempre o `op` da requisição acrescido de `_response`; exceto `ERROR_OP`, quando a requisição não pôde ser interpretada
public enum Op implements WireValue {
  REGISTER("register"),
  LOGIN("login"),
  LOGOUT("logout"),
  READ_USER("read_user"),
  UPDATE_USER("update_user"),
  DELETE_USER("delete_user"),

  ADMIN_LIST_USERS("admin_list_users"),
  ADMIN_READ_USER("admin_read_user"),
  ADMIN_UPDATE_USER("admin_update_user"),
  ADMIN_DELETE_USER("admin_delete_user"),

  CREATE_ROOM("create_room"),
  LIST_ROOMS("list_rooms"),
  READ_ROOM("read_room"),
  UPDATE_ROOM("update_room"),
  DELETE_ROOM("delete_room"),

  CHECK_AVAILABILITY("check_availability"),
  CREATE_RESERVATION("create_reservation"),
  LIST_RESERVATIONS("list_reservations"),
  READ_RESERVATION("read_reservation"),
  UPDATE_RESERVATION("update_reservation"),
  DELETE_RESERVATION("delete_reservation");

  public static final String ERROR_OP = "error";
  private static final String RESPONSE_SUFFIX = "_response";
  private final String wireName;

  Op(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }

  public String responseName() {
    return wireName + RESPONSE_SUFFIX;
  }

  public static Optional<Op> fromWire(String wireName) {
    return WireValues.fromWire(values(), wireName);
  }
}
