package application.components;

import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Toast notification component that displays error messages at the top-right corner.
 * Automatically hides after 3 seconds with fade animation.
 */
public class ToastNotification extends StackPane {
    
    private final Label messageLabel;
    private SequentialTransition showAndHideAnimation;
    private FadeTransition fadeIn;
    private FadeTransition fadeOut;
    private TranslateTransition slideIn;
    private TranslateTransition slideOut;
    private PauseTransition pause;
    
    // Position constants (top-right corner)
    private static final double TOAST_X = 1920 - 500 - 20; // 500px width + 20px margin
    private static final double TOAST_Y = 20; // 20px from top
    private static final double TOAST_WIDTH = 500;
    private static final double TOAST_HEIGHT = 80;
    
    public ToastNotification(UIState state) {
        setPrefSize(TOAST_WIDTH, TOAST_HEIGHT);
        setLayoutX(TOAST_X);
        setLayoutY(TOAST_Y);
        setStyle("-fx-background-color: transparent;");
        setPickOnBounds(false);
        setMouseTransparent(true); // Don't block mouse events
        
        // Background for toast - black with transparency
        Rectangle toastBg = new Rectangle(TOAST_WIDTH, TOAST_HEIGHT);
        toastBg.setFill(Color.color(0, 0, 0, 0.5)); // Black background with 50% opacity (more transparent)
        toastBg.setArcWidth(15);
        toastBg.setArcHeight(15);
        toastBg.setStroke(Color.color(1.0, 0.0, 0.0, 0.6)); // Red border with 60% opacity (more transparent)
        toastBg.setStrokeWidth(1); // Thin border
        
        // Message text - default font, smaller size
        messageLabel = new Label();
        messageLabel.setStyle(
            "-fx-font-size: 20px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true; " +
            "-fx-text-alignment: center;"
        );
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setPadding(new Insets(10));
        messageLabel.setMaxWidth(TOAST_WIDTH - 20);
        
        getChildren().addAll(toastBg, messageLabel);
        StackPane.setAlignment(toastBg, Pos.CENTER);
        StackPane.setAlignment(messageLabel, Pos.CENTER);
        
        // Initially hidden
        setOpacity(0);
        setVisible(false);
        setManaged(false);
        
        // Setup animations
        setupAnimations();
        
        // Listen to toast message property
        state.toastMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                showToast(newVal);
            }
        });
    }
    
    private void setupAnimations() {
        // Slide in from right
        slideIn = new TranslateTransition(Duration.millis(300), this);
        slideIn.setFromX(TOAST_WIDTH + 50); // Start from right (off-screen)
        slideIn.setToX(0);
        
        // Fade in
        fadeIn = new FadeTransition(Duration.millis(300), this);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        
        // Pause for 2 seconds
        pause = new PauseTransition(Duration.seconds(2));
        
        // Fade out
        fadeOut = new FadeTransition(Duration.millis(300), this);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        
        // Slide out to right
        slideOut = new TranslateTransition(Duration.millis(300), this);
        slideOut.setFromX(0);
        slideOut.setToX(TOAST_WIDTH + 50);
        
        // Combine animations: slide in + fade in -> pause -> fade out + slide out
        showAndHideAnimation = new SequentialTransition(
            new javafx.animation.ParallelTransition(slideIn, fadeIn),
            pause,
            new javafx.animation.ParallelTransition(fadeOut, slideOut)
        );
        
        // When animation finishes, hide the toast
        showAndHideAnimation.setOnFinished(e -> {
            setVisible(false);
            setManaged(false);
        });
    }
    
    private void showToast(String message) {
        // Stop any ongoing animation
        if (showAndHideAnimation != null) {
            showAndHideAnimation.stop();
        }
        
        // Set message
        messageLabel.setText(message);
        
        // Reset position and opacity
        setTranslateX(0);
        setOpacity(0);
        setVisible(true);
        setManaged(true);
        
        // Start animation
        showAndHideAnimation.play();
    }
}

