package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;

/**
 * Manager để quản lý hiển thị các quân cờ đã bị ăn
 */
public class CapturedPiecesManager {
    
    private final UIState state;
    private final IGamePanel gamePanel;
    
    // Track captured pieces grouped by WHO captured (player vs opponent)
    // This giúp bố trí đúng vị trí theo avatar của từng người chơi
    private final java.util.Map<String, Integer> playerCapturedPieces = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> opponentCapturedPieces = new java.util.HashMap<>();
    
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
        // Xác định màu của NGƯỜI CHƠI hiện tại
        String playerColor = state.isPlayerRed() ? "red" : "black";
        String opponentColor = playerColor.equals("red") ? "black" : "red";

        // Quân bị ăn màu gì? -> Người ăn là bên đối lập
        // Nếu quân bị ăn màu opponent -> Player là người ăn -> hiển thị cạnh avatar Player (bottom-right)
        // Nếu quân bị ăn màu player -> Opponent là người ăn -> hiển thị cạnh avatar Opponent (top-left)
        boolean capturedByPlayer = capturedPieceColor.equals(opponentColor);

        java.util.Map<String, Integer> targetMap = capturedByPlayer ? playerCapturedPieces : opponentCapturedPieces;
        targetMap.put(pieceType, targetMap.getOrDefault(pieceType, 0) + 1);

        System.out.println("[CapturedPiecesManager] addCapturedPiece: pieceType=" + pieceType + 
            ", capturedPieceColor=" + capturedPieceColor + ", playerColor=" + playerColor + 
            ", capturedByPlayer=" + capturedByPlayer + ", targetMapSize=" + targetMap.size());

        // Cập nhật UI: 
        // Trong GamePanel: playerCapturedPieces = createCapturedPiecesDisplay(true) = topLeftCapturedPieces (Manager)
        //                  opponentCapturedPieces = createCapturedPiecesDisplay(false) = bottomRightCapturedPieces (Manager)
        // Nhưng GamePanel đặt: playerCapturedPieces ở bottom-right, opponentCapturedPieces ở top-left
        // Vậy: topLeftCapturedPieces (Manager) = bottom-right (vị trí player)
        //      bottomRightCapturedPieces (Manager) = top-left (vị trí opponent)
        // => Khi player ăn quân (capturedByPlayer=true) -> hiển thị ở topLeftCapturedPieces
        VBox displayContainer = capturedByPlayer ? topLeftCapturedPieces : bottomRightCapturedPieces;
        // Màu của quân trong map: playerCapturedPieces chứa quân màu opponent, opponentCapturedPieces chứa quân màu player
        String mapPieceColor = capturedByPlayer ? opponentColor : playerColor;
        
        if (displayContainer != null) {
            HBox piecesContainer = (HBox) displayContainer.getUserData();
            if (piecesContainer != null) {
                Platform.runLater(() -> {
                    updateCapturedPiecesDisplay(piecesContainer, targetMap, mapPieceColor);
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
     * Tạo icon cho quân cờ đã bị ăn - sử dụng hình ảnh từ assets/pieces
     */
    private StackPane createCapturedPieceIcon(String pieceType, String pieceColor, int count) {
        StackPane iconContainer = new StackPane();
        iconContainer.setPrefWidth(50);
        iconContainer.setPrefHeight(50);
        
        // Load hình ảnh quân cờ từ assets/pieces/{color}/Chinese-{PieceType}-{Color}.png
        String colorCapitalized = pieceColor.substring(0, 1).toUpperCase() + pieceColor.substring(1).toLowerCase();
        String imagePath = "pieces/" + pieceColor.toLowerCase() + "/Chinese-" + pieceType + "-" + colorCapitalized + ".png";
        
        try {
            ImageView pieceImage = new ImageView(AssetHelper.image(imagePath));
            pieceImage.setFitWidth(45);
            pieceImage.setFitHeight(45);
            pieceImage.setPreserveRatio(true);
            iconContainer.getChildren().add(pieceImage);
        } catch (Exception e) {
            System.err.println("[CapturedPiecesManager] Failed to load image: " + imagePath + ", error: " + e.getMessage());
            // Fallback: tạo placeholder nếu không load được hình
            Label fallbackLabel = new Label(pieceType.substring(0, 1));
            fallbackLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: " + (pieceColor.equals("red") ? "#DC143C" : "#1C1C1C") + ";");
            iconContainer.getChildren().add(fallbackLabel);
        }
        
        // Label hiển thị số lượng (luôn hiển thị nếu > 1) - đặt ở góc dưới bên phải
        if (count > 1) {
            Label countLabel = new Label("x" + count);
            // Màu chữ cùng với màu quân cờ bị ăn
            String textColor = pieceColor.equals("red") ? "#DC143C" : "#1C1C1C";
            countLabel.setStyle(String.format("-fx-font-size: 14px; -fx-text-fill: %s; -fx-background-color: rgba(255,255,255,0.8); -fx-padding: 1 3; -fx-font-weight: bold;", textColor));
            // Đặt ở góc dưới bên phải của icon
            StackPane.setAlignment(countLabel, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(countLabel, new Insets(0, 0, 0, 0));
            iconContainer.getChildren().add(countLabel);
        }
        
        return iconContainer;
    }
    
    /**
     * Reset danh sách quân cờ đã bị ăn (khi bắt đầu game mới)
     */
    public void resetCapturedPieces() {
        playerCapturedPieces.clear();
        opponentCapturedPieces.clear();
        
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


