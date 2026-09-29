package reservas.server;

import java.time.Clock;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Formats;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;
import reservas.protocol.Responses;
import reservas.protocol.Role;
import reservas.protocol.Status;

public final class AccountHandlers {
  static final String READ_USER_BAD_REQUEST = "Token em formato invalido";
  static final String UPDATE_USER_BAD_REQUEST = "Dados em formato invalido";
  static final String DELETE_USER_BAD_REQUEST = "Senha em formato invalido";

  private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private AccountHandlers() {
  }

  public static void install(Dispatcher dispatcher) {
    dispatcher.register(Op.READ_USER, AccountHandlers::readUser);
    dispatcher.register(Op.UPDATE_USER, AccountHandlers::updateUser);
    dispatcher.register(Op.DELETE_USER, AccountHandlers::deleteUser);
  }

  static JsonObject readUser(JsonObject json, SqliteStore store, Clock clock, TokenGenerator tokens) {
    Request request = new Request(json, READ_USER_BAD_REQUEST);
    User user = Caller.authenticate(request, store, clock).user();

    JsonObject response = Responses.of(Op.READ_USER, Status.OK, "Consulta realizada com sucesso");
    response.addProperty(Fields.USER, user.name());
    response.addProperty(Fields.EMAIL, user.email());
    response.addProperty(Fields.ROLE, user.role().wireName());
    response.addProperty(Fields.CREATED_AT, createdAt(user, clock));
    return response;
  }

  static JsonObject updateUser(JsonObject json, SqliteStore store, Clock clock, TokenGenerator tokens) {
    Request request = new Request(json, UPDATE_USER_BAD_REQUEST);
    request.mustBeAbsent(Fields.EMAIL);
    String name = request.optional(Fields.USER, Formats.USER).orElse(null);
    String password = request.optional(Fields.PASSWORD, Formats.PASSWORD).orElse(null);

    User updated = Caller.authenticate(request, store, clock).user().with(name, password);
    if (store.updateUser(updated).isEmpty()) {
      throw ProtocolException.conflict("Usuario ja esta em uso",
          "user \"" + updated.name() + "\" ja pertence a outro cadastro");
    }

    return Responses.of(Op.UPDATE_USER, Status.OK, "Dados atualizados com sucesso");
  }

  static JsonObject deleteUser(JsonObject json, SqliteStore store, Clock clock, TokenGenerator tokens) {
    Request request = new Request(json, DELETE_USER_BAD_REQUEST);
    String password = request.required(Fields.PASSWORD, Formats.PASSWORD);

    User user = Caller.authenticate(request, store, clock).user();
    confirmPassword(user, password);
    protectLastAdmin(store, user);

    store.deleteUser(user.id());
    return Responses.of(Op.DELETE_USER, Status.OK, "Usuario removido com sucesso");
  }

  private static String createdAt(User user, Clock clock) {
    return CREATED_AT.withZone(clock.getZone()).format(user.createdAt());
  }

  private static void confirmPassword(User user, String password) {
    if (!user.password().equals(password)) {
      throw ProtocolException.unauthorized(Caller.UNAUTHORIZED, "senha incorreta para user \"" + user.name() + "\"");
    }
  }

  private static void protectLastAdmin(SqliteStore store, User user) {
    if (user.role() == Role.ADMIN && store.countAdmins() == 1) {
      throw ProtocolException.forbidden("Nao e possivel remover o ultimo administrador",
          "user \"" + user.name() + "\" e o unico admin");
    }
  }
}
