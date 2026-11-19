package reservas.protocol;

// Textos de protocolo que não pertencem a uma operação só
public final class Messages {
  public static final String INVALID_REQUEST = "Requisicao invalida";
  public static final String UNKNOWN_OPERATION = "Operacao desconhecida";
  public static final String MESSAGE_TOO_LARGE = "Mensagem excede o tamanho maximo";
  public static final String INTERNAL_ERROR = "Erro interno do servidor";
  public static final String TOKEN_BAD_REQUEST = "Token em formato invalido";
  public static final String DATA_BAD_REQUEST = "Dados em formato invalido";
  public static final String NOTHING_TO_UPDATE = "Nenhum dado para atualizar";

  private Messages() {
  }
}
