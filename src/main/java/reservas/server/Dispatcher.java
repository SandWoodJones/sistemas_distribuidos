package reservas.server;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reservas.protocol.Codec;
import reservas.protocol.Fields;
import reservas.protocol.Op;
import reservas.protocol.ProtocolException;
import reservas.protocol.Responses;

// Escolhe o handler pelo `op`. requisição recebida por inteiro é sempre respondida
public final class Dispatcher {
  private final SqliteStore store;
  private final Clock clock;
  private final TokenGenerator tokens;
  private final Consumer<ProtocolException> diagnostics;
  private final Map<Op, Handler> handlers = new EnumMap<>(Op.class);

  public Dispatcher(SqliteStore store, Clock clock, TokenGenerator tokens, Consumer<ProtocolException> diagnostics) {
    this.store = store;
    this.clock = clock;
    this.tokens = tokens;
    this.diagnostics = diagnostics;
  }

  // Tabela preenchida de fora
  public void register(Op op, Handler handler) {
    handlers.put(op, handler);
  }

  // Os dois `try` são separados pelo instante em que o `op` passa a ser conhecido
  public JsonObject handle(JsonObject request) {
    Op op;
    Handler handler;

    try {
      op = operation(request);
      handler = handlerFor(op);
    } catch (ProtocolException e) {
      // Sem `op` reconhecido não há `<op>_response`
      return report(e);
    }

    try {
      return handler.handle(request, store, clock, tokens);
    } catch (ProtocolException e) {
      // 400, 401, 403, 409
      return report(op, e);
    } catch (RuntimeException e) {
      // 500
      return report(op, internalError(op, request, e));
    }
  }

  private JsonObject report(ProtocolException failure) {
    diagnostics.accept(failure);
    return Responses.error(failure);
  }

  private JsonObject report(Op op, ProtocolException failure) {
    diagnostics.accept(failure);
    return Responses.of(op, failure);
  }

  // `op` ausente ou com tipo errado é "Requisicao invalida"; bem formado mas inexistente é "Operacao desconhecida"
  private static Op operation(JsonObject request) {
    JsonElement op = request.get(Fields.OP);
    if (op == null || !op.isJsonPrimitive() || !op.getAsJsonPrimitive().isString()) {
      throw reject(request, Codec.INVALID_REQUEST, "campo 'op' ausente ou nao-string");
    }

    String wireName = op.getAsString();
    return Op.fromWire(wireName).orElseThrow(
        () -> reject(request, Codec.UNKNOWN_OPERATION, "op \"" + Codec.snippet(wireName) + "\" nao existe"));
  }

  private Handler handlerFor(Op op) {
    Handler handler = handlers.get(op);
    if (handler == null) {
      throw ProtocolException.badRequest(Codec.UNKNOWN_OPERATION,
          "op \"" + op.wireName() + "\" existe no protocolo mas nao tem handler registrado");
    }

    return handler;
  }

  private static ProtocolException reject(JsonObject request, String wireMessage, String detail) {
    return ProtocolException.badRequest(wireMessage, Codec.diagnosis(detail, request.toString()));
  }

  private static ProtocolException internalError(Op op, JsonObject request, RuntimeException cause) {
    return ProtocolException.internal(Codec.diagnosis("excecao nao tratada em " + op.wireName(), request.toString()),
        cause);
  }
}
