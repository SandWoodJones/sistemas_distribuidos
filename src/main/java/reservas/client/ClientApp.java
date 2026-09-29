package reservas.client;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

// Ponto de entrada do cliente
public final class ClientApp {
  private ClientApp() {
  }

  public static void main(String[] args) {
    smoothText();
    
    SwingUtilities.invokeLater(() -> {
      useNimbus();
      new ClientWindow().setVisible(true);
    });
  }

  private static void smoothText() {
    System.setProperty("awt.useSystemAAFontSettings", "lcd");
    System.setProperty("swing.aatext", "true");
  }

  private static void useNimbus() {
    try {
      UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
      System.err.println("Nimbus indisponivel, usando o tema padrao: " + e);
    }
  }
}
