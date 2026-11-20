package reservas.client;

// Horário, assunto e participantes de uma reserva; na alteração, `""` mantém o valor atual
public record ReservationFields(String startTime, String endTime, String topic, String participants) {}
