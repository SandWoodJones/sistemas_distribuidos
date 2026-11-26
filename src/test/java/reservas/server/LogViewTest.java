package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogViewTest {
  private static final String RECEIVED = "10:00:00.000 INFO  [t] c1 p - Connection - recebido {\"op\":\"login\"}\n";
  private static final String SENT = "10:00:00.001 INFO  [t] c1 p - Connection - enviado {\"op\":\"login_response\"}\n";
  private static final String OPENED = "10:00:00.002 INFO  [t] c1 p - Connection - conexao aberta\n";
  private static final String WARNED = "10:00:00.003 WARN  [t] c1 p - ServerApp - 400 Dados em formato invalido\n";

  @Test
  void classifiesLinesByLevelAndTraffic() {
    assertEquals(LogLine.TRAFFIC, LogLine.of(RECEIVED));
    assertEquals(LogLine.TRAFFIC, LogLine.of(SENT));
    assertEquals(LogLine.OTHER, LogLine.of(OPENED));
    assertEquals(LogLine.WARN, LogLine.of(WARNED));
    assertEquals(LogLine.ERROR, LogLine.of("10:00:00.004 ERROR [t] - - - ServerApp - falha\n"));
  }

  @Test
  void keepsOnlyTheLastLines() {
    LogView view = new LogView(3);
    for (String line : new String[] { OPENED, RECEIVED, WARNED, SENT }) {
      view.add(line);
    }

    assertEquals(RECEIVED + WARNED + SENT, view.getText());
  }
}
