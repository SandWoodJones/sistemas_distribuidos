package reservas.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


class RequestTest {
  // Texto 400 de `update_user`
  private static final String BAD_REQUEST = "Dados em formato invalido";

  private static Request of(String json) {
    return new Request(TestJson.object(json), BAD_REQUEST);
  }

  @Test
  void readsAValidRequiredField() {
    assertEquals("joao", of("{'user':'joao'}").required(Fields.USER, Formats.USER));
  }

  @Test
  void rejectsMissingRequiredField() {
    ProtocolException thrown = assertThrows(ProtocolException.class,
        () -> of("{}").required(Fields.USER, Formats.USER));
    assertEquals(Status.BAD_REQUEST, thrown.status());
    assertEquals(BAD_REQUEST, thrown.wireMessage());
  }

  // `null` é 400 mesmo onde `""` seria aceito
  @Test
  void rejectsJsonNull() {
    assertThrows(ProtocolException.class, () -> of("{'user':null}").required(Fields.USER, Formats.USER));
    assertThrows(ProtocolException.class, () -> of("{'user':null}").optional(Fields.USER, Formats.USER));
  }

  @Test
  void rejectsNonStringValues() {
    for (String json : new String[] { "{'user':12}", "{'user':true}", "{'user':[]}", "{'user':{}}" }) {
      assertThrows(ProtocolException.class, () -> of(json).required(Fields.USER, Formats.USER));
    }
  }

  @Test
  void rejectsValuesOutsideTheFormat() {
    assertThrows(ProtocolException.class, () -> of("{'user':'joao123'}").required(Fields.USER, Formats.USER));
    assertThrows(ProtocolException.class, () -> of("{'user':''}").required(Fields.USER, Formats.USER));
  }

  // Em atualização, ausente e `""` são o mesmo
  @Test
  void treatsAbsentAndEmptyAsNoChange() {
    assertTrue(of("{}").optional(Fields.USER, Formats.USER).isEmpty());
    assertTrue(of("{'user':''}").optional(Fields.USER, Formats.USER).isEmpty());
  }

  @Test
  void readsAValidOptionalField() {
    assertEquals("maria", of("{'user':'maria'}").optional(Fields.USER, Formats.USER).orElseThrow());
  }

  @Test
  void rejectsOptionalValueOutsideTheFormat() {
    assertThrows(ProtocolException.class, () -> of("{'user':'Joao'}").optional(Fields.USER, Formats.USER));
  }

  // A chave presente já é 400, mesmo vazia
  @Test
  void rejectsFieldThatMustBeAbsent() {
    assertThrows(ProtocolException.class, () -> of("{'email':'a@b.com'}").mustBeAbsent(Fields.EMAIL));
    assertThrows(ProtocolException.class, () -> of("{'email':''}").mustBeAbsent(Fields.EMAIL));
    of("{}").mustBeAbsent(Fields.EMAIL);
  }

  @Test
  void readsAToken() {
    String token = "a".repeat(64);
    assertEquals(token, of("{'token':'" + token + "'}").token());
    assertThrows(ProtocolException.class, () -> of("{'token':'ABC'}").token());
  }

  // Campo desconhecido nunca é motivo de recusa
  @Test
  void ignoresUnknownFields() {
    assertEquals("joao", of("{'user':'joao','campo_novo':'x'}").required(Fields.USER, Formats.USER));
  }

  // A rede recebe o texto da operação, o diagnóstico diz qual campo e qual valor
  @Test
  void diagnosisNamesTheFieldWhileTheWireTextStaysGeneric() {
    ProtocolException thrown = assertThrows(ProtocolException.class,
        () -> of("{'op':'update_user','user':'joao123'}").required(Fields.USER, Formats.USER));

    assertEquals(BAD_REQUEST, thrown.wireMessage());
    assertTrue(thrown.getMessage().contains("'user'"));
    assertTrue(thrown.getMessage().contains("joao123"));
    assertTrue(thrown.getMessage().contains(Formats.USER.pattern()));
    assertTrue(thrown.getMessage().contains("update_user"));
  }
}
