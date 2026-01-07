package application.components;

import application.state.UIState;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;

/**
 * Manager để quản lý lịch sử nước đi
 */
public class MoveHistoryManager {
    
    private final GamePanel gamePanel;
    
    private StackPane movePanel = null;
    private ScrollPane moveHistoryScrollPane = null;
    private VBox moveHistoryContainer = null;
    private final java.util.List<String> moveHistory = new java.util.ArrayList<>();
    
    public MoveHistoryManager(UIState state, GamePanel gamePanel, Pane rootPane) {
        this.gamePanel = gamePanel;
    }
    
    /**
     * Hiển thị panel lịch sử nước đi
     */
    public void showMovePanel() {
        // Nếu đã có panel, xóa nó trước
        if (movePanel != null && gamePanel != null && gamePanel.getChildren().contains(movePanel)) {
            gamePanel.getChildren().remove(movePanel);
        }
        
        // Tạo panel "Move" bên phải - kích thước 450x755
        movePanel = new StackPane();
        movePanel.setLayoutX(1920 - 450);  // Bên phải, rộng 450px
        movePanel.setLayoutY((1080 - 755) / 2 - 70);  // Dịch lên 100px so với căn giữa
        movePanel.setPrefSize(450, 755);  // Kích thước 450x755
        
        // Background cho panel (rounded rectangle màu xám nhạt)
        Rectangle panelBg = new Rectangle(450, 755);
        panelBg.setFill(Color.color(0.9, 0.9, 0.9, 0.95));  // Màu xám nhạt, hơi trong suốt
        panelBg.setArcWidth(40);  // Tăng từ 20 lên 40 để bo góc thêm
        panelBg.setArcHeight(40);  // Tăng từ 20 lên 40 để bo góc thêm
        panelBg.setLayoutX(0);
        panelBg.setLayoutY(0);
        
        // Thêm shadow cho panel
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0, 0, 0, 0.3));  // Màu đen với độ trong suốt 30%
        shadow.setRadius(15);  // Độ mờ của shadow
        shadow.setOffsetX(5);  // Độ lệch theo chiều ngang
        shadow.setOffsetY(5);  // Độ lệch theo chiều dọc
        panelBg.setEffect(shadow);
        
        // Header container
        HBox header = new HBox();
        header.setPrefSize(450, 80);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 10 20 20 20;");  // Giảm padding top từ 20 xuống 10
        
        // Container cho Move label và gạch ngang
        VBox moveLabelContainer = new VBox(5);  // Khoảng cách 5px giữa label và gạch
        moveLabelContainer.setAlignment(Pos.CENTER_LEFT);
        
        // Label "Move" (font calligraphic)
        Label moveLabel = new Label("Move");
        moveLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 80px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Gạch ngang dưới chữ Move
        Rectangle dividerLine = new Rectangle(200, 3);  // Chiều rộng 200px, chiều cao 3px
        dividerLine.setFill(Color.color(0.3, 0.3, 0.3));  // Màu xám đậm
        dividerLine.setArcWidth(2);
        dividerLine.setArcHeight(2);
        
        moveLabelContainer.getChildren().addAll(moveLabel, dividerLine);
        moveLabelContainer.setTranslateY(-35);  // Dùng setTranslateY để dịch lên (hoạt động với layout containers)
        
        // Thêm Move label container vào header
        header.getChildren().add(moveLabelContainer);
        
        // Container chính cho panel
        VBox panelContainer = new VBox();
        panelContainer.setPrefSize(450, 755);
        panelContainer.setStyle("-fx-background-color: rgba(230, 230, 230, 0.95);");  // Cùng màu với panel background
        panelContainer.getChildren().add(header);
        
        // ScrollPane để hiển thị lịch sử nước đi
        moveHistoryScrollPane = new ScrollPane();
        moveHistoryScrollPane.setPrefSize(450, 675);  // Chiều cao = 755 - 80 (header)
        moveHistoryScrollPane.setStyle(
            "-fx-background: rgba(230, 230, 230, 0.95); " +  // Dùng -fx-background thay vì -fx-background-color
            "-fx-background-color: rgba(230, 230, 230, 0.95); " +
            "-fx-border-color: transparent;"
        );
        moveHistoryScrollPane.setFitToWidth(true);
        moveHistoryScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        moveHistoryScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        // Container cho danh sách nước đi
        moveHistoryContainer = new VBox(10);
        moveHistoryContainer.setPrefWidth(430);  // Nhỏ hơn scrollPane một chút để tránh scroll ngang
        moveHistoryContainer.setStyle(
            "-fx-padding: 20; " +
            "-fx-background-color: transparent;"  // Trong suốt để hiển thị màu của ScrollPane
        );
        moveHistoryContainer.setAlignment(Pos.TOP_LEFT);
        
        // Hiển thị các nước đi đã lưu
        for (String move : moveHistory) {
            Label moveHistoryLabel = createMoveLabel(move);
            moveHistoryContainer.getChildren().add(moveHistoryLabel);
        }
        
        moveHistoryScrollPane.setContent(moveHistoryContainer);
        
        // Thêm ScrollPane vào panel container
        panelContainer.getChildren().add(moveHistoryScrollPane);
        
        movePanel.getChildren().addAll(panelBg, panelContainer);
        
        // Đảm bảo panel có thể nhận mouse events
        movePanel.setPickOnBounds(true);
        movePanel.setMouseTransparent(false);
        
        // Thêm vào GamePanel (StackPane) để đảm bảo panel ở trên cùng
        gamePanel.getChildren().add(movePanel);
        
        // Đưa panel lên trên cùng để không bị che
        movePanel.toFront();
        
        // Fade in animation
        movePanel.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), movePanel);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    /**
     * Ẩn panel lịch sử nước đi
     */
    public void hideMovePanel() {
        if (movePanel != null && gamePanel != null && gamePanel.getChildren().contains(movePanel)) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), movePanel);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (gamePanel.getChildren().contains(movePanel)) {
                    gamePanel.getChildren().remove(movePanel);
                }
            });
            fadeOut.play();
        }
    }
    
    /**
     * Tạo label cho mỗi nước đi
     */
    private Label createMoveLabel(String moveText) {
        Label moveLabel = new Label(moveText);
        
        // Xác định màu chữ dựa trên moveText (red hoặc black)
        String textColor = "black"; // Mặc định
        if (moveText.contains("red:")) {
            textColor = "#DC143C"; // Màu đỏ (Crimson)
        } else if (moveText.contains("black:")) {
            textColor = "#000000"; // Màu đen
        }
        
        moveLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 35px; " +
            "-fx-text-fill: " + textColor + "; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        moveLabel.setPrefWidth(410);
        moveLabel.setAlignment(Pos.CENTER_LEFT);
        return moveLabel;
    }
    
    /**
     * Thêm nước đi mới vào lịch sử
     */
    public void addMove(String color, String pieceType, int fromRow, int fromCol, int toRow, int toCol) {
        addMove(color, pieceType, fromRow, fromCol, toRow, toCol, "");
    }
    
    /**
     * Thêm nước đi mới vào lịch sử (với thông tin quân cờ bị ăn)
     */
    public void addMove(String color, String pieceType, int fromRow, int fromCol, int toRow, int toCol, String capturedInfo) {
        String moveText = String.format("%d. %s: %s (%d,%d) -> (%d,%d)%s", 
            moveHistory.size() + 1, 
            color, 
            pieceType, 
            fromRow, fromCol, 
            toRow, toCol,
            capturedInfo
        );
        moveHistory.add(moveText);
        
        // Cập nhật UI nếu panel đang hiển thị
        if (moveHistoryContainer != null) {
            Label newMoveLabel = createMoveLabel(moveText);
            moveHistoryContainer.getChildren().add(newMoveLabel);
            
            // Scroll xuống nước đi mới nhất
            Platform.runLater(() -> {
                if (moveHistoryScrollPane != null) {
                    moveHistoryScrollPane.setVvalue(1.0);
                }
            });
        }
    }
    
    /**
     * Xóa lịch sử nước đi (khi bắt đầu game mới)
     */
    public void clearMoveHistory() {
        moveHistory.clear();
        if (moveHistoryContainer != null) {
            moveHistoryContainer.getChildren().clear();
        }
    }
    
    public boolean isMovePanelVisible() {
        return movePanel != null && gamePanel != null && gamePanel.getChildren().contains(movePanel);
    }
}

