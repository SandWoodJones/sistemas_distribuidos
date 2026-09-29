package reservas.protocol;

// Textos de protocolo que não pertencem a nenhuma operação
public final class Messages {
  public static final String INVALID_REQUEST = "Requisicao invalida";
  public static final String UNKNOWN_OPERATION = "Operacao desconhecida";
  public static final String MESSAGE_TOO_LARGE = "Mensagem excede o tamanho maximo";
  public static final String INTERNAL_ERROR = "Erro interno do servidor";

  private Messages() {
  }
}
