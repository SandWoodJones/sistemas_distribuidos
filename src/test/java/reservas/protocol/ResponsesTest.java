package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class ResponsesTest {
  // `op` é sempre a primeira chave, status trafega como string
  @Test
  void envelopeKeepsKeyOrderAndStringStatus() {
    JsonObject response = Responses.of(Op.LOGIN, Status.OK, "Login realizado com sucesso");
    assertEquals(List.of(Fields.OP, Fields.STATUS, Fields.MESSAGE), List.copyOf(response.keySet()));
    assertEquals("200", response.get(Fields.STATUS).getAsString());
    assertEquals("{\"op\":\"login_response\",\"status\":\"200\",\"message\":\"Login realizado com sucesso\"}\n",
        Codec.encode(response));
  }

  @Test
  void responseOpCarriesTheSuffix() {
    assertEquals("register_response",
        Responses.of(Op.REGISTER, Status.CREATED, "Usuario cadastrado com sucesso").get(Fields.OP).getAsString());
  }

  @Test
  void protocolErrorsUseTheErrorOp() {
    assertEquals("{\"op\":\"error\",\"status\":\"400\",\"message\":\"Requisicao invalida\"}",
        Responses.invalidRequest().toString());
    assertEquals("{\"op\":\"error\",\"status\":\"400\",\"message\":\"Operacao desconhecida\"}",
        Responses.unknownOperation().toString());
    assertEquals("{\"op\":\"error\",\"status\":\"400\",\"message\":\"Mensagem excede o tamanho maximo\"}",
        Responses.messageTooLarge().toString());
  }
}
