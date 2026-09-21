package reservas.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import reservas.protocol.Formats;

class RandomTokenGeneratorTest {
  @Test
  void generatesDistinctTokensInTheProtocolFormat() {
    TokenGenerator generator = new RandomTokenGenerator();
    Set<String> seen = new HashSet<>();

    for (int i = 0; i < 1000; i++) {
      String token = generator.next();
      assertTrue(Formats.matches(Formats.TOKEN, token), "fora do formato: " + token);
      assertTrue(seen.add(token), "token repetido: " + token);
    }
  }
}
