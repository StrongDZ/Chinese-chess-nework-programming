package application.components;

import application.state.UIState;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
    
    private final GamePanel gamePanel;
    private final Pane rootPane;  // Lưu reference đến rootPane để thêm UI elements
    
    private StackPane chatInputContainer = null;
    private Pane inputPane = null;  // Lưu reference trực tiếp đến inputPane để dễ remove
    private StackPane chatPopup = null;
    
    public ChatManager(UIState state, GamePanel gamePanel, Pane rootPane) {
        this.gamePanel = gamePanel;
        this.rootPane = rootPane;
    }
    
    /**
     * Ẩn chat input field
     */
    public void hideChatInput() {
        if (inputPane != null && rootPane != null && rootPane.getChildren().contains(inputPane)) {
            rootPane.getChildren().remove(inputPane);
            inputPane = null;
            chatInputContainer = null;
        }
    }
    
    /**
     * Hiển thị chat input field
     */
    public void showChatInput() {
        // Nếu đã có input field, ẩn nó trước
        hideChatInput();
        
        // Tạo chat input field - đặt ở góc trên cùng bên phải (gần chat icon)
        // Không có tail, chỉ có khung text đơn giản
        // Tăng chiều rộng để hợp lý hơn
        double bgWidth = 450.0;  // Tăng từ 400 lên 450
        inputPane = new Pane();
        inputPane.setLayoutX(1920 - bgWidth - 20);  // Cách lề phải 20px
        inputPane.setLayoutY(120);  // Dưới topRightIcons (20 + 85 + 15)
        
        // Tính toán chiều cao ban đầu
        double paddingX = 15.0;  // Padding trái/phải
        double paddingY = 15.0;  // Padding trên/dưới (tăng lên để đảm bảo không bị tràn)
        double baseContentHeight = 35.0;  // Chiều cao nội dung text (tăng lên một chút)
        double lineHeight = 22.0;  // Chiều cao mỗi dòng text
        double minContentHeight = baseContentHeight;
        double maxContentHeight = 180.0;
        
        // Background chính (white với rounded corners) - KHÔNG có tail
        // Chiều cao background = content height + padding trên + padding dưới + thêm một chút để đảm bảo
        double extraPadding = 5.0;  // Thêm padding dư để đảm bảo không bị tràn
        double initialBgHeight = baseContentHeight + (paddingY * 2) + extraPadding;
        Rectangle inputBg = new Rectangle(bgWidth, initialBgHeight);
        inputBg.setFill(Color.WHITE);
        inputBg.setStroke(Color.color(0.3, 0.3, 0.3));
        inputBg.setStrokeWidth(2);
        inputBg.setArcWidth(15);
        inputBg.setArcHeight(15);
        inputBg.setLayoutX(0);
        inputBg.setLayoutY(0);
        
        // TextArea để nhập chat - hoàn toàn không có border
        TextArea chatInput = new TextArea();
        double textAreaWidth = bgWidth - (paddingX * 2);  // Chiều rộng = background - padding 2 bên
        chatInput.setPrefWidth(textAreaWidth);
        chatInput.setPrefRowCount(1);
        chatInput.setWrapText(true);
        // Loại bỏ hoàn toàn mọi border, background và insets
        chatInput.setStyle(
            "-fx-font-size: 16px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-background-insets: 0; " +
            "-fx-border-color: transparent; " +
            "-fx-border-width: 0; " +
            "-fx-border-insets: 0; " +
            "-fx-focus-color: transparent; " +
            "-fx-faint-focus-color: transparent; " +
            "-fx-padding: 0; " +  // Không có padding trong CSS, dùng layoutX/Y
            "-fx-control-inner-background: transparent;"
        );
        chatInput.setPromptText("Nhập tin nhắn...");
        
        // Loại bỏ border và insets của các node con sau khi render
        Platform.runLater(() -> {
            javafx.scene.Node scrollPane = chatInput.lookup(".scroll-pane");
            if (scrollPane != null) {
                scrollPane.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-border-color: transparent; " +
                    "-fx-border-width: 0; " +
                    "-fx-background-insets: 0; " +
                    "-fx-padding: 0;"
                );
            }
            javafx.scene.Node viewport = chatInput.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            }
            javafx.scene.Node content = chatInput.lookup(".content");
            if (content != null) {
                content.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-border-color: transparent; " +
                    "-fx-border-width: 0; " +
                    "-fx-padding: " + paddingY + " " + paddingX + " " + paddingY + " " + paddingX + ";"
                );
            }
        });
        
        // Listener để tự động điều chỉnh chiều cao khi text thay đổi
        chatInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            
            // Tính số dòng
            String[] lines = newVal.split("\n");
            int lineCount = lines.length;
            for (String line : lines) {
                int charsPerLine = (int)(textAreaWidth / 8);  // Ước tính ký tự/dòng
                lineCount += Math.max(0, (line.length() - 1) / charsPerLine);
            }
            
            // Tính chiều cao nội dung mới (không bao gồm padding)
            double newContentHeight = baseContentHeight + (lineCount - 1) * lineHeight;
            newContentHeight = Math.max(minContentHeight, Math.min(maxContentHeight, newContentHeight));
            
            // Chiều cao TextArea = chiều cao nội dung
            chatInput.setPrefHeight(newContentHeight);
            
            // Chiều cao background = chiều cao nội dung + padding trên + padding dưới + extra padding
            // Đảm bảo luôn có đủ không gian ở dưới
            double newBgHeight = newContentHeight + (paddingY * 2) + extraPadding;
            inputBg.setHeight(newBgHeight);
        });
        
        // Đặt vị trí TextArea trong Pane - căn chỉnh chính xác với background
        // TextArea bắt đầu từ paddingY (có padding ở trên)
        chatInput.setLayoutX(paddingX);
        chatInput.setLayoutY(paddingY);
        chatInput.setPrefHeight(baseContentHeight);  // Chiều cao ban đầu = baseContentHeight
        
        // Xử lý khi nhấn Enter (Ctrl+Enter để xuống dòng)
        chatInput.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (e.isControlDown() || e.isMetaDown()) {
                    // Ctrl+Enter hoặc Cmd+Enter: xuống dòng
                    return;  // Cho phép xuống dòng
                } else {
                    // Enter đơn: gửi message
                    e.consume();
                    String message = chatInput.getText().trim();
                    if (!message.isEmpty()) {
                        // Gửi message qua network
                        try {
                            application.network.NetworkManager.getInstance().game().sendMessage(message);
                        } catch (Exception ex) {
                            System.err.println("[ChatManager] Error sending message: " + ex.getMessage());
                        }
                        
                        // Hiển thị popup cho chính mình (từ player, hiển thị ở avatar player)
                        showChatPopup(message, true);
                        chatInput.clear();
                        // Ẩn input field
                        hideChatInput();
                    }
                }
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                // Ẩn input field khi nhấn ESC
                hideChatInput();
            }
        });
        
        // Thêm background và TextArea vào Pane (không có tail)
        inputPane.getChildren().addAll(inputBg, chatInput);
        
        // Đảm bảo container có thể nhận mouse events
        inputPane.setPickOnBounds(true);
        inputPane.setMouseTransparent(false);
        
        // Lưu reference để có thể remove sau này
        chatInputContainer = new StackPane();
        chatInputContainer.getChildren().add(inputPane);
        
        // Thêm vào rootPane (Pane) để đảm bảo layoutX và layoutY hoạt động đúng
        if (rootPane != null) {
            rootPane.getChildren().add(inputPane);
            // Đưa input lên trên cùng để không bị che
            inputPane.toFront();
        }
        
        // Focus vào input field
        Platform.runLater(() -> chatInput.requestFocus());
    }
    
    /**
     * Hiển thị popup chat message
     * @param message Nội dung tin nhắn
     * @param isFromPlayer true nếu tin nhắn từ player (hiển thị ở avatar player), false nếu từ opponent (hiển thị ở avatar opponent)
     */
    public void showChatPopup(String message, boolean isFromPlayer) {
        // Nếu đã có popup, xóa nó trước
        if (chatPopup != null && rootPane != null && rootPane.getChildren().contains(chatPopup)) {
            rootPane.getChildren().remove(chatPopup);
        }
        
        // Xác định vị trí avatar dựa trên người gửi
        double avatarX, avatarY;
        double avatarWidth = 450;  // Width của profile container
        
        if (isFromPlayer) {
            // Player ở bottom-right
            avatarX = 1920 - 450;
            avatarY = 1080 - 200;
        } else {
            // Opponent ở top-left
            avatarX = 50;
            avatarY = 50;
        }
        
        // Tính toán kích thước popup dựa trên độ dài message
        double popupWidth = 300;  // Chiều rộng cố định
        double paddingX = 20;
        double paddingY = 15;
        double baseHeight = 40;
        double lineHeight = 22.0;  // Chiều cao mỗi dòng (font-size 16px + line spacing)
        
        // Tính số dòng dựa trên độ dài text
        String[] lines = message.split("\n");
        int lineCount = lines.length;
        for (String line : lines) {
            // Ước tính số dòng wrap dựa trên chiều rộng (300px - padding 40px = 260px)
            // Giả sử mỗi ký tự chiếm ~8px (font-size 16px)
            int charsPerLine = 32;  // 260 / 8 ≈ 32 ký tự/dòng
            lineCount += Math.max(0, (line.length() - 1) / charsPerLine);
        }
        
        // Tính chiều cao động
        double popupHeight = Math.max(baseHeight, paddingY * 2 + lineCount * lineHeight);
        double maxHeight = 300.0;  // Giới hạn tối đa
        popupHeight = Math.min(popupHeight, maxHeight);
        
        double popupX = avatarX + (avatarWidth - popupWidth) / 2;  // Căn giữa theo avatar
        // Đảm bảo không tràn ra ngoài màn hình bên phải
        if (popupX + popupWidth > 1920) {
            popupX = 1920 - popupWidth - 20;  // Cách lề phải 20px
        }
        // Đảm bảo không tràn ra ngoài màn hình bên trái
        if (popupX < 0) {
            popupX = 20;  // Cách lề trái 20px
        }
        double popupY;
        if (isFromPlayer) {
            // Player ở bottom-right: popup ở phía trên avatar
            popupY = avatarY - popupHeight - 20;  // Phía trên avatar, cách 20px
        } else {
            // Opponent ở top-left: popup ở phía dưới avatar
            popupY = avatarY + 120 + 20;  // Phía dưới avatar (height 120), cách 20px
        }
        
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
        
        // Tạo tail (đuôi nhọn) trỏ về phía avatar
        // Tính toán vị trí tail để trỏ vào giữa avatar
        double avatarCenterX = avatarX + avatarWidth / 2;
        // Vị trí tail tương đối với popupContainer
        double tailX = avatarCenterX - popupX;
        
        // Giới hạn tail trong phạm vi popup (tránh tail ra ngoài)
        tailX = Math.max(30, Math.min(popupWidth - 70, tailX));
        
        Polygon tail = new Polygon();
        if (isFromPlayer) {
            // Player ở bottom-right: tail trỏ xuống
            tail.getPoints().addAll(
                Double.valueOf(tailX), Double.valueOf(popupHeight),           // Điểm bắt đầu từ bubble (góc dưới)
                Double.valueOf(tailX - 15), Double.valueOf(popupHeight + 20), // Điểm nhọn trỏ xuống
                Double.valueOf(tailX + 15), Double.valueOf(popupHeight + 20)  // Điểm kết thúc
            );
        } else {
            // Opponent ở top-left: tail trỏ lên
            tail.getPoints().addAll(
                Double.valueOf(tailX), Double.valueOf(0.0),                      // Điểm bắt đầu từ bubble (góc trên)
                Double.valueOf(tailX - 15), Double.valueOf(-20.0),              // Điểm nhọn trỏ lên
                Double.valueOf(tailX + 15), Double.valueOf(-20.0)               // Điểm kết thúc
            );
        }
        tail.setFill(Color.WHITE);
        tail.setStroke(Color.color(0.6, 0.4, 0.3));
        tail.setStrokeWidth(2);
        
        // Label để hiển thị message - font mặc định, tự động wrap
        Label messageLabel = new Label(message);
        messageLabel.setStyle(
            "-fx-font-size: 16px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        messageLabel.setLayoutX(paddingX);  // Padding trái
        messageLabel.setLayoutY(paddingY);  // Padding trên
        messageLabel.setPrefWidth(popupWidth - paddingX * 2);  // Chiều rộng còn lại
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.TOP_LEFT);
        
        popupContainer.getChildren().addAll(popupBg, tail, messageLabel);
        
        // Lưu vào chatPopup - KHÔNG wrap trong StackPane, dùng trực tiếp Pane
        chatPopup = new StackPane();
        // Set vị trí cho chatPopup
        chatPopup.setLayoutX(popupX);
        chatPopup.setLayoutY(popupY);
        chatPopup.getChildren().add(popupContainer);
        
        // Đảm bảo popup có thể nhận mouse events (mặc dù không cần thiết vì chỉ hiển thị)
        chatPopup.setPickOnBounds(true);
        chatPopup.setMouseTransparent(true);  // Cho phép click xuyên qua popup
        
        // Thêm vào rootPane (Pane) để đảm bảo layoutX và layoutY hoạt động đúng
        if (rootPane != null) {
            rootPane.getChildren().add(chatPopup);
            // Đưa popup lên trên cùng để không bị che
            chatPopup.toFront();
        }
        
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
                    if (rootPane != null && rootPane.getChildren().contains(chatPopup)) {
                        rootPane.getChildren().remove(chatPopup);
                    }
                });
                fadeOut.play();
            })
        );
        hideTimer.play();
    }
    
    public boolean isChatInputVisible() {
        return inputPane != null && rootPane != null && rootPane.getChildren().contains(inputPane);
    }
}


