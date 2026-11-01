package reservas.server;

import org.slf4j.Logger;

import reservas.protocol.ProtocolException;
import reservas.protocol.Status;

// Falha esperada vira uma linha
final class Diagnostics {
  private Diagnostics() {

  }

  static void report(Logger log, ProtocolException failure) {
    if (failure.status() == Status.INTERNAL_SERVER_ERROR) {
      log.error(failure.getMessage(), failure);
    } else {
      log.warn("{} {}{}", failure.status().code(), failure.getMessage(), cause(failure));
    }
  }

  private static String cause(ProtocolException failure) {
    Throwable cause = failure.getCause();
    return cause == null ? "" : " | causa: " + cause;
  }
}
