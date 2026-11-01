package reservas.server;

import java.util.List;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Formats;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;
import reservas.protocol.Responses;
import reservas.protocol.Role;
import reservas.protocol.Status;

public final class AdminHandlers {
  static final String LIST_USERS_BAD_REQUEST = "Token em formato invalido";
  static final String TARGET_BAD_REQUEST = "Dados em formato invalido";
  static final String USER_NOT_FOUND = "Usuario nao encontrado";

  private AdminHandlers() {
  }

  public static void install(Dispatcher dispatcher) {
    dispatcher.register(Op.ADMIN_LIST_USERS, AdminHandlers::listUsers);
    dispatcher.register(Op.ADMIN_READ_USER, AdminHandlers::readUser);
    dispatcher.register(Op.ADMIN_UPDATE_USER, AdminHandlers::updateUser);
    dispatcher.register(Op.ADMIN_DELETE_USER, AdminHandlers::deleteUser);
  }

  static JsonObject listUsers(JsonObject json, ServerContext context) {
    Request request = new Request(json, LIST_USERS_BAD_REQUEST);

    Caller.authenticate(request, context).requireAdmin();
    List<User> users = context.store().listUsers();

    return Responses.listing(Op.ADMIN_LIST_USERS, Fields.USERS, users.stream().map(UserViews::summary).toList());
  }

  static JsonObject readUser(JsonObject json, ServerContext context) {
    Request request = new Request(json, TARGET_BAD_REQUEST);
    String target = request.required(Fields.TARGET_USER, Formats.USER);

    Caller.authenticate(request, context).requireAdmin();
    User user = findTarget(context.store(), target);

    return Responses.found(Op.ADMIN_READ_USER, UserViews.details(user, context.clock().getZone()));
  }

  static JsonObject updateUser(JsonObject json, ServerContext context) {
    Request request = new Request(json, TARGET_BAD_REQUEST);
    request.mustBeAbsent(Fields.EMAIL);
    String target = request.required(Fields.TARGET_USER, Formats.USER);
    UserChanges changes = changes(request);

    Caller.authenticate(request, context).requireAdmin();

    SqliteStore store = context.store();
    User user = findTarget(store, target);
    store.updateUser(user.id(), changes).orThrow(user, changes);
    return Responses.of(Op.ADMIN_UPDATE_USER, Status.OK, "Dados atualizados com sucesso");
  }

  static JsonObject deleteUser(JsonObject json, ServerContext context) {
    Request request = new Request(json, TARGET_BAD_REQUEST);
    String target = request.required(Fields.TARGET_USER, Formats.USER);

    Caller.authenticate(request, context).requireAdmin();

    SqliteStore store = context.store();
    User user = findTarget(store, target);
    if (!store.deleteUserUnlessLastAdmin(user.id())) {
      throw Admin.lastAdmin(user);
    }

    return Responses.of(Op.ADMIN_DELETE_USER, Status.OK, "Usuario removido com sucesso");
  }

  private static UserChanges changes(Request request) {
    String name = request.optional(Fields.USER, Formats.USER).orElse(null);
    String password = request.optional(Fields.PASSWORD, Formats.PASSWORD).orElse(null);
    Role role = request.optional(Fields.ROLE, Formats.ROLE).flatMap(Role::fromWire).orElse(null);
    return new UserChanges(name, password, role);
  }

  private static User findTarget(SqliteStore store, String target) {
    return store.findUser(target)
        .orElseThrow(() -> ProtocolException.notFound(USER_NOT_FOUND, "target_user \"" + target + "\" nao cadastrado"));
  }
}
