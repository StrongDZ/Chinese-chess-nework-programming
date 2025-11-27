package application.components;

import application.state.UIState;
import application.state.UIState.BoardState;
import application.util.AssetHelper;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Image titles that animate between "normal" and "small" frames.
 */
public class TitleImage extends StackPane {

    private final Timeline transition = new Timeline();
    private final Frame normalFrame;
    private final Frame smallFrame;
    private final ImageView imageView;
    private final DoubleProperty imageFitWidth = new SimpleDoubleProperty();

    private TitleImage(String assetName, Frame normal, Frame small, UIState state) {
        this.normalFrame = normal;
        this.smallFrame = small;

        imageView = new ImageView(AssetHelper.image(assetName));
        imageView.setPreserveRatio(true);
        imageFitWidth.set(normal.width());
        imageView.fitWidthProperty().bind(imageFitWidth);
        getChildren().add(imageView);
        setAlignment(Pos.TOP_LEFT);

        applyFrame(normalFrame);

        BooleanBinding showBinding = state.loginVisibleProperty().not().and(state.registerVisibleProperty().not());
        visibleProperty().bind(showBinding);

        state.boardStateProperty().addListener((obs, oldVal, newVal) -> {
            Frame target = newVal == BoardState.SMALL ? smallFrame : normalFrame;
            animateTo(target);
        });
    }

    public static TitleImage cnTitle(UIState state) {
        Frame normal = new Frame(500, 70, 935, 268);
        Frame small = new Frame(690, 76, 554, 138);
        return new TitleImage("title_cn.png", normal, small, state);
    }

    public static TitleImage enTitle(UIState state) {
        Frame normal = new Frame(740, 346, 461, 145);
        Frame small = new Frame(805, 233, 320, 70);
        return new TitleImage("title_en.png", normal, small, state);
    }

    private void applyFrame(Frame frame) {
        setLayoutX(frame.x());
        setLayoutY(frame.y());
        setPrefWidth(frame.width());
        setPrefHeight(frame.height());
    }

    private void animateTo(Frame frame) {
        transition.stop();
        transition.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(layoutXProperty(), getLayoutX()),
                        new KeyValue(layoutYProperty(), getLayoutY()),
                        new KeyValue(prefWidthProperty(), getPrefWidth()),
                        new KeyValue(prefHeightProperty(), getPrefHeight()),
                        new KeyValue(imageFitWidth, imageView.getFitWidth())
                ),
                new KeyFrame(Duration.millis(450),
                        new KeyValue(layoutXProperty(), frame.x()),
                        new KeyValue(layoutYProperty(), frame.y()),
                        new KeyValue(prefWidthProperty(), frame.width()),
                        new KeyValue(prefHeightProperty(), frame.height()),
                        new KeyValue(imageFitWidth, frame.width())
                )
        );
        transition.play();
    }

    private static class Frame {
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        private Frame(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        double x() { return x; }
        double y() { return y; }
        double width() { return width; }
        double height() { return height; }
    }
}

