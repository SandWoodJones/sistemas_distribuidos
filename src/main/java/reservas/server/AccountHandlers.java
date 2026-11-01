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

  static JsonObject readUser(JsonObject json, ServerContext context) {
    Request request = new Request(json, READ_USER_BAD_REQUEST);

    User user = Caller.authenticate(request, context).user();

    return Responses.found(Op.READ_USER, UserViews.details(user, context.clock().getZone()));
  }

  static JsonObject updateUser(JsonObject json, ServerContext context) {
    Request request = new Request(json, UPDATE_USER_BAD_REQUEST);
    request.mustBeAbsent(Fields.EMAIL);
    String name = request.optional(Fields.USER, Formats.USER).orElse(null);
    String password = request.optional(Fields.PASSWORD, Formats.PASSWORD).orElse(null);
    UserChanges changes = new UserChanges(name, password, null);

    User user = Caller.authenticate(request, context).user();
    context.store().updateUser(user.id(), changes).orThrow(user, changes);
    return Responses.of(Op.UPDATE_USER, Status.OK, "Dados atualizados com sucesso");
  }

  static JsonObject deleteUser(JsonObject json, ServerContext context) {
    Request request = new Request(json, DELETE_USER_BAD_REQUEST);
    String password = request.required(Fields.PASSWORD, Formats.PASSWORD);

    User user = Caller.authenticate(request, context).user();
    confirmPassword(user, password);
    if (!context.store().deleteUserUnlessLastAdmin(user.id())) {
      throw Admin.lastAdmin(user);
    }

    return Responses.of(Op.DELETE_USER, Status.OK, "Usuario removido com sucesso");
  }

  private static String createdAt(User user, Clock clock) {
    return CREATED_AT.withZone(clock.getZone()).format(user.createdAt());
  }

  private static void confirmPassword(User user, String password) {
    if (!user.password().equals(password)) {
      throw ProtocolException.forbidden("Senha incorreta", "senha incorreta para user \"" + user.name() + "\"");
    }
  }
}
