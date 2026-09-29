package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MessagesTest {

  @Test
  void protocolLevelTextsMatchTheSpreadsheet() {
    assertEquals("Requisicao invalida", Messages.INVALID_REQUEST);
    assertEquals("Operacao desconhecida", Messages.UNKNOWN_OPERATION);
    assertEquals("Mensagem excede o tamanho maximo", Messages.MESSAGE_TOO_LARGE);
    assertEquals("Erro interno do servidor", Messages.INTERNAL_ERROR);
  }
}
