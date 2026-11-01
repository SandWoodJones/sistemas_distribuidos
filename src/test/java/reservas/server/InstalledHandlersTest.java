package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static reservas.protocol.ProtocolAssertions.assertResponse;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.LOGIN_JOAO;
import static reservas.server.Fixtures.REGISTER_JOAO;
import static reservas.server.Fixtures.T0_ON_THE_WIRE;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;

class InstalledHandlersTest {
  private final SqliteStore store = SqliteStore.openInMemory();
  private final List<ProtocolException> reported = new ArrayList<>();
  private final Dispatcher dispatcher = Fixtures.dispatcher(store, reported::add);

  @BeforeEach
  void installHandlers() {
    ServerApp.installHandlers(dispatcher);
  }

  @AfterEach
  void closeStore() {
    store.close();
  }

  private JsonObject handle(String json) {
    return dispatcher.handle(TestJson.object(json));
  }

  @Test
  void everyInstalledOpAnswersItsOwnBadRequestText() {
    String bad = "'token':'abc'";
    String[][] cases = {
        { "register", "{'op':'register'}", "Dados de cadastro em formato invalido" },
        { "login", "{'op':'login'}", "Email ou senha em formato invalido" },
        { "logout", "{'op':'logout'," + bad + "}", "Token em formato invalido" },
        { "read_user", "{'op':'read_user'," + bad + "}", "Token em formato invalido" },
        { "update_user", "{'op':'update_user'," + bad + ",'user':'joao'}", "Dados em formato invalido" },
        { "delete_user", "{'op':'delete_user'," + bad + ",'password':'senha123'}", "Senha em formato invalido" },
        { "admin_list_users", "{'op':'admin_list_users'," + bad + "}", "Token em formato invalido" },
        { "admin_read_user", "{'op':'admin_read_user'," + bad + ",'target_user':'joao'}", "Dados em formato invalido" },
        { "admin_update_user", "{'op':'admin_update_user'," + bad + ",'target_user':'joao','role':'admin'}",
            "Dados em formato invalido" },
        { "admin_delete_user", "{'op':'admin_delete_user'," + bad + ",'target_user':'joao'}",
            "Dados em formato invalido" } };

    for (String[] c : cases) {
      assertResponse(c[0] + "_response", "400", c[2], handle(c[1]));
    }
  }

  @Test
  void walksTheWholeAccountLifecycle() {
    assertStatus("201", handle(REGISTER_JOAO));

    JsonObject login = handle(LOGIN_JOAO);
    assertStatus("200", login);
    String token = login.get(Fields.TOKEN).getAsString();

    JsonObject read = handle("{'op':'read_user','token':'" + token + "'}");
    assertStatus("200", read);
    assertEquals("joao", read.get(Fields.USER).getAsString());
    assertEquals(T0_ON_THE_WIRE, read.get(Fields.CREATED_AT).getAsString());

    assertStatus("200", handle("{'op':'update_user','token':'" + token + "','user':'joaosilva'}"));
    assertEquals("joaosilva",
        handle("{'op':'read_user','token':'" + token + "'}").get(Fields.USER).getAsString());

    assertStatus("200", handle("{'op':'logout','token':'" + token + "'}"));
    assertStatus("401", handle("{'op':'read_user','token':'" + token + "'}"));

    String again = handle(LOGIN_JOAO).get(Fields.TOKEN).getAsString();
    assertStatus("200", handle("{'op':'delete_user','token':'" + again + "','password':'senha123'}"));
    assertStatus("401", handle("{'op':'read_user','token':'" + again + "'}"));

    assertEquals(2, reported.size(), reported.toString());
  }
}
