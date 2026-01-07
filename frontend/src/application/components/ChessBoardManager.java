package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Manager để quản lý bàn cờ và quân cờ
 */
public class ChessBoardManager {
    
    private final UIState state;
    private final GamePanel gamePanel;
    
    // Callbacks để giao tiếp với các manager khác
    private java.util.function.BiConsumer<String, String> onPieceCaptured = null;  // (color, pieceType)
    private java.util.function.Consumer<String> onMoveAdded = null;  // (moveText)
    private java.util.function.Consumer<String> onMoveAddedWithDetails = null;  // (color, pieceType, fromRow, fromCol, toRow, toCol, capturedInfo)
    private Runnable onTurnChanged = null;
    
    // Helper class để lưu thông tin quân cờ
    public static class PieceInfo {
        public String color;
        public String pieceType;
        public String imagePath;
        
        public PieceInfo(String color, String pieceType, String imagePath) {
            this.color = color;
            this.pieceType = pieceType;
            this.imagePath = imagePath;
        }
    }
    
    public ChessBoardManager(UIState state, GamePanel gamePanel) {
        this.state = state;
        this.gamePanel = gamePanel;
    }
    
    /**
     * Set callback khi quân cờ bị ăn
     */
    public void setOnPieceCaptured(java.util.function.BiConsumer<String, String> callback) {
        this.onPieceCaptured = callback;
    }
    
    /**
     * Set callback khi thêm nước đi
     */
    public void setOnMoveAdded(java.util.function.Consumer<String> callback) {
        this.onMoveAdded = callback;
    }
    
    /**
     * Set callback khi thêm nước đi với chi tiết
     */
    public void setOnMoveAddedWithDetails(java.util.function.Consumer<String> callback) {
        this.onMoveAddedWithDetails = callback;
    }
    
    /**
     * Set callback khi đổi lượt
     */
    public void setOnTurnChanged(Runnable callback) {
        this.onTurnChanged = callback;
    }
    
    /**
     * Tạo container chứa các quân cờ
     */
    public Pane createChessPieces() {
        Pane container = new Pane();
        container.setPrefSize(923, 923);
        container.setVisible(true);
        container.setManaged(true);
        container.setMouseTransparent(false);
        container.setPickOnBounds(true);
        
        // Kích thước bàn cờ: 9 cột x 10 hàng (giao điểm)
        // Bàn cờ có 9 giao điểm theo chiều ngang và 10 giao điểm theo chiều dọc
        // Quân cờ được đặt tại các giao điểm, tâm quân cờ trùng với giao điểm
        // Nghiên cứu kỹ: các giao điểm thường được phân bố đều từ một điểm bắt đầu
        double boardSize = 923.0;
        // Vị trí giao điểm đầu tiên và cuối cùng (có thể điều chỉnh để khớp với ảnh thực tế)
        double startX = 45.0;  // Vị trí giao điểm đầu tiên theo chiều ngang
        double startY = 45.0;  // Vị trí giao điểm đầu tiên theo chiều dọc
        double endX = boardSize - 45.0;  // Vị trí giao điểm cuối cùng theo chiều ngang
        double endY = boardSize - 45.0;  // Vị trí giao điểm cuối cùng theo chiều dọc
        
        // Khoảng cách giữa các giao điểm
        final double intersectionSpacingX = (endX - startX) / 8.0;  // 8 khoảng cách cho 9 giao điểm
        final double intersectionSpacingY = (endY - startY) / 9.0;  // 9 khoảng cách cho 10 giao điểm
        
        // Kích thước quân cờ
        final double pieceWidth = intersectionSpacingX * 0.8;
        final double pieceHeight = intersectionSpacingY * 0.8;
        
        // Kích thước cell cho clickLayer
        final double cellWidth = boardSize / 9.0;
        final double cellHeight = boardSize / 10.0;
        
        // Pane để chứa các highlight rectangles
        // Đặt ở cuối để nằm trên các quân cờ
        final Pane highlightLayer = new Pane();
        highlightLayer.setPrefSize(923, 923);
        highlightLayer.setMouseTransparent(true); // Không chặn mouse events
        
        // Pane invisible để detect click vào các ô trên bàn cờ
        Pane clickLayer = new Pane();
        clickLayer.setPrefSize(923, 923);
        clickLayer.setStyle("-fx-background-color: transparent;");
        // Tạo các ô invisible để detect click
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 9; col++) {
                Rectangle cell = new Rectangle(cellWidth, cellHeight);
                cell.setFill(Color.TRANSPARENT);
                cell.setLayoutX(col * cellWidth);
                cell.setLayoutY(row * cellHeight);
                cell.setUserData(new int[]{row, col}); // Lưu row, col vào userData
                clickLayer.getChildren().add(cell);
            }
        }
        
        // Lưu quân cờ đang được chọn và highlight shapes (có thể là Rectangle hoặc Circle)
        final ImageView[] selectedPiece = new ImageView[1];
        final java.util.List<javafx.scene.Node>[] highlightRects = new java.util.List[]{new java.util.ArrayList<>()};
        // Lưu danh sách các ô hợp lệ để validate khi thả quân cờ
        final java.util.List<int[]>[] validMovesList = new java.util.List[]{new java.util.ArrayList<>()};
        
        // Method để xóa tất cả highlights
        java.util.function.Consumer<Void> clearHighlights = (v) -> {
            highlightLayer.getChildren().removeAll(highlightRects[0]);
            highlightRects[0].clear();
            validMovesList[0].clear();
            if (selectedPiece[0] != null) {
                // Khôi phục shadow ban đầu cho quân cờ đã chọn
                DropShadow normalShadow = new DropShadow();
                normalShadow.setColor(Color.color(0, 0, 0, 0.5));
                normalShadow.setRadius(8);
                normalShadow.setOffsetX(3);
                normalShadow.setOffsetY(3);
                selectedPiece[0].setEffect(normalShadow);
                selectedPiece[0] = null;
            }
        };
        
        // Method để highlight các ô hợp lệ
        java.util.function.BiConsumer<Integer, Integer> highlightValidMoves = (row, col) -> {
            // Xóa highlights cũ
            clearHighlights.accept(null);
            
            // Tạo board state từ các quân cờ hiện tại
            char[][] board = new char[10][9];
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    board[i][j] = ' ';
                }
            }
            
            // Lấy thông tin quân cờ từ userData để xác định loại quân
            ImageView piece = null;
            for (javafx.scene.Node node : container.getChildren()) {
                if (node instanceof ImageView) {
                    ImageView imgView = (ImageView) node;
                    if (imgView.getUserData() instanceof PieceInfo) {
                        int pieceRow = (int) Math.round(imgView.getLayoutY() / cellHeight);
                        int pieceCol = (int) Math.round(imgView.getLayoutX() / cellWidth);
                        pieceRow = Math.max(0, Math.min(9, pieceRow));
                        pieceCol = Math.max(0, Math.min(8, pieceCol));
                        
                        PieceInfo info = (PieceInfo) imgView.getUserData();
                        // Convert piece type to character
                        char pieceChar = getPieceChar(info.pieceType, info.color.equals("red"));
                        board[pieceRow][pieceCol] = pieceChar;
                        
                        // Tìm quân cờ tại vị trí được click
                        if (pieceRow == row && pieceCol == col) {
                            piece = imgView;
                        }
                    }
                }
            }
            
            if (piece == null) {
                return;
            }
            
            // Kiểm tra lượt: chỉ cho phép chọn quân cờ của lượt hiện tại
            PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
            String currentTurn = gamePanel.getCurrentTurn();
            if (pieceInfo == null || !pieceInfo.color.equals(currentTurn)) {
                return; // Không phải lượt của quân cờ này
            }
            
            // Đánh dấu quân cờ đang được chọn
            selectedPiece[0] = piece;
            
            // Highlight quân cờ đang được chọn với màu vàng
            DropShadow selectedShadow = new DropShadow();
            selectedShadow.setColor(Color.web("#FFD700", 0.8)); // Màu vàng với độ trong suốt
            selectedShadow.setRadius(15);
            selectedShadow.setOffsetX(0);
            selectedShadow.setOffsetY(0);
            piece.setEffect(selectedShadow);
            
            // Tính toán các nước đi hợp lệ
            java.util.List<int[]> validMoves = application.game.MoveValidator.getValidMoves(board, row, col);
            // Lưu danh sách các ô hợp lệ để validate khi thả quân cờ
            validMovesList[0].clear();
            validMovesList[0].addAll(validMoves);
            
            // Xác định màu của quân cờ để chọn màu chấm tròn (pieceInfo đã được khai báo ở trên)
            boolean isRedPiece = pieceInfo != null && pieceInfo.color.equals("red");
            Color dotColor = isRedPiece ? Color.web("#DC143C") : Color.web("#1C1C1C"); // Đỏ hoặc đen
            
            // Vẽ dấu chấm tròn hoặc vòng tròn có viền cho mỗi ô hợp lệ
            for (int[] move : validMoves) {
                int toRow = move[0];
                int toCol = move[1];
                
                // Kiểm tra xem có quân cờ nào ở ô đích không (sử dụng board state đã tạo)
                boolean hasEnemyPiece = false;
                char targetPiece = board[toRow][toCol];
                
                if (targetPiece != ' ' && targetPiece != '\0') {
                    // Có quân cờ ở ô đích
                    // Kiểm tra màu: red pieces là uppercase (K, A, B, N, R, C, P)
                    // black pieces là lowercase (k, a, b, n, r, c, p)
                    boolean targetIsRed = Character.isUpperCase(targetPiece);
                    boolean selectedIsRed = pieceInfo != null && pieceInfo.color.equals("red");
                    
                    // Nếu khác màu, đây là quân cờ địch có thể bị ăn
                    if (targetIsRed != selectedIsRed) {
                        hasEnemyPiece = true;
                    }
                }
                
                // Tính toán vị trí tại giao điểm (intersection) - giống với vị trí quân cờ
                double intersectionX = startX + toCol * intersectionSpacingX;
                double intersectionY = startY + toRow * intersectionSpacingY;
                
                // Áp dụng offset tương tự như quân cờ
                // Nếu có quân cờ địch, dùng offset của quân cờ địch; nếu không, dùng offset của quân cờ đang chọn
                double offsetX = 0;
                double offsetY = 0;
                boolean useRedOffset = isRedPiece;
                
                // Nếu có quân cờ địch, dùng offset của quân cờ địch
                if (hasEnemyPiece) {
                    useRedOffset = Character.isUpperCase(targetPiece);
                }
                
                if (useRedOffset) {
                    offsetY = -10;  // Dịch lên trên 10px
                    // Điều chỉnh thêm cho các cột bên phải
                    if (toCol >= 5) {  // Cột 5, 6, 7, 8
                        offsetX = 5;  // Dịch sang phải thêm 5px
                    } else {
                        // Cột 0-4: dịch sang trái thêm để khớp với giao điểm
                        offsetX = -4;  // Dịch sang trái 4px
                    }
                } else {
                    // black pieces: điều chỉnh offsetX cho cột 0-4
                    if (toCol < 5) {  // Cột 0, 1, 2, 3, 4
                        offsetX = -5;  // Dịch sang trái 5px để khớp với giao điểm
                    } else {  // Cột 5, 6, 7, 8
                        offsetX = 9;  // Dịch sang phải 9px (4 + 5)
                    }
                }
                
                // Vị trí cuối cùng với offset
                double x = intersectionX + offsetX;
                double y = intersectionY + offsetY;
                
                if (hasEnemyPiece) {
                    // Nếu có thể ăn quân cờ địch: vẽ vòng tròn có viền lớn bao quanh quân cờ địch
                    Circle captureCircle = new Circle();
                    // Vòng tròn lớn hơn để bao quanh quân cờ (khoảng 45% kích thước intersection spacing)
                    double circleRadius = Math.min(intersectionSpacingX, intersectionSpacingY) * 0.45;
                    captureCircle.setRadius(circleRadius);
                    captureCircle.setFill(Color.TRANSPARENT); // Trong suốt
                    captureCircle.setStroke(dotColor); // Viền cùng màu với quân cờ đang chọn
                    captureCircle.setStrokeWidth(4.5); // Viền dày để nổi bật
                    
                    // Đặt vị trí tại giao điểm với offset (nơi quân cờ địch đang đứng)
                    captureCircle.setLayoutX(x);
                    captureCircle.setLayoutY(y);
                    
                    highlightLayer.getChildren().add(captureCircle);
                    highlightRects[0].add(captureCircle);
                } else {
                    // Nếu ô trống: dấu chấm tròn đầy
                    Circle dot = new Circle();
                    double dotRadius = Math.min(intersectionSpacingX, intersectionSpacingY) * 0.15; // 15% kích thước intersection spacing
                    dot.setRadius(dotRadius);
                    dot.setFill(dotColor);
                    dot.setStroke(Color.WHITE); // Viền trắng để nổi bật
                    dot.setStrokeWidth(1.5);
                    
                    // Đặt vị trí tại giao điểm với offset
                    dot.setLayoutX(x);
                    dot.setLayoutY(y);
                    
                    highlightLayer.getChildren().add(dot);
                    highlightRects[0].add(dot);
                }
            }
        };
        
        // Method để di chuyển quân cờ đến vị trí mới
        java.util.function.BiConsumer<Integer, Integer> movePieceTo = (toRow, toCol) -> {
            if (selectedPiece[0] == null) {
                return;
            }
            
            // Lấy vị trí ban đầu
            int fromRow = (int) Math.round(selectedPiece[0].getLayoutY() / cellHeight);
            int fromCol = (int) Math.round(selectedPiece[0].getLayoutX() / cellWidth);
            fromRow = Math.max(0, Math.min(9, fromRow));
            fromCol = Math.max(0, Math.min(8, fromCol));
            
            // Kiểm tra xem có hợp lệ không (theo luật cờ tướng)
            boolean isValidMove = false;
            for (int[] validMove : validMovesList[0]) {
                if (validMove[0] == toRow && validMove[1] == toCol) {
                    isValidMove = true;
                    break;
                }
            }
            
            if (isValidMove) {
                // Kiểm tra xem có quân cờ nào ở vị trí đích không
                ImageView capturedPiece = null;
                for (javafx.scene.Node node : container.getChildren()) {
                    if (node instanceof ImageView && node != selectedPiece[0] && 
                        node != highlightLayer && node != clickLayer) {
                        ImageView imgView = (ImageView) node;
                        if (imgView.getUserData() instanceof PieceInfo) {
                            int pieceRow = (int) Math.round(imgView.getLayoutY() / cellHeight);
                            int pieceCol = (int) Math.round(imgView.getLayoutX() / cellWidth);
                            pieceRow = Math.max(0, Math.min(9, pieceRow));
                            pieceCol = Math.max(0, Math.min(8, pieceCol));
                            
                            if (pieceRow == toRow && pieceCol == toCol) {
                                // Tìm thấy quân cờ ở vị trí đích
                                capturedPiece = imgView;
                                
                                // Kiểm tra màu của quân cờ bị ăn
                                PieceInfo capturedInfo = (PieceInfo) capturedPiece.getUserData();
                                PieceInfo selectedInfo = (PieceInfo) selectedPiece[0].getUserData();
                                
                                if (capturedInfo != null && selectedInfo != null) {
                                    boolean capturedIsRed = capturedInfo.color.equals("red");
                                    boolean selectedIsRed = selectedInfo.color.equals("red");
                                    
                                    // Nếu cùng màu, không cho phép ăn (đã được validate bởi MoveValidator, nhưng double check)
                                    if (capturedIsRed == selectedIsRed) {
                                        return; // Không cho phép ăn quân cờ cùng màu
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                
                // Nếu có quân cờ khác màu ở vị trí đích, xóa nó (ăn quân cờ)
                if (capturedPiece != null) {
                    PieceInfo capInfo = (PieceInfo) capturedPiece.getUserData();
                    if (capInfo != null) {
                        // Thông báo quân cờ đã bị ăn
                        if (onPieceCaptured != null) {
                            onPieceCaptured.accept(capInfo.color, capInfo.pieceType);
                        }
                    }
                    container.getChildren().remove(capturedPiece);
                }
                
                // Tính toán vị trí mới
                double newX = toCol * cellWidth + (cellWidth - selectedPiece[0].getFitWidth()) / 2;
                double newY = toRow * cellHeight + (cellHeight - selectedPiece[0].getFitHeight()) / 2;
                
                // Di chuyển quân cờ
                selectedPiece[0].setLayoutX(newX);
                selectedPiece[0].setLayoutY(newY);
                
                // Lấy thông tin quân cờ
                PieceInfo pieceInfo = (PieceInfo) selectedPiece[0].getUserData();
                if (pieceInfo != null && (fromRow != toRow || fromCol != toCol)) {
                    // Thêm nước đi vào history với thông tin quân cờ bị ăn (nếu có)
                    String capturedInfo = "";
                    if (capturedPiece != null) {
                        PieceInfo capInfo = (PieceInfo) capturedPiece.getUserData();
                        if (capInfo != null) {
                            capturedInfo = String.format(" (captured %s %s)", 
                                capInfo.color, capInfo.pieceType);
                        }
                    }
                    
                    // Thông báo nước đi đã được thực hiện
                    if (onMoveAddedWithDetails != null) {
                        String moveDetails = String.format("%s|%s|%d|%d|%d|%d|%s",
                            pieceInfo.color, pieceInfo.pieceType, fromRow, fromCol, toRow, toCol, capturedInfo);
                        onMoveAddedWithDetails.accept(moveDetails);
                    }
                    
                    // Đổi lượt sau khi đi xong
                    gamePanel.setCurrentTurn(gamePanel.getCurrentTurn().equals("red") ? "black" : "red");
                    
                    // Thông báo đã đổi lượt
                    if (onTurnChanged != null) {
                        onTurnChanged.run();
                    }
                }
                
                // Xóa highlights
                clearHighlights.accept(null);
            }
        };
        
        // Thêm click handler cho clickLayer - detect click vào các ô
        for (javafx.scene.Node node : clickLayer.getChildren()) {
            if (node instanceof Rectangle) {
                Rectangle cell = (Rectangle) node;
                int[] cellPos = (int[]) cell.getUserData();
                int cellRow = cellPos[0];
                int cellCol = cellPos[1];
                
                cell.setOnMouseClicked(e -> {
                    // Kiểm tra lượt: chỉ cho phép di chuyển nếu đang là lượt của quân cờ đã chọn
                    if (selectedPiece[0] != null) {
                        PieceInfo selectedPieceInfo = (PieceInfo) selectedPiece[0].getUserData();
                        String currentTurn = gamePanel.getCurrentTurn();
                        if (selectedPieceInfo == null || !selectedPieceInfo.color.equals(currentTurn)) {
                            e.consume();
                            return; // Không phải lượt của quân cờ đã chọn
                        }
                        
                        // Nếu đã chọn quân cờ và ô này trống, thử di chuyển đến ô này
                        movePieceTo.accept(cellRow, cellCol);
                    } else {
                        // Nếu chưa chọn quân cờ, xóa highlights nếu có
                        clearHighlights.accept(null);
                    }
                    e.consume();
                });
            }
        }
        
        // Hàm helper để tạo quân cờ với drag functionality
        java.util.function.BiFunction<String, String, ImageView> createPiece = (color, pieceType) -> {
            // Capitalize first letter of color for filename (Red/Black)
            String colorCapitalized = color.substring(0, 1).toUpperCase() + color.substring(1);
            String imagePath = "pieces/" + color + "/Chinese-" + pieceType + "-" + colorCapitalized + ".png";
            ImageView piece = new ImageView(AssetHelper.image(imagePath));
            piece.setFitWidth(pieceWidth);  // Kích thước quân cờ
            piece.setFitHeight(pieceHeight);
            piece.setPreserveRatio(true);
            piece.setSmooth(true);
            piece.setVisible(true);
            piece.setManaged(true);
            piece.setMouseTransparent(false);
            piece.setPickOnBounds(true);
            
            // Lưu thông tin quân cờ vào userData
            piece.setUserData(new PieceInfo(color, pieceType, imagePath));
            
            // Thêm shadow cho quân cờ
            DropShadow shadow = new DropShadow();
            shadow.setColor(Color.color(0, 0, 0, 0.5));  // Màu đen với độ trong suốt 50%
            shadow.setRadius(8);  // Độ mờ của shadow
            shadow.setOffsetX(3);  // Độ lệch theo chiều ngang
            shadow.setOffsetY(3);  // Độ lệch theo chiều dọc
            piece.setEffect(shadow);
            
            // Cho phép click để chọn
            piece.setCursor(Cursor.HAND);
            
            piece.setOnMouseClicked(e -> {
                PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
                String currentTurn = gamePanel.getCurrentTurn();
                
                // Nếu đã có quân cờ được chọn, kiểm tra xem có thể di chuyển đến quân cờ này không
                if (selectedPiece[0] != null && selectedPiece[0] != piece) {
                    // Kiểm tra lượt: chỉ cho phép di chuyển nếu đang là lượt của quân cờ đã chọn
                    PieceInfo selectedPieceInfo = (PieceInfo) selectedPiece[0].getUserData();
                    if (selectedPieceInfo == null || !selectedPieceInfo.color.equals(currentTurn)) {
                        e.consume();
                        return; // Không phải lượt của quân cờ đã chọn
                    }
                    
                    // Tính toán row và col của quân cờ được click
                    int pieceRow = (int) Math.round(piece.getLayoutY() / cellHeight);
                    int pieceCol = (int) Math.round(piece.getLayoutX() / cellWidth);
                    pieceRow = Math.max(0, Math.min(9, pieceRow));
                    pieceCol = Math.max(0, Math.min(8, pieceCol));
                    
                    // Kiểm tra xem quân cờ được click có phải là quân cờ địch không
                    if (pieceInfo != null && selectedPieceInfo != null) {
                        boolean clickedIsRed = pieceInfo.color.equals("red");
                        boolean selectedIsRed = selectedPieceInfo.color.equals("red");
                        
                        // Nếu khác màu, thử di chuyển và ăn quân cờ này
                        if (clickedIsRed != selectedIsRed) {
                            movePieceTo.accept(pieceRow, pieceCol);
                            e.consume();
                            return;
                        }
                    }
                }
                
                // Kiểm tra lượt: chỉ cho phép chọn quân cờ của lượt hiện tại
                if (pieceInfo == null || !pieceInfo.color.equals(currentTurn)) {
                    e.consume();
                    return; // Không phải lượt của quân cờ này
                }
                
                // Xóa highlights cũ nếu có (khi click vào quân cờ khác)
                clearHighlights.accept(null);
                
                // Tính toán row và col của quân cờ
                int pieceRow = (int) Math.round(piece.getLayoutY() / cellHeight);
                int pieceCol = (int) Math.round(piece.getLayoutX() / cellWidth);
                // Giới hạn trong phạm vi bàn cờ
                pieceRow = Math.max(0, Math.min(9, pieceRow));
                pieceCol = Math.max(0, Math.min(8, pieceCol));
                
                // Highlight các ô hợp lệ khi click vào quân cờ
                highlightValidMoves.accept(pieceRow, pieceCol);
                
                // Stop propagation để không trigger click của cell layer
                e.consume();
            });
            
            return piece;
        };
        
        // Hàm helper để đặt quân cờ vào vị trí
        // Sử dụng BiFunction để có thể truyền thêm thông tin màu
        java.util.function.Function<ImageView, java.util.function.BiConsumer<int[], String>> createPlacePiece = (piece) -> {
            return (pos, color) -> {
            // pos[0] = row (0-9), pos[1] = col (0-8)
                // Quân cờ được đặt tại giao điểm, tâm quân cờ trùng với giao điểm
                // Vị trí giao điểm từ startX, startY
                double intersectionX = startX + pos[1] * intersectionSpacingX;
                double intersectionY = startY + pos[0] * intersectionSpacingY;
                
                // Offset điều chỉnh: quân đỏ dịch lên trên, quân đen dịch sang phải
                double offsetX = 0;
                double offsetY = 0;
                if ("red".equals(color)) {
                    offsetY = -10;  // Dịch lên trên 10px
                    // Điều chỉnh thêm cho các cột bên phải
                    if (pos[1] >= 5) {  // Cột 5, 6, 7, 8
                        offsetX = 5;  // Dịch sang phải thêm 5px
                    }
                } else if ("black".equals(color)) {
                    offsetX = 4;   // Dịch sang phải 4px
                    // Điều chỉnh thêm cho các cột bên phải
                    if (pos[1] >= 5) {  // Cột 5, 6, 7, 8
                        offsetX += 5;  // Dịch sang phải thêm 5px
                    }
                }
                
                // Đặt tâm quân cờ tại giao điểm (trừ đi một nửa kích thước)
                double x = intersectionX - pieceWidth / 2.0 + offsetX;
                double y = intersectionY - pieceHeight / 2.0 + offsetY;
            piece.setLayoutX(x);
            piece.setLayoutY(y);
            container.getChildren().add(piece);
            };
        };
        
        // Wrapper để tương thích với code cũ
        java.util.function.BiConsumer<ImageView, int[]> placePiece = (piece, pos) -> {
            // Lấy màu từ userData (đã được set trong createPiece)
            PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
            String color = pieceInfo != null ? pieceInfo.color : "";
            createPlacePiece.apply(piece).accept(pos, color);
        };
        
        // Check xem có custom board setup không - CHỈ load khi đang ở Custom Mode
        String currentMode = state.getCurrentGameMode();
        java.util.Map<String, String> customSetup = state.getCustomBoardSetup();
        if ("custom".equals(currentMode) && customSetup != null && !customSetup.isEmpty() && state.isUseCustomBoard()) {
            // Áp dụng custom board setup - CHỈ khi đang ở Custom Mode
            for (java.util.Map.Entry<String, String> entry : customSetup.entrySet()) {
                String[] posParts = entry.getKey().split("_");
                int row = Integer.parseInt(posParts[0]);
                int col = Integer.parseInt(posParts[1]);
                String[] pieceParts = entry.getValue().split("_");
                String color = pieceParts[0];
                String pieceType = pieceParts[1];
                
                placePiece.accept(createPiece.apply(color, pieceType), new int[]{row, col});
            }
        } else {
            // Standard starting positions - Sắp xếp quân cờ ĐỎ (hàng 0-4, dưới cùng)
            // Hàng 0: Xe, Mã, Tượng, Sĩ, Tướng, Sĩ, Tượng, Mã, Xe
            placePiece.accept(createPiece.apply("red", "Rook"), new int[]{0, 0});
            placePiece.accept(createPiece.apply("red", "Horse"), new int[]{0, 1});
            placePiece.accept(createPiece.apply("red", "Elephant"), new int[]{0, 2});
            placePiece.accept(createPiece.apply("red", "Advisor"), new int[]{0, 3});
            placePiece.accept(createPiece.apply("red", "King"), new int[]{0, 4});
            placePiece.accept(createPiece.apply("red", "Advisor"), new int[]{0, 5});
            placePiece.accept(createPiece.apply("red", "Elephant"), new int[]{0, 6});
            placePiece.accept(createPiece.apply("red", "Horse"), new int[]{0, 7});
            placePiece.accept(createPiece.apply("red", "Rook"), new int[]{0, 8});
            
            // Hàng 2: Pháo ở cột 1 và 7
            placePiece.accept(createPiece.apply("red", "Cannon"), new int[]{2, 1});
            placePiece.accept(createPiece.apply("red", "Cannon"), new int[]{2, 7});
            
            // Hàng 3: Tốt ở cột 0, 2, 4, 6, 8
            placePiece.accept(createPiece.apply("red", "Pawn"), new int[]{3, 0});
            placePiece.accept(createPiece.apply("red", "Pawn"), new int[]{3, 2});
            placePiece.accept(createPiece.apply("red", "Pawn"), new int[]{3, 4});
            placePiece.accept(createPiece.apply("red", "Pawn"), new int[]{3, 6});
            placePiece.accept(createPiece.apply("red", "Pawn"), new int[]{3, 8});
            
            // Sắp xếp quân cờ ĐEN (hàng 5-9, trên cùng)
            // Hàng 9: Xe, Mã, Tượng, Sĩ, Tướng, Sĩ, Tượng, Mã, Xe
            placePiece.accept(createPiece.apply("black", "Rook"), new int[]{9, 0});
            placePiece.accept(createPiece.apply("black", "Horse"), new int[]{9, 1});
            placePiece.accept(createPiece.apply("black", "Elephant"), new int[]{9, 2});
            placePiece.accept(createPiece.apply("black", "Advisor"), new int[]{9, 3});
            placePiece.accept(createPiece.apply("black", "King"), new int[]{9, 4});
            placePiece.accept(createPiece.apply("black", "Advisor"), new int[]{9, 5});
            placePiece.accept(createPiece.apply("black", "Elephant"), new int[]{9, 6});
            placePiece.accept(createPiece.apply("black", "Horse"), new int[]{9, 7});
            placePiece.accept(createPiece.apply("black", "Rook"), new int[]{9, 8});
            
            // Hàng 7: Pháo ở cột 1 và 7
            placePiece.accept(createPiece.apply("black", "Cannon"), new int[]{7, 1});
            placePiece.accept(createPiece.apply("black", "Cannon"), new int[]{7, 7});
            
            // Hàng 6: Tốt ở cột 0, 2, 4, 6, 8
            placePiece.accept(createPiece.apply("black", "Pawn"), new int[]{6, 0});
            placePiece.accept(createPiece.apply("black", "Pawn"), new int[]{6, 2});
            placePiece.accept(createPiece.apply("black", "Pawn"), new int[]{6, 4});
            placePiece.accept(createPiece.apply("black", "Pawn"), new int[]{6, 6});
            placePiece.accept(createPiece.apply("black", "Pawn"), new int[]{6, 8});
        }
        
        // Thêm highlight layer và click layer
        // Thứ tự: click layer (dưới cùng, invisible) -> quân cờ -> highlight layer (trên cùng)
        // Click layer được thêm trước để không chặn click vào quân cờ
        // Nhưng vì nó transparent và ở dưới, click vào quân cờ sẽ được xử lý bởi quân cờ trước
        container.getChildren().add(0, clickLayer); // Thêm vào đầu để ở dưới cùng
        container.getChildren().add(highlightLayer); // Highlight layer ở trên cùng
        
        return container;
    }
    
    /**
     * Helper method để convert piece type và color thành character
     */
    public static char getPieceChar(String pieceType, boolean isRed) {
        char baseChar = ' ';
        switch (pieceType) {
            case "King": baseChar = 'K'; break;
            case "Advisor": baseChar = 'A'; break;
            case "Elephant": baseChar = 'B'; break;
            case "Horse": baseChar = 'N'; break;
            case "Rook": baseChar = 'R'; break;
            case "Cannon": baseChar = 'C'; break;
            case "Pawn": baseChar = 'P'; break;
        }
        return isRed ? baseChar : Character.toLowerCase(baseChar);
    }
}


