package reservas.server;

import java.time.LocalDate;

// Filtros do `list_reservations`; `null` é sem filtro
public record ReservationFilter(Long userId, LocalDate date, Long roomId) {}
