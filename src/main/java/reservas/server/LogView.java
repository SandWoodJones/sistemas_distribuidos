package reservas.server;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

// Painel de log com cor por nível, guardando só as últimas linhas. Só na EDT
final class LogView extends JTextPane {
  private static final Color ERROR = new Color(0xB0, 0x00, 0x20);
  private static final Color WARN = new Color(0xB3, 0x5C, 0x00);
  private static final Color OTHER = new Color(0x60, 0x60, 0x60);

  private final int maxLines;
  // Tamanho de cada linha, na ordem, para tirar a mais antiga do documento sem varrer o texto
  private final Deque<Integer> lengths = new ArrayDeque<>();

  LogView(int maxLines) {
    this.maxLines = maxLines;
    setEditable(false);
    setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
  }

  void add(String line) {
    SimpleAttributeSet style = new SimpleAttributeSet();
    StyleConstants.setForeground(style, switch (LogLine.of(line)) {
      case ERROR -> ERROR;
      case WARN -> WARN;
      case TRAFFIC -> Color.BLACK;
      case OTHER -> OTHER;
    });

    Document document = getDocument();
    try {
      document.insertString(document.getLength(), line, style);
      lengths.addLast(line.length());
      if (lengths.size() > maxLines) {
        document.remove(0, lengths.removeFirst());
      }
    } catch (BadLocationException e) {
      throw new IllegalStateException("linha fora do log com " + document.getLength() + " caracteres", e);
    }

    setCaretPosition(document.getLength());
  }
}
