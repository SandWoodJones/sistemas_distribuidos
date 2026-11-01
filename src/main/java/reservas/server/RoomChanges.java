package reservas.server;

import java.util.List;

import reservas.protocol.RoomStatus;

// Alteração de sala já validada
public record RoomChanges(String name, Integer capacity, String location, List<String> resources, RoomStatus status) {

}
