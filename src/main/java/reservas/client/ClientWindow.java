package reservas.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;

// Janela do cliente
public final class ClientWindow extends JFrame {
  private static final int TIMEOUT_MILLIS = 10_000;

  private final ExecutorService network = Executors.newSingleThreadExecutor();
  private final List<JButton> actions = new ArrayList<>();

  private final JTextField host = new JTextField("localhost", 12);
  private final JTextField port = new JTextField("5000", 5);
  private final JButton connection = new JButton("Conectar");
  private final JLabel status = new JLabel("desconectado");

  private final JTextField registerEmail = new JTextField(18);
  private final JTextField registerUser = new JTextField(18);
  private final JPasswordField registerPassword = new JPasswordField(18);

  private final JTextField loginEmail = new JTextField(18);
  private final JPasswordField loginPassword = new JPasswordField(18);

  private final JTextField newUser = new JTextField(18);
  private final JPasswordField newPassword = new JPasswordField(18);
  private final JPasswordField deletePassword = new JPasswordField(18);

  private final JTextArea messages = new JTextArea(16, 90);

  private Session session;

  public ClientWindow() {
    super("Reserva de Salas - Cliente");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    add(connectionBar(), BorderLayout.NORTH);
    add(operations(), BorderLayout.CENTER);
    add(messagePane(), BorderLayout.SOUTH);

    connection.addActionListener(event -> toggleConnection());
    setConnected(false);
    pack();
    setLocationRelativeTo(null);
  }

  private JPanel connectionBar() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    bar.add(new JLabel("IP:"));
    bar.add(host);
    bar.add(new JLabel("Porta:"));
    bar.add(port);
    bar.add(connection);
    bar.add(status);
    return bar;
  }

  private JTabbedPane operations() {
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Cadastro",
        stack(row("Email", registerEmail), row("Usuario", registerUser), row("Senha", registerPassword), action(
            "Cadastrar", () -> session.register(text(registerEmail), text(registerUser), secret(registerPassword)))));

    tabs.addTab("Login",
        stack(row("Email", loginEmail), row("Senha", loginPassword),
            action("Entrar", () -> session.login(text(loginEmail), secret(loginPassword))), action("Sair",
                () -> session.logout())));

    return tabs;
  }

  private JScrollPane messagePane() {
    messages.setEditable(false);
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

  private void send(Callable<JsonObject> call) {
    if (session == null) {
      append("conecte antes de enviar");
      return;
    }

    network.execute(() -> {
      try {
        JsonObject response = call.call();
        onEdt(() -> status.setText(summary(response)));
      } catch (Exception e) {
        onEdt(() -> append("falha na requisicao: " + e));
      }
    });
  }

  private void setConnected(boolean connected) {
    connection.setText(connected ? "Desconectar" : "Conectar");
    host.setEnabled(!connected);
    port.setEnabled(!connected);
    status.setText(connected ? "conectado" : "desconectado");

    for (JButton action : actions) {
      action.setEnabled(connected);
    }
  }

  private JButton action(String label, Callable<JsonObject> call) {
    JButton button = new JButton(label);
    button.addActionListener(event -> send(call));
    actions.add(button);
    return button;
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

  private static JPanel stack(JComponent... rows) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    for (JComponent component : rows) {
      panel.add(component);
    }

    return panel;
  }

  private static JPanel row(String label, JComponent field) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
    row.add(new JLabel(label));
    row.add(field);
    return row;
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
}
