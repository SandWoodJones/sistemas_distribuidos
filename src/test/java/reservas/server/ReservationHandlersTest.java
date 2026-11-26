package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertKeys;
import static reservas.protocol.ProtocolAssertions.assertResponse;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.OTHER_TOKEN;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.T0_ON_THE_WIRE;
import static reservas.server.Fixtures.TOKEN;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;
import reservas.protocol.TestJson;
import reservas.protocol.TimeWindow;

class ReservationHandlersTest {
  private static final String ADMIN = TOKEN;
  private static final String JOAO = OTHER_TOKEN;
  private static final String ANA = "c".repeat(64);
  // Terça, T1 e T2 da sala B12; T0 é a quarta anterior
  private static final String T1_T2 = ",'date':'2026-09-15','start_time':'13:00','end_time':'14:40'";
  private static final String MEETING = ",'topic':'Reuniao de projeto','participants':'6'";

  private final SqliteStore store = SqliteStore.openInMemory();
  private final List<ProtocolException> reported = new ArrayList<>();
  private final Dispatcher dispatcher = Fixtures.dispatcher(store, reported::add);
  private Room b12;
  private User joao;

  @BeforeEach
  void openSessionsAndARoom() {
    ServerApp.installHandlers(dispatcher);

    User maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createSession(ADMIN, maria.id(), T0).orElseThrow();

    joao = store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
    store.createSession(JOAO, joao.id(), T0).orElseThrow();

    User ana = store.createUser("ana", "ana@email.com", "senha123", Role.USER, T0).orElseThrow();
    store.createSession(ANA, ana.id(), T0).orElseThrow();

    b12 = store.createRoom("Sala B12", 12, "Bloco B - 1 andar", List.of(), List.of(slot("3T1"), slot("3T2")))
        .orElseThrow();
  }

  @AfterEach
  void closeStore() {
    store.close();
  }

  private static Slot slot(String wireName) {
    return Slot.fromWire(wireName).orElseThrow();
  }

  private JsonObject handle(String op, String token, String fields) {
    return dispatcher.handle(TestJson.object("{'op':'" + op + "','token':'" + token + "'" + fields + "}"));
  }

  private JsonObject reserve(String token, String window) {
    return handle("create_reservation", token, ",'room_id':'" + b12.id() + "'" + window + MEETING);
  }

  private String reservedId(String token, String window) {
    JsonObject response = reserve(token, window);
    assertStatus("201", response);
    return response.get(Fields.RESERVATION_ID).getAsString();
  }

  private String reservationId(String id) {
    return ",'reservation_id':'" + id + "'";
  }

  private String lastDiagnosis() {
    return reported.get(reported.size() - 1).getMessage();
  }

  private Reservation endedLastTuesday(int participants) {
    TimeWindow lastTuesday = new TimeWindow(LocalDate.parse("2026-09-08"), LocalTime.parse("13:00"),
        LocalTime.parse("14:40"));
    return store.createReservation(new Booking(b12.id(), lastTuesday, "Aula", participants), joao.id(), T0);
  }

  @Test
  void createReservationAnswersTheNewIdAndTheOwnerReadsItBack() {
    JsonObject created = reserve(JOAO, T1_T2);
    assertKeys(created, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.RESERVATION_ID);
    assertResponse("create_reservation_response", "201", "Reserva realizada com sucesso", created);

    String id = created.get(Fields.RESERVATION_ID).getAsString();
    JsonObject read = handle("read_reservation", JOAO, reservationId(id));
    assertResponse("read_reservation_response", "200", "Consulta realizada com sucesso", read);
    assertEquals(TestJson.object("{'op':'read_reservation_response','status':'200',"
        + "'message':'Consulta realizada com sucesso','reservation_id':'" + id + "','room_id':'" + b12.id()
        + "','name':'Sala B12','user':'joao','date':'2026-09-15','start_time':'13:00','end_time':'14:40',"
        + "'topic':'Reuniao de projeto','participants':'6','created_at':'" + T0_ON_THE_WIRE + "'}"), read);
  }

  @Test
  void createReservationRejectsEachFieldOutsideTheFormat() {
    String room = ",'room_id':'" + b12.id() + "'";
    String[][] cases = {
        { "room_id", T1_T2 + MEETING },
        { "room_id", ",'room_id':''" + T1_T2 + MEETING },
        { "date", room + ",'start_time':'13:00','end_time':'14:40'" + MEETING },
        { "date", room + ",'date':'2026-02-30','start_time':'13:00','end_time':'14:40'" + MEETING },
        { "date", room + ",'date':'15/09/2026','start_time':'13:00','end_time':'14:40'" + MEETING },
        { "start_time", room + ",'date':'2026-09-15','start_time':'13:10','end_time':'14:40'" + MEETING },
        { "end_time", room + ",'date':'2026-09-15','start_time':'13:00','end_time':'13:00'" + MEETING },
        { "end_time", room + ",'date':'2026-09-15','start_time':'13:50','end_time':'13:50'" + MEETING },
        { "end_time", room + ",'date':'2026-09-15','start_time':'14:40','end_time':'13:50'" + MEETING },
        { "start_time", room + ",'date':'2026-09-08','start_time':'13:00','end_time':'14:40'" + MEETING },
        { "start_time", room + ",'date':'2026-09-09','start_time':'13:50','end_time':'15:30'" + MEETING },
        { "topic", room + T1_T2 + ",'participants':'6'" },
        { "topic", room + T1_T2 + ",'topic':'Reuniao/projeto','participants':'6'" },
        { "participants", room + T1_T2 + ",'topic':'Reuniao','participants':'0'" },
        { "participants", room + T1_T2 + ",'topic':'Reuniao','participants':6" } };

    for (String[] c : cases) {
      assertResponse("create_reservation_response", "400", "Dados da reserva em formato invalido",
          handle("create_reservation", JOAO, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), c[1] + " -> " + lastDiagnosis());
    }
  }

  // Hoje vale, desde que a aula ainda não tenha começado (T0 = 14:32 de quarta)
  @Test
  void createReservationAcceptsTodayInAClassThatHasNotStarted() {
    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, null, List.of(slot("4T4"))));

    assertStatus("201", reserve(JOAO, ",'date':'2026-09-09','start_time':'15:50','end_time':'16:40'"));
  }

  @Test
  void createReservationAnswers404ForAMissingRoom() {
    assertResponse("create_reservation_response", "404", "Sala nao encontrada",
        handle("create_reservation", JOAO, ",'room_id':'99'" + T1_T2 + MEETING));
  }

  @Test
  void createReservationRejectsMoreParticipantsThanTheRoomHolds() {
    JsonObject response = handle("create_reservation", JOAO,
        ",'room_id':'" + b12.id() + "'" + T1_T2 + ",'topic':'Aula','participants':'13'");

    assertResponse("create_reservation_response", "400", "Dados da reserva em formato invalido", response);
    assertTrue(lastDiagnosis().contains("campo 'participants'"), lastDiagnosis());
  }

  @Test
  void createReservationRejectsARoomThatIsInactiveOrClosedAtThatTime() {
    String wednesday = ",'date':'2026-09-16','start_time':'13:00','end_time':'14:40'";
    String intoT3 = ",'date':'2026-09-15','start_time':'13:50','end_time':'15:30'";
    String sunday = ",'date':'2026-09-13','start_time':'13:00','end_time':'14:40'";
    for (String window : List.of(wednesday, intoT3, sunday)) {
      assertResponse("create_reservation_response", "409", "Horario fora do funcionamento da sala",
          reserve(JOAO, window));
    }

    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE, null));
    assertResponse("create_reservation_response", "409", "Sala indisponivel para reserva", reserve(JOAO, T1_T2));
    assertResponse("create_reservation_response", "409", "Sala indisponivel para reserva", reserve(JOAO, wednesday));
  }

  @Test
  void createReservationRejectsAnOverlapButNotABookingThatOnlyTouches() {
    reservedId(ANA, ",'date':'2026-09-15','start_time':'13:00','end_time':'13:50'");

    assertResponse("create_reservation_response", "409", "Sala ja reservada no periodo solicitado",
        reserve(JOAO, T1_T2));
    assertTrue(lastDiagnosis().contains("13:00-13:50"), lastDiagnosis());
    assertStatus("201", reserve(JOAO, ",'date':'2026-09-15','start_time':'13:50','end_time':'14:40'"));
  }

  @Test
  void checkAvailabilityAnswersFalseWhenTakenInactiveOrClosed() {
    String room = ",'room_id':'" + b12.id() + "'";
    JsonObject free = handle("check_availability", JOAO, room + T1_T2);
    assertEquals(TestJson.object("{'op':'check_availability_response','status':'200',"
        + "'message':'Consulta realizada com sucesso','room_id':'" + b12.id() + "','date':'2026-09-15',"
        + "'start_time':'13:00','end_time':'14:40','available':'true'}"), free);

    reservedId(ANA, ",'date':'2026-09-15','start_time':'13:00','end_time':'13:50'");
    String[][] cases = {
        { "false", T1_T2 },
        { "true", ",'date':'2026-09-15','start_time':'13:50','end_time':'14:40'" },
        { "true", ",'date':'2026-09-08','start_time':'13:00','end_time':'14:40'" },
        { "false", ",'date':'2026-09-16','start_time':'13:00','end_time':'14:40'" } };
    for (String[] c : cases) {
      assertEquals(c[0], handle("check_availability", JOAO, room + c[1]).get(Fields.AVAILABLE).getAsString(), c[1]);
    }

    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE, null));
    assertEquals("false", handle("check_availability", JOAO, room + ",'date':'2026-09-15','start_time':'13:50',"
        + "'end_time':'14:40'").get(Fields.AVAILABLE).getAsString());
  }

  @Test
  void checkAvailabilityRejectsABadWindowAndAMissingRoom() {
    assertResponse("check_availability_response", "400", "Dados em formato invalido", handle("check_availability",
        JOAO, ",'room_id':'" + b12.id() + "','date':'2026-09-15','start_time':'14:40','end_time':'13:50'"));
    assertTrue(lastDiagnosis().contains("campo 'end_time'"), lastDiagnosis());

    assertResponse("check_availability_response", "404", "Sala nao encontrada",
        handle("check_availability", JOAO, ",'room_id':'99'" + T1_T2));
  }

  @Test
  void listRoomsCountsReservationsInAvailable() {
    reservedId(ANA, ",'date':'2026-09-15','start_time':'13:50','end_time':'14:40'");

    JsonObject rooms = handle("list_rooms", JOAO, T1_T2);
    assertEquals("false",
        rooms.getAsJsonArray(Fields.ROOMS).get(0).getAsJsonObject().get(Fields.AVAILABLE).getAsString());
  }

  // Quem reservou aparece para todos; o `reservation_id`, só para o dono e o admin
  @Test
  void readRoomAgendaShowsWhoBookedEachClass() {
    String id = reservedId(JOAO, ",'date':'2026-09-15','start_time':'13:00','end_time':'13:50'");
    String tuesday = ",'room_id':'" + b12.id() + "','date':'2026-09-15'";
    String free = "{'slot':'3T2','start_time':'13:50','end_time':'14:40','slot_status':'livre'}";

    for (String token : List.of(JOAO, ADMIN)) {
      assertEquals(TestJson.json("[{'slot':'3T1','start_time':'13:00','end_time':'13:50','slot_status':'indisponivel',"
          + "'user':'joao','reservation_id':'" + id + "'}," + free + "]"),
          handle("read_room", token, tuesday).get(Fields.AGENDA).toString());
    }
    assertEquals(TestJson.json("[{'slot':'3T1','start_time':'13:00','end_time':'13:50','slot_status':'indisponivel',"
        + "'user':'joao'}," + free + "]"), handle("read_room", ANA, tuesday).get(Fields.AGENDA).toString());
  }

  @Test
  void listReservationsShowsMineOrEveryonesToTheAdmin() {
    String mine = reservedId(JOAO, ",'date':'2026-09-15','start_time':'13:00','end_time':'13:50'");
    reservedId(ANA, ",'date':'2026-09-15','start_time':'13:50','end_time':'14:40'");

    JsonObject listed = handle("list_reservations", JOAO, "");
    assertResponse("list_reservations_response", "200", "Consulta realizada com sucesso", listed);
    assertEquals(TestJson.json("[{'reservation_id':'" + mine + "','room_id':'" + b12.id() + "','name':'Sala B12',"
        + "'user':'joao','date':'2026-09-15','start_time':'13:00','end_time':'13:50',"
        + "'topic':'Reuniao de projeto','participants':'6'}]"), listed.get(Fields.RESERVATIONS).toString());
    assertEquals("1", handle("list_reservations", JOAO, ",'scope':'mine'").get(Fields.COUNT).getAsString());

    assertResponse("list_reservations_response", "403", "Permissao insuficiente",
        handle("list_reservations", JOAO, ",'scope':'all'"));
    assertEquals("2", handle("list_reservations", ADMIN, ",'scope':'all'").get(Fields.COUNT).getAsString());
  }

  @Test
  void listReservationsFiltersByDateAndRoom() {
    reservedId(JOAO, T1_T2);
    String[][] cases = {
        { "1", ",'date':'2026-09-15'" },
        { "0", ",'date':'2026-09-22'" },
        { "1", ",'room_id':'" + b12.id() + "'" },
        { "0", ",'room_id':'99'" },
        { "1", ",'date':'2026-09-15','room_id':'" + b12.id() + "'" } };

    for (String[] c : cases) {
      JsonObject listed = handle("list_reservations", ADMIN, ",'scope':'all'" + c[1]);
      assertStatus("200", listed);
      assertEquals(c[0], listed.get(Fields.COUNT).getAsString(), c[1]);
    }
  }

  @Test
  void listReservationsRejectsFiltersOutsideTheFormat() {
    String[][] cases = {
        { "scope", ",'scope':''" },
        { "scope", ",'scope':'todos'" },
        { "date", ",'date':''" },
        { "date", ",'date':'2026-02-30'" },
        { "room_id", ",'room_id':'abc'" },
        { "room_id", ",'room_id':null" } };

    for (String[] c : cases) {
      assertResponse("list_reservations_response", "400", "Filtros em formato invalido",
          handle("list_reservations", JOAO, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), c[1] + " -> " + lastDiagnosis());
    }
  }

  @Test
  void readReservationIsForTheOwnerAndTheAdmin() {
    String id = reservedId(JOAO, T1_T2);

    assertStatus("200", handle("read_reservation", ADMIN, reservationId(id)));
    assertResponse("read_reservation_response", "403", "Permissao insuficiente",
        handle("read_reservation", ANA, reservationId(id)));
    assertResponse("read_reservation_response", "404", "Reserva nao encontrada",
        handle("read_reservation", ANA, reservationId("99")));
    assertResponse("read_reservation_response", "400", "Dados em formato invalido",
        handle("read_reservation", JOAO, reservationId("")));
    assertTrue(lastDiagnosis().contains("campo 'reservation_id'"), lastDiagnosis());
  }

  private JsonObject update(String token, String id, String start, String end, String topic, String participants) {
    return handle("update_reservation", token, reservationId(id) + ",'start_time':'" + start + "','end_time':'" + end
        + "','topic':'" + topic + "','participants':'" + participants + "'");
  }

  private JsonObject read(String id) {
    return handle("read_reservation", ADMIN, reservationId(id));
  }

  @Test
  void updateReservationChangesOnlyWhatIsFilledIn() {
    String id = reservedId(JOAO, ",'date':'2026-09-15','start_time':'13:00','end_time':'13:50'");

    assertResponse("update_reservation_response", "200", "Reserva atualizada com sucesso",
        update(JOAO, id, "", "14:40", "", "8"));

    JsonObject read = read(id);
    assertEquals("13:00", read.get(Fields.START_TIME).getAsString());
    assertEquals("14:40", read.get(Fields.END_TIME).getAsString());
    assertEquals("Reuniao de projeto", read.get(Fields.TOPIC).getAsString());
    assertEquals("8", read.get(Fields.PARTICIPANTS).getAsString());
  }

  @Test
  void updateReservationRejectsEachFieldOutsideTheFormat() {
    String id = reservedId(JOAO, T1_T2);
    String keep = ",'start_time':'','end_time':'','topic':'','participants':'8'";
    String[][] cases = {
        { "reservation_id", ",'reservation_id':''" + keep },
        { "room_id", reservationId(id) + ",'room_id':'B12'" + keep },
        { "room_id", reservationId(id) + ",'room_id':null" + keep },
        { "date", reservationId(id) + ",'date':'2026-02-30'" + keep },
        { "date", reservationId(id) + ",'date':'15/09/2026'" + keep },
        { "start_time", reservationId(id) + ",'start_time':'13:10','end_time':'','topic':'','participants':''" },
        { "start_time", reservationId(id) + ",'start_time':null,'end_time':'','topic':'','participants':'8'" },
        { "end_time", reservationId(id) + ",'start_time':'','end_time':'13:00','topic':'','participants':''" },
        { "topic", reservationId(id) + ",'start_time':'','end_time':'','topic':'Aula/1','participants':''" },
        { "participants", reservationId(id) + ",'start_time':'','end_time':'','topic':'','participants':'0'" } };

    for (String[] c : cases) {
      assertResponse("update_reservation_response", "400", "Dados da reserva em formato invalido",
          handle("update_reservation", JOAO, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), c[1] + " -> " + lastDiagnosis());
    }

    assertResponse("update_reservation_response", "400", "Nenhum dado para atualizar",
        update(JOAO, id, "", "", "", ""));
    assertResponse("update_reservation_response", "400", "Nenhum dado para atualizar",
        handle("update_reservation", JOAO, reservationId(id)));
  }

  // 404 antes do 403, e nem o admin altera reserva de outro
  @Test
  void updateReservationIsForTheOwnerOnly() {
    String id = reservedId(JOAO, T1_T2);

    assertResponse("update_reservation_response", "404", "Reserva nao encontrada",
        update(ANA, "99", "", "", "Aula", ""));
    for (String token : List.of(ANA, ADMIN)) {
      assertResponse("update_reservation_response", "403", "Permissao insuficiente",
          update(token, id, "", "", "Aula", ""));
    }
  }

  @Test
  void updateReservationRejectsOneThatHasEnded() {
    Reservation ended = endedLastTuesday(6);

    assertResponse("update_reservation_response", "409", "Reserva ja encerrada",
        update(JOAO, Long.toString(ended.id()), "", "", "Outra aula", ""));
  }

  @Test
  void updateReservationChecksTheResultingTimesAndParticipants() {
    String id = reservedId(JOAO, ",'date':'2026-09-15','start_time':'13:50','end_time':'14:40'");

    assertResponse("update_reservation_response", "400", "Dados da reserva em formato invalido",
        update(JOAO, id, "", "13:50", "", ""));
    assertTrue(lastDiagnosis().contains("campo 'end_time'"), lastDiagnosis());

    assertResponse("update_reservation_response", "400", "Dados da reserva em formato invalido",
        update(JOAO, id, "", "", "", "13"));
    assertTrue(lastDiagnosis().contains("campo 'participants'"), lastDiagnosis());
  }

  // Só um início novo no passado é 400: a reserva em andamento ainda pode ser estendida (T0 = 14:32 de quarta)
  @Test
  void updateReservationRejectsOnlyANewStartInThePast() {
    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, null,
        List.of(slot("4T1"), slot("4T2"), slot("4T3"), slot("4T4"))));
    TimeWindow now = new TimeWindow(LocalDate.parse("2026-09-09"), LocalTime.parse("13:50"), LocalTime.parse("14:40"));
    String ongoing = Long.toString(store.createReservation(new Booking(b12.id(), now, "Aula", 6), joao.id(), T0).id());

    assertStatus("200", update(JOAO, ongoing, "", "15:30", "", ""));
    assertResponse("update_reservation_response", "400", "Dados da reserva em formato invalido",
        update(JOAO, ongoing, "13:00", "", "", ""));
    assertTrue(lastDiagnosis().contains("campo 'start_time'"), lastDiagnosis());
  }

  // Alterar não encerra reserva: o fim resultante no passado é 400, mas encurtar até uma aula que não terminou vale
  @Test
  void updateReservationRejectsAnEndInThePastButLetsAnOngoingOneShrink() {
    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, null,
        List.of(slot("4T1"), slot("4T2"), slot("4T3"))));
    TimeWindow now = new TimeWindow(LocalDate.parse("2026-09-09"), LocalTime.parse("13:00"), LocalTime.parse("15:30"));
    String ongoing = Long.toString(store.createReservation(new Booking(b12.id(), now, "Aula", 6), joao.id(), T0).id());

    assertResponse("update_reservation_response", "400", "Dados da reserva em formato invalido",
        update(JOAO, ongoing, "", "13:50", "", ""));
    assertTrue(lastDiagnosis().contains("campo 'end_time'"), lastDiagnosis());
    assertStatus("200", update(JOAO, ongoing, "", "14:40", "", ""));
  }

  private JsonObject move(String token, String id, String roomId, String date) {
    return handle("update_reservation", token, reservationId(id) + ",'room_id':'" + roomId + "','date':'" + date
        + "','start_time':'','end_time':'','topic':'','participants':''");
  }

  private Room lab(String name, int capacity, String... slots) {
    return store.createRoom(name, capacity, "Bloco C", List.of(), List.of(slots).stream().map(ReservationHandlersTest::slot)
        .toList()).orElseThrow();
  }

  // A troca libera o horário antigo na hora
  @Test
  void updateReservationMovesToAnotherRoomAndDay() {
    Room lab = lab("Lab 3", 20, "3T1", "3T2");
    String id = reservedId(JOAO, T1_T2);
    String labId = Long.toString(lab.id());

    assertResponse("update_reservation_response", "200", "Reserva atualizada com sucesso",
        move(JOAO, id, labId, ""));
    assertEquals(labId, read(id).get(Fields.ROOM_ID).getAsString());
    assertStatus("201", reserve(ANA, T1_T2));

    assertStatus("200", move(JOAO, id, "", "2026-09-22"));
    JsonObject moved = read(id);
    assertEquals("2026-09-22", moved.get(Fields.DATE).getAsString());
    assertEquals("13:00", moved.get(Fields.START_TIME).getAsString());
  }

  // 404 da reserva, depois 404 da sala de destino, depois o 403 de dono
  @Test
  void updateReservationChecksTheDestinationRoomLikeABooking() {
    String id = reservedId(JOAO, T1_T2);
    Room small = lab("Sala 4", 4, "3T1", "3T2");
    Room closed = lab("Sala 5", 20, "3T1");
    Room taken = lab("Sala 6", 20, "3T1", "3T2");
    store.createReservation(new Booking(taken.id(), new TimeWindow(LocalDate.parse("2026-09-15"),
        LocalTime.parse("13:50"), LocalTime.parse("14:40")), "Aula", 2), joao.id(), T0);
    Room inactive = lab("Sala 7", 20, "3T1", "3T2");
    store.updateRoom(inactive.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE, null));

    assertResponse("update_reservation_response", "404", "Sala nao encontrada", move(ANA, id, "99", ""));
    assertResponse("update_reservation_response", "403", "Permissao insuficiente",
        move(ANA, id, Long.toString(small.id()), ""));

    String[][] cases = {
        { Long.toString(small.id()), "", "400", "Dados da reserva em formato invalido" },
        { Long.toString(inactive.id()), "", "409", "Sala indisponivel para reserva" },
        { Long.toString(closed.id()), "", "409", "Horario fora do funcionamento da sala" },
        { "", "2026-09-16", "409", "Horario fora do funcionamento da sala" },
        { Long.toString(taken.id()), "", "409", "Sala ja reservada no periodo solicitado" },
        { "", "2026-09-08", "400", "Dados da reserva em formato invalido" } };
    for (String[] c : cases) {
      assertResponse("update_reservation_response", c[2], c[3], move(JOAO, id, c[0], c[1]));
    }
    assertEquals(Long.toString(b12.id()), read(id).get(Fields.ROOM_ID).getAsString());
  }

  // Sair de uma sala inativa para uma ativa é permitido: manda a sala de destino
  @Test
  void updateReservationCanLeaveAnInactiveRoom() {
    String id = reservedId(JOAO, T1_T2);
    Room lab = lab("Lab 3", 20, "3T1", "3T2");
    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE, null));

    assertStatus("200", move(JOAO, id, Long.toString(lab.id()), ""));
  }

  @Test
  void updateReservationRejectsATimeTheRoomCannotTake() {
    String id = reservedId(JOAO, ",'date':'2026-09-15','start_time':'13:00','end_time':'13:50'");
    reservedId(ANA, ",'date':'2026-09-15','start_time':'13:50','end_time':'14:40'");

    assertResponse("update_reservation_response", "409", "Sala ja reservada no periodo solicitado",
        update(JOAO, id, "", "14:40", "", ""));
    assertResponse("update_reservation_response", "409", "Horario fora do funcionamento da sala",
        update(JOAO, id, "", "15:30", "", ""));
  }

  @Test
  void updateReservationDoesNotCollideWithItself() {
    String id = reservedId(JOAO, T1_T2);

    assertStatus("200", update(JOAO, id, "", "13:50", "", ""));
    assertStatus("200", update(JOAO, id, "", "14:40", "", ""));
  }

  // Sala inativa: o horário não muda, o resto sim
  @Test
  void updateReservationInAnInactiveRoomKeepsItsTime() {
    String id = reservedId(JOAO, T1_T2);
    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE, null));

    assertResponse("update_reservation_response", "409", "Sala indisponivel para reserva",
        update(JOAO, id, "", "13:50", "", ""));
    assertStatus("200", update(JOAO, id, "", "", "Outro assunto", ""));
  }

  @Test
  void deleteReservationFreesTheRoom() {
    String id = reservedId(JOAO, T1_T2);

    assertResponse("delete_reservation_response", "200", "Reserva cancelada com sucesso",
        handle("delete_reservation", JOAO, reservationId(id)));
    assertResponse("read_reservation_response", "404", "Reserva nao encontrada",
        handle("read_reservation", JOAO, reservationId(id)));
    assertStatus("201", reserve(ANA, T1_T2));
  }

  @Test
  void deleteReservationOfSomeoneElseIsForTheAdminOnly() {
    String id = reservedId(JOAO, T1_T2);

    assertResponse("delete_reservation_response", "403", "Permissao insuficiente",
        handle("delete_reservation", ANA, reservationId(id)));
    assertResponse("delete_reservation_response", "404", "Reserva nao encontrada",
        handle("delete_reservation", ANA, reservationId("99")));
    assertStatus("200", handle("delete_reservation", ADMIN, reservationId(id)));
  }

  @Test
  void deleteReservationRejectsOneThatHasEnded() {
    Reservation ended = endedLastTuesday(6);

    assertResponse("delete_reservation_response", "409", "Reserva ja encerrada",
        handle("delete_reservation", JOAO, reservationId(Long.toString(ended.id()))));
    assertTrue(store.findReservation(ended.id()).isPresent());
  }

  @Test
  void deleteRoomRefusesARoomWithActiveReservations() {
    reservedId(JOAO, T1_T2);

    assertResponse("delete_room_response", "409", "Sala possui reservas ativas",
        handle("delete_room", ADMIN, ",'room_id':'" + b12.id() + "'"));
    assertTrue(store.findRoom(b12.id()).isPresent());
  }

  @Test
  void deleteRoomTakesItsEndedReservationsAlong() {
    Reservation ended = endedLastTuesday(6);

    assertStatus("200", handle("delete_room", ADMIN, ",'room_id':'" + b12.id() + "'"));
    assertTrue(store.findReservation(ended.id()).isEmpty());
  }

  // Reserva encerrada não segura a capacidade
  @Test
  void updateRoomKeepsTheCapacityAboveActiveReservations() {
    endedLastTuesday(12);
    reservedId(JOAO, T1_T2);
    String room = ",'room_id':'" + b12.id() + "'";

    assertResponse("update_room_response", "409", "Capacidade menor que participantes de reservas ativas",
        handle("update_room", ADMIN, room + ",'capacity':'5'"));
    assertStatus("200", handle("update_room", ADMIN, room + ",'capacity':'6'"));
  }

  @Test
  void updateRoomReportsATakenNameBeforeTheCapacity() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of(), List.of(slot("2M1"))).orElseThrow();
    reservedId(JOAO, T1_T2);

    assertResponse("update_room_response", "409", "Sala ja cadastrada",
        handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','name':'Lab 3','capacity':'5'"));
  }

  @Test
  void deletingTheAccountErasesItsReservations() {
    String id = reservedId(JOAO, T1_T2);

    assertStatus("200", handle("delete_user", JOAO, ",'password':'senha123'"));
    assertTrue(store.findReservation(Long.parseLong(id)).isEmpty());
  }
}
