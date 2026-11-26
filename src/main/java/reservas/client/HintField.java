package reservas.client;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JTextField;

// Campo de texto que mostra uma dica em cinza enquanto está vazio
final class HintField extends JTextField {
  private static final Color HINT = new Color(0x90, 0x90, 0x90);

  private final String hint;

  HintField(String text, int columns, String hint) {
    super(text, columns);
    this.hint = hint;
    setToolTipText(hint.isEmpty() ? null : hint);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    if (!getText().isEmpty() || hint.isEmpty()) {
      return;
    }

    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(HINT);
    g.setFont(getFont().deriveFont(getFont().getSize2D() - 1));
    Insets insets = getInsets();
    g.drawString(hint, insets.left + 2, getHeight() / 2 + g.getFontMetrics().getAscent() / 2 - 2);
    g.dispose();
  }
}
