package application.components;

import application.state.UIState;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

/**
 * Manager để quản lý chat UI
 */
public class ChatManager {
    
    private final UIState state;
    private final GamePanel gamePanel;
    private final Pane rootPane;
    
    private StackPane chatInputContainer = null;
    private StackPane chatPopup = null;
    
    public ChatManager(UIState state, GamePanel gamePanel, Pane rootPane) {
        this.state = state;
        this.gamePanel = gamePanel;
        this.rootPane = rootPane;
    }
    
    /**
     * Hiển thị chat input field
     */
    public void showChatInput() {
        // Nếu đã có input field, ẩn nó trước
        if (chatInputContainer != null && rootPane != null && rootPane.getChildren().contains(chatInputContainer)) {
            rootPane.getChildren().remove(chatInputContainer);
        }
        
        // Tạo chat input field
        chatInputContainer = new StackPane();
        chatInputContainer.setLayoutX((1920 - 400) / 2 + 750);  // Dịch sang phải 100px
        chatInputContainer.setLayoutY(100);
        chatInputContainer.setPrefSize(400, 60);    
        
        // Background cho input
        Rectangle inputBg = new Rectangle(400, 60);
        inputBg.setFill(Color.WHITE);
        inputBg.setStroke(Color.color(0.3, 0.3, 0.3));
        inputBg.setStrokeWidth(2);
        inputBg.setArcWidth(10);
        inputBg.setArcHeight(10);
        
        // TextField để nhập chat
        TextField chatInput = new TextField();
        chatInput.setPrefSize(380, 40);
        chatInput.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 24px; " +
            "-fx-background-color: transparent; " +
            "-fx-border-color: transparent;"
        );
        chatInput.setPromptText("Nhập tin nhắn...");
        
        // Xử lý khi nhấn Enter
        chatInput.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                String message = chatInput.getText().trim();
                if (!message.isEmpty()) {
                    showChatPopup(message);
                    chatInput.clear();
                    // Ẩn input field
                    if (rootPane.getChildren().contains(chatInputContainer)) {
                        rootPane.getChildren().remove(chatInputContainer);
                    }
                }
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                // Ẩn input field khi nhấn ESC
                if (rootPane.getChildren().contains(chatInputContainer)) {
                    rootPane.getChildren().remove(chatInputContainer);
                }
            }
        });
        
        chatInputContainer.getChildren().addAll(inputBg, chatInput);
        chatInputContainer.setAlignment(Pos.CENTER);
        
        // Thêm vào root pane
        rootPane.getChildren().add(chatInputContainer);
        
        // Focus vào input field
        Platform.runLater(() -> chatInput.requestFocus());
    }
    
    /**
     * Hiển thị popup chat message
     */
    public void showChatPopup(String message) {
        // Nếu đã có popup, xóa nó trước
        if (chatPopup != null && rootPane != null && rootPane.getChildren().contains(chatPopup)) {
            rootPane.getChildren().remove(chatPopup);
        }
        
        // Vị trí avatar của đối thủ (bottom-right)
        double avatarX = 1920 - 525;  // 1470
        double avatarY = 1080 - 200;  // 880
        double avatarWidth = 450;  // Width của profile container
        double avatarHeight = 120;  // Height của profile container
        
        // Tính toán vị trí popup - đặt phía trên avatar, căn chỉnh để không tràn ra ngoài
        double popupWidth = 300;
        double popupHeight = 120;
        double popupX = avatarX + (avatarWidth - popupWidth) / 2;  // Căn giữa theo avatar
        // Đảm bảo không tràn ra ngoài màn hình bên phải
        if (popupX + popupWidth > 1920) {
            popupX = 1920 - popupWidth - 20;  // Cách lề phải 20px
        }
        // Đảm bảo không tràn ra ngoài màn hình bên trái
        if (popupX < 0) {
            popupX = 20;  // Cách lề trái 20px
        }
        double popupY = avatarY - popupHeight - 20;  // Phía trên avatar, cách 20px
        
        // Tạo popup container
        Pane popupContainer = new Pane();
        popupContainer.setLayoutX(popupX);
        popupContainer.setLayoutY(popupY);
        popupContainer.setPrefSize(popupWidth, popupHeight);
        
        // Background chính cho popup (nền trắng, viền đỏ nâu)
        Rectangle popupBg = new Rectangle(popupWidth, popupHeight);
        popupBg.setFill(Color.WHITE);
        popupBg.setStroke(Color.color(0.6, 0.4, 0.3));  // Màu đỏ nâu giống border của avatar
        popupBg.setStrokeWidth(2);
        popupBg.setArcWidth(20);
        popupBg.setArcHeight(20);
        popupBg.setLayoutX(0);
        popupBg.setLayoutY(0);
        
        // Tạo tail (đuôi nhọn) trỏ xuống về phía avatar
        // Tính toán vị trí tail để trỏ vào giữa avatar
        double avatarCenterX = avatarX + avatarWidth / 2;  // Giữa avatar: 1470 + 225 = 1695
        // Vị trí tail tương đối với popupContainer (không phải popupX vì đã wrap trong StackPane)
        double tailX = avatarCenterX - popupX - 90;  // Dịch sang trái 30px
        
        // Giới hạn tail trong phạm vi popup (tránh tail ra ngoài)
        tailX = Math.max(30, Math.min(popupWidth - 70, tailX));
        
        Polygon tail = new Polygon();
        tail.getPoints().addAll(
            tailX, popupHeight,           // Điểm bắt đầu từ bubble (góc dưới)
            tailX - 15, popupHeight + 20, // Điểm nhọn trỏ xuống
            tailX + 15, popupHeight + 20  // Điểm kết thúc
        );
        tail.setFill(Color.WHITE);
        tail.setStroke(Color.color(0.6, 0.4, 0.3));
        tail.setStrokeWidth(2);
        
        // Label để hiển thị message
        Label messageLabel = new Label(message);
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 36px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        messageLabel.setLayoutX(20);  // Padding trái
        messageLabel.setLayoutY(40);  // Căn giữa theo chiều dọc
        messageLabel.setPrefWidth(popupWidth - 40);  // Chiều rộng còn lại
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.CENTER_LEFT);
        
        popupContainer.getChildren().addAll(popupBg, tail, messageLabel);
        
        // Lưu vào chatPopup - KHÔNG wrap trong StackPane, dùng trực tiếp Pane
        chatPopup = new StackPane();
        // Set vị trí cho chatPopup
        chatPopup.setLayoutX(popupX);
        chatPopup.setLayoutY(popupY);
        chatPopup.getChildren().add(popupContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(chatPopup);
        
        // Fade in animation
        chatPopup.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), chatPopup);
        fadeIn.setToValue(1.0);
        fadeIn.play();
        
        // Tự động ẩn sau 5 giây
        javafx.animation.Timeline hideTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.seconds(5), e -> {
                // Fade out animation
                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), chatPopup);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(event -> {
                    if (rootPane.getChildren().contains(chatPopup)) {
                        rootPane.getChildren().remove(chatPopup);
                    }
                });
                fadeOut.play();
            })
        );
        hideTimer.play();
    }
    
    public boolean isChatInputVisible() {
        return chatInputContainer != null && rootPane != null && rootPane.getChildren().contains(chatInputContainer);
    }
}


