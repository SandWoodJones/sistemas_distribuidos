package reservas.protocol;

// Mensagem que não pode ser processada, carrega o código de status e o texto que o protocolo exige na resposta
public class ProtocolException extends RuntimeException {
  private final Status status;
  private final String wireMessage;

  public ProtocolException(Status status, String wireMessage) {
    this(status, wireMessage, null, null);
  }

  public ProtocolException(Status status, String wireMessage, String detail) {
    this(status, wireMessage, detail, null);
  }

  public ProtocolException(Status status, String wireMessage, String detail, Throwable cause) {
    super(detail == null ? wireMessage : wireMessage + " | " + detail, cause);
    this.status = status;
    this.wireMessage = wireMessage;
  }

  public Status status() {
    return status;
  }

  public String wireMessage() {
    return wireMessage;
  }
}
