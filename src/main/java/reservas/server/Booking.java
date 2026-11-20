package reservas.server;

import reservas.protocol.TimeWindow;

// Pedido de reserva já validado, sem id
public record Booking(long roomId, TimeWindow window, String topic, int participants) {
}
