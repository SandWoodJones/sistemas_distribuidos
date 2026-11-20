package reservas.server;

// Reserva com os nomes da sala e do dono, para a janela do servidor
public record ReservationRow(Reservation reservation, String roomName, String userName) {}
