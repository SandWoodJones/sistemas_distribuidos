package reservas.server;

import java.time.ZoneId;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;

final class ReservationViews {
  private ReservationViews() {
  }

  static JsonObject summary(Reservation reservation, String roomName, String userName) {
    JsonObject view = new JsonObject();
    view.addProperty(Fields.RESERVATION_ID, Long.toString(reservation.id()));
    view.addProperty(Fields.ROOM_ID, Long.toString(reservation.roomId()));
    view.addProperty(Fields.NAME, roomName);
    view.addProperty(Fields.USER, userName);
    view.addProperty(Fields.DATE, reservation.window().date().toString());
    view.addProperty(Fields.START_TIME, reservation.window().start().toString());
    view.addProperty(Fields.END_TIME, reservation.window().end().toString());
    view.addProperty(Fields.TOPIC, reservation.topic());
    view.addProperty(Fields.PARTICIPANTS, Integer.toString(reservation.participants()));
    return view;
  }

  static JsonObject details(Reservation reservation, String roomName, String userName, ZoneId zone) {
    JsonObject view = summary(reservation, roomName, userName);
    view.addProperty(Fields.CREATED_AT, UserViews.CREATED_AT.withZone(zone).format(reservation.createdAt()));
    return view;
  }
}
