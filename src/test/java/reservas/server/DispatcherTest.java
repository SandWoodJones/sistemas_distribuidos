package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.TestJson;
import reservas.protocol.Codec;
import reservas.protocol.Fields;
import reservas.protocol.Messages;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;
import reservas.protocol.Responses;
import reservas.protocol.Status;

class DispatcherTest {
  private static final Instant T0 = Instant.parse("2026-09-09T14:32:10Z");

  private final SqliteStore store = SqliteStore.openInMemory();
  private final List<ProtocolException> reported = new ArrayList<>();
  private final Dispatcher dispatcher = new Dispatcher(store, Clock.fixed(T0, ZoneOffset.UTC), () -> "a".repeat(64),
      reported::add);

  @AfterEach
  void closeStore() {
    store.close();
  }

  private static JsonObject request(String json) {
    return TestJson.object(json);
  }

  private static void assertResponse(JsonObject response, String op, Status status, String message) {
    assertEquals(op, response.get(Fields.OP).getAsString());
    assertEquals(status.code(), response.get(Fields.STATUS).getAsString());
    assertEquals(message, response.get(Fields.MESSAGE).getAsString());
  }

  // Sem `op` não há resposta
  @Test
  void answersErrorWhenOpIsMissingOrNotAString() {
    for (String json : new String[] { "{}", "{'op':42}", "{'op':null}", "{'op':['login']}" }) {
      assertResponse(dispatcher.handle(request(json)), Op.ERROR_OP, Status.BAD_REQUEST, Messages.INVALID_REQUEST);
    }
  }

  @Test
  void answersErrorForAnOpOutsideTheProtocol() {
    assertResponse(dispatcher.handle(request("{'op':'fazer_cafe'}")), Op.ERROR_OP, Status.BAD_REQUEST,
        Messages.UNKNOWN_OPERATION);
  }

  @Test
  void opIsCaseSensitive() {
    assertResponse(dispatcher.handle(request("{'op':'LOGIN'}")), Op.ERROR_OP, Status.BAD_REQUEST,
        Messages.UNKNOWN_OPERATION);
  }

  @Test
  void separatesUnimplementedFromUnknownInTheDiagnosis() {
    assertResponse(dispatcher.handle(request("{'op':'login'}")), Op.ERROR_OP, Status.BAD_REQUEST,
        Messages.UNKNOWN_OPERATION);
    assertTrue(reported.get(0).getMessage().contains("nao tem handler registrado"), reported.get(0).getMessage());

    dispatcher.handle(request("{'op':'fazer_cafe'}"));
    assertTrue(reported.get(1).getMessage().contains("nao existe"), reported.get(1).getMessage());
  }

  @Test
  void routesToTheRegisteredHandler() {
    JsonObject expected = Responses.of(Op.LOGIN, Status.OK, "Login realizado com sucesso");
    dispatcher.register(Op.LOGIN, (json, context) -> expected);

    assertSame(expected, dispatcher.handle(request("{'op':'login'}")));
    assertTrue(reported.isEmpty(), "sucesso nao gera diagnostico");
  }

  // `op` implementado responde com o próprio nome, não com `error`
  @Test
  void answersWithTheOpNameWhenAHandlerRejects() {
    dispatcher.register(Op.LOGOUT, (json, context) -> {
      throw ProtocolException.unauthorized("Token invalido ou expirado", "sessao inexistente");
    });

    assertResponse(dispatcher.handle(request("{'op':'logout'}")), Op.LOGOUT.responseName(), Status.UNAUTHORIZED,
        "Token invalido ou expirado");
    assertEquals(1, reported.size());
    assertTrue(reported.get(0).getMessage().contains("sessao inexistente"));
  }

  // Bug no handler vira 500, nunca uma conexão sem resposta
  @Test
  void turnsAnUnexpectedExceptionIntoInternalError() {
    dispatcher.register(Op.READ_USER, (json, context) -> {
      throw new NullPointerException("esqueci de checar");
    });

    assertResponse(dispatcher.handle(request("{'op':'read_user'}")), Op.READ_USER.responseName(),
        Status.INTERNAL_SERVER_ERROR, Messages.INTERNAL_ERROR);
  }

  // O 500 enxuto na rede vem com a causa original no diagnóstico
  @Test
  void keepsTheCauseOfAnUnexpectedException() {
    dispatcher.register(Op.READ_USER, (json, context) -> {
      throw new NullPointerException("esqueci de checar");
    });
    dispatcher.handle(request("{'op':'read_user','token':'abc'}"));

    ProtocolException failure = reported.get(0);
    assertInstanceOf(NullPointerException.class, failure.getCause());
    assertTrue(failure.getMessage().contains("read_user"));
    assertTrue(failure.getMessage().contains("\"token\":\"abc\""), failure.getMessage());
  }

  @Test
  void everyResponseCarriesTheThreeMandatoryFields() {
    dispatcher.register(Op.REGISTER, (json, context) -> {
      throw new IllegalStateException("qualquer coisa");
    });

    for (String json : new String[] { "{}", "{'op':'fazer_cafe'}", "{'op':'login'}",
        "{'op':'register'}" }) {
      JsonObject response = dispatcher.handle(request(json));
      for (String field : new String[] { Fields.OP, Fields.STATUS, Fields.MESSAGE }) {
        assertTrue(response.has(field), field + " ausente em " + response);
      }
    }
  }
}
