package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;

class LogPaneAppenderTest {
  // Contexto próprio: os testes usam o logback-test.xml, sem o painel
  private static Logger paneLogger() {
    LoggerContext context = new LoggerContext();
    PatternLayout layout = new PatternLayout();
    layout.setContext(context);
    layout.setPattern("%msg%n");
    layout.start();

    LogPaneAppender appender = new LogPaneAppender();
    appender.setContext(context);
    appender.setLayout(layout);
    appender.start();

    Logger logger = context.getLogger("painel");
    logger.addAppender(appender);
    return logger;
  }

  private static List<String> replayed() {
    List<String> lines = new ArrayList<>();
    Consumer<String> listener = lines::add;
    LogPaneAppender.subscribe(listener);
    LogPaneAppender.unsubscribe(listener);
    return lines;
  }

  @Test
  void aLoggedLineReachesTheSubscribersAndIsReplayedToLateOnes() {
    List<String> received = new ArrayList<>();
    Consumer<String> listener = received::add;
    LogPaneAppender.subscribe(listener);
    received.clear();

    try {
      paneLogger().info("marca-para-o-painel");
    } finally {
      LogPaneAppender.unsubscribe(listener);
    }

    assertEquals(List.of("marca-para-o-painel" + System.lineSeparator()), received);
    assertTrue(replayed().contains("marca-para-o-painel" + System.lineSeparator()));
  }

  @Test
  void keepsOnlyTheLastLines() {
    for (int i = 0; i <= LogPaneAppender.KEPT_LINES; i++) {
      LogPaneAppender.publish("linha " + i + "\n");
    }

    List<String> lines = replayed();
    assertEquals(LogPaneAppender.KEPT_LINES, lines.size());
    assertEquals("linha " + LogPaneAppender.KEPT_LINES + "\n", lines.get(lines.size() - 1));
  }
}
