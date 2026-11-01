package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertKeys;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.protocol.ProtocolAssertions.assertResponse;
import static reservas.server.Fixtures.OTHER_TOKEN;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.T0_ON_THE_WIRE;
import static reservas.server.Fixtures.TOKEN;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.TestJson;

class AdminHandlersTest {
  private static final String ADMIN = TOKEN;
  private static final String COMMON = OTHER_TOKEN;

  private final SqliteStore store = SqliteStore.openInMemory();
  private final List<ProtocolException> reported = new ArrayList<>();
  private final Dispatcher dispatcher = Fixtures.dispatcher(store, reported::add);
  private User maria;
  private User joao;

  @BeforeEach
  void openSessions() {
    ServerApp.installHandlers(dispatcher);

    maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createSession(ADMIN, maria.id(), T0).orElseThrow();

    joao = store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
    store.createSession(COMMON, joao.id(), T0).orElseThrow();
  }

  @AfterEach
  void closeStore() {
    store.close();
  }

  private JsonObject handle(String op, String token, String fields) {
    return dispatcher.handle(TestJson.object("{'op':'" + op + "','token':'" + token + "'" + fields + "}"));
  }

  private User stored(User user) {
    return store.findUserById(user.id()).orElseThrow();
  }

  private String lastDiagnosis() {
    return reported.get(reported.size() - 1).getMessage();
  }

  @Test
  void listsUsersAnswersEveryAccountWithoutPasswords() {
    JsonObject response = handle("admin_list_users", ADMIN, "");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.COUNT, Fields.USERS);
    assertResponse("admin_list_users_response", "200", "Consulta realizada com sucesso", response);
    assertEquals("2", response.get(Fields.COUNT).getAsString());

    JsonArray users = response.getAsJsonArray(Fields.USERS);
    JsonObject first = users.get(0).getAsJsonObject();
    assertKeys(first, Fields.USER, Fields.EMAIL, Fields.ROLE);
    assertEquals("joao", first.get(Fields.USER).getAsString());
    assertEquals("admin", users.get(1).getAsJsonObject().get(Fields.ROLE).getAsString());
  }

  @Test
  void readUserAnswersTheTargetsWholeAccount() {
    JsonObject response = handle("admin_read_user", ADMIN, ",'target_user':'joao'");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.USER, Fields.EMAIL, Fields.ROLE,
        Fields.CREATED_AT);
    assertResponse("admin_read_user_response", "200", "Consulta realizada com sucesso", response);
    assertEquals("joao.silva@email.com", response.get(Fields.EMAIL).getAsString());
    assertEquals("user", response.get(Fields.ROLE).getAsString());
    assertEquals(T0_ON_THE_WIRE, response.get(Fields.CREATED_AT).getAsString());
  }

  @Test
  void updateUserChangesOnlyTheFieldsSent() {
    JsonObject response = handle("admin_update_user", ADMIN,
        ",'target_user':'joao','user':'joaosilva','password':'','role':''");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("admin_update_user_response", "200", "Dados atualizados com sucesso", response);
    assertEquals("joaosilva", stored(joao).name());
    assertEquals("senha123", stored(joao).password());
    assertEquals(Role.USER, stored(joao).role());

    assertStatus("200",
        handle("admin_update_user", ADMIN, ",'target_user':'joaosilva','user':'','password':'novasenha1','role':''"));
    assertEquals("novasenha1", stored(joao).password());
    assertEquals(joao.id(), store.findSession(COMMON).orElseThrow().userId());
  }

  @Test
  void aRoleChangeTakesEffectOnTheTargetsLiveToken() {
    String role = ",'target_user':'joao','user':'','password':'','role':";

    assertStatus("403", handle("admin_list_users", COMMON, ""));

    assertStatus("200", handle("admin_update_user", ADMIN, role + "'admin'"));
    assertStatus("200", handle("admin_list_users", COMMON, ""));

    assertStatus("200", handle("admin_update_user", ADMIN, role + "'user'"));
    assertStatus("403", handle("admin_list_users", COMMON, ""));
  }

  @Test
  void everyAdminOpRefusesACommonUser() {
    String[][] cases = {
        { "admin_list_users", "" },
        { "admin_read_user", ",'target_user':'maria'" },
        { "admin_update_user", ",'target_user':'maria','user':'','password':'','role':'user'" },
        { "admin_delete_user", ",'target_user':'maria'" } };

    for (String[] c : cases) {
      assertResponse(c[0] + "_response", "403", "Permissao insuficiente", handle(c[0], COMMON, c[1]));
    }
    assertEquals(Role.ADMIN, stored(maria).role());
  }

  @Test
  void answers404ForAnUnknownTarget() {
    String[][] cases = {
        { "admin_read_user", ",'target_user':'pedro'" },
        { "admin_update_user", ",'target_user':'pedro','user':'','password':'novasenha1','role':''" },
        { "admin_delete_user", ",'target_user':'pedro'" } };

    for (String[] c : cases) {
      assertResponse(c[0] + "_response", "404", "Usuario nao encontrado", handle(c[0], ADMIN, c[1]));
      assertTrue(lastDiagnosis().contains("\"pedro\""), lastDiagnosis());
    }
  }

  @Test
  void updateUserRejectsEachFieldOutsideTheFormat() {
    String[][] cases = {
        { "target_user", ",'user':'joaosilva','password':'','role':''" },
        { "target_user", ",'target_user':'Joao','user':'joaosilva','password':'','role':''" },
        { "user", ",'target_user':'joao','user':'JOAO','password':'','role':''" },
        { "password", ",'target_user':'joao','user':'','password':'senha 123','role':''" },
        { "role", ",'target_user':'joao','user':'','password':'','role':'chefe'" },
        { "role", ",'target_user':'joao','user':'','password':'','role':null" },
        { "email", ",'target_user':'joao','email':'','user':'joaosilva','password':'','role':''" } };

    for (String[] c : cases) {
      assertResponse("admin_update_user_response", "400", "Dados em formato invalido",
          handle("admin_update_user", COMMON, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), lastDiagnosis());
    }
  }

  @Test
  void updateUserRejectsNothingToChange() {
    for (String fields : new String[] { ",'target_user':'joao','user':'','password':'','role':''",
        ",'target_user':'joao'" }) {
      assertResponse("admin_update_user_response", "400", "Nenhum dado para atualizar",
          handle("admin_update_user", ADMIN, fields));
    }
  }

  @Test
  void updateUserRejectsANameTakenByAnotherUser() {
    assertResponse("admin_update_user_response", "409", "Usuario ja esta em uso",
        handle("admin_update_user", ADMIN, ",'target_user':'joao','user':'maria','password':'','role':''"));
    assertStatus("200",
        handle("admin_update_user", ADMIN, ",'target_user':'joao','user':'joao','password':'','role':''"));
  }

  @Test
  void deleteUserRemovesTheTargetAndItsSession() {
    JsonObject response = handle("admin_delete_user", ADMIN, ",'target_user':'joao'");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("admin_delete_user_response", "200", "Usuario removido com sucesso", response);
    assertTrue(store.findUserById(joao.id()).isEmpty());
    assertTrue(store.findSession(COMMON).isEmpty());
  }

  @Test
  void keepsTheLastAdmin() {
    String demote = ",'target_user':'maria','user':'','password':'','role':'user'";

    assertResponse("admin_update_user_response", "403", "Nao e possivel remover o ultimo administrador",
        handle("admin_update_user", ADMIN, demote));
    assertResponse("admin_delete_user_response", "403", "Nao e possivel remover o ultimo administrador",
        handle("admin_delete_user", ADMIN, ",'target_user':'maria'"));
    assertEquals(Role.ADMIN, stored(maria).role());

    store.createUser("ana", "ana@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    assertStatus("200", handle("admin_delete_user", ADMIN, ",'target_user':'ana'"));

    store.createUser("ana", "ana@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    assertStatus("200", handle("admin_update_user", ADMIN, demote));
    assertEquals(Role.USER, stored(maria).role());
  }
}
