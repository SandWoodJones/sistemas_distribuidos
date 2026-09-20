package reservas.protocol;

import java.util.regex.Pattern;

public final class Formats {
  private Formats() {
  }

  // Envelope
  public static final Pattern OP = Pattern.compile("^[a-z_]{3,30}$");
  public static final Pattern STATUS = Pattern.compile("^[0-9]{3}$");
  public static final Pattern TOKEN = Pattern.compile("^[a-f0-9]{64}$");

  // Usuários
  public static final Pattern USER = Pattern.compile("^[a-z]{1,30}$");
  public static final Pattern PASSWORD = Pattern.compile("^[A-Za-z0-9]{1,20}$");
  public static final Pattern EMAIL = Pattern.compile("^[a-z0-9.]+@[a-z0-9]+(\\.[a-z]+){1,2}$");
  public static final Pattern ROLE = Pattern.compile("^(user|admin)$");

  // IDs (`room_id`, `reservation_id`, `count`)
  public static final Pattern ID = Pattern.compile("^[0-9]{1,10}$");

  // Salas
  public static final Pattern ROOM_NAME = Pattern.compile("^[A-Za-z0-9 -]{1,30}$");

  // (`capacity`, `participants`, `min_capacity`)
  public static final Pattern CAPACITY = Pattern.compile("^[1-9][0-9]{0,3}$");
  public static final Pattern LOCATION = Pattern.compile("^[A-Za-z0-9 ,.-]{1,60}$");
  public static final Pattern ROOM_STATUS = Pattern.compile("^(active|inactive)$");

  // Reservas
  public static final Pattern DATE = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
  public static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
  public static final Pattern TOPIC = Pattern.compile("^[A-Za-z0-9 ,.-]{1,60}$");
  public static final Pattern AVAILABLE = Pattern.compile("^(true|false)$");
  public static final Pattern CREATED_AT = Pattern
      .compile("^[0-9]{4}-[0-9]{2}-[0-9]{2} " + "([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$");

  // Filtros
  public static final Pattern SCOPE = Pattern.compile("^(mine|all)$");

  public static boolean matches(Pattern format, String value) {
    return value != null && format.matcher(value).matches();
  }
}
