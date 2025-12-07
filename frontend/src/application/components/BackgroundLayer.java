package application.components;

import application.util.AssetHelper;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * Full-screen background image.
 */
public class BackgroundLayer extends StackPane {

    public BackgroundLayer(String fileName) {
        ImageView imageView = new ImageView(AssetHelper.image(fileName));
        imageView.setPreserveRatio(false);
        imageView.setFitWidth(1920);
        imageView.setFitHeight(1080);
        imageView.setOpacity(1.0);

        getChildren().add(imageView);
        setPrefSize(1920, 1080);
    }
}

