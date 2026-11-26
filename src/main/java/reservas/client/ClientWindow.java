package reservas.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import com.google.gson.JsonObject;

import reservas.protocol.ClassPeriod;
import reservas.protocol.Fields;
import reservas.swing.ReadableTable;

// Janela do cliente
public final class ClientWindow extends JFrame {
  private static final int TIMEOUT_MILLIS = 10_000;
  private static final int CLOSE_WAIT_MILLIS = 3_000;
  private static final int LABEL_WIDTH = 120;
  private static final Color OK = new Color(0x1B, 0x7F, 0x3B);
  private static final Color FAILED = new Color(0xB0, 0x00, 0x20);
  private static final Color SENT = new Color(0x1F, 0x4E, 0x9A);
  private static final Color NOTE = new Color(0x70, 0x70, 0x70);
  private static final String HOST_KEY = "host";
  private static final String PORT_KEY = "port";

  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private final List<JButton> actions = new ArrayList<>();
  // Chave do protocolo -> campos preenchidos ao clicar numa linha do Resultado
  private final Map<String, List<Consumer<String>>> pickers = new HashMap<>();
  private final Preferences preferences = Preferences.userNodeForPackage(ClientWindow.class);

  private final JTextField host = new JTextField(preferences.get(HOST_KEY, "localhost"), 12);
  private final JTextField port = new JTextField(preferences.get(PORT_KEY, "5000"), 5);
  private final JButton connection = new JButton("Conectar");
  private final JLabel account = new JLabel();
  private final JLabel status = new JLabel("desconectado");
  private final JLabel hint = new JLabel(" ");

  private final JTable results = new JTable();
  private final JTextPane messages = new JTextPane();

  private Session session;

  public ClientWindow() {
    super("Reserva de Salas - Cliente");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    add(connectionBar(), BorderLayout.NORTH);
    add(operations(), BorderLayout.WEST);
    add(output(), BorderLayout.CENTER);

    connection.addActionListener(event -> toggleConnection());
    port.addActionListener(event -> toggleConnection());
    setConnected(false);
    pack();
    setMinimumSize(getSize());
    setLocationRelativeTo(null);
  }

  private JPanel connectionBar() {
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controls.add(new JLabel("IP:"));
    controls.add(host);
    controls.add(new JLabel("Porta:"));
    controls.add(port);
    controls.add(connection);
    controls.add(Box.createHorizontalStrut(12));
    controls.add(account);

    hint.setForeground(NOTE);
    hint.setFont(hint.getFont().deriveFont(Font.ITALIC));

    JPanel bar = new JPanel(new BorderLayout());
    bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
    bar.add(controls, BorderLayout.WEST);
    bar.add(status, BorderLayout.EAST);
    bar.add(hint, BorderLayout.SOUTH);
    return bar;
  }

  private JTabbedPane operations() {
    JTabbedPane conta = new JTabbedPane();
    conta.addTab("Login", loginTab());
    conta.addTab("Cadastro", registerTab());
    conta.addTab("Minha conta", accountTab());

    JTabbedPane reservas = new JTabbedPane();
    reservas.addTab("Reservar", bookTab());
    reservas.addTab("Minhas reservas", reservationsTab());

    JTabbedPane admin = new JTabbedPane();
    admin.addTab("Usuarios", usersTab());
    admin.addTab("Nova sala", newRoomTab());
    admin.addTab("Editar sala", editRoomTab());

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Conta", conta);
    tabs.addTab("Salas", roomsTab());
    tabs.addTab("Reservas", reservas);
    tabs.addTab("Admin", admin);
    return tabs;
  }

  private JPanel registerTab() {
    Form form = new Form();
    JTextField email = form.field("Email", "ex.: joao@email.com");
    JTextField user = form.field("Usuario", "so letras minusculas");
    JPasswordField password = form.password("Senha");
    form.button("Cadastrar", () -> session.register(text(email), text(user), secret(password)));

    return form.panel();
  }

  private JPanel loginTab() {
    Form form = new Form();
    JTextField email = form.field("Email", "");
    JPasswordField password = form.password("Senha");
    form.button("Entrar", () -> session.login(text(email), secret(password)));
    form.button("Sair", () -> session.logout());

    return form.panel();
  }

  private JPanel accountTab() {
    Form form = new Form();
    form.button("Ler meus dados", () -> session.readUser());
    form.separator();

    JTextField user = form.field("Novo usuario", "em branco mantem");
    JPasswordField password = form.password("Nova senha");
    form.button("Atualizar", () -> session.updateUser(text(user), secret(password)));
    form.separator();

    JPasswordField confirm = form.password("Confirme a senha");
    form.dangerButton("Remover conta", () -> "Remover a sua conta e todas as suas reservas?",
        () -> session.deleteUser(secret(confirm)));

    return form.panel();
  }

  private JPanel usersTab() {
    Form form = new Form();
    form.button("Listar usuarios", () -> session.adminListUsers());
    form.separator();

    JTextField target = form.field("Usuario alvo", "clique num usuario do Resultado");
    pick(Fields.USER, target::setText);
    form.button("Ler usuario", () -> session.adminReadUser(text(target)));
    form.separator();

    JTextField user = form.field("Novo usuario", "em branco mantem");
    JPasswordField password = form.password("Nova senha");
    JComboBox<String> role = form.choice("Novo perfil", "", "user", "admin");
    form.button("Atualizar usuario",
        () -> session.adminUpdateUser(text(target), text(user), secret(password), selected(role)));
    form.separator();

    form.dangerButton("Remover usuario", () -> "Remover o usuario \"" + text(target) + "\" e as reservas dele?",
        () -> session.adminDeleteUser(text(target)));

    return form.panel();
  }

  private JPanel roomsTab() {
    Form form = new Form();
    JTextField date = form.dateField("Data", "");
    JComboBox<String> start = form.choice("Inicio", keepOr(classBoundaries(ClassPeriod::start)));
    JComboBox<String> end = form.choice("Fim", keepOr(classBoundaries(ClassPeriod::end)));
    start.addActionListener(event -> setOptions(end, keepOr(ClassChoices.endsAfter(selected(start)))));
    JTextField minCapacity = form.field("Capacidade min.", "opcional");
    form.button("Listar salas",
        () -> session.listRooms(text(date), selected(start), selected(end), text(minCapacity)));
    form.separator();

    JTextField id = form.field("ID da sala", "clique numa sala do Resultado");
    pick(Fields.ROOM_ID, id::setText);
    JTextField day = form.dateField("Data da agenda", "");
    form.button("Ler sala", () -> session.readRoom(text(id), text(day)));
    form.button("Ler todas as salas", () -> session.readRooms(text(day)));

    return form.panel();
  }

  private JPanel newRoomTab() {
    Form form = new Form();
    JTextField name = form.field("Nome", "letras, numeros, espaco, -");
    JTextField capacity = form.field("Capacidade", "1 a 9999");
    JTextField location = form.field("Local", "ex.: Bloco B - 1 andar");
    JTextField resources = form.field("Recursos", "separados por virgula");
    SlotGrid slots = form.slotGrid("Horarios");
    form.button("Cadastrar sala", () -> session.createRoom(roomFields(name, capacity, location, resources, slots)));

    return form.panel();
  }

  private JPanel editRoomTab() {
    Form form = new Form();
    JTextField id = form.field("ID da sala", "clique numa sala do Resultado");
    pick(Fields.ROOM_ID, id::setText);
    JTextField name = form.field("Nome", "");
    JTextField capacity = form.field("Capacidade", "");
    JTextField location = form.field("Local", "");
    JTextField resources = form.field("Recursos", "separados por virgula");
    SlotGrid slots = form.slotGrid("Horarios");
    JComboBox<String> roomStatus = form.choice("Estado", "active", "inactive");

    form.button("Carregar", () -> session.readRoom(text(id), ""), room -> {
      RoomFields loaded = RoomFields.from(room);
      name.setText(loaded.name());
      capacity.setText(loaded.capacity());
      location.setText(loaded.location());
      resources.setText(ListText.format(loaded.resources()));
      slots.select(loaded.slots());
      roomStatus.setSelectedItem(room.get(Fields.ROOM_STATUS).getAsString());
    });
    form.button("Atualizar sala",
        () -> session.updateRoom(text(id), roomFields(name, capacity, location, resources, slots),
            selected(roomStatus)));
    form.dangerButton("Remover sala", () -> "Remover a sala " + text(id) + "?", () -> session.deleteRoom(text(id)));

    return form.panel();
  }

  private JPanel bookTab() {
    Form form = new Form();
    JTextField room = form.field("ID da sala", "clique numa sala do Resultado");
    pick(Fields.ROOM_ID, room::setText);
    JTextField date = form.dateField("Data", LocalDate.now().toString());
    form.button("Ver horarios do dia", () -> session.readRoom(text(room), text(date)));
    form.separator();

    JComboBox<String> start = form.choice("Inicio");
    JComboBox<String> end = form.choice("Fim");
    Runnable refreshEnds = () -> setOptions(end, ClassChoices.endsAfter(selected(start)));
    Runnable refreshStarts = () -> {
      setOptions(start, ClassChoices.starts(text(date), LocalDateTime.now()));
      refreshEnds.run();
    };
    start.addActionListener(event -> refreshEnds.run());
    onChange(date, refreshStarts);
    refreshStarts.run();
    pick(Fields.START_TIME, start::setSelectedItem);
    pick(Fields.END_TIME, end::setSelectedItem);
    form.button("Verificar disponibilidade",
        () -> session.checkAvailability(text(room), text(date), selected(start), selected(end)));
    form.separator();

    JTextField topic = form.field("Assunto", "letras, numeros, espaco , . -");
    JTextField participants = form.field("Participantes", "ate a capacidade da sala");
    form.button("Reservar", () -> session.createReservation(text(room), text(date),
        new ReservationFields(selected(start), selected(end), text(topic), text(participants))));

    return form.panel();
  }

  private JPanel reservationsTab() {
    Form form = new Form();
    JComboBox<String> scope = form.choice("Quais", Fields.SCOPE_MINE, Fields.SCOPE_ALL);
    JTextField date = form.dateField("Data", "");
    JTextField room = form.field("ID da sala", "opcional");
    form.button("Listar reservas", () -> session.listReservations(selected(scope), text(date), text(room)));
    form.separator();

    JTextField id = form.field("ID da reserva", "clique numa reserva do Resultado");
    pick(Fields.RESERVATION_ID, id::setText);
    form.button("Ler reserva", () -> session.readReservation(text(id)));
    form.separator();

    JTextField newRoom = form.field("Nova sala", "em branco mantem");
    JTextField newDate = form.dateField("Nova data", "");
    JComboBox<String> start = form.choice("Novo inicio", keepOr(classBoundaries(ClassPeriod::start)));
    JComboBox<String> end = form.choice("Novo fim", keepOr(classBoundaries(ClassPeriod::end)));
    start.addActionListener(event -> setOptions(end, keepOr(ClassChoices.endsAfter(selected(start)))));
    JTextField topic = form.field("Novo assunto", "em branco mantem");
    JTextField participants = form.field("Participantes", "em branco mantem");
    form.button("Atualizar reserva", () -> session.updateReservation(text(id), text(newRoom), text(newDate),
        new ReservationFields(selected(start), selected(end), text(topic), text(participants))));
    form.separator();

    form.dangerButton("Cancelar reserva", () -> "Cancelar a reserva " + text(id) + "?",
        () -> session.deleteReservation(text(id)));

    return form.panel();
  }

  // Início ou fim de cada aula da tabela da UTFPR
  private static List<String> classBoundaries(Function<ClassPeriod, LocalTime> boundary) {
    return Arrays.stream(ClassPeriod.values()).map(boundary).map(LocalTime::toString).toList();
  }

  // Opção vazia primeiro: mantém o valor atual, ou sem filtro
  private static List<String> keepOr(List<String> options) {
    return Stream.concat(Stream.of(""), options.stream()).toList();
  }

  // Troca as opções e mantém a escolhida, se ela continua valendo
  private static void setOptions(JComboBox<String> choice, List<String> options) {
    Object kept = choice.getSelectedItem();
    choice.setModel(new DefaultComboBoxModel<>(options.toArray(String[]::new)));
    if (options.contains(kept)) {
      choice.setSelectedItem(kept);
    }
  }

  private void pick(String key, Consumer<String> target) {
    pickers.computeIfAbsent(key, k -> new ArrayList<>()).add(target);
  }

  private JSplitPane output() {
    results.setAutoCreateRowSorter(true);
    ReadableTable.install(results);
    results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    results.getSelectionModel().addListSelectionListener(event -> {
      if (!event.getValueIsAdjusting()) {
        pickSelectedRow();
      }
    });

    JScrollPane table = new JScrollPane(results);
    table.setBorder(BorderFactory.createTitledBorder("Resultado (um clique usa os IDs, dois mostram a linha)"));
    table.setPreferredSize(new Dimension(table.getPreferredSize().width, 160));

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, table, messagePane());
    split.setResizeWeight(0.4);
    return split;
  }

  private JScrollPane messagePane() {
    messages.setEditorKit(new WrappingKit());
    messages.setEditable(false);
    messages.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

    JScrollPane scroll = new JScrollPane(messages);
    scroll.getVerticalScrollBar().setUnitIncrement(16);
    scroll.setBorder(BorderFactory.createTitledBorder("Mensagens enviadas e recebidas"));
    scroll.setPreferredSize(new Dimension(560, 360));
    return scroll;
  }

  // Cada coluna com campo registrado em `pickers` passa o valor da linha para ele
  private void pickSelectedRow() {
    int row = results.getSelectedRow();
    if (row < 0) {
      return;
    }

    TableModel model = results.getModel();
    int modelRow = results.convertRowIndexToModel(row);
    for (int column = 0; column < model.getColumnCount(); column++) {
      String value = String.valueOf(model.getValueAt(modelRow, column));
      pickers.getOrDefault(model.getColumnName(column), List.of()).forEach(target -> target.accept(value));
    }
  }

  private void toggleConnection() {
    if (session == null) {
      connect();
    } else {
      disconnect();
    }
  }

  private void connect() {
    String address = text(host);
    int number;

    try {
      number = Integer.parseInt(text(port));
    } catch (NumberFormatException e) {
      append("porta invalida: \"" + text(port) + "\"");
      return;
    }

    network.execute(() -> {
      try {
        Session opened = new Session(
            ProtocolClient.connect(address, number, TIMEOUT_MILLIS, line -> onEdt(() -> append(line))));
        onEdt(() -> {
          session = opened;
          preferences.put(HOST_KEY, address);
          preferences.put(PORT_KEY, Integer.toString(number));
          setConnected(true);
        });
      } catch (IOException e) {
        onEdt(() -> append("falha conectando em " + address + ":" + number + " - " + e.getMessage()));
      }
    });
  }

  private void disconnect() {
    Session closing = session;
    session = null;
    setConnected(false);

    network.execute(() -> {
      try {
        closing.close();
      } catch (IOException e) {
        onEdt(() -> append("falha fechando a conexao: " + e.getMessage()));
      }
    });
  }

  private void send(Callable<JsonObject> call, Consumer<JsonObject> onSuccess) {
    if (session == null) {
      append("conecte antes de enviar");
      return;
    }

    network.execute(() -> {
      try {
        JsonObject response = call.call();
        onEdt(() -> {
          showResult(response);
          if (succeeded(response)) {
            onSuccess.accept(response);
          }
        });
      } catch (Exception e) {
        onEdt(() -> {
          append("falha na requisicao: " + e);
          status.setText("falha na requisicao");
          status.setForeground(FAILED);
        });
      }
    });
  }

  private void setConnected(boolean connected) {
    connection.setText(connected ? "Desconectar" : "Conectar");
    host.setEnabled(!connected);
    port.setEnabled(!connected);
    status.setText(connected ? "conectado" : "desconectado");
    status.setForeground(Color.DARK_GRAY);
    hint.setText(" ");
    showAccount();

    for (JButton action : actions) {
      action.setEnabled(connected);
    }
  }

  private void showAccount() {
    String who = session == null ? "" : session.account().map(name -> "logado: " + name).orElse("sem login");
    account.setText(who);
  }

  private void append(String line) {
    SimpleAttributeSet style = new SimpleAttributeSet();
    StyleConstants.setForeground(style, switch (TrafficLine.of(line)) {
      case SENT -> SENT;
      case RECEIVED_OK -> Color.BLACK;
      case RECEIVED_ERROR -> FAILED;
      case NOTE -> NOTE;
    });

    try {
      messages.getDocument().insertString(messages.getDocument().getLength(), line + "\n", style);
    } catch (BadLocationException e) {
      throw new IllegalStateException("fim do painel de mensagens fora do documento", e);
    }
    messages.setCaretPosition(messages.getDocument().getLength());
  }

  // Espera o logout de quem fecha a janela logado, até CLOSE_WAIT_MILLIS
  @Override
  public void dispose() {
    if (session != null) {
      disconnect();
    }

    network.shutdown();
    try {
      network.awaitTermination(CLOSE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    super.dispose();
  }

  private static String summary(JsonObject response) {
    return response.get(Fields.STATUS).getAsString() + " " + response.get(Fields.MESSAGE).getAsString();
  }

  private void showResult(JsonObject response) {
    status.setText(summary(response));
    status.setForeground(succeeded(response) ? OK : FAILED);
    hint.setText(ErrorHints.of(response).map(text -> "dica: " + text).orElse(" "));
    showAccount();

    results.setModel(ResultTable.of(response));
    for (int i = 0; i < results.getColumnCount(); i++) {
      TableColumn column = results.getColumnModel().getColumn(i);
      column.setHeaderValue(ResultTable.header(results.getModel().getColumnName(column.getModelIndex())));
    }
    results.getTableHeader().repaint();
  }

  private static boolean succeeded(JsonObject response) {
    return response.get(Fields.STATUS).getAsString().startsWith("2");
  }

  private static RoomFields roomFields(JTextField name, JTextField capacity, JTextField location,
      JTextField resources, SlotGrid slots) {
    return new RoomFields(text(name), text(capacity), text(location), ListText.parse(text(resources)),
        slots.selected());
  }

  private static String selected(JComboBox<String> choice) {
    Object item = choice.getSelectedItem();
    return item == null ? "" : (String) item;
  }

  private static String text(JTextField field) {
    return field.getText().strip();
  }

  private static String secret(JPasswordField field) {
    return new String(field.getPassword());
  }

  private static void onChange(JTextField field, Runnable action) {
    field.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent event) {
        action.run();
      }

      @Override
      public void removeUpdate(DocumentEvent event) {
        action.run();
      }

      @Override
      public void changedUpdate(DocumentEvent event) {
        action.run();
      }
    });
  }

  // Só mexe no campo, não fala com o servidor
  private static JButton dayButton(String arrow, String tip, JTextField date, int days) {
    JButton button = new JButton(arrow);
    button.setToolTipText(tip);
    button.addActionListener(event -> date.setText(DateText.shift(text(date), days, LocalDate.now())));
    return button;
  }

  private static void onEdt(Runnable update) {
    SwingUtilities.invokeLater(update);
  }

  private final class Form {
    private final JPanel panel = new JPanel();
    // Campos desde o último botão: Enter neles aperta o próximo botão
    private final List<JTextField> waiting = new ArrayList<>();

    private Form() {
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
      panel.add(Box.createVerticalGlue());
    }

    private JTextField field(String label, String hint) {
      return waits(labelled(label, new HintField("", 18, hint)));
    }

    private JPasswordField password(String label) {
      return waits(labelled(label, new JPasswordField(18)));
    }

    // Data com botões para o dia seguinte e o anterior; em branco, as setas partem de hoje
    private JTextField dateField(String label, String initial) {
      JTextField date = new HintField(initial, 10, "AAAA-MM-DD");
      JPanel box = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
      box.add(date);
      box.add(dayButton("▲", "Amanha", date, 1));
      box.add(dayButton("▼", "Ontem", date, -1));
      labelled(label, box);

      return waits(date);
    }

    private JComboBox<String> choice(String label, String... options) {
      return labelled(label, new JComboBox<>(options));
    }

    private JComboBox<String> choice(String label, List<String> options) {
      return choice(label, options.toArray(String[]::new));
    }

    private SlotGrid slotGrid(String title) {
      SlotGrid grid = new SlotGrid();
      grid.setBorder(BorderFactory.createTitledBorder(title));

      JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
      row.add(grid);
      fixHeight(row);
      panel.add(row);

      return grid;
    }

    private void button(String label, Callable<JsonObject> call) {
      button(label, call, response -> {
      });
    }

    private void button(String label, Callable<JsonObject> call, Consumer<JsonObject> onSuccess) {
      addButton(label, () -> send(call, onSuccess));
    }

    // Pergunta antes de enviar: apagar não tem volta
    private void dangerButton(String label, Supplier<String> question, Callable<JsonObject> call) {
      addButton(label, () -> {
        int answer = JOptionPane.showConfirmDialog(ClientWindow.this, question.get(), label,
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
          send(call, response -> {
          });
        }
      });
    }

    private void addButton(String label, Runnable action) {
      JButton button = new JButton(label);
      button.setEnabled(false);
      button.addActionListener(event -> action.run());
      actions.add(button);
      waiting.forEach(field -> field.addActionListener(event -> {
        if (button.isEnabled()) {
          button.doClick();
        }
      }));
      waiting.clear();

      JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
      row.setBorder(BorderFactory.createEmptyBorder(12, 0, 4, 0));
      row.add(button);
      fixHeight(row);
      panel.add(row);
    }

    private JPanel panel() {
      panel.add(Box.createVerticalGlue());
      return panel;
    }

    private void separator() {
      JPanel line = new JPanel(new BorderLayout());
      line.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
      line.add(new JSeparator(), BorderLayout.CENTER);
      fixHeight(line);
      panel.add(line);
    }

    private <T extends JTextField> T waits(T field) {
      waiting.add(field);
      return field;
    }

    private <T extends JComponent> T labelled(String label, T field) {
      JLabel name = new JLabel(label);
      name.setPreferredSize(new Dimension(LABEL_WIDTH, name.getPreferredSize().height));

      JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
      row.add(name);
      row.add(field);
      fixHeight(row);
      panel.add(row);

      return field;
    }

    private void fixHeight(JPanel row) {
      row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    }
  }
}
