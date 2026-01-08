package application.components;

import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Overlay component that shows "Đang kết nối lại server" message
 * and dims the screen when connection is lost.
 */
public class ReconnectingOverlay extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(300), this);
    private final UIState state;
    private final Label messageLabel;
    private Timeline dotsAnimation;

    public ReconnectingOverlay(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        setMouseTransparent(false); // Block all mouse events
        
        // Semi-transparent dark overlay to dim the screen
        Rectangle dimOverlay = new Rectangle(1920, 1080);
        dimOverlay.setFill(Color.color(0, 0, 0, 0.7)); // 70% opacity black
        
        // Message container
        StackPane messageContainer = new StackPane();
        messageContainer.setAlignment(Pos.CENTER);
        
        // Background for message
        Rectangle messageBg = new Rectangle(600, 150);
        messageBg.setFill(Color.color(0.2, 0.2, 0.2, 0.95)); // Dark grey background
        messageBg.setArcWidth(20);
        messageBg.setArcHeight(20);
        messageBg.setStroke(Color.color(0.5, 0.5, 0.5)); // Light grey border
        messageBg.setStrokeWidth(2);
        
        // Message text
        messageLabel = new Label("Server disconnected. Try to reconnect");
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        messageLabel.setAlignment(Pos.CENTER);
        
        // Create dots animation: ".", "..", "...", then repeat
        // Use a counter to cycle through dot states
        final int[] dotCount = {0};
        final String[] dotStrings = {".", "..", "..."};
        dotsAnimation = new Timeline(
            new KeyFrame(Duration.millis(500), e -> {
                dotCount[0] = (dotCount[0] + 1) % 3;
                messageLabel.setText("Server disconnected. Try to reconnect" + dotStrings[dotCount[0]]);
            })
        );
        dotsAnimation.setCycleCount(Timeline.INDEFINITE);
        
        messageContainer.getChildren().addAll(messageBg, messageLabel);
        StackPane.setAlignment(messageBg, Pos.CENTER);
        StackPane.setAlignment(messageLabel, Pos.CENTER);
        
        getChildren().addAll(dimOverlay, messageContainer);
        
        // Bind visibility with reconnectingVisible property
        visibleProperty().bind(state.reconnectingVisibleProperty());
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation when reconnectingVisible changes
        state.reconnectingVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                fadeTo(1);
                // Start dots animation
                dotsAnimation.play();
            } else {
                fadeTo(0);
                // Stop dots animation
                dotsAnimation.stop();
                // Reset to base text
                messageLabel.setText("Server disconnected. Try to reconnect");
            }
        });
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}

