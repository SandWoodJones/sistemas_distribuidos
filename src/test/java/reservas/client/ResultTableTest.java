package reservas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.TableModel;

import org.junit.jupiter.api.Test;

import reservas.protocol.TestJson;

class ResultTableTest {
  private static List<List<Object>> cells(TableModel model) {
    List<List<Object>> rows = new ArrayList<>();
    List<Object> header = new ArrayList<>();
    for (int column = 0; column < model.getColumnCount(); column++) {
      header.add(model.getColumnName(column));
    }
    rows.add(header);

    for (int row = 0; row < model.getRowCount(); row++) {
      List<Object> line = new ArrayList<>();
      for (int column = 0; column < model.getColumnCount(); column++) {
        line.add(model.getValueAt(row, column));
      }
      rows.add(line);
    }

    return rows;
  }

  @Test
  void aListingBecomesOneRowPerItemAndAReadBecomesOneRow() {
    TableModel listing = ResultTable.of(TestJson.object("{'op':'list_rooms_response','status':'200','message':'ok',"
        + "'count':'2','rooms':[{'room_id':'1','name':'Sala B12','resources':['projetor','tv']},"
        + "{'room_id':'2','name':'Lab 3','resources':[],'available':'true'}]}"));
    assertEquals(List.of(
        List.of("room_id", "name", "resources", "available"),
        List.of("1", "Sala B12", "projetor, tv", ""),
        List.of("2", "Lab 3", "", "true")), cells(listing));

    TableModel read = ResultTable.of(TestJson.object(
        "{'op':'read_room_response','status':'200','message':'ok','room_id':'1','resources':['tv']}"));
    assertEquals(List.of(List.of("room_id", "resources"), List.of("1", "tv")), cells(read));

    TableModel failure = ResultTable.of(TestJson.object("{'op':'read_room_response','status':'404','message':'x'}"));
    assertEquals(0, failure.getColumnCount());
  }

  @Test
  void resourcesAreTypedAsCommaSeparatedText() {
    String[][] cases = {
        { "projetor, tv", "projetor|tv" },
        { "  projetor  ,tv,  ", "projetor|tv" },
        { "quadro branco", "quadro branco" },
        { " , ", "" },
        { "", "" }
    };

    for (String[] c : cases) {
      List<String> parsed = ListText.parse(c[0]);
      assertEquals(c[1], String.join("|", parsed), c[0]);
      assertEquals(parsed, ListText.parse(ListText.format(parsed)), c[0]);
    }
  }
}
