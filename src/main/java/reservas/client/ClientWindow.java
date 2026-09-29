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
    tabs.addTab("Cadastro", registerTab());
    tabs.addTab("Login", loginTab());
    tabs.addTab("Minha conta", accountTab());
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

    JTextField user = form.field("Novo usuario");
    JPasswordField password = form.password("Nova senha");
    form.button("Atualizar", () -> session.updateUser(text(user), secret(password)));

    JPasswordField confirm = form.password("Confirme a senha");
    form.button("Remover conta", () -> session.deleteUser(secret(confirm)));

    return form.panel();
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
    }

    private JTextField field(String label) {
      return labelled(label, new JTextField());
    }

    private JPasswordField password(String label) {
      return labelled(label, new JPasswordField(18));
    }

    private void button(String label, Callable<JsonObject> call) {
      JButton button = new JButton(label);
      button.setEnabled(false);
      button.addActionListener(event -> send(call));

      actions.add(button);
      panel.add(button);
    }

    private JPanel panel() {
      return panel;
    }

    private <T extends JComponent> T labelled(String label, T field) {
      JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
      row.add(new JLabel(label));
      row.add(field);
      panel.add(row);

      return field;
    }
  }
}
