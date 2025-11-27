package application.components;

import application.state.UIState;
import application.state.UIState.BoardState;
import application.util.AssetHelper;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Chess board component that shrinks and expands based on the current state.
 */
public class BoardComponent extends StackPane {

    private final UIState state;
    private final Timeline transition = new Timeline();

    private static final Frame NORMAL = new Frame(680, 630, 607);
    private static final Frame SMALL = new Frame(760, 700, 400);

    public BoardComponent(UIState state) {
        this.state = state;
        ImageView imageView = new ImageView(AssetHelper.image("board.png"));
        imageView.setFitWidth(NORMAL.size());
        imageView.setPreserveRatio(true);
        imageView.setEffect(new DropShadow(20, Color.rgb(0, 0, 0, 0.25)));
        getChildren().add(imageView);

        setLayoutX(NORMAL.x());
        setLayoutY(NORMAL.y());
        setPrefWidth(NORMAL.size());
        setPickOnBounds(false);
        setCursor(Cursor.HAND);

        state.boardStateProperty().addListener((obs, oldVal, newVal) -> animateTo(newVal == BoardState.SMALL ? SMALL : NORMAL));
    }

    public void reset() {
        state.setBoardState(BoardState.NORMAL);
    }

    private void animateTo(Frame frame) {
        transition.stop();
        transition.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(layoutXProperty(), getLayoutX()),
                        new KeyValue(layoutYProperty(), getLayoutY()),
                        new KeyValue(prefWidthProperty(), getPrefWidth())),
                new KeyFrame(Duration.millis(450),
                        new KeyValue(layoutXProperty(), frame.x()),
                        new KeyValue(layoutYProperty(), frame.y()),
                        new KeyValue(prefWidthProperty(), frame.size()))
        );
        transition.play();
    }

    private static class Frame {
        private final double x;
        private final double y;
        private final double size;

        private Frame(double x, double y, double size) {
            this.x = x;
            this.y = y;
            this.size = size;
        }

        double x() { return x; }
        double y() { return y; }
        double size() { return size; }
    }
}

