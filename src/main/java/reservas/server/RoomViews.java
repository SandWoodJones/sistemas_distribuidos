package reservas.server;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;
import reservas.protocol.SlotStatus;
import reservas.protocol.StringArrays;
import reservas.protocol.TimeWindow;

final class RoomViews {
  private RoomViews() {
  }

  static JsonObject details(Room room) {
    JsonObject view = new JsonObject();
    view.addProperty(Fields.ROOM_ID, Long.toString(room.id()));
    view.addProperty(Fields.NAME, room.name());
    view.addProperty(Fields.CAPACITY, Integer.toString(room.capacity()));
    view.addProperty(Fields.LOCATION, room.location());
    view.add(Fields.RESOURCES, StringArrays.toJson(room.resources()));
    view.addProperty(Fields.ROOM_STATUS, room.status().wireName());
    view.add(Fields.SLOTS, StringArrays.toJson(room.slots().stream().map(Slot::wireName).toList()));
    return view;
  }

  static JsonObject withAvailability(Room room, boolean available) {
    JsonObject view = details(room);
    view.addProperty(Fields.AVAILABLE, Boolean.toString(available));
    return view;
  }

  static JsonObject withAgenda(Room room, LocalDate date, List<AgendaBooking> bookings) {
    JsonObject view = details(room);
    view.addProperty(Fields.DATE, date.toString());
    view.add(Fields.AGENDA, agenda(room, date, bookings));
    return view;
  }

  // Item de `rooms` no `read_room` sem `room_id`: a data vai uma vez só, fora da lista
  static JsonObject withDayAgenda(Room room, LocalDate date, List<AgendaBooking> bookings) {
    JsonObject view = details(room);
    view.add(Fields.AGENDA, agenda(room, date, bookings));
    return view;
  }

  private static JsonArray agenda(Room room, LocalDate date, List<AgendaBooking> bookings) {
    JsonArray agenda = new JsonArray();
    room.slotsOn(date.getDayOfWeek()).forEach(slot -> agenda.add(agendaItem(room, date, slot, bookings)));
    return agenda;
  }

  private static JsonObject agendaItem(Room room, LocalDate date, Slot slot, List<AgendaBooking> bookings) {
    TimeWindow lesson = new TimeWindow(date, slot.period().start(), slot.period().end());
    Optional<AgendaBooking> booked = bookings.stream().filter(booking -> booking.window().overlaps(lesson))
        .findFirst();
    boolean free = room.status() == RoomStatus.ACTIVE && booked.isEmpty();

    JsonObject item = new JsonObject();
    item.addProperty(Fields.SLOT, slot.wireName());
    item.addProperty(Fields.START_TIME, slot.period().start().toString());
    item.addProperty(Fields.END_TIME, slot.period().end().toString());
    item.addProperty(Fields.SLOT_STATUS, (free ? SlotStatus.FREE : SlotStatus.UNAVAILABLE).wireName());
    booked.ifPresent(booking -> addBooking(item, booking));
    return item;
  }

  private static void addBooking(JsonObject item, AgendaBooking booking) {
    item.addProperty(Fields.USER, booking.user());
    if (booking.reservationId() != null) {
      item.addProperty(Fields.RESERVATION_ID, Long.toString(booking.reservationId()));
    }
  }
}
