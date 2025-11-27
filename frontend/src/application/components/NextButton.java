package application.components;

import application.util.AssetHelper;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Circular arrow button that matches the web component.
 */
public class NextButton extends StackPane {

    public NextButton() {
        ImageView arrow = new ImageView(AssetHelper.image("next-button.png"));
        arrow.setFitWidth(80);
        arrow.setFitHeight(80);
        arrow.setPreserveRatio(true);

        getChildren().add(arrow);
        setCursor(Cursor.HAND);
        setEffect(new DropShadow(20, Color.rgb(255, 215, 64, 0.45)));
    }
}

