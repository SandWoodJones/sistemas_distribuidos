package reservas.client;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

// Quebra a linha em qualquer caractere: um JSON sem espaços não sai da tela
final class WrappingKit extends StyledEditorKit {
  @Override
  public ViewFactory getViewFactory() {
    return element -> switch (element.getName()) {
      case AbstractDocument.ContentElementName -> new AnywhereLabelView(element);
      case AbstractDocument.ParagraphElementName -> new ParagraphView(element);
      case AbstractDocument.SectionElementName -> new BoxView(element, View.Y_AXIS);
      case StyleConstants.ComponentElementName -> new ComponentView(element);
      case StyleConstants.IconElementName -> new IconView(element);
      default -> new LabelView(element);
    };
  }

  private static final class AnywhereLabelView extends LabelView {
    AnywhereLabelView(Element element) {
      super(element);
    }

    @Override
    public float getMinimumSpan(int axis) {
      return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
    }
  }
}
