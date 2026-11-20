package reservas.server;

import reservas.protocol.TimeWindow;

// Reserva como aparece na agenda; `reservationId` é nulo para quem não é o dono nem admin
public record AgendaBooking(TimeWindow window, String user, Long reservationId) {}
