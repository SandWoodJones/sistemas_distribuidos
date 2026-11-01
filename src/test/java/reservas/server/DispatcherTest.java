package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertResponse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;

class DispatcherTest {
  private final SqliteStore store = SqliteStore.openInMemory();
  private final List<ProtocolException> reported = new ArrayList<>();
  private final Dispatcher dispatcher = Fixtures.dispatcher(store, reported::add);

  @AfterEach
  void closeStore() {
    store.close();
  }

  private JsonObject handle(String json) {
    return dispatcher.handle(TestJson.object(json));
  }

  // Sem `op` não há resposta
  @Test
  void answersErrorWhenOpIsMissingOrNotAString() {
    for (String json : new String[] { "{}", "{'op':42}", "{'op':null}", "{'op':['login']}" }) {
      assertResponse("error", "400", "Requisicao invalida", handle(json));
    }
  }

  @Test
  void answersErrorForAnOpWithoutAHandler() {
    for (String json : new String[] { "{'op':'fazer_cafe'}", "{'op':'LOGIN'}", "{'op':'login'}" }) {
      assertResponse("error", "400", "Operacao desconhecida", handle(json));
    }

    assertTrue(reported.get(0).getMessage().contains("nao existe"), reported.get(0).getMessage());
    assertTrue(reported.get(1).getMessage().contains("nao existe"), reported.get(1).getMessage());
    assertTrue(reported.get(2).getMessage().contains("nao tem handler registrado"), reported.get(2).getMessage());
  }

  // `op` implementado responde com o próprio nome, não com `error`
  @Test
  void answersWithTheOpNameWhenAHandlerRejects() {
    dispatcher.register(Op.LOGOUT, (json, context) -> {
      throw ProtocolException.unauthorized("Token invalido ou expirado", "sessao inexistente");
    });

    assertResponse("logout_response", "401", "Token invalido ou expirado", handle("{'op':'logout'}"));
    assertEquals(1, reported.size());
    assertTrue(reported.get(0).getMessage().contains("sessao inexistente"), reported.get(0).getMessage());
  }

  // Bug no handler vira 500, nunca uma conexão sem resposta
  @Test
  void turnsAnUnexpectedExceptionIntoInternalError() {
    dispatcher.register(Op.READ_USER, (json, context) -> {
      throw new NullPointerException("esqueci de checar");
    });

    assertResponse("read_user_response", "500", "Erro interno do servidor",
        handle("{'op':'read_user','token':'abc'}"));

    ProtocolException failure = reported.get(0);
    assertInstanceOf(NullPointerException.class, failure.getCause());
    assertTrue(failure.getMessage().contains("read_user"), failure.getMessage());
    assertTrue(failure.getMessage().contains("\"token\":\"abc\""), failure.getMessage());
  }
}
