package reservas.client;

import java.io.IOException;
import java.util.Optional;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.Op;
import reservas.protocol.Status;

// Sessão do lado do cliente. Token vive em memória
public final class Session implements AutoCloseable {
  private final ProtocolClient client;

  private String token;
  private String role;

  public Session(ProtocolClient client) {
    this.client = client;
  }

  public boolean isLoggedIn() {
    return token != null;
  }

  public Optional<String> role() {
    return Optional.ofNullable(role);
  }

  public JsonObject register(String email, String user, String password) throws IOException {
    JsonObject request = request(Op.REGISTER);
    request.addProperty(Fields.EMAIL, email);
    request.addProperty(Fields.USER, user);
    request.addProperty(Fields.PASSWORD, password);

    return client.ask(request);
  }

  public JsonObject login(String email, String password) throws IOException {
    JsonObject request = request(Op.LOGIN);
    request.addProperty(Fields.EMAIL, email);
    request.addProperty(Fields.PASSWORD, password);

    JsonObject response = client.ask(request);
    if (succeeded(response)) {
      token = response.get(Fields.TOKEN).getAsString();
      role = response.get(Fields.ROLE).getAsString();
    }

    return response;
  }

  public JsonObject logout() throws IOException {
    JsonObject response = client.ask(authenticated(Op.LOGOUT));
    if (succeeded(response)) {
      forget();
    }

    return response;
  }

  public JsonObject readUser() throws IOException {
    return client.ask(authenticated(Op.READ_USER));
  }

  public JsonObject updateUser(String user, String password) throws IOException {
    JsonObject request = authenticated(Op.UPDATE_USER);
    request.addProperty(Fields.USER, user);
    request.addProperty(Fields.PASSWORD, password);

    return client.ask(request);
  }

  public JsonObject deleteUser(String password) throws IOException {
    JsonObject request = authenticated(Op.DELETE_USER);
    request.addProperty(Fields.PASSWORD, password);

    JsonObject response = client.ask(request);
    if (succeeded(response)) {
      forget();
    }

    return response;
  }

  @Override
  public void close() throws IOException {
    forget();
    client.close();
  }

  private void forget() {
    token = null;
    role = null;
  }

  private JsonObject request(Op op) {
    JsonObject request = new JsonObject();
    request.addProperty(Fields.OP, op.wireName());
    return request;
  }

  // Token ausente vai como `""`
  private JsonObject authenticated(Op op) {
    JsonObject request = request(op);
    request.addProperty(Fields.TOKEN, token == null ? "" : token);
    return request;
  }

  private static boolean succeeded(JsonObject response) {
    return Status.OK.code().equals(response.get(Fields.STATUS).getAsString());
  }
}
