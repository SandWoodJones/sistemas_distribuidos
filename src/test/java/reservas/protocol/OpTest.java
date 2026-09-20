package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class OpTest {
  @Test
  void hasTwentyOneOperations() {
    assertEquals(21, Op.values().length);
  }

  @Test
  void wireNamesAreUniqueAndWellFormed() {
    Set<String> names = Arrays.stream(Op.values()).map(Op::wireName).collect(Collectors.toSet());
    assertEquals(Op.values().length, names.size(), "existe wireName duplicado");
    for (Op op : Op.values()) {
      assertTrue(Formats.matches(Formats.OP, op.wireName()), op.wireName());
    }
  }

  // op da resposta = op da requisição + "_response"
  @Test
  void responseNameIsRequestPlusSuffix() {
    assertEquals("login_response", Op.LOGIN.responseName());
    assertEquals("admin_list_users_response", Op.ADMIN_LIST_USERS.responseName());
    for (Op op : Op.values()) {
      assertEquals(op.wireName() + "_response", op.responseName());
      assertTrue(Formats.matches(Formats.OP, op.responseName()), op.responseName());
    }
  }

  @Test
  void fromWireRoundTripsEveryOperation() {
    for (Op op : Op.values()) {
      assertEquals(op, Op.fromWire(op.wireName()).orElseThrow());
    }
  }

  @Test
  void fromWireIsCaseSensitiveAndRejectsUnknown() {
    assertTrue(Op.fromWire("Login").isEmpty());
    assertTrue(Op.fromWire("LOGIN").isEmpty());
    assertTrue(Op.fromWire("nope").isEmpty());
    assertTrue(Op.fromWire("").isEmpty());
    // Sufixo de resposta nunca é op de requisição
    assertTrue(Op.fromWire("login_response").isEmpty());
  }

  @Test
  void onlyRegisterAndLoginSkipTheToken() {
    Set<Op> withoutToken = Arrays.stream(Op.values()).filter(op -> !op.requiresToken()).collect(Collectors.toSet());
    assertEquals(Set.of(Op.REGISTER, Op.LOGIN), withoutToken);
  }

  // `LIST_RESERVATIONS` só exige admin quando `scope` é `all`, isso é verificado
  // por requisição, não pelo `op`
  @Test
  void adminOperationsAreTheAdminPrefixPlusRoomWrites() {
    Set<Op> adminOnly = Arrays.stream(Op.values()).filter(Op::requiresAdmin).collect(Collectors.toSet());
    assertEquals(EnumSet.of(Op.ADMIN_LIST_USERS, Op.ADMIN_READ_USER, Op.ADMIN_UPDATE_USER, Op.ADMIN_DELETE_USER,
        Op.CREATE_ROOM, Op.UPDATE_ROOM, Op.DELETE_ROOM), adminOnly);
  }
}
