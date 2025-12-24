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
        setPadding(new Insets(16));  // Tăng từ 12 lên 16
        setSpacing(10);
        getStyleClass().add("cc-input");

        if (password) {
            PasswordField pf = new PasswordField();
            pf.setPromptText(placeholder);
            pf.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");  // Thêm font size
            textField = pf;
        } else {
            TextField tf = new TextField();
            tf.setPromptText(placeholder);
            tf.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");  // Thêm font size
            textField = tf;
        }
        textField.setPrefWidth(420);  // Tăng từ 360 lên 420
        textField.setPrefHeight(40);  // Thêm chiều cao
        textField.setFocusTraversable(false);

        // Thêm listener để thêm/xóa style class khi focus
        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                if (!getStyleClass().contains("cc-input-focused")) {
                    getStyleClass().add("cc-input-focused");
                }
            } else {
                getStyleClass().remove("cc-input-focused");
            }
        });

        getChildren().add(textField);
    }

    public String getValue() {
        return textField.getText();
    }

    public void clear() {
        textField.clear();
    }
}

