package application.components;

import javafx.geometry.Insets;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * Styled input used by login/register panels.
 */
public class InputField extends HBox {

    private final TextField textField;
    private final boolean password;

    public InputField(String placeholder, boolean password) {
        this.password = password;
        setPadding(new Insets(12));
        setSpacing(10);
        getStyleClass().add("cc-input");

        if (password) {
            PasswordField pf = new PasswordField();
            pf.setPromptText(placeholder);
            textField = pf;
        } else {
            TextField tf = new TextField();
            tf.setPromptText(placeholder);
            textField = tf;
        }
        textField.setPrefWidth(360);
        textField.setFocusTraversable(false);

        getChildren().add(textField);
    }

    public String getValue() {
        return textField.getText();
    }

    public void clear() {
        textField.clear();
    }
}

