package reservas.protocol;

// Códigos de resposta, trafegam como strings
public enum Status {
  OK(200),
  CREATED(201),
  BAD_REQUEST(400),
  UNAUTHORIZED(401),
  FORBIDDEN(403),
  NOT_FOUND(404),
  CONFLICT(409),
  INTERNAL_SERVER_ERROR(500);

  private final String code;

  Status(int code) {
    this.code = Integer.toString(code);
  }

  public String code() {
    return code;
  }
}
