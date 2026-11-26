package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrafficLineTest {
  @Test
  void colorsAnsweredErrorsApartFromSuccesses() {
    assertEquals(TrafficLine.SENT, TrafficLine.of("-> {\"op\":\"login\"}"));
    assertEquals(TrafficLine.RECEIVED_OK, TrafficLine.of("<- {\"op\":\"login_response\",\"status\":\"200\"}"));
    assertEquals(TrafficLine.RECEIVED_ERROR, TrafficLine.of("<- {\"op\":\"login_response\",\"status\":\"409\"}"));
    assertEquals(TrafficLine.RECEIVED_ERROR, TrafficLine.of("<- {\"op\":\"error\",\"status\":\"500\"}"));
    assertEquals(TrafficLine.NOTE, TrafficLine.of("** conexao perdida, reconectando"));
  }
}
