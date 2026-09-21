package reservas.server;

import java.security.SecureRandom;
import java.util.HexFormat;

// 32 bytes aleatórios em hexadecimal minúsculo
public final class RandomTokenGenerator implements TokenGenerator {
  private static final int TOKEN_BYTES = 32;

  private final SecureRandom random = new SecureRandom();
  private final HexFormat hex = HexFormat.of();

  @Override
  public String next() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return hex.formatHex(bytes);
  }
}
