package application.components;

import application.network.NetworkManager;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;
import java.io.IOException;

/**
 * Dialog to show when receiving a friend request from another user.
 */
public class FriendRequestNotificationDialog extends StackPane {
    
    private final NetworkManager networkManager = NetworkManager.getInstance();
    private final String fromUser;
    private Runnable onClose;
    
    public FriendRequestNotificationDialog(String fromUser, Runnable onClose) {
        this.fromUser = fromUser;
        this.onClose = onClose;
        
        setPrefSize(600, 350);
        setLayoutX((1920 - 600) / 2);
        setLayoutY((1080 - 350) / 2);
        
        // Background
        Rectangle bg = new Rectangle(600, 350);
        bg.setFill(Color.WHITE);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(2);
        bg.setArcWidth(20);
        bg.setArcHeight(20);
        
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0, 0, 0, 0.5));
        shadow.setRadius(20);
        shadow.setOffsetX(5);
        shadow.setOffsetY(5);
        bg.setEffect(shadow);
        
        // Content
        VBox content = new VBox(30);
        content.setPrefSize(600, 350);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        
        // Message
        Label messageLabel = new Label(fromUser + " wants to be your friend");
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setPrefWidth(520);
        
        // Buttons
        HBox buttonsContainer = new HBox(20);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Accept button
        StackPane acceptButton = createDialogButton("Accept", true);
        acceptButton.setOnMouseClicked(e -> {
            try {
                networkManager.friend().respondFriendRequest(fromUser, true);
                hide();
            } catch (IOException ex) {
                System.err.println("[FriendRequestNotificationDialog] Failed to accept friend request: " + ex.getMessage());
            }
            e.consume();
        });
        
        // Decline button
        StackPane declineButton = createDialogButton("Decline", false);
        declineButton.setOnMouseClicked(e -> {
            try {
                networkManager.friend().respondFriendRequest(fromUser, false);
                hide();
            } catch (IOException ex) {
                System.err.println("[FriendRequestNotificationDialog] Failed to decline friend request: " + ex.getMessage());
            }
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(acceptButton, declineButton);
        
        content.getChildren().addAll(messageLabel, buttonsContainer);
        
        getChildren().addAll(bg, content);
        
        // Fade in animation
        setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), this);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    private StackPane createDialogButton(String text, boolean isPrimary) {
        StackPane button = new StackPane();
        button.setPrefSize(180, 60);
        
        Rectangle buttonBg = new Rectangle(180, 60);
        if (isPrimary) {
            buttonBg.setFill(Color.web("#A65252"));
        } else {
            buttonBg.setFill(Color.color(0.7, 0.7, 0.7));
        }
        buttonBg.setArcWidth(15);
        buttonBg.setArcHeight(15);
        
        Label buttonLabel = new Label(text);
        buttonLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 32px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        
        button.getChildren().addAll(buttonBg, buttonLabel);
        button.setCursor(Cursor.HAND);
        
        // Hover effect
        button.setOnMouseEntered(e -> {
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), button);
            scaleIn.setToX(1.05);
            scaleIn.setToY(1.05);
            scaleIn.play();
        });
        button.setOnMouseExited(e -> {
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), button);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            scaleOut.play();
        });
        
        return button;
    }
    
    public void hide() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), this);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> {
            if (getParent() != null && getParent() instanceof javafx.scene.layout.Pane) {
                ((javafx.scene.layout.Pane) getParent()).getChildren().remove(this);
            }
            if (onClose != null) {
                onClose.run();
            }
        });
        fadeOut.play();
    }
}


