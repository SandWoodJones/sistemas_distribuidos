package reservas.protocol;

// Nomes de chaves e formatos de valores
public final class Fields {
  private Fields() {
  }

  // Envelope
  public static final String OP = "op";
  public static final String STATUS = "status";
  public static final String MESSAGE = "message";
  public static final String TOKEN = "token";

  // Usuários
  public static final String USER = "user";
  public static final String PASSWORD = "password";
  public static final String EMAIL = "email";
  public static final String ROLE = "role";
  public static final String TARGET_USER = "target_user";
  public static final String CREATED_AT = "created_at";

  // Salas
  public static final String ROOM_ID = "room_id";
  public static final String NAME = "name";
  public static final String CAPACITY = "capacity";
  public static final String LOCATION = "location";
  public static final String RESOURCES = "resources";
  public static final String ROOM_STATUS = "room_status";

  // Reservas
  public static final String RESERVATION_ID = "reservation_id";
  public static final String DATE = "date";
  public static final String START_TIME = "start_time";
  public static final String END_TIME = "end_time";
  public static final String TOPIC = "topic";
  public static final String PARTICIPANTS = "participants";
  public static final String AVAILABLE = "available";

  // Listagens
  public static final String COUNT = "count";
  public static final String USERS = "users";
  public static final String ROOMS = "rooms";
  public static final String RESERVATIONS = "reservations";

  // Filtros
  public static final String SCOPE = "scope";
  public static final String MIN_CAPACITY = "min_capacity";

  public static final String SCOPE_MINE = "mine";
  public static final String SCOPE_ALL = "all";
}
