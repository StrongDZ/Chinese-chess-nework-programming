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

public class FriendRequestNotificationDialog extends StackPane {
    
    private final NetworkManager networkManager = NetworkManager.getInstance();
    private final String fromUser;
    private final String mode;
    private final int timeLimit;
    private Runnable onClose;
    private Runnable onHide;
    
    public FriendRequestNotificationDialog(String fromUser, Runnable onClose) {
        this(fromUser, "", 0, onClose);
    }
    
    public FriendRequestNotificationDialog(String fromUser, String mode, int timeLimit, Runnable onClose) {
        this.fromUser = fromUser;
        this.mode = mode != null ? mode : "";
        this.timeLimit = timeLimit;
        this.onClose = onClose;
        this.onHide = null;
        
        // Tăng chiều cao nếu có mode và timer
        int height = (mode != null && !mode.isEmpty()) ? 450 : 350;
        setPrefSize(600, height);
        setLayoutX((1920 - 600) / 2);
        setLayoutY((1080 - height) / 2);
        
        setVisible(true);
        setManaged(true);
        setMouseTransparent(false);
        setPickOnBounds(true);
        setFocusTraversable(true);
        setViewOrder(-2000); // Đảm bảo luôn nằm trên Overlay
        setDisable(false);
        
        // Background
        Rectangle bg = new Rectangle(600, height);
        bg.setFill(Color.WHITE);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(2);
        bg.setArcWidth(20);
        bg.setArcHeight(20);
        
        // Background phải mouseTransparent(true) để không chặn clicks vào buttons
        // Dialog container (StackPane) sẽ chặn clicks phía sau, không cần background chặn
        bg.setMouseTransparent(true); 
        
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0, 0, 0, 0.5));
        shadow.setRadius(20);
        shadow.setOffsetX(5);
        shadow.setOffsetY(5);
        bg.setEffect(shadow);
        
        // Content
        VBox content = new VBox(20);
        content.setPrefSize(600, height);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        content.setMouseTransparent(false);
        content.setPickOnBounds(true);
        
        // Message
        String messageText = fromUser + " wants to be your friend";
        Label messageLabel = new Label(messageText);
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setPrefWidth(520);
        messageLabel.setMouseTransparent(true);
        
        // Hiển thị mode và timer nếu có
        if (mode != null && !mode.isEmpty()) {
            String modeText = mode.equals("blitz") ? "Blitz Mode" : "Classic Mode";
            String timeText = timeLimit > 0 ? formatTime(timeLimit) : "Unlimited time";
            
            Label modeLabel = new Label("Game Mode: " + modeText);
            modeLabel.setStyle(
                "-fx-font-family: 'Kolker Brush'; " +
                "-fx-font-size: 36px; " +
                "-fx-text-fill: #4A4A4A; " +
                "-fx-background-color: transparent;"
            );
            modeLabel.setAlignment(Pos.CENTER);
            modeLabel.setMouseTransparent(true);
            
            Label timeLabel = new Label("Time: " + timeText);
            timeLabel.setStyle(
                "-fx-font-family: 'Kolker Brush'; " +
                "-fx-font-size: 36px; " +
                "-fx-text-fill: #4A4A4A; " +
                "-fx-background-color: transparent;"
            );
            timeLabel.setAlignment(Pos.CENTER);
            timeLabel.setMouseTransparent(true);
            
            content.getChildren().addAll(messageLabel, modeLabel, timeLabel);
        } else {
            content.getChildren().add(messageLabel);
        }
        
        // Buttons Container
        HBox buttonsContainer = new HBox(20);
        buttonsContainer.setAlignment(Pos.CENTER);
        buttonsContainer.setMouseTransparent(false);
        buttonsContainer.setPickOnBounds(true);
        
        // Accept button
        StackPane acceptButton = createDialogButton("Accept", true);
        acceptButton.setOnMouseClicked(e -> {
            System.out.println("[Dialog] Accept clicked"); // Debug log
            try {
                networkManager.friend().respondFriendRequest(fromUser, true);
                hide(true);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.consume();
        });
        
        // Decline button
        StackPane declineButton = createDialogButton("Decline", false);
        declineButton.setOnMouseClicked(e -> {
            System.out.println("[Dialog] Decline clicked"); // Debug log
            try {
                networkManager.friend().respondFriendRequest(fromUser, false);
                hide(true);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(acceptButton, declineButton);
        content.getChildren().add(buttonsContainer);
        
        // Close button (X)
        StackPane closeButton = createCloseButton();
        closeButton.setOnMouseClicked(e -> {
            System.out.println("[Dialog] Close (X) clicked"); // Debug log
            hide();
            e.consume();
        });
        
        javafx.scene.layout.AnchorPane closeButtonContainer = new javafx.scene.layout.AnchorPane();
        closeButtonContainer.setPrefSize(600, height);
        // QUAN TRỌNG: pickOnBounds = FALSE để clicks vùng trống đi xuyên qua đến content (Accept/Decline buttons)
        // Chỉ close button mới nhận clicks, không phải toàn bộ AnchorPane
        closeButtonContainer.setMouseTransparent(false);
        closeButtonContainer.setPickOnBounds(false);
        
        javafx.scene.layout.AnchorPane.setTopAnchor(closeButton, 15.0);
        javafx.scene.layout.AnchorPane.setRightAnchor(closeButton, 15.0);
        closeButtonContainer.getChildren().add(closeButton);
        
        getChildren().addAll(bg, content, closeButtonContainer);
        
        setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), this);
        fadeIn.setToValue(1.0);
        fadeIn.setOnFinished(e -> javafx.application.Platform.runLater(this::toFront));
        fadeIn.play();
    }
    
    private StackPane createCloseButton() {
        StackPane button = new StackPane();
        button.setPrefSize(40, 40);
        
        javafx.scene.shape.Circle circleBg = new javafx.scene.shape.Circle(20);
        circleBg.setFill(Color.color(0.8, 0.8, 0.8));
        circleBg.setStroke(Color.color(0.5, 0.5, 0.5));
        
        javafx.scene.shape.Line line1 = new javafx.scene.shape.Line(12, 12, 28, 28);
        line1.setStroke(Color.color(0.3, 0.3, 0.3));
        line1.setStrokeWidth(3);
        
        javafx.scene.shape.Line line2 = new javafx.scene.shape.Line(28, 12, 12, 28);
        line2.setStroke(Color.color(0.3, 0.3, 0.3));
        line2.setStrokeWidth(3);
        
        // --- 2. Button Children: ĐỔI VỀ TRUE ---
        // Cho phép click đi xuyên qua hình vẽ để chạm vào StackPane (button)
        circleBg.setMouseTransparent(true); 
        line1.setMouseTransparent(true);
        line2.setMouseTransparent(true);
        
        button.getChildren().addAll(circleBg, line1, line2);
        button.setCursor(Cursor.HAND);
        
        // --- 3. StackPane Container: Đón nhận click ---
        button.setMouseTransparent(false);
        button.setPickOnBounds(true); // Bắt click trong toàn bộ vùng 40x40
        
        button.setOnMouseEntered(e -> {
            circleBg.setFill(Color.color(0.9, 0.9, 0.9));
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(1.1); st.setToY(1.1); st.play();
        });
        button.setOnMouseExited(e -> {
            circleBg.setFill(Color.color(0.8, 0.8, 0.8));
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });
        
        return button;
    }
    
    private StackPane createDialogButton(String text, boolean isPrimary) {
        StackPane button = new StackPane();
        button.setPrefSize(180, 60);
        
        Rectangle buttonBg = new Rectangle(180, 60);
        if (isPrimary) buttonBg.setFill(Color.web("#A65252"));
        else buttonBg.setFill(Color.color(0.7, 0.7, 0.7));
        buttonBg.setArcWidth(15);
        buttonBg.setArcHeight(15);
        
        // --- 2. Button Children: ĐỔI VỀ TRUE ---
        // Cho phép click đi xuyên qua hình nền và chữ
        buttonBg.setMouseTransparent(true); 
        
        Label buttonLabel = new Label(text);
        buttonLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 32px; -fx-text-fill: white; -fx-background-color: transparent;");
        buttonLabel.setMouseTransparent(true); 
        
        button.getChildren().addAll(buttonBg, buttonLabel);
        button.setCursor(Cursor.HAND);
        
        // --- 3. StackPane Container: Đón nhận click ---
        button.setMouseTransparent(false);
        button.setPickOnBounds(true); // Bắt click trong toàn bộ vùng 180x60
        
        button.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(1.05); st.setToY(1.05); st.play();
        });
        button.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), button);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });
        
        return button;
    }
    
    private String formatTime(int seconds) {
        if (seconds <= 0) {
            return "Unlimited time";
        }
        int minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " mins";
        }
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (remainingMinutes == 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        }
        return hours + "h " + remainingMinutes + "m";
    }
    
    public void hide() { hide(false); }
    public void setOnHide(Runnable onHide) { this.onHide = onHide; }
    
    public void hide(boolean removeFromPending) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), this);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> {
            if (getParent() instanceof javafx.scene.layout.Pane) {
                ((javafx.scene.layout.Pane) getParent()).getChildren().remove(this);
            }
            if (onHide != null) onHide.run();
            if (removeFromPending && onClose != null) onClose.run();
        });
        fadeOut.play();
    }
}