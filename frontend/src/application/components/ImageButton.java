package application.components;

import application.util.AssetHelper;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Shared image button with hover affordance.
 */
public class ImageButton extends StackPane {

    public ImageButton(String assetName, double width) {
        ImageView imageView = new ImageView(AssetHelper.image(assetName));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(width);

        setCursor(Cursor.HAND);
        getChildren().add(imageView);
        setEffect(new DropShadow(30, Color.rgb(255, 215, 64, 0.35)));

        setOnMouseEntered(e -> {
            setScaleX(1.02);
            setScaleY(1.02);
        });
        setOnMouseExited(e -> {
            setScaleX(1);
            setScaleY(1);
        });
    }
}

