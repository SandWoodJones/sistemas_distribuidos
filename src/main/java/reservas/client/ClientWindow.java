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

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
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

  private final JTextArea messages = new JTextArea(20, 58);

  private Session session;

  public ClientWindow() {
    super("Reserva de Salas - Cliente");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    add(connectionBar(), BorderLayout.NORTH);
    add(operations(), BorderLayout.WEST);
    add(messagePane(), BorderLayout.CENTER);

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

  private void send(Callable<JsonObject> call) {
    if (session == null) {
      append("conecte antes de enviar");
      return;
    }

    network.execute(() -> {
      try {
        JsonObject response = call.call();
        onEdt(() -> showResult(response));
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
    String code = response.get(Fields.STATUS).getAsString();
    status.setText(summary(response));
    status.setForeground(code.startsWith("2") ? OK : FAILED);
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

    private void button(String label, Callable<JsonObject> call) {
      JButton button = new JButton(label);
      button.setEnabled(false);
      button.addActionListener(event -> send(call));
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
