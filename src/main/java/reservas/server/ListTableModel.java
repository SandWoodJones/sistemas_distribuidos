package reservas.server;

import java.util.List;
import java.util.function.Function;

import javax.swing.table.AbstractTableModel;

// Tabela só de leitura de uma lista que a janela troca inteira a cada atualização
final class ListTableModel<T> extends AbstractTableModel {
  record Column<T>(String name, Function<T, String> value) {}

  private final List<Column<T>> columns;
  private List<T> rows = List.of();

  ListTableModel(List<Column<T>> columns) {
    this.columns = List.copyOf(columns);
  }

  void show(List<T> current) {
    if (!current.equals(rows)) {
      rows = List.copyOf(current);
      fireTableDataChanged();
    }
  }

  @Override
  public int getRowCount() {
    return rows.size();
  }

  @Override
  public int getColumnCount() {
    return columns.size();
  }

  @Override
  public String getColumnName(int column) {
    return columns.get(column).name();
  }

  @Override
  public Object getValueAt(int row, int column) {
    return columns.get(column).value().apply(rows.get(row));
  }
}
