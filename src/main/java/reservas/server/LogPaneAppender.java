package reservas.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;

// Leva o log para a janela do servidor. Guarda as últimas linhas para quem se inscreve depois de o log começar
public final class LogPaneAppender extends AppenderBase<ILoggingEvent> {
  static final int KEPT_LINES = 1000;

  private static final Deque<String> recent = new ArrayDeque<>();
  private static final List<Consumer<String>> listeners = new ArrayList<>();

  private Layout<ILoggingEvent> layout;

  // Chamado pelo logback.xml
  public void setLayout(Layout<ILoggingEvent> layout) {
    this.layout = layout;
  }

  @Override
  public void start() {
    if (layout == null) {
      addError("LogPaneAppender \"" + name + "\" sem <layout>");
      return;
    }

    super.start();
  }

  @Override
  protected void append(ILoggingEvent event) {
    publish(layout.doLayout(event));
  }

  // Recebe as linhas guardadas e depois cada linha nova, na thread que logou
  public static void subscribe(Consumer<String> listener) {
    synchronized (recent) {
      recent.forEach(listener);
      listeners.add(listener);
    }
  }

  public static void unsubscribe(Consumer<String> listener) {
    synchronized (recent) {
      listeners.remove(listener);
    }
  }

  static void publish(String line) {
    synchronized (recent) {
      recent.addLast(line);
      if (recent.size() > KEPT_LINES) {
        recent.removeFirst();
      }

      listeners.forEach(listener -> listener.accept(line));
    }
  }
}
