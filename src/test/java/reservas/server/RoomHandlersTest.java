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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import reservas.protocol.Fields;
import reservas.protocol.ProtocolException;
import reservas.protocol.Role;
import reservas.protocol.RoomStatus;
import reservas.protocol.TestJson;

class RoomHandlersTest {
  private static final String ADMIN = TOKEN;
  private static final String COMMON = OTHER_TOKEN;
  private static final String B12 = ",'name':'Sala B12','capacity':'12','location':'Bloco B - 1 andar','resources':['projetor','tv']";
  private static final String SLOT = ",'date':'2026-09-15','start_time':'14:00','end_time':'16:00'";

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

    b12 = store.createRoom("Sala B12", 12, "Bloco B - 1 andar", List.of("projetor", "tv")).orElseThrow();
  }

  @AfterEach
  void closeStore() {
    store.close();
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
        ",'name':'Lab 3','capacity':'30','location':'Bloco C','resources':[]");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.ROOM_ID);
    assertResponse("create_room_response", "201", "Sala cadastrada com sucesso", response);

    Room lab = store.findRoom(Long.parseLong(response.get(Fields.ROOM_ID).getAsString())).orElseThrow();
    assertEquals("Lab 3", lab.name());
    assertEquals(30, lab.capacity());
    assertEquals(List.of(), lab.resources());
    assertEquals(RoomStatus.ACTIVE, lab.status());
  }

  @Test
  void createRoomRejectsANameAlreadyTaken() {
    assertResponse("create_room_response", "409", "Sala ja cadastrada", handle("create_room", ADMIN, B12));
    assertTrue(lastDiagnosis().contains("\"Sala B12\""), lastDiagnosis());
  }

  @Test
  void createRoomRejectsEachFieldOutsideTheFormat() {
    String[][] cases = {
        { "name", ",'capacity':'12','location':'Bloco B','resources':[]" },
        { "name", ",'name':'','capacity':'12','location':'Bloco B','resources':[]" },
        { "name", ",'name':'Sala Ã','capacity':'12','location':'Bloco B','resources':[]" },
        { "capacity", ",'name':'Lab 3','capacity':'0','location':'Bloco B','resources':[]" },
        { "capacity", ",'name':'Lab 3','capacity':12,'location':'Bloco B','resources':[]" },
        { "location", ",'name':'Lab 3','capacity':'12','location':'Bloco B / 2','resources':[]" },
        { "resources", ",'name':'Lab 3','capacity':'12','location':'Bloco B'" },
        { "resources", ",'name':'Lab 3','capacity':'12','location':'Bloco B','resources':'projetor'" },
        { "resources", ",'name':'Lab 3','capacity':'12','location':'Bloco B','resources':['tv',1]" },
        { "resources", ",'name':'Lab 3','capacity':'12','location':'Bloco B','resources':null" } };

    for (String[] c : cases) {
      assertResponse("create_room_response", "400", "Dados da sala em formato invalido",
          handle("create_room", COMMON, c[1]));
      assertTrue(lastDiagnosis().contains("campo '" + c[0] + "'"), lastDiagnosis());
    }
  }

  @Test
  void listRoomsAnswersEveryRoomToAnyLoggedUser() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of()).orElseThrow();

    JsonObject response = handle("list_rooms", COMMON, "");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE, Fields.COUNT, Fields.ROOMS);
    assertResponse("list_rooms_response", "200", "Consulta realizada com sucesso", response);
    assertEquals("2", response.get(Fields.COUNT).getAsString());

    JsonObject first = response.getAsJsonArray(Fields.ROOMS).get(0).getAsJsonObject();
    assertKeys(first, Fields.ROOM_ID, Fields.NAME, Fields.CAPACITY, Fields.LOCATION, Fields.RESOURCES,
        Fields.ROOM_STATUS);
    assertEquals(TestJson.object("{'room_id':'" + b12.id() + "','name':'Sala B12','capacity':'12',"
        + "'location':'Bloco B - 1 andar','resources':['projetor','tv'],'room_status':'active'}"), first);
  }

  @Test
  void listRoomsFiltersByCapacityAndMarksAvailabilityForASlot() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of()).orElseThrow();
    store.updateRoom(b12.id(), new RoomChanges(null, null, null, null, RoomStatus.INACTIVE));

    JsonArray all = handle("list_rooms", COMMON, SLOT).getAsJsonArray(Fields.ROOMS);
    assertEquals("false", all.get(0).getAsJsonObject().get(Fields.AVAILABLE).getAsString());
    assertEquals("true", all.get(1).getAsJsonObject().get(Fields.AVAILABLE).getAsString());

    JsonObject large = handle("list_rooms", COMMON, ",'min_capacity':'13'");
    assertEquals("1", large.get(Fields.COUNT).getAsString());
    assertEquals("Lab 3", large.getAsJsonArray(Fields.ROOMS).get(0).getAsJsonObject().get(Fields.NAME).getAsString());

    JsonObject none = handle("list_rooms", COMMON, ",'min_capacity':'31','date':'','start_time':'','end_time':''");
    assertEquals("0", none.get(Fields.COUNT).getAsString());
    assertEquals(0, none.getAsJsonArray(Fields.ROOMS).size());
  }

  @Test
  void listRoomsRejectsAPartialSlotAndFiltersOutsideTheFormat() {
    String[][] cases = {
        { "start_time", ",'date':'2026-09-15'" },
        { "end_time", ",'date':'2026-09-15','start_time':'14:00'" },
        { "date", ",'start_time':'14:00','end_time':'16:00','date':''" },
        { "date", ",'date':'15/09/2026','start_time':'14:00','end_time':'16:00'" },
        { "start_time", ",'date':'2026-09-15','start_time':'24:00','end_time':'16:00'" },
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
        Fields.LOCATION, Fields.RESOURCES, Fields.ROOM_STATUS);
    assertResponse("read_room_response", "200", "Consulta realizada com sucesso", response);
    assertEquals("Sala B12", response.get(Fields.NAME).getAsString());
    assertEquals(TestJson.json("['projetor','tv']"), response.get(Fields.RESOURCES).toString());
  }

  @Test
  void updateRoomChangesOnlyTheFieldsSent() {
    JsonObject response = handle("update_room", ADMIN, ",'room_id':'" + b12.id()
        + "','name':'','capacity':'16','location':'','resources':['projetor','tv','quadro'],'room_status':''");

    assertKeys(response, Fields.OP, Fields.STATUS, Fields.MESSAGE);
    assertResponse("update_room_response", "200", "Sala atualizada com sucesso", response);
    assertEquals(
        new Room(b12.id(), "Sala B12", 16, "Bloco B - 1 andar", List.of("projetor", "tv", "quadro"), RoomStatus.ACTIVE),
        stored(b12));

    assertStatus("200", handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','room_status':'inactive'"));
    assertEquals(RoomStatus.INACTIVE, stored(b12).status());
    assertEquals(3, stored(b12).resources().size());

    assertStatus("200", handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','resources':''"));
    assertEquals(3, stored(b12).resources().size());

    assertStatus("200", handle("update_room", ADMIN, ",'room_id':'" + b12.id() + "','resources':[]"));
    assertEquals(List.of(), stored(b12).resources());
  }

  @Test
  void updateRoomRejectsANameTakenByAnotherRoom() {
    store.createRoom("Lab 3", 30, "Bloco C", List.of()).orElseThrow();

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
        { "create_room", ",'name':'Lab 3','capacity':'30','location':'Bloco C','resources':[]" },
        { "update_room", id + ",'room_status':'inactive'" },
        { "delete_room", id } };

    for (String[] c : cases) {
      assertResponse(c[0] + "_response", "403", "Permissao insuficiente", handle(c[0], COMMON, c[1]));
    }
    assertEquals(b12, stored(b12));
    assertFalse(store.findRoomByName("Lab 3").isPresent());
  }
}
