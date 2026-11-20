package reservas.server;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.table.AbstractTableModel;

// Usuários com sessão viva, uma linha por usuário
final class LoggedInTableModel extends AbstractTableModel {
  private static final String[] COLUMNS = { "Usuario", "Perfil", "Ultima atividade" };
  private static final DateTimeFormatter LAST_USED = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final ZoneId zone;
  private List<LoggedInUser> users = List.of();

  LoggedInTableModel(ZoneId zone) {
    this.zone = zone;
  }

  void show(List<LoggedInUser> current) {
    if (!current.equals(users)) {
      users = List.copyOf(current);
      fireTableDataChanged();
    }
  }

  @Override
  public int getRowCount() {
    return users.size();
  }

  @Override
  public int getColumnCount() {
    return COLUMNS.length;
  }

  @Override
  public String getColumnName(int column) {
    return COLUMNS[column];
  }

  @Override
  public Object getValueAt(int row, int column) {
    LoggedInUser user = users.get(row);
    return switch (column) {
      case 0 -> user.name();
      case 1 -> user.role().wireName();
      default -> LAST_USED.withZone(zone).format(user.session().lastUsed());
    };
  }
}
