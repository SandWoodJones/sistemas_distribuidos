package reservas.protocol;

// Mensagem que não pode ser processada, carrega o código de status e o texto que o protocolo exige na resposta
public final class ProtocolException extends RuntimeException {
  private final Status status;
  private final String wireMessage;

  private ProtocolException(Status status, String wireMessage, String detail, Throwable cause) {
    super(detail == null ? wireMessage : wireMessage + " | " + detail, cause);
    this.status = status;
    this.wireMessage = wireMessage;
  }

  public static ProtocolException badRequest(String wireMessage, String detail) {
    return badRequest(wireMessage, detail, null);
  }

  public static ProtocolException badRequest(String wireMessage, String detail, Throwable cause) {
    return new ProtocolException(Status.BAD_REQUEST, wireMessage, detail, cause);
  }

  public static ProtocolException unauthorized(String wireMessage, String detail) {
    return new ProtocolException(Status.UNAUTHORIZED, wireMessage, detail, null);
  }

  public static ProtocolException forbidden(String wireMessage, String detail) {
    return new ProtocolException(Status.FORBIDDEN, wireMessage, detail, null);
  }

  public static ProtocolException conflict(String wireMessage, String detail) {
    return new ProtocolException(Status.CONFLICT, wireMessage, detail, null);
  }

  public static ProtocolException internal(String detail) {
    return internal(detail, null);
  }

  public static ProtocolException internal(String detail, Throwable cause) {
    return new ProtocolException(Status.INTERNAL_SERVER_ERROR, Messages.INTERNAL_ERROR, detail, cause);
  }

  public Status status() {
    return status;
  }

  public String wireMessage() {
    return wireMessage;
  }
}
