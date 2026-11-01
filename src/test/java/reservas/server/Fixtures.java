package reservas.server;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

import reservas.protocol.ProtocolException;

public final class Fixtures {
  public static final Instant T0 = Instant.parse("2026-09-09T17:32:10Z");
  public static final String T0_ON_THE_WIRE = "2026-09-09 14:32:10";

  public static final String TOKEN = "a".repeat(64);
  public static final String OTHER_TOKEN = "b".repeat(64);

  public static final String REGISTER_JOAO = "{'op':'register','email':'joao.silva@email.com','user':'joao','password':'senha123'}";
  public static final String LOGIN_JOAO = "{'op':'login','email':'joao.silva@email.com','password':'senha123'}";

  private Fixtures() {
  }

  public static Clock clockAt(Instant now) {
    return Clock.fixed(now, ServerApp.ZONE);
  }

  public static ServerContext context(SqliteStore store, Instant now) {
    return new ServerContext(store, clockAt(now), () -> TOKEN);
  }

  public static ServerContext context(SqliteStore store) {
    return context(store, T0);
  }

  public static Dispatcher dispatcher(SqliteStore store, Consumer<ProtocolException> diagnostics) {
    return new Dispatcher(store, clockAt(T0), () -> TOKEN, diagnostics);
  }
}
