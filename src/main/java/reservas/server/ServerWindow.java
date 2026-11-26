package reservas.server;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
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
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reservas.swing.ReadableTable;

// Porta, tabelas do que o servidor guarda e log. Os listeners só leem a tela e chamam o servidor
public final class ServerWindow extends JFrame {
  private static final Logger log = LoggerFactory.getLogger(ServerWindow.class);
  private static final int MAX_LOG_LINES = 5000;
  private static final long REFRESH_MILLIS = 1000;
  private static final String USERS_TAB = "Usuarios";
  private static final String LOGGED_IN_TAB = "Usuarios logados";
  private static final String ROOMS_TAB = "Salas";
  private static final String RESERVATIONS_TAB = "Reservas";
  private static final Color RUNNING = new Color(0x1B, 0x7F, 0x3B);
  private static final Color STOPPED = new Color(0xB0, 0x00, 0x20);

  private final SqliteStore store;
  private final Clock clock;
  private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor();
  private final LogView logView = new LogView(MAX_LOG_LINES);
  private final Consumer<String> logListener = line -> SwingUtilities.invokeLater(() -> logView.add(line));
  private final List<String> addresses = LocalAddresses.ipv4();

  private final JTextField port;
  private final JButton toggle = new JButton("Iniciar");
  private final JLabel status = new JLabel();
  private final ListTableModel<User> users;
  private final ListTableModel<LoggedInUser> loggedIn;
  private final ListTableModel<Room> rooms = ServerTables.rooms();
  private final ListTableModel<ReservationRow> reservations;
  private final JTabbedPane tables = new JTabbedPane();

  private ServerSocketLoop server;

  public ServerWindow(SqliteStore store, Clock clock, int initialPort) {
    super("Reserva de Salas - Servidor");
    this.store = store;
    this.clock = clock;
    this.port = new JTextField(Integer.toString(initialPort), 6);
    this.users = ServerTables.users(clock.getZone());
    this.loggedIn = ServerTables.loggedIn(clock.getZone());
    this.reservations = ServerTables.reservations(clock.getZone());

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    add(controlBar(), BorderLayout.NORTH);
    add(panes(), BorderLayout.CENTER);

    toggle.addActionListener(event -> toggleServer());
    port.addActionListener(event -> toggleServer());
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        shutDown();
      }
    });

    showStopped("parado: escolha a porta e clique em Iniciar");
    LogPaneAppender.subscribe(logListener);
    refresher.scheduleWithFixedDelay(this::refreshTables, 0, REFRESH_MILLIS, TimeUnit.MILLISECONDS);

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
    status.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
    bar.add(controls, BorderLayout.CENTER);
    bar.add(status, BorderLayout.SOUTH);
    return bar;
  }

  private JSplitPane panes() {
    tables.addTab(USERS_TAB, sortable(users));
    tables.addTab(LOGGED_IN_TAB, sortable(loggedIn));
    tables.addTab(ROOMS_TAB, sortable(rooms));
    tables.addTab(RESERVATIONS_TAB, sortable(reservations));
    tables.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tables, logPanel());
    split.setDividerLocation(420);
    split.setResizeWeight(0.5);
    return split;
  }

  // Clicar no cabeçalho ordena, números como números; dois cliques mostram a linha inteira
  private JScrollPane sortable(TableModel model) {
    JTable table = new JTable(model);
    TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
    for (int column = 0; column < model.getColumnCount(); column++) {
      sorter.setComparator(column, ServerTables.NATURAL);
    }
    table.setRowSorter(sorter);
    ReadableTable.install(table);
    return new JScrollPane(table);
  }

  private JPanel logPanel() {

    // Num painel, o texto fica com a largura que precisa: o JSON de uma mensagem não quebra no meio
    JPanel noWrap = new JPanel(new BorderLayout());
    noWrap.add(logView);
    JScrollPane scroll = new JScrollPane(noWrap);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.setPreferredSize(new Dimension(900, 420));

    JPanel messages = new JPanel(new BorderLayout(0, 4));
    messages.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
    messages.add(new JLabel("Mensagens recebidas e enviadas"), BorderLayout.NORTH);
    messages.add(scroll, BorderLayout.CENTER);
    return messages;
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
    showRunning();
  }

  // Para de aceitar conexões; as já abertas seguem até o cliente fechar
  private void stopServer() {
    server.close();
    server = null;

    port.setEnabled(true);
    toggle.setText("Iniciar");
    showStopped("parado");
  }

  private void showRunning() {
    int open = server.openConnections();
    status.setForeground(RUNNING);
    status.setText("escutando na porta " + server.port() + LocalAddresses.describe(addresses) + " - " + open
        + (open == 1 ? " conexao aberta" : " conexoes abertas"));
  }

  private void showStopped(String message) {
    status.setForeground(STOPPED);
    status.setText(message);
  }

  // Roda na thread do `refresher`, fora da EDT
  private void refreshTables() {
    try {
      List<User> registered = store.listUsers();
      List<LoggedInUser> online = store.listLoggedIn(clock.instant());
      List<Room> stored = store.listRooms(1);
      List<ReservationRow> booked = ServerTables.reservationRows(
          store.listReservations(new ReservationFilter(null, null, null)), stored, registered,
          LocalDateTime.now(clock));
      SwingUtilities.invokeLater(() -> {
        users.show(registered);
        loggedIn.show(online);
        rooms.show(stored);
        reservations.show(booked);
        tables.setTitleAt(0, USERS_TAB + " (" + registered.size() + ")");
        tables.setTitleAt(1, LOGGED_IN_TAB + " (" + online.size() + ")");
        tables.setTitleAt(2, ROOMS_TAB + " (" + stored.size() + ")");
        tables.setTitleAt(3, RESERVATIONS_TAB + " (" + booked.size() + ")");
        if (server != null) {
          showRunning();
        }
      });
    } catch (RuntimeException e) {
      log.error("falha atualizando as tabelas da janela", e);
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
