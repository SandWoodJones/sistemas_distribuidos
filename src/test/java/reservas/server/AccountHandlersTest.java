package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertFails;
import static reservas.protocol.ProtocolAssertions.assertKeys;
import static reservas.protocol.ProtocolAssertions.assertResponse;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.OTHER_TOKEN;
import static reservas.server.Fixtures.REGISTER_JOAO;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.T0_ON_THE_WIRE;
import static reservas.server.Fixtures.TOKEN;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.Status;

class AccountHandlersTest {
  private static final String ADMIN_TOKEN = OTHER_TOKEN;
  private static final String UPDATE = "{'op':'update_user','token':'" + TOKEN + "'";
  private static final String DELETE = "{'op':'delete_user','token':'" + TOKEN + "'";

  private final SqliteStore store = SqliteStore.openInMemory();
  private final ServerContext context = Fixtures.context(store);
  private User joao;

  @BeforeEach
  void openSession() {
    joao = store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
    store.createSession(TOKEN, joao.id(), T0).orElseThrow();
  }

  @AfterEach
  void closeStore() {
    store.close();
  }

  private JsonObject readUser(Clock at) {
    return AccountHandlers.readUser(TestJson.object("{'op':'read_user','token':'" + TOKEN + "'}"),
        new ServerContext(store, at, context.tokens()));
  }

  private JsonObject updateUser(String body) {
    return AccountHandlers.updateUser(TestJson.object(body), context);
  }

  private JsonObject deleteUser(String body) {
    return AccountHandlers.deleteUser(TestJson.object(body), context);
  }

  private User stored() {
    return store.findUserById(joao.id()).orElseThrow();
  }

  @Test
  void readUserAnswersTheWholeAccountInTheClockZone() {
    JsonObject response = readUser(context.clock());

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.USER, Fields.EMAIL, Fields.ROLE, Fields.CREATED_AT);
    assertResponse("read_user_response", "200", "Consulta realizada com sucesso", response);
    assertEquals("joao", response.get(Fields.USER).getAsString());
    assertEquals("joao.silva@email.com", response.get(Fields.EMAIL).getAsString());
    assertEquals("user", response.get(Fields.ROLE).getAsString());
    assertEquals(T0_ON_THE_WIRE, response.get(Fields.CREATED_AT).getAsString());

    assertEquals("2026-09-09 17:32:10", readUser(Clock.fixed(T0, ZoneOffset.UTC)).get(Fields.CREATED_AT).getAsString());
  }

  @Test
  void updateChangesOnlyTheFieldsSentAndKeepsTheToken() {
    JsonObject response = updateUser(UPDATE + ",'user':'joaosilva','password':''}");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("update_user_response", "200", "Dados atualizados com sucesso", response);
    assertEquals("joaosilva", stored().name());
    assertEquals("senha123", stored().password());

    updateUser(UPDATE + ",'user':'','password':'novasenha1'}");
    assertEquals("joaosilva", stored().name());
    assertEquals("novasenha1", stored().password());
    assertEquals(joao.id(), store.findSession(TOKEN).orElseThrow().userId());
  }

  @Test
  void updateRejectsNothingToChange() {
    for (String body : new String[] { UPDATE + ",'user':'','password':''}", UPDATE + "}" }) {
      assertFails(Status.BAD_REQUEST, "Nenhum dado para atualizar", () -> updateUser(body));
    }
  }

  @Test
  void updateRejectsEachFieldOutsideTheFormat() {
    String[][] cases = {
        { "user", UPDATE + ",'user':null}" },
        { "user", UPDATE + ",'user':'joao123'}" },
        { "user", UPDATE + ",'user':'JOAO'}" },
        { "user", UPDATE + ",'user':12}" },
        { "password", UPDATE + ",'password':null}" },
        { "password", UPDATE + ",'password':'senha 123'}" },
        { "email", UPDATE + ",'email':'outro@email.com'}" },
        { "email", UPDATE + ",'email':''}" },
        { "email", UPDATE + ",'email':null}" } };

    for (String[] c : cases) {
      ProtocolException failure = assertFails(Status.BAD_REQUEST, "Dados em formato invalido", () -> updateUser(c[1]));
      assertTrue(failure.getMessage().contains("campo '" + c[0] + "'"), failure.getMessage());
    }
  }

  @Test
  void updateRejectsANameTakenByAnotherUser() {
    store.createUser("maria", "maria@email.com", "senha123", Role.USER, T0).orElseThrow();

    assertFails(Status.CONFLICT, "Usuario ja esta em uso", () -> updateUser(UPDATE + ",'user':'maria'}"));
    assertStatus("200", updateUser(UPDATE + ",'user':'joao'}"));
  }

  @Test
  void deleteRemovesTheAccountAndFreesItsName() {
    JsonObject response = deleteUser(DELETE + ",'password':'senha123'}");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("delete_user_response", "200", "Usuario removido com sucesso", response);
    assertTrue(store.findUserById(joao.id()).isEmpty());
    assertTrue(store.findSession(TOKEN).isEmpty());

    JsonObject again = AuthHandlers.register(TestJson.object(REGISTER_JOAO), context);
    assertStatus("201", again);
  }

  @Test
  void deleteRefusesTheWrongPasswordAndKeepsTheSession() {
    ProtocolException failure = assertFails(Status.FORBIDDEN, "Senha incorreta",
        () -> deleteUser(DELETE + ",'password':'errada'}"));

    assertTrue(failure.getMessage().contains("senha incorreta"), failure.getMessage());
    assertTrue(store.findUserById(joao.id()).isPresent());
    assertTrue(store.findSession(TOKEN).isPresent());
  }

  @Test
  void deleteRejectsAnAbsentOrMalformedPassword() {
    for (String body : new String[] { DELETE + "}", DELETE + ",'password':''}", DELETE + ",'password':'senha 123'}",
        DELETE + ",'password':null}" }) {
      assertFails(Status.BAD_REQUEST, "Senha em formato invalido", () -> deleteUser(body));
    }
  }

  @Test
  void deleteKeepsTheLastAdmin() {
    User maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createSession(ADMIN_TOKEN, maria.id(), T0).orElseThrow();
    String body = "{'op':'delete_user','token':'" + ADMIN_TOKEN + "','password':'senha123'}";

    assertFails(Status.FORBIDDEN, "Nao e possivel remover o ultimo administrador", () -> deleteUser(body));
    assertTrue(store.findUserById(maria.id()).isPresent());

    store.createUser("ana", "ana@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    assertStatus("200", deleteUser(body));
    assertTrue(store.findUserById(maria.id()).isEmpty());
  }
}
