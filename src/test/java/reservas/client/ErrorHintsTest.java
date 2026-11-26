package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import reservas.protocol.TestJson;

class ErrorHintsTest {
  private static Optional<String> hint(String op, String status, String message) {
    return ErrorHints.of(
        TestJson.object("{'op':'" + op + "','status':'" + status + "','message':'" + message + "'}"));
  }

  @Test
  void explainsWhatTheFixedServerTextCannot() {
    assertTrue(hint("create_reservation_response", "400", "Dados da reserva em formato invalido").orElseThrow()
        .contains("aula que ainda nao comecou"));
    assertEquals(Optional.of("preencha pelo menos um campo"),
        hint("update_reservation_response", "400", "Nenhum dado para atualizar"));
    assertTrue(hint("read_user_response", "401", "Token invalido ou expirado").orElseThrow().contains("login"));
    assertTrue(hint("update_reservation_response", "403", "Permissao insuficiente").orElseThrow().contains("dono"));
  }

  @Test
  void staysQuietWhenTheServerTextIsEnough() {
    assertEquals(Optional.empty(),
        hint("create_reservation_response", "409", "Sala ja reservada no periodo solicitado"));
    assertEquals(Optional.empty(), hint("delete_user_response", "403", "Senha incorreta"));
    assertEquals(Optional.empty(), hint("create_reservation_response", "201", "Reserva realizada com sucesso"));
  }
}
