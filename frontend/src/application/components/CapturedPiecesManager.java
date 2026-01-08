package application.components;

import application.state.UIState;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Insets;

/**
 * Manager để quản lý hiển thị các quân cờ đã bị ăn
 */
public class CapturedPiecesManager {
    
    private final UIState state;
    private final IGamePanel gamePanel;
    
    // Track captured pieces for each player
    private final java.util.Map<String, Integer> redCapturedPieces = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> blackCapturedPieces = new java.util.HashMap<>();
    
    // UI components for captured pieces display
    private VBox topLeftCapturedPieces = null;
    private VBox bottomRightCapturedPieces = null;
    
    public CapturedPiecesManager(UIState state, IGamePanel gamePanel) {
        this.state = state;
        this.gamePanel = gamePanel;
    }
    
    /**
     * Tạo UI để hiển thị các quân cờ đã ăn được
     * @param isTopLeft true nếu là người chơi top-left (red), false nếu là bottom-right (black)
     */
    public VBox createCapturedPiecesDisplay(boolean isTopLeft) {
        VBox container = new VBox(5);
        container.setAlignment(Pos.TOP_LEFT);
        container.setPrefWidth(450);
        container.setPrefHeight(100);
        
        // HBox để chứa các icon quân cờ đã bị ăn
        HBox piecesContainer = new HBox(8);
        piecesContainer.setAlignment(Pos.CENTER_LEFT);
        piecesContainer.setPrefWidth(450);
        piecesContainer.setPrefHeight(80);
        
        // Lưu reference để có thể cập nhật sau
        container.setUserData(piecesContainer);
        
        container.getChildren().add(piecesContainer);
        
        // Lưu reference
        if (isTopLeft) {
            this.topLeftCapturedPieces = container;
        } else {
            this.bottomRightCapturedPieces = container;
        }
        
        return container;
    }
    
    /**
     * Thêm quân cờ đã bị ăn vào danh sách và cập nhật UI
     * @param capturedPieceColor Màu của quân cờ bị ăn ("red" hoặc "black")
     * @param pieceType Loại quân cờ (King, Advisor, Elephant, Horse, Rook, Cannon, Pawn)
     */
    public void addCapturedPiece(String capturedPieceColor, String pieceType) {
        // Xác định người chơi nào đã ăn (người chơi đối lập với màu quân cờ bị ăn)
        boolean isRedPlayer = capturedPieceColor.equals("black");  // Nếu ăn quân black thì là red player
        
        // Cập nhật map
        java.util.Map<String, Integer> capturedMap = isRedPlayer ? redCapturedPieces : blackCapturedPieces;
        capturedMap.put(pieceType, capturedMap.getOrDefault(pieceType, 0) + 1);
        
        // Cập nhật UI
        VBox displayContainer = isRedPlayer ? topLeftCapturedPieces : bottomRightCapturedPieces;
        if (displayContainer != null) {
            HBox piecesContainer = (HBox) displayContainer.getUserData();
            if (piecesContainer != null) {
                Platform.runLater(() -> {
                    updateCapturedPiecesDisplay(piecesContainer, capturedMap, capturedPieceColor);
                });
            }
        }
    }
    
    /**
     * Cập nhật hiển thị các quân cờ đã bị ăn
     */
    private void updateCapturedPiecesDisplay(HBox container, java.util.Map<String, Integer> capturedMap, String pieceColor) {
        container.getChildren().clear();
        
        // Thứ tự hiển thị các loại quân cờ (theo giá trị)
        String[] pieceOrder = {"King", "Advisor", "Elephant", "Horse", "Rook", "Cannon", "Pawn"};
        
        for (String pieceType : pieceOrder) {
            int count = capturedMap.getOrDefault(pieceType, 0);
            if (count > 0) {
                // Chỉ tạo 1 icon cho mỗi loại quân cờ, hiển thị số lượng nếu > 1
                StackPane pieceIcon = createCapturedPieceIcon(pieceType, pieceColor, count);
                container.getChildren().add(pieceIcon);
            }
        }
    }
    
    /**
     * Tạo icon cho quân cờ đã bị ăn
     */
    private StackPane createCapturedPieceIcon(String pieceType, String pieceColor, int count) {
        StackPane iconContainer = new StackPane();
        iconContainer.setPrefWidth(50);
        iconContainer.setPrefHeight(50);
        
        // Vòng tròn với màu theo màu quân cờ bị ăn
        Circle circle = new Circle(25);
        if (pieceColor.equals("red")) {
            circle.setFill(Color.web("#DC143C"));  // Màu đỏ
        } else {
            circle.setFill(Color.web("#1C1C1C"));  // Màu đen
        }
        circle.setStroke(Color.WHITE);
        circle.setStrokeWidth(2);
        
        // Text hiển thị chữ quân cờ (chữ Hán)
        Label pieceLabel = new Label();
        pieceLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: white; -fx-background-color: transparent;");
        
        // Map piece type to Chinese character
        java.util.Map<String, String> pieceChars = new java.util.HashMap<>();
        pieceChars.put("King", pieceColor.equals("red") ? "帥" : "將");
        pieceChars.put("Advisor", pieceColor.equals("red") ? "仕" : "士");
        pieceChars.put("Elephant", pieceColor.equals("red") ? "相" : "象");
        pieceChars.put("Horse", pieceColor.equals("red") ? "傌" : "馬");
        pieceChars.put("Rook", pieceColor.equals("red") ? "俥" : "車");
        pieceChars.put("Cannon", pieceColor.equals("red") ? "炮" : "砲");
        pieceChars.put("Pawn", pieceColor.equals("red") ? "兵" : "卒");
        
        pieceLabel.setText(pieceChars.getOrDefault(pieceType, "?"));
        
        // Label hiển thị số lượng (luôn hiển thị nếu > 1) - đặt ở góc dưới bên phải
        Label countLabel = null;
        if (count > 1) {
            countLabel = new Label(String.valueOf(count));
            // Màu chữ cùng với màu quân cờ bị ăn
            String textColor = pieceColor.equals("red") ? "#DC143C" : "#1C1C1C";
            countLabel.setStyle(String.format("-fx-font-family: 'Kolker Brush'; -fx-font-size: 20px; -fx-text-fill: %s; -fx-background-color: transparent; -fx-font-weight: 900;", textColor));
            // Đặt ở góc dưới bên phải của icon
            StackPane.setAlignment(countLabel, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(countLabel, new Insets(0, 3, 3, 0));
        }
        
        iconContainer.getChildren().addAll(circle, pieceLabel);
        if (countLabel != null) {
            iconContainer.getChildren().add(countLabel);
        }
        
        return iconContainer;
    }
    
    /**
     * Reset danh sách quân cờ đã bị ăn (khi bắt đầu game mới)
     */
    public void resetCapturedPieces() {
        redCapturedPieces.clear();
        blackCapturedPieces.clear();
        
        if (topLeftCapturedPieces != null) {
            HBox piecesContainer = (HBox) topLeftCapturedPieces.getUserData();
            if (piecesContainer != null) {
                piecesContainer.getChildren().clear();
            }
        }
        
        if (bottomRightCapturedPieces != null) {
            HBox piecesContainer = (HBox) bottomRightCapturedPieces.getUserData();
            if (piecesContainer != null) {
                piecesContainer.getChildren().clear();
            }
        }
    }
    
    public VBox getTopLeftCapturedPieces() {
        return topLeftCapturedPieces;
    }
    
    public VBox getBottomRightCapturedPieces() {
        return bottomRightCapturedPieces;
    }
}


