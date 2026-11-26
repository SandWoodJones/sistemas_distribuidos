package reservas.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static reservas.protocol.ProtocolAssertions.assertKeys;
import static reservas.protocol.ProtocolAssertions.assertResponse;
import static reservas.protocol.ProtocolAssertions.assertStatus;
import static reservas.server.Fixtures.OTHER_TOKEN;
import static reservas.server.Fixtures.T0;
import static reservas.server.Fixtures.TOKEN;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import reservas.protocol.ClassPeriod;
import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.RoomStatus;
import reservas.protocol.Slot;
import reservas.protocol.TestJson;

class RoomHandlersTest {
  private static final String ADMIN = TOKEN;
  private static final String COMMON = OTHER_TOKEN;
  private static final String B12 = ",'name':'Sala B12','capacity':'12','location':'Bloco B - 1 andar','resources':['projetor','tv'],'slots':['3T1','3T2']";
  private static final String SLOT = ",'date':'2026-09-15','start_time':'13:00','end_time':'14:40'";
  private static final List<Slot> TUESDAY_AFTERNOON = List.of(new Slot(DayOfWeek.TUESDAY, ClassPeriod.T1),
      new Slot(DayOfWeek.TUESDAY, ClassPeriod.T2));

  private final SqliteStore store = SqliteStore.openInMemory();
  private final List<ProtocolException> reported = new ArrayList<>();
  private final Dispatcher dispatcher = Fixtures.dispatcher(store, reported::add);
  private Room b12;

  @BeforeEach
  void openSessionsAndARoom() {
    ServerApp.installHandlers(dispatcher);

    User maria = store.createUser("maria", "maria@email.com", "senha123", Role.ADMIN, T0).orElseThrow();
    store.createSession(ADMIN, maria.id(), T0).orElseThrow();

    User joao = store.createUser("joao", "joao.silva@email.com", "senha123", Role.USER, T0).orElseThrow();
    store.createSession(COMMON, joao.id(), T0).orElseThrow();

    b12 = store.createRoom("Sala B12", 12, "Bloco B - 1 andar", List.of("projetor", "tv"), TUESDAY_AFTERNOON)
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

  private Room stored(Room room) {
    return store.findRoom(room.id()).orElseThrow();
  }

  private String lastDiagnosis() {
    return reported.get(reported.size() - 1).getMessage();
  }

  @Test
  void createRoomAnswersTheNewIdAndTheRoomStartsActive() {
    JsonObject response = handle("create_room", ADMIN,
        ",'name':'Lab 3','capacity':'30','location':'Bloco C','resources':[],'slots':['5N1','2M1','7M6']");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.ROOM_ID);
    assertResponse("create_room_response", "201", "Sala cadastrada com sucesso", response);

    Room lab = store.findRoom(Long.parseLong(response.get(Fields.ROOM_ID).getAsString())).orElseThrow();
    assertEquals("Lab 3", lab.name());
    assertEquals(30, lab.capacity());
    assertEquals(List.of(), lab.resources());
    assertEquals(RoomStatus.ACTIVE, lab.status());
    assertEquals(List.of(new Slot(DayOfWeek.THURSDAY, ClassPeriod.N1), new Slot(DayOfWeek.MONDAY, ClassPeriod.M1),
        new Slot(DayOfWeek.SATURDAY, ClassPeriod.M6)), lab.slots());
  }

  @Test
  void createRoomRejectsANameAlreadyTaken() {
    assertResponse("create_room_response", "409", "Sala ja cadastrada", handle("create_room", ADMIN, B12));
    assertTrue(lastDiagnosis().contains("\"Sala B12\""), lastDiagnosis());
  }

  @Test
  void createRoomRejectsEachFieldOutsideTheFormat() {
    String slots = ",'slots':['2M1']";
    String lab = ",'name':'Lab 3','capacity':'12','location':'Bloco B'";
    String[][] cases = {
        { "name", ",'capacity':'12','location':'Bloco B','resources':[]" + slots },
        { "name", ",'name':'','capacity':'12','location':'Bloco B','resources':[]" + slots },
        { "name", ",'name':'Sala Ã','capacity':'12','location':'Bloco B','resources':[]" + slots },
        { "capacity", ",'name':'Lab 3','capacity':'0','location':'Bloco B','resources':[]" + slots },
        { "capacity", ",'name':'Lab 3','capacity':12,'location':'Bloco B','resources':[]" + slots },
        { "location", ",'name':'Lab 3','capacity':'12','location':'Bloco B / 2','resources':[]" + slots },
        { "resources", lab + slots },
        { "resources", lab + ",'resources':'projetor'" + slots },
        { "resources", lab + ",'resources':['tv',1]" + slots },
        { "resources", lab + ",'resources':null" + slots },
        { "resources", lab + ",'resources':['projetor/tv']" + slots },
        { "resources", lab + ",'resources':['0']" + slots },
        { "slots", lab + ",'resources':[]" },
        { "slots", lab + ",'resources':[],'slots':[]" },
        { "slots", lab + ",'resources':[],'slots':'2M1'" },
        { "slots", lab + ",'resources':[],'slots':null" },
        { "slots", lab + ",'resources':[],'slots':['1M1']" },
        { "slots", lab + ",'resources':[],'slots':['2N6']" },
        { "slots", lab + ",'resources':[],'slots':['2M1','3T1','2M1']" }
    };

    for (String[] c : cases) {
      assertResponse("create_room_response", "400", "Dados da sala em formato invalido",
          handle("create_room", COMMON, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), lastDiagnosis());
    }
  }

  @Test
  void listRoomsAnswersEveryRoomToAnyLoggedUser() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of(), TUESDAY_AFTERNOON).orElseThrow();

    JsonObject response = handle("list_rooms", COMMON, "");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.COUNT, Fields.ROOMS);
    assertResponse("list_rooms_response", "200", "Consulta realizada com sucesso", response);
    assertEquals("2", response.get(Fields.COUNT).getAsString());

    JsonObject first = response.getAsJsonArray(Fields.ROOMS).get(0).getAsJsonObject();
    assertKeys(first, Fields.ROOM_ID, Fields.NAME, Fields.CAPACITY, Fields.LOCATION, Fields.RESOURCES,
        Fields.ROOM_STATUS, Fields.SLOTS);
    assertEquals(TestJson.object("{'room_id':'" + b12.id()
        + "','name':'Sala B12','capacity':'12','location':'Bloco B - 1 andar','resources':['projetor','tv'],'room_status':'active','slots':['3T1','3T2']}"),
        first);
  }

  @Test
  void listRoomsFiltersByCapacityAndMarksAvailabilityForASlot() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of(), TUESDAY_AFTERNOON).orElseThrow();
    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE, null));

    JsonArray all = handle("list_rooms", COMMON, SLOT).getAsJsonArray(Fields.ROOMS);
    assertEquals("false", all.get(0).getAsJsonObject().get(Fields.AVAILABLE).getAsString());
    assertEquals("true", all.get(1).getAsJsonObject().get(Fields.AVAILABLE).getAsString());

    JsonObject large = handle("list_rooms", COMMON, ",'min_capacity':'13'");
    assertEquals("1", large.get(Fields.COUNT).getAsString());
    assertEquals("Lab 3", large.getAsJsonArray(Fields.ROOMS).get(0).getAsJsonObject().get(Fields.NAME).getAsString());

    JsonObject none = handle("list_rooms", COMMON, ",'min_capacity':'31'");
    assertEquals("0", none.get(Fields.COUNT).getAsString());
    assertEquals(0, none.getAsJsonArray(Fields.ROOMS).size());
  }

  @Test
  void listRoomsMarksARoomAvailableOnlyInsideItsSlots() {
    String[][] cases = {
        { "true", ",'date':'2026-09-15','start_time':'13:00','end_time':'14:40'" },
        { "true", ",'date':'2026-09-15','start_time':'13:50','end_time':'14:40'" },
        { "false", ",'date':'2026-09-15','start_time':'13:00','end_time':'15:30'" },
        { "false", ",'date':'2026-09-16','start_time':'13:00','end_time':'14:40'" },
        { "false", ",'date':'2026-09-13','start_time':'13:00','end_time':'14:40'" } };

    for (String[] c : cases) {
      JsonObject room = handle("list_rooms", COMMON, c[1]).getAsJsonArray(Fields.ROOMS).get(0).getAsJsonObject();
      assertEquals(c[0], room.get(Fields.AVAILABLE).getAsString(), c[1]);
    }

    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, null, List.of(slot("2M3"), slot("2M4"))));
    JsonObject accrosTheBreak = handle("list_rooms", COMMON,
        ",'date':'2026-09-14','start_time':'09:10','end_time':'11:10'").getAsJsonArray(Fields.ROOMS).get(0)
        .getAsJsonObject();
    assertEquals("true", accrosTheBreak.get(Fields.AVAILABLE).getAsString());
  }

  @Test
  void listRoomsRejectsAPartialSlotABlankFilterAndAnImpossibleSlot() {
    String[][] cases = {
        { "start_time", ",'date':'2026-09-15'" },
        { "end_time", ",'date':'2026-09-15','start_time':'13:00'" },
        { "date", ",'start_time':'13:00','end_time':'14:40','date':''" },
        { "date", ",'date':'15/09/2026','start_time':'13:00','end_time':'14:40'" },
        { "start_time", ",'date':'2026-09-15','start_time':'14:00','end_time':'14:40'" },
        { "date", ",'date':'2026-02-30','start_time':'13:00','end_time':'14:40'" },
        { "end_time", ",'date':'2026-09-15','start_time':'14:40','end_time':'14:40'" },
        { "end_time", ",'date':'2026-09-15','start_time':'14:40','end_time':'13:50'" },
        { "date", ",'date':null" },
        { "min_capacity", ",'min_capacity':''" },
        { "min_capacity", ",'min_capacity':'0'" } };

    for (String[] c : cases) {
      assertResponse("list_rooms_response", "400", "Filtros em formato invalido", handle("list_rooms", COMMON, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), lastDiagnosis());
    }
  }

  @Test
  void readRoomAnswersTheWholeRoomToAnyLoggedUser() {
    JsonObject response = handle("read_room", COMMON, ",'room_id':'" + b12.id() + "'");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.ROOM_ID, Fields.NAME, Fields.CAPACITY,
        Fields.LOCATION, Fields.RESOURCES, Fields.ROOM_STATUS, Fields.SLOTS);
    assertResponse("read_room_response", "200", "Consulta realizada com sucesso", response);
    assertEquals("Sala B12", response.get(Fields.NAME).getAsString());
    assertEquals(TestJson.json("['projetor','tv']"), response.get(Fields.RESOURCES).toString());
    assertEquals(TestJson.json("['3T1','3T2']"), response.get(Fields.SLOTS).toString());
  }

  @Test
  void readRoomWithADateAnswersThatDaysAgenda() {
    store.updateRoom(b12.id(),
        new RoomChanges(null, null, null, null, null, List.of(slot("3T2"), slot("5N1"), slot("3T1"))));
    String tuesday = ",'room_id':'" + b12.id() + "','date':'2026-09-15'";

    JsonObject response = handle("read_room", COMMON, tuesday);
    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.ROOM_ID, Fields.NAME, Fields.CAPACITY,
        Fields.LOCATION, Fields.RESOURCES, Fields.ROOM_STATUS, Fields.SLOTS, Fields.DATE, Fields.AGENDA);
    assertEquals("2026-09-15", response.get(Fields.DATE).getAsString());
    assertEquals(TestJson.json("[{'slot':'3T1','start_time':'13:00','end_time':'13:50','slot_status':'livre'},"
        + "{'slot':'3T2','start_time':'13:50','end_time':'14:40','slot_status':'livre'}]"),
        response.get(Fields.AGENDA).toString());

    JsonObject wednesday = handle("read_room", COMMON, ",'room_id':'" + b12.id() + "','date':'2026-09-16'");
    assertEquals(0, wednesday.getAsJsonArray(Fields.AGENDA).size());

    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE, null));
    JsonArray inactive = handle("read_room", COMMON, tuesday).getAsJsonArray(Fields.AGENDA);
    assertEquals("indisponivel", inactive.get(0).getAsJsonObject().get(Fields.SLOT_STATUS).getAsString());
  }

  // Sem `room_id`: todas as salas, nunca 404; com `date`, cada uma traz a sua agenda
  @Test
  void readRoomWithoutAnIdReadsEveryRoom() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of(), List.of(slot("2M1"))).orElseThrow();

    JsonObject all = handle("read_room", COMMON, "");
    assertResponse("read_room_response", "200", "Consulta realizada com sucesso", all);
    assertEquals("2", all.get(Fields.COUNT).getAsString());
    JsonArray rooms = all.getAsJsonArray(Fields.ROOMS);
    assertEquals("Sala B12", rooms.get(0).getAsJsonObject().get(Fields.NAME).getAsString());
    assertFalse(rooms.get(0).getAsJsonObject().has(Fields.AGENDA));

    JsonObject tuesday = handle("read_room", COMMON, ",'date':'2026-09-15'");
    assertEquals("2026-09-15", tuesday.get(Fields.DATE).getAsString());
    JsonArray withAgenda = tuesday.getAsJsonArray(Fields.ROOMS);
    assertEquals(2, withAgenda.get(0).getAsJsonObject().getAsJsonArray(Fields.AGENDA).size());
    assertEquals(0, withAgenda.get(1).getAsJsonObject().getAsJsonArray(Fields.AGENDA).size());
    assertFalse(withAgenda.get(0).getAsJsonObject().has(Fields.DATE));

    store.deleteRoom(b12.id());
    store.deleteRoom(store.findRoomByName("Lab 3").orElseThrow().id());
    assertEquals("0", handle("read_room", COMMON, "").get(Fields.COUNT).getAsString());
  }

  @Test
  void readRoomRejectsAnIdSentOutsideTheFormat() {
    for (String id : new String[] { "''", "null", "'B12'" }) {
      assertResponse("read_room_response", "400", "Dados em formato invalido",
          handle("read_room", COMMON, ",'room_id':" + id));
      assertTrue(lastDiagnosis().contains("campo 'room_id'"), lastDiagnosis());
    }
  }

  @Test
  void readRoomRejectsADateOutsideTheFormat() {
    String id = ",'room_id':'" + b12.id() + "'";
    for (String date : new String[] { "''", "null", "'15/09/2026'", "'2026-02-30'" }) {
      assertResponse("read_room_response", "400", "Dados em formato invalido",
          handle("read_room", COMMON, id + ",'date':" + date));
      assertTrue(lastDiagnosis().contains("campo 'date'"), lastDiagnosis());
    }
  }

  @Test
  void updateRoomChangesOnlyTheFieldsSent() {
    JsonObject response = handle("update_room", ADMIN, ",'room_id':'" + b12.id()
        + "','name':'','capacity':'16','location':'','resources':['projetor','tv','quadro'],'room_status':''");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("update_room_response", "200", "Sala atualizada com sucesso", response);
    assertEquals(
        new Room(b12.id(), "Sala B12", 16, "Bloco B - 1 andar", List.of("projetor", "tv", "quadro"), RoomStatus.ACTIVE,
            TUESDAY_AFTERNOON),
        stored(b12));

    assertStatus("200", handle("update_room", ADMIN,
        ",'room_id':'" + b12.id() + "','room_status':'inactive','resources':[],'slots':[]"));
    assertEquals(RoomStatus.INACTIVE, stored(b12).status());
    assertEquals(3, stored(b12).resources().size());
    assertEquals(TUESDAY_AFTERNOON, stored(b12).slots());

    assertStatus("200", handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','slots':['4T3']"));
    assertEquals(List.of(slot("4T3")), stored(b12).slots());

    assertStatus("200", handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','resources':['0']"));
    assertEquals(List.of(), stored(b12).resources());
  }

  @Test
  void updateRoomRejectsNothingToChange() {
    String id = ",'room_id':'" + b12.id() + "'";
    for (String fields : new String[] { id, id + ",'name':'','capacity':'','location':'','room_status':''",
        id + ",'name':'','capacity':'','location':'','room_status':'','resources':[],'slots':[]" }) {
      assertResponse("update_room_response", "400", "Nenhum dado para atualizar", handle("update_room", ADMIN, fields));
    }

    assertResponse("update_room_response", "400", "Dados da sala em formato invalido",
        handle("update_room", ADMIN, id + ",'name':'','slots':['9M1']"));
    assertEquals(b12, stored(b12));
  }

  @Test
  void updateRoomRejectsANameTakenByAnotherRoom() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of(), TUESDAY_AFTERNOON).orElseThrow();

    assertResponse("update_room_response", "409", "Sala ja cadastrada",
        handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','name':'Lab 3'"));
    assertStatus("200", handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','name':'Sala B12'"));
  }

  @Test
  void updateRoomRejectsEachFieldOutsideTheFormat() {
    String id = ",'room_id':'" + b12.id() + "'";
    String[][] cases = {
        { "room_id", ",'name':'Lab 3'" },
        { "room_id", ",'room_id':'abc','name':'Lab 3'" },
        { "name", id + ",'name':null" },
        { "capacity", id + ",'capacity':'10000'" },
        { "location", id + ",'location':'Bloco Ç'" },
        { "resources", id + ",'resources':null" },
        { "resources", id + ",'resources':[['tv']]" },
        { "resources", id + ",'resources':''" },
        { "resources", id + ",'resources':['0','tv']" },
        { "slots", id + ",'slots':null" },
        { "slots", id + ",'slots':['2M1','2M1']" },
        { "slots", id + ",'slots':['9M1']" },
        { "room_status", id + ",'room_status':'fechada'" } };

    for (String[] c : cases) {
      assertResponse("update_room_response", "400", "Dados da sala em formato invalido",
          handle("update_room", COMMON, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), lastDiagnosis());
    }
  }

  @Test
  void deleteRoomRemovesTheRoom() {
    JsonObject response = handle("delete_room", ADMIN, ",'room_id':'" + b12.id() + "'");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("delete_room_response", "200", "Sala removida com sucesso", response);
    assertTrue(store.findRoom(b12.id()).isEmpty());
    assertStatus("201", handle("create_room", ADMIN, B12));
  }

  @Test
  void answers404ForAnUnknownRoom() {
    String unknown = ",'room_id':'" + (b12.id() + 1) + "'";
    String[][] cases = {
        { "read_room", unknown },
        { "update_room", unknown + ",'capacity':'20'" },
        { "delete_room", unknown } };

    for (String[] c : cases) {
      assertResponse(c[0] + "_response", "404", "Sala nao encontrada", handle(c[0], ADMIN, c[1]));
      assertTrue(lastDiagnosis().contains("room_id " + (b12.id() + 1)), lastDiagnosis());
    }
  }

  @Test
  void everyRoomWriteRefusesACommonUser() {
    String id = ",'room_id':'" + b12.id() + "'";
    String[][] cases = {
        { "create_room", ",'name':'Lab 3','capacity':'30','location':'Bloco C','resources':[],'slots':['2M1']" },
        { "update_room", id + ",'room_status':'inactive'" },
        { "delete_room", id } };

    for (String[] c : cases) {
      assertResponse(c[0] + "_response", "403", "Permissao insuficiente", handle(c[0], COMMON, c[1]));
    }
    assertEquals(b12, stored(b12));
    assertFalse(store.findRoomByName("Lab 3").isPresent());
  }
}
