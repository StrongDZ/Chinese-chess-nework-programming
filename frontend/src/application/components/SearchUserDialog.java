package application.components;

import application.network.NetworkManager;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
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
 * Dialog for searching and adding friends by username.
 */
public class SearchUserDialog extends StackPane {
    
    private final NetworkManager networkManager = NetworkManager.getInstance();
    private Runnable onClose;
    
    public SearchUserDialog(Runnable onClose) {
        this.onClose = onClose;
        
        setPrefSize(600, 400);
        setLayoutX((1920 - 600) / 2);
        setLayoutY((1080 - 400) / 2);
        
        // Background
        Rectangle bg = new Rectangle(600, 400);
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
        content.setPrefSize(600, 400);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        
        // Title
        Label title = new Label("Add Friend");
        title.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        title.setAlignment(Pos.CENTER);
        
        // Search input
        VBox inputContainer = new VBox(10);
        inputContainer.setAlignment(Pos.CENTER);
        
        Label searchLabel = new Label("Search by username:");
        searchLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 20px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        TextField searchField = new TextField();
        searchField.setPrefWidth(400);
        searchField.setPrefHeight(50);
        searchField.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 18px; " +
            "-fx-background-color: white; " +
            "-fx-border-color: #A65252; " +
            "-fx-border-width: 2px; " +
            "-fx-border-radius: 5px;"
        );
        searchField.setPromptText("Enter username...");
        
        inputContainer.getChildren().addAll(searchLabel, searchField);
        
        // Buttons
        HBox buttonsContainer = new HBox(20);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Send Request button
        StackPane sendButton = createDialogButton("Send Request", true);
        sendButton.setOnMouseClicked(e -> {
            String username = searchField.getText().trim();
            if (username != null && !username.isEmpty()) {
                try {
                    networkManager.friend().sendFriendRequest(username);
                    hide();
                } catch (IOException ex) {
                    System.err.println("[SearchUserDialog] Failed to send friend request: " + ex.getMessage());
                    // TODO: Show error message to user
                }
            }
            e.consume();
        });
        
        // Cancel button
        StackPane cancelButton = createDialogButton("Cancel", false);
        cancelButton.setOnMouseClicked(e -> {
            hide();
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(sendButton, cancelButton);
        
        content.getChildren().addAll(title, inputContainer, buttonsContainer);
        
        getChildren().addAll(bg, content);
        
        // Fade in animation
        setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), this);
        fadeIn.setToValue(1.0);
        fadeIn.play();
        
        // Focus search field
        javafx.application.Platform.runLater(() -> searchField.requestFocus());
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


