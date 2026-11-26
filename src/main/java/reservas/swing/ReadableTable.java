package reservas.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.StringJoiner;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

// Tabela em que dá para ler tudo: colunas na largura do conteúdo (com rolagem horizontal), o texto inteiro
// da célula ao passar o mouse e a linha toda, campo por campo, num duplo clique
public final class ReadableTable {
  // Coluna mais larga que isso só pela dica ou pelo duplo clique: os horários de uma sala passam de 600 caracteres
  static final int MAX_COLUMN_WIDTH = 360;
  private static final int PADDING = 14;
  private static final int MEASURED_ROWS = 300;

  private ReadableTable() {
  }

  public static void install(JTable table) {
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setDefaultRenderer(Object.class, new FullTextRenderer());
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        int row = table.rowAtPoint(event.getPoint());
        if (event.getClickCount() == 2 && row >= 0) {
          showRow(table, row);
        }
      }
    });

    TableModelListener refit = event -> SwingUtilities.invokeLater(() -> fitColumns(table));
    table.getModel().addTableModelListener(refit);
    table.addPropertyChangeListener("model", event -> {
      ((TableModel) event.getOldValue()).removeTableModelListener(refit);
      ((TableModel) event.getNewValue()).addTableModelListener(refit);
      SwingUtilities.invokeLater(() -> fitColumns(table));
    });
    fitColumns(table);
  }

  // Cada coluna na largura do cabeçalho ou da célula mais larga, até MAX_COLUMN_WIDTH
  static void fitColumns(JTable table) {
    int rows = Math.min(table.getRowCount(), MEASURED_ROWS);
    for (int column = 0; column < table.getColumnCount(); column++) {
      TableColumn tableColumn = table.getColumnModel().getColumn(column);
      int width = headerWidth(table, tableColumn);
      for (int row = 0; row < rows; row++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component cell = table.prepareRenderer(renderer, row, column);
        width = Math.max(width, cell.getPreferredSize().width);
      }
      tableColumn.setPreferredWidth(Math.min(width + PADDING, MAX_COLUMN_WIDTH));
    }
  }

  // "Cabeçalho: valor", um campo por linha, na ordem em que as colunas aparecem
  static String describe(JTable table, int row) {
    StringJoiner lines = new StringJoiner("\n");
    for (int column = 0; column < table.getColumnCount(); column++) {
      Object header = table.getColumnModel().getColumn(column).getHeaderValue();
      lines.add(header + ": " + text(table.getValueAt(row, column)));
    }
    return lines.toString();
  }

  private static void showRow(JTable table, int row) {
    JTextArea text = new JTextArea(describe(table, row));
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setColumns(60);
    text.setCaretPosition(0);

    JScrollPane scroll = new JScrollPane(text);
    scroll.setPreferredSize(new Dimension(560, Math.min(420, 40 + 20 * table.getColumnCount())));
    JOptionPane.showMessageDialog(table, scroll, "Linha " + (row + 1), JOptionPane.PLAIN_MESSAGE);
  }

  private static int headerWidth(JTable table, TableColumn column) {
    TableCellRenderer renderer = column.getHeaderRenderer() != null ? column.getHeaderRenderer()
        : table.getTableHeader().getDefaultRenderer();
    return renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1, 0)
        .getPreferredSize().width;
  }

  private static String text(Object value) {
    return value == null ? "" : value.toString();
  }

  // A dica mostra o texto inteiro da célula, cortada ou não
  private static final class FullTextRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
        int row, int column) {
      super.getTableCellRendererComponent(table, value, selected, focused, row, column);
      String text = text(value);
      setToolTipText(text.isEmpty() ? null : text);
      return this;
    }
  }
}
