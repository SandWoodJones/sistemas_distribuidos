package reservas.server;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Formats;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;
import reservas.protocol.Request;
import reservas.protocol.Responses;
import reservas.protocol.Role;
import reservas.protocol.Status;

// `register`, `login` e `logout`
public final class AuthHandlers {
  static final String REGISTER_BAD_REQUEST = "Dados de cadastro em formato invalido";
  static final String LOGIN_BAD_REQUEST = "Email ou senha em formato invalido";
  static final String LOGIN_UNAUTHORIZED = "Email ou senha incorretos";
  static final String LOGOUT_BAD_REQUEST = "Token em formato invalido";

  private AuthHandlers() {
  }

  public static void install(Dispatcher dispatcher) {
    dispatcher.register(Op.REGISTER, AuthHandlers::register);
    dispatcher.register(Op.LOGIN, AuthHandlers::login);
    dispatcher.register(Op.LOGOUT, AuthHandlers::logout);
  }

  // Todo cadastro nasce `user` sem sessão
  static JsonObject register(JsonObject json, ServerContext context) {
    Request request = new Request(json, REGISTER_BAD_REQUEST);
    String email = request.required(Fields.EMAIL, Formats.EMAIL);
    String user = request.required(Fields.USER, Formats.USER);
    String password = request.required(Fields.PASSWORD, Formats.PASSWORD);

    SqliteStore store = context.store();

    if (store.createUser(user, email, password, Role.USER, context.clock().instant()).isEmpty()) {
      throw ProtocolException.conflict("Usuario ou email ja cadastrado", taken(store, user, email));
    }

    return Responses.of(Op.REGISTER, Status.CREATED, "Usuario cadastrado com sucesso");
  }

  static JsonObject login(JsonObject json, ServerContext context) {
    Request request = new Request(json, LOGIN_BAD_REQUEST);
    String email = request.required(Fields.EMAIL, Formats.EMAIL);
    String password = request.required(Fields.PASSWORD, Formats.PASSWORD);

    SqliteStore store = context.store();

    User user = authenticate(store, email, password);
    Session session = store.createSession(context.tokens().next(), user.id(), context.clock().instant()).orElseThrow(() -> ProtocolException
        .conflict("Usuario ja possui sessao ativa", "user \"" + user.name() + "\" ja tem sessao viva"));

    JsonObject response = Responses.of(Op.LOGIN, Status.OK, "Login realizado com sucesso");
    response.addProperty(Fields.TOKEN, session.token());
    response.addProperty(Fields.ROLE, user.role().wireName());
    return response;
  }

  static JsonObject logout(JsonObject json, ServerContext context) {
    Request request = new Request(json, LOGOUT_BAD_REQUEST);
    Caller caller = Caller.authenticate(request, context);

    context.store().deleteSession(caller.session().token());
    return Responses.of(Op.LOGOUT, Status.OK, "Logout realizado com sucesso");
  }

  // Email inexistente e senha errada dão o mesmo 404 para não revelar quais usuários existem
  private static User authenticate(SqliteStore store, String email, String password) {
    User user = store.findUserByEmail(email).orElseThrow(
        () -> ProtocolException.unauthorized(LOGIN_UNAUTHORIZED, "email \"" + email + "\" nao cadastrado"));
    if (!user.password().equals(password)) {
      throw ProtocolException.unauthorized(LOGIN_UNAUTHORIZED, "senha incorreta para email \"" + email + "\"");
    }

    return user;
  }

  private static String taken(SqliteStore store, String user, String email) {
    return store.findUser(user).isPresent() ? "user \"" + user + "\" ja cadastrado"
        : "email \"" + email + "\" ja cadastrado";
  }
}
