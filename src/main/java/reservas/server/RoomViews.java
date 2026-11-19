package reservas.server;

import java.time.LocalDate;

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

  static JsonObject withAvailability(Room room, TimeWindow window) {
    JsonObject view = details(room);
    boolean available = room.status() == RoomStatus.ACTIVE && room.worksDuring(window);
    view.addProperty(Fields.AVAILABLE, Boolean.toString(available));
    return view;
  }

  static JsonObject withAgenda(Room room, LocalDate date) {
    SlotStatus status = room.status() == RoomStatus.ACTIVE ? SlotStatus.FREE : SlotStatus.UNAVAILABLE;
    JsonArray agenda = new JsonArray();
    room.slotsOn(date.getDayOfWeek()).forEach(slot -> agenda.add(agendaItem(slot, status)));

    JsonObject view = details(room);
    view.addProperty(Fields.DATE, date.toString());
    view.add(Fields.AGENDA, agenda);
    return view;
  }

  private static JsonObject agendaItem(Slot slot, SlotStatus status) {
    JsonObject item = new JsonObject();
    item.addProperty(Fields.SLOT, slot.wireName());
    item.addProperty(Fields.START_TIME, slot.period().start().toString());
    item.addProperty(Fields.END_TIME, slot.period().end().toString());
    item.addProperty(Fields.SLOT_STATUS, status.wireName());
    return item;
  }
}
