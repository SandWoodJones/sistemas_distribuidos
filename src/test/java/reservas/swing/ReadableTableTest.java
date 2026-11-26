package reservas.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.Test;

class ReadableTableTest {
  private static JTable table() {
    DefaultTableModel model = new DefaultTableModel(new Object[] { "Nome", "Horarios" }, 0);
    model.addRow(new Object[] { "Laboratorio de Redes 1", "2M1, 2M2, 2M3, ".repeat(60) });
    JTable table = new JTable(model);
    ReadableTable.install(table);
    return table;
  }

  @Test
  void widensColumnsToTheirContentUpToALimit() {
    JTable table = table();
    ReadableTable.fitColumns(table);

    int name = table.getColumnModel().getColumn(0).getPreferredWidth();
    int width = table.getFontMetrics(table.getFont()).stringWidth("Laboratorio de Redes 1");
    assertTrue(name >= width, name + " < " + width);
    assertEquals(ReadableTable.MAX_COLUMN_WIDTH, table.getColumnModel().getColumn(1).getPreferredWidth());
    assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode());
  }

  @Test
  void describesTheWholeRowFieldByField() {
    String row = ReadableTable.describe(table(), 0);

    assertTrue(row.startsWith("Nome: Laboratorio de Redes 1\nHorarios: 2M1, 2M2"), row);
    assertTrue(row.endsWith("2M3, "), row);
  }
}
