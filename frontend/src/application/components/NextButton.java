package application.components;

import application.util.AssetHelper;
import javafx.animation.ScaleTransition;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Circular arrow button that matches the web component.
 */
public class NextButton extends StackPane {

    private final ScaleTransition scaleTransition;

    public NextButton() {
        ImageView arrow = new ImageView(AssetHelper.image("next-button.png"));
        arrow.setFitWidth(110);
        arrow.setFitHeight(110);
        arrow.setPreserveRatio(true);

        getChildren().add(arrow);
        setCursor(Cursor.HAND);
        setEffect(new DropShadow(20, Color.rgb(255, 215, 64, 0.45)));

        // Tạo scale transition cho hiệu ứng hover
        scaleTransition = new ScaleTransition(Duration.millis(200), this);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.15);  // Phóng to 15% khi hover
        scaleTransition.setToY(1.15);

        // Hiệu ứng khi trỏ chuột vào
        setOnMouseEntered(e -> {
            scaleTransition.setToX(1.15);
            scaleTransition.setToY(1.15);
            scaleTransition.play();
        });

        // Hiệu ứng khi trỏ chuột ra ngoài
        setOnMouseExited(e -> {
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);
            scaleTransition.play();
        });
    }
}

