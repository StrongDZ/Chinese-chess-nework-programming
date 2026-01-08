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
    
    private final IGamePanel gamePanel;
    private final Pane rootPane;  // Root pane để thêm move panel với absolute positioning
    
    private StackPane movePanel = null;
    private ScrollPane moveHistoryScrollPane = null;
    private VBox moveHistoryContainer = null;
    private final java.util.List<String> moveHistory = new java.util.ArrayList<>();
    
    // Lưu reference đến các Label để có thể highlight
    private final java.util.List<Label> moveLabels = new java.util.ArrayList<>();
    private int currentHighlightedIndex = -1;  // Index của move đang được highlight (-1 = không có)
    
    // Callback để tạo replay control buttons
    private java.util.function.Supplier<javafx.scene.Node> replayControlsSupplier = null;
    
    public MoveHistoryManager(UIState state, IGamePanel gamePanel, Pane rootPane) {
        this.gamePanel = gamePanel;
        this.rootPane = rootPane;
    }
    
    /**
     * Set supplier để tạo replay control buttons
     * CHỈ được gọi từ ReplayPanel, KHÔNG được gọi từ GamePanel
     * Nếu supplier là null (như trong GamePanel), các nút sẽ không xuất hiện
     */
    public void setReplayControlsSupplier(java.util.function.Supplier<javafx.scene.Node> supplier) {
        this.replayControlsSupplier = supplier;
    }
    
    /**
     * Hiển thị panel lịch sử nước đi
     */
    public void showMovePanel() {
        // Nếu đã có panel, xóa nó trước
        if (movePanel != null) {
            if (rootPane != null && rootPane.getChildren().contains(movePanel)) {
                rootPane.getChildren().remove(movePanel);
            } else if (gamePanel != null && gamePanel.getChildren().contains(movePanel)) {
                gamePanel.getChildren().remove(movePanel);
            }
        }
        
        // Tạo panel "Move" bên phải - kích thước 450x755
        movePanel = new StackPane();
        movePanel.setLayoutX(1920 - 450 - 25);  // Bên phải, rộng 450px, dịch sang trái 50px
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
        // Chiều cao sẽ được điều chỉnh nếu có replay controls
        int scrollPaneHeight = replayControlsSupplier != null ? 575 : 675;  // Giảm 100px nếu có buttons
        moveHistoryScrollPane = new ScrollPane();
        moveHistoryScrollPane.setPrefSize(450, scrollPaneHeight);  // Chiều cao = 755 - 80 (header) - 100 (buttons nếu có)
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
        moveLabels.clear();  // Clear list khi tạo lại panel
        for (String move : moveHistory) {
            Label moveHistoryLabel = createMoveLabel(move);
            moveLabels.add(moveHistoryLabel);
            moveHistoryContainer.getChildren().add(moveHistoryLabel);
        }
        
        // Highlight move hiện tại nếu có
        if (currentHighlightedIndex >= 0 && currentHighlightedIndex < moveLabels.size()) {
            highlightMove(currentHighlightedIndex);
        }
        
        moveHistoryScrollPane.setContent(moveHistoryContainer);
        
        // Thêm ScrollPane vào panel container
        panelContainer.getChildren().add(moveHistoryScrollPane);
        
        // Thêm replay control buttons CHỈ khi có supplier (chỉ trong ReplayPanel, không có trong GamePanel)
        // Trong GamePanel, replayControlsSupplier sẽ là null, nên các nút sẽ không xuất hiện
        if (replayControlsSupplier != null) {
            javafx.scene.Node replayControls = replayControlsSupplier.get();
            if (replayControls != null) {
                // Đặt buttons ở dưới cùng của panel, căn giữa
                javafx.geometry.Insets margin = new javafx.geometry.Insets(10, 0, 10, 0);
                if (replayControls instanceof javafx.scene.layout.HBox) {
                    ((javafx.scene.layout.HBox) replayControls).setAlignment(javafx.geometry.Pos.CENTER);
                }
                VBox.setMargin(replayControls, margin);
                panelContainer.getChildren().add(replayControls);
            }
        }
        
        movePanel.getChildren().addAll(panelBg, panelContainer);
        
        // Đảm bảo panel có thể nhận mouse events
        movePanel.setPickOnBounds(true);
        movePanel.setMouseTransparent(false);
        
        // Thêm vào rootPane (Pane) để sử dụng absolute positioning với setLayoutX/setLayoutY
        if (rootPane != null) {
            rootPane.getChildren().add(movePanel);
            // Đưa panel lên trên cùng để không bị che
            movePanel.toFront();
        } else {
            // Fallback: thêm vào gamePanel nếu rootPane không có
            gamePanel.getChildren().add(movePanel);
            movePanel.toFront();
        }
        
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
        if (movePanel != null) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), movePanel);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane != null && rootPane.getChildren().contains(movePanel)) {
                    rootPane.getChildren().remove(movePanel);
                } else if (gamePanel != null && gamePanel.getChildren().contains(movePanel)) {
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
     * Highlight nước đi tại index chỉ định (dùng trong replay mode)
     * @param moveIndex Index của move cần highlight (0-based, -1 để bỏ highlight)
     */
    public void highlightMove(int moveIndex) {
        Platform.runLater(() -> {
            // Bỏ highlight tất cả moves trước
            for (int i = 0; i < moveLabels.size(); i++) {
                Label label = moveLabels.get(i);
                if (label != null) {
                    // Reset về style mặc định
                    String moveText = label.getText();
                    String textColor = "black";
                    if (moveText.contains("red:")) {
                        textColor = "#DC143C";
                    } else if (moveText.contains("black:")) {
                        textColor = "#000000";
                    }
                    
                    label.setStyle(
                        "-fx-font-family: 'Kolker Brush'; " +
                        "-fx-font-size: 35px; " +
                        "-fx-text-fill: " + textColor + "; " +
                        "-fx-background-color: transparent; " +
                        "-fx-wrap-text: true;"
                    );
                }
            }
            
            // Highlight move tại index
            if (moveIndex >= 0 && moveIndex < moveLabels.size()) {
                Label label = moveLabels.get(moveIndex);
                if (label != null) {
                    String moveText = label.getText();
                    String textColor = "black";
                    if (moveText.contains("red:")) {
                        textColor = "#DC143C";
                    } else if (moveText.contains("black:")) {
                        textColor = "#000000";
                    }
                    
                    // Highlight với background màu vàng nhạt và border
                    label.setStyle(
                        "-fx-font-family: 'Kolker Brush'; " +
                        "-fx-font-size: 35px; " +
                        "-fx-text-fill: " + textColor + "; " +
                        "-fx-background-color: rgba(255, 255, 0, 0.3); " +  // Màu vàng nhạt
                        "-fx-background-radius: 8px; " +
                        "-fx-border-color: rgba(255, 200, 0, 0.8); " +  // Border vàng đậm hơn
                        "-fx-border-width: 2px; " +
                        "-fx-border-radius: 8px; " +
                        "-fx-padding: 5px 10px; " +
                        "-fx-wrap-text: true;"
                    );
                    
                    // Scroll đến move được highlight
                    if (moveHistoryScrollPane != null && moveHistoryContainer != null) {
                        // Tính toán vị trí của label trong container
                        double labelY = label.getLayoutY();
                        double containerHeight = moveHistoryContainer.getHeight();
                        double scrollPaneHeight = moveHistoryScrollPane.getHeight();
                        
                        if (containerHeight > scrollPaneHeight) {
                            // Normalize vị trí (0.0 = top, 1.0 = bottom)
                            double vvalue = labelY / (containerHeight - scrollPaneHeight);
                            vvalue = Math.max(0.0, Math.min(1.0, vvalue));
                            moveHistoryScrollPane.setVvalue(vvalue);
                        }
                    }
                }
            }
            
            currentHighlightedIndex = moveIndex;
        });
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
            moveLabels.add(newMoveLabel);  // Thêm vào list để có thể highlight sau
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
        moveLabels.clear();
        currentHighlightedIndex = -1;
        if (moveHistoryContainer != null) {
            moveHistoryContainer.getChildren().clear();
        }
    }
    
    public boolean isMovePanelVisible() {
        if (movePanel == null) return false;
        if (rootPane != null) {
            return rootPane.getChildren().contains(movePanel);
        }
        return gamePanel != null && gamePanel.getChildren().contains(movePanel);
    }
    
    public StackPane getMovePanel() {
        return movePanel;
    }
}

