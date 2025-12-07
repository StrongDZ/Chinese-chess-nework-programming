package application.components;

import javafx.scene.Cursor;
import javafx.scene.control.Label;

/**
 * Simple clickable text link.
 */
public class LinkLabel extends Label {

    public LinkLabel(String text, Runnable action) {
        super(text);
        getStyleClass().add("cc-link");
        setCursor(Cursor.HAND);
        setOnMouseClicked(e -> action.run());
    }
}

