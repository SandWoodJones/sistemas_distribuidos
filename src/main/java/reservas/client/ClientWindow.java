package reservas.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;

// Janela do cliente
public final class ClientWindow extends JFrame {
  private static final int TIMEOUT_MILLIS = 10_000;
  private static final int LABEL_WIDTH = 120;
  private static final Color OK = new Color(0x1B, 0x7F, 0x3B);
  private static final Color FAILED = new Color(0xB0, 0x00, 0x20);

  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private final List<JButton> actions = new ArrayList<>();

  private final JTextField host = new JTextField("localhost", 12);
  private final JTextField port = new JTextField("5000", 5);
  private final JButton connection = new JButton("Conectar");
  private final JLabel status = new JLabel("desconectado");

  private final JTable results = new JTable();
  private final JTextArea messages = new JTextArea(20, 58);

  private Session session;

  public ClientWindow() {
    super("Reserva de Salas - Cliente");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    add(connectionBar(), BorderLayout.NORTH);
    add(operations(), BorderLayout.WEST);
    add(output(), BorderLayout.CENTER);

    connection.addActionListener(event -> toggleConnection());
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

    JPanel bar = new JPanel(new BorderLayout());
    bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    bar.add(controls, BorderLayout.WEST);
    bar.add(status, BorderLayout.EAST);
    return bar;
  }

  private JTabbedPane operations() {
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Cadastro", registerTab());
    tabs.addTab("Login", loginTab());
    tabs.addTab("Minha conta", accountTab());
    tabs.addTab("Usuarios", usersTab());
    tabs.addTab("Salas", roomsTab());
    tabs.addTab("Nova sala", newRoomTab());
    tabs.addTab("Editar sala", editRoomTab());
    return tabs;
  }

  private JPanel registerTab() {
    Form form = new Form();
    JTextField email = form.field("Email");
    JTextField user = form.field("Usuario");
    JPasswordField password = form.password("Senha");
    form.button("Cadastrar", () -> session.register(text(email), text(user), secret(password)));

    return form.panel();
  }

  private JPanel loginTab() {
    Form form = new Form();
    JTextField email = form.field("Email");
    JPasswordField password = form.password("Senha");
    form.button("Entrar", () -> session.login(text(email), secret(password)));
    form.button("Sair", () -> session.logout());

    return form.panel();
  }

  private JPanel accountTab() {
    Form form = new Form();
    form.button("Ler meus dados", () -> session.readUser());
    form.separator();

    JTextField user = form.field("Novo usuario");
    JPasswordField password = form.password("Nova senha");
    form.button("Atualizar", () -> session.updateUser(text(user), secret(password)));
    form.separator();

    JPasswordField confirm = form.password("Confirme a senha");
    form.button("Remover conta", () -> session.deleteUser(secret(confirm)));

    return form.panel();
  }

  private JPanel usersTab() {
    Form form = new Form();
    form.button("Listar usuarios", () -> session.adminListUsers());
    form.separator();

    JTextField target = form.field("Usuario alvo");
    form.button("Ler usuario", () -> session.adminReadUser(text(target)));
    form.separator();

    JTextField user = form.field("Novo usuario");
    JPasswordField password = form.password("Nova senha");
    JComboBox<String> role = form.choice("Novo perfil", "", "user", "admin");
    form.button("Atualizar usuario",
        () -> session.adminUpdateUser(text(target), text(user), secret(password), selected(role)));
    form.separator();

    form.button("Remover usuario", () -> session.adminDeleteUser(text(target)));

    return form.panel();
  }

  private JPanel roomsTab() {
    Form form = new Form();
    JTextField date = form.field("Data");
    JTextField start = form.field("Inicio");
    JTextField end = form.field("Fim");
    JTextField minCapacity = form.field("Capacidade min.");
    form.button("Listar salas", () -> session.listRooms(text(date), text(start), text(end), text(minCapacity)));
    form.separator();

    JTextField id = form.field("ID da sala");
    form.button("Ler sala", () -> session.readRoom(text(id)));

    return form.panel();
  }

  private JPanel newRoomTab() {
    Form form = new Form();
    JTextField name = form.field("Nome");
    JTextField capacity = form.field("Capacidade");
    JTextField location = form.field("Local");
    JTextField resources = form.field("Recursos");
    form.button("Cadastrar sala", () -> session.createRoom(roomFields(name, capacity, location, resources)));

    return form.panel();
  }

  private JPanel editRoomTab() {
    Form form = new Form();
    JTextField id = form.field("ID da sala");
    JTextField name = form.field("Nome");
    JTextField capacity = form.field("Capacidade");
    JTextField location = form.field("Local");
    JTextField resources = form.field("Recursos");
    JComboBox<String> roomStatus = form.choice("Estado", "active", "inactive");

    form.button("Carregar", () -> session.readRoom(text(id)), room -> {
      RoomFields loaded = RoomFields.from(room);
      name.setText(loaded.name());
      capacity.setText(loaded.capacity());
      location.setText(loaded.capacity());
      resources.setText(ListText.format(loaded.resources()));
      roomStatus.setSelectedItem(room.get(Fields.ROOM_STATUS).getAsString());
    });
    form.button("Atualizar sala",
        () -> session.updateRoom(text(id), roomFields(name, capacity, location, resources), selected(roomStatus)));
    form.button("Remover sala", () -> session.deleteRoom(text(id)));

    return form.panel();
  }

  private JSplitPane output() {
    JScrollPane table = new JScrollPane(results);
    table.setBorder(BorderFactory.createTitledBorder("Resultado"));
    table.setPreferredSize(new Dimension(table.getPreferredSize().width, 160));

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, table, messagePane());
    split.setResizeWeight(0.4);
    return split;
  }

  private JScrollPane messagePane() {
    messages.setEditable(false);
    messages.setLineWrap(true);
    messages.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

    JScrollPane scroll = new JScrollPane(messages);
    scroll.setBorder(BorderFactory.createTitledBorder("Mensagens enviadas e recebidas"));
    return scroll;
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

    for (JButton action : actions) {
      action.setEnabled(connected);
    }
  }

  private void append(String line) {
    messages.append(line + "\n");
    messages.setCaretPosition(messages.getDocument().getLength());
  }

  @Override
  public void dispose() {
    network.shutdownNow();
    super.dispose();
  }

  private static String summary(JsonObject response) {
    return response.get(Fields.STATUS).getAsString() + " " + response.get(Fields.MESSAGE).getAsString();
  }

  private void showResult(JsonObject response) {
    status.setText(summary(response));
    status.setForeground(succeeded(response) ? OK : FAILED);
    results.setModel(ResultTable.of(response));
  }

  private static boolean succeeded(JsonObject response) {
    return response.get(Fields.STATUS).getAsString().startsWith("2");
  }

  private static RoomFields roomFields(JTextField name, JTextField capacity, JTextField location,
      JTextField resources) {
    return new RoomFields(text(name), text(capacity), text(location), ListText.parse(text(resources)));
  }

  private static String selected(JComboBox<String> choice) {
    return (String) choice.getSelectedItem();
  }

  private static String text(JTextField field) {
    return field.getText().strip();
  }

  private static String secret(JPasswordField field) {
    return new String(field.getPassword());
  }

  private static void onEdt(Runnable update) {
    SwingUtilities.invokeLater(update);
  }

  private final class Form {
    private final JPanel panel = new JPanel();

    private Form() {
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
      panel.add(Box.createVerticalGlue());
    }

    private JTextField field(String label) {
      return labelled(label, new JTextField(18));
    }

    private JPasswordField password(String label) {
      return labelled(label, new JPasswordField(18));
    }

    private JComboBox<String> choice(String label, String... options) {
      return labelled(label, new JComboBox<>(options));
    }

    private void button(String label, Callable<JsonObject> call) {
      button(label, call, response -> {
      });
    }

    private void button(String label, Callable<JsonObject> call, Consumer<JsonObject> onSuccess) {
      JButton button = new JButton(label);
      button.setEnabled(false);
      button.addActionListener(event -> send(call, onSuccess));
      actions.add(button);

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
