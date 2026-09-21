package reservas.server;

@FunctionalInterface
public interface TokenGenerator {
  String next();
}
