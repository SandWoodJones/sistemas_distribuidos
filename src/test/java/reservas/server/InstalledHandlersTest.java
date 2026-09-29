package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Fields;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;

class InstalledHandlersTest {
  private static final Instant T0 = Instant.parse("2026-09-09T17:32:10Z");

  private final SqliteStore store = SqliteStore.openInMemory();
  private final List<ProtocolException> reported = new ArrayList<>();
  private final Dispatcher dispatcher = new Dispatcher(store, Clock.fixed(T0, ZoneId.of("America/Sao_Paulo")),
      () -> "a".repeat(64), reported::add);

  @BeforeEach
  void installHandlers() {
    AuthHandlers.install(dispatcher);
    AccountHandlers.install(dispatcher);
  }

  @AfterEach
  void closeStore() {
    store.close();
  }

  private JsonObject handle(String json) {
    return dispatcher.handle(TestJson.object(json));
  }

  private static void assertStatus(String status, JsonObject response) {
    assertEquals(status, response.get(Fields.STATUS).getAsString(), response.toString());
  }

  @Test
  void everyOpHasAHandler() {
    for (Op op : new Op[] { Op.REGISTER, Op.LOGIN, Op.LOGOUT, Op.READ_USER, Op.UPDATE_USER, Op.DELETE_USER }) {
      assertEquals(op.responseName(), handle("{'op':'" + op.wireName() + "'}").get(Fields.OP).getAsString());
    }

    assertTrue(reported.stream().noneMatch(e -> e.getMessage().contains("nao tem handler registrado")),
        reported.toString());
  }

  @Test
  void walksTheWholeAccountLifecycle() {
    assertStatus("201", handle(
        "{'op':'register','email':'joao.silva@email.com','user':'joao','password':'senha123'}"));

    JsonObject login = handle("{'op':'login','email':'joao.silva@email.com','password':'senha123'}");
    assertStatus("200", login);
    String token = login.get(Fields.TOKEN).getAsString();

    JsonObject read = handle("{'op':'read_user','token':'" + token + "'}");
    assertStatus("200", read);
    assertEquals("joao", read.get(Fields.USER).getAsString());
    assertEquals("2026-09-09 14:32:10", read.get(Fields.CREATED_AT).getAsString());

    assertStatus("200", handle("{'op':'update_user','token':'" + token + "','user':'joaosilva'}"));
    assertEquals("joaosilva",
        handle("{'op':'read_user','token':'" + token + "'}").get(Fields.USER).getAsString());

    assertStatus("200", handle("{'op':'logout','token':'" + token + "'}"));
    assertStatus("401", handle("{'op':'read_user','token':'" + token + "'}"));

    String again = handle("{'op':'login','email':'joao.silva@email.com','password':'senha123'}")
        .get(Fields.TOKEN).getAsString();
    assertStatus("200", handle("{'op':'delete_user','token':'" + again + "','password':'senha123'}"));
    assertStatus("401", handle("{'op':'read_user','token':'" + again + "'}"));

    assertEquals(2, reported.size(), reported.toString());
  }

  // A excecao do handler vira resposta, e o diagnostico fica no log
  @Test
  void turnsAHandlerRejectionIntoAResponse() {
    handle("{'op':'register','email':'joao.silva@email.com','user':'joao','password':'senha123'}");

    JsonObject conflict = handle(
        "{'op':'register','email':'joao.silva@email.com','user':'maria','password':'senha123'}");
    assertEquals("register_response", conflict.get(Fields.OP).getAsString());
    assertStatus("409", conflict);
    assertEquals("Usuario ou email ja cadastrado", conflict.get(Fields.MESSAGE).getAsString());

    assertEquals(1, reported.size());
    assertTrue(reported.get(0).getMessage().contains("ja cadastrado"), reported.get(0).getMessage());
  }
}
