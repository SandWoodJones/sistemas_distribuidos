package reservas.server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Endereços IPv4 desta máquina, para dizer aos clientes de outras máquinas onde conectar
final class LocalAddresses {
  private static final Logger log = LoggerFactory.getLogger(LocalAddresses.class);

  private LocalAddresses() {
  }

  // Vazio se só houver loopback ou se a lista de interfaces falhar (a falha vai para o log)
  static List<String> ipv4() {
    try {
      return NetworkInterface.networkInterfaces().filter(LocalAddresses::isReachable)
          .flatMap(NetworkInterface::inetAddresses).filter(Inet4Address.class::isInstance)
          .map(InetAddress::getHostAddress).sorted().toList();
    } catch (SocketException e) {
      log.warn("nao foi possivel listar os enderecos da maquina: {}", e.getMessage(), e);
      return List.of();
    }
  }

  private static boolean isReachable(NetworkInterface network) {
    try {
      return network.isUp() && !network.isLoopback();
    } catch (SocketException e) {
      log.warn("interface {} ignorada: {}", network.getName(), e.getMessage(), e);
      return false;
    }
  }

  // " em 192.168.0.12, 10.0.0.5", ou nada
  static String describe(List<String> addresses) {
    return addresses.isEmpty() ? "" : " em " + String.join(", ", addresses);
  }
}
