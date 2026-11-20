package reservas.server;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Porta, usuários logados e log. Os listeners só leem a tela e chamam o servidor
public final class ServerWindow extends JFrame {
  private static final Logger log = LoggerFactory.getLogger(ServerWindow.class);
  private static final int MAX_LOG_LINES = 5000;
  private static final long REFRESH_MILLIS = 1000;
  private static final Color RUNNING = new Color(0x1B, 0x7F, 0x3B);
  private static final Color STOPPED = new Color(0xB0, 0x00, 0x20);

  private final SqliteStore store;
  private final Clock clock;
  private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor();
  private final Consumer<String> logListener = line -> SwingUtilities.invokeLater(() -> appendLog(line));

  private final JTextField port;
  private final JButton toggle = new JButton("Iniciar");
  private final JLabel status = new JLabel();
  private final LoggedInTableModel loggedIn;
  private final JLabel loggedInTitle = new JLabel();
  private final JTextArea logPane = new JTextArea(30, 100);

  private ServerSocketLoop server;

  public ServerWindow(SqliteStore store, Clock clock, int initialPort) {
    super("Reserva de Salas - Servidor");
    this.store = store;
    this.clock = clock;
    this.port = new JTextField(Integer.toString(initialPort), 6);
    this.loggedIn = new LoggedInTableModel(clock.getZone());

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    add(controlBar(), BorderLayout.NORTH);
    add(panes(), BorderLayout.CENTER);

    toggle.addActionListener(event -> toggleServer());
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        shutDown();
      }
    });

    showStopped("parado: escolha a porta e clique em Iniciar");
    LogPaneAppender.subscribe(logListener);
    refresher.scheduleWithFixedDelay(this::refreshLoggedIn, 0, REFRESH_MILLIS, TimeUnit.MILLISECONDS);

    pack();
    setLocationRelativeTo(null);
  }

  private JPanel controlBar() {
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(new JLabel("Porta:"));
    controls.add(port);
    controls.add(toggle);

    JPanel bar = new JPanel(new BorderLayout());
    bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    bar.add(controls, BorderLayout.WEST);
    bar.add(status, BorderLayout.EAST);
    return bar;
  }

  private JSplitPane panes() {
    JPanel users = new JPanel(new BorderLayout(0, 4));
    users.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 4));
    users.add(loggedInTitle, BorderLayout.NORTH);
    users.add(new JScrollPane(new JTable(loggedIn)), BorderLayout.CENTER);

    logPane.setEditable(false);
    logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    JPanel messages = new JPanel(new BorderLayout(0, 4));
    messages.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 8));
    messages.add(new JLabel("Mensagens recebidas e enviadas"), BorderLayout.NORTH);
    messages.add(new JScrollPane(logPane), BorderLayout.CENTER);

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, users, messages);
    split.setDividerLocation(300);
    return split;
  }

  private void toggleServer() {
    if (server == null) {
      startServer();
    } else {
      stopServer();
    }
  }

  private void startServer() {
    int chosen;
    try {
      chosen = ServerApp.parsePort(port.getText().strip());
    } catch (IllegalArgumentException e) {
      showStopped(e.getMessage());
      return;
    }

    try {
      server = ServerApp.start(chosen, store, clock);
    } catch (IOException e) {
      log.error("porta {} ja esta em uso ou nao pode ser aberta: {}", chosen, e.getMessage());
      showStopped("porta " + chosen + " indisponivel: " + e.getMessage());
      return;
    }

    Thread acceptor = new Thread(server::acceptForever, "aceita-conexoes");
    acceptor.setDaemon(true);
    acceptor.start();

    port.setEnabled(false);
    toggle.setText("Parar");
    status.setForeground(RUNNING);
    status.setText("escutando na porta " + server.port());
  }

  // Para de aceitar conexões; as já abertas seguem até o cliente fechar
  private void stopServer() {
    server.close();
    server = null;

    port.setEnabled(true);
    toggle.setText("Iniciar");
    showStopped("parado");
  }

  private void showStopped(String message) {
    status.setForeground(STOPPED);
    status.setText(message);
  }

  // Roda na thread do `refresher`, fora da EDT
  private void refreshLoggedIn() {
    try {
      List<LoggedInUser> users = store.listLoggedIn(clock.instant());
      SwingUtilities.invokeLater(() -> {
        loggedIn.show(users);
        loggedInTitle.setText("Usuarios logados: " + users.size());
      });
    } catch (RuntimeException e) {
      log.error("falha atualizando a lista de usuarios logados", e);
    }
  }

  private void appendLog(String line) {
    logPane.append(line);
    trimLog();
    logPane.setCaretPosition(logPane.getDocument().getLength());
  }

  private void trimLog() {
    int excess = logPane.getLineCount() - MAX_LOG_LINES;
    if (excess <= 0) {
      return;
    }

    try {
      logPane.getDocument().remove(0, logPane.getLineStartOffset(excess));
    } catch (BadLocationException e) {
      throw new IllegalStateException("linha " + excess + " fora do log com " + logPane.getLineCount(), e);
    }
  }

  private void shutDown() {
    if (server != null) {
      server.close();
    }

    refresher.shutdownNow();
    LogPaneAppender.unsubscribe(logListener);
    dispose();
    store.close();
    System.exit(0);
  }
}
