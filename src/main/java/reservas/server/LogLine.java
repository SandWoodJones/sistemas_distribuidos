package reservas.server;

// Tipo de uma linha do log, para a cor e o filtro da janela do servidor
enum LogLine {
  ERROR, WARN, TRAFFIC, OTHER;

  static LogLine of(String line) {
    if (line.contains(" ERROR ")) {
      return ERROR;
    }
    if (line.contains(" WARN ")) {
      return WARN;
    }
    if (line.contains(" - recebido ") || line.contains(" - enviado ")) {
      return TRAFFIC;
    }

    return OTHER;
  }
}
