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
    TimeWindow lastTuesday = new TimeWindow(LocalDate.parse("2026-09-08"), LocalTime.parse("13:00"),
        LocalTime.parse("14:40"));
    Reservation ended = store.createReservation(new Booking(b12.id(), lastTuesday, "Aula", 6), joao.id(), T0);

    assertResponse("delete_reservation_response", "409", "Reserva ja encerrada",
        handle("delete_reservation", JOAO, reservationId(Long.toString(ended.id()))));
    assertTrue(store.findReservation(ended.id()).isPresent());
  }

  @Test
  void deletingTheAccountErasesItsReservations() {
    String id = reservedId(JOAO, T1_T2);

    assertStatus("200", handle("delete_user", JOAO, ",'password':'senha123'"));
    assertTrue(store.findReservation(Long.parseLong(id)).isEmpty());
  }
}
