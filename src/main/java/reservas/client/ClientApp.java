package reservas.client;

import javax.swing.SwingUtilities;

// Ponto de entrada do cliente
public final class ClientApp {
  private ClientApp() {
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new ClientWindow().setVisible(true));
  }
}
