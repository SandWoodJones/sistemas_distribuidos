package reservas.client;

import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;

// Dica local para uma resposta de erro: o texto do servidor é fixo pelo protocolo e não diz qual campo falhou
final class ErrorHints {
  private static final String RESERVATION = "confira: data AAAA-MM-DD, aula que ainda nao comecou, assunto so com "
      + "letras, numeros, espaco , . - e participantes ate a capacidade da sala";
  private static final String ROOM = "confira: nome ate 30 letras, numeros, espaco ou -, capacidade de 1 a 9999, "
      + "local ate 60 caracteres e pelo menos um horario marcado";
  private static final String FILTERS = "confira os filtros: data AAAA-MM-DD, inicio e fim juntos com a data";

  private static final Map<String, String> BAD_REQUEST_BY_OP = Map.ofEntries(
      Map.entry("register_response", "usuario so letras minusculas, senha so letras e numeros (ate 20), email valido"),
      Map.entry("login_response", "confira o email e a senha"),
      Map.entry("update_user_response", "usuario so letras minusculas, senha so letras e numeros (ate 20)"),
      Map.entry("create_room_response", ROOM),
      Map.entry("update_room_response", ROOM),
      Map.entry("list_rooms_response", FILTERS),
      Map.entry("list_reservations_response", FILTERS),
      Map.entry("read_room_response", "confira o ID da sala (so numeros) e a data AAAA-MM-DD"),
      Map.entry("check_availability_response", "confira o ID da sala e a data AAAA-MM-DD"),
      Map.entry("create_reservation_response", RESERVATION),
      Map.entry("update_reservation_response", RESERVATION));

  private ErrorHints() {
  }

  static Optional<String> of(JsonObject response) {
    String status = response.get(Fields.STATUS).getAsString();
    String message = response.get(Fields.MESSAGE).getAsString();
    String op = response.get(Fields.OP).getAsString();

    return switch (status) {
      case "400" -> message.equals("Nenhum dado para atualizar") ? Optional.of("preencha pelo menos um campo")
          : Optional.ofNullable(BAD_REQUEST_BY_OP.get(op));
      case "401" -> Optional.of("sessao inexistente ou expirada: faca login de novo");
      case "403" -> message.equals("Permissao insuficiente")
          ? Optional.of("so o admin pode, ou o dono da reserva (alterar reserva e so do dono)")
          : Optional.empty();
      default -> Optional.empty();
    };
  }
}
