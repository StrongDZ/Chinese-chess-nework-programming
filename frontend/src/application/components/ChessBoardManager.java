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
    private final IGamePanel gamePanel;
    
    // Reference to pieces container để có thể highlight suggest move
    private Pane piecesContainerRef = null;
    
    // Callbacks để giao tiếp với các manager khác
    private java.util.function.BiConsumer<String, String> onPieceCaptured = null;  // (color, pieceType)
    private java.util.function.Consumer<String> onMoveAdded = null;  // (moveText)
    private java.util.function.Consumer<String> onMoveAddedWithDetails = null;  // (color, pieceType, fromRow, fromCol, toRow, toCol, capturedInfo)
    private Runnable onTurnChanged = null;
    
    // Callback để gửi MOVE message đến server
    private MoveCallback onMoveMade = null;
    
    // Interface cho callback gửi move
    @FunctionalInterface
    public interface MoveCallback {
        void onMove(int fromRow, int fromCol, int toRow, int toCol, String piece, String captured);
    }
    
    // Helper class để lưu thông tin quân cờ
    public static class PieceInfo {
        public String color;
        public String pieceType;
        public String imagePath;
        public int row;  // Vị trí lưới: row (0-9)
        public int col;  // Vị trí lưới: col (0-8)
        
        public PieceInfo(String color, String pieceType, String imagePath, int row, int col) {
            this.color = color;
            this.pieceType = pieceType;
            this.imagePath = imagePath;
            this.row = row;
            this.col = col;
        }
    }
    
    public ChessBoardManager(UIState state, IGamePanel gamePanel) {
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
     * Set callback khi thực hiện nước đi (để gửi lên server)
     */
    public void setOnMoveMade(MoveCallback callback) {
        this.onMoveMade = callback;
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
        highlightLayer.setId("highlightLayer"); // Đặt ID để dễ tìm
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
            
            // Lấy thông tin quân cờ từ userData để xác định loại quân (dùng row/col từ PieceInfo)
            ImageView piece = null;
            for (javafx.scene.Node node : container.getChildren()) {
                if (node instanceof ImageView) {
                    ImageView imgView = (ImageView) node;
                    if (imgView.getUserData() instanceof PieceInfo) {
                        PieceInfo info = (PieceInfo) imgView.getUserData();
                        // Dùng row/col từ PieceInfo thay vì tính từ pixel
                        int pieceRow = info.row;
                        int pieceCol = info.col;
                        
                        // Convert piece type to character
                        char pieceChar = getPieceChar(info.pieceType, info.color.equals("red"));
                        board[pieceRow][pieceCol] = pieceChar;
                        
                        // Tìm quân cờ tại vị trí được click (so sánh bằng row/col)
                        if (pieceRow == row && pieceCol == col) {
                            piece = imgView;
                        }
                    }
                }
            }
            
            if (piece == null) {
                return;
            }
            
            // Kiểm tra lượt: chỉ cho phép chọn quân cờ của lượt hiện tại VÀ quân cờ của người chơi
            PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
            String currentTurn = gamePanel.getCurrentTurn();
            boolean playerIsRed = state.isPlayerRed();
            String playerColor = playerIsRed ? "red" : "black";
            
            // Kiểm tra 1: Phải là lượt của quân cờ này
            if (pieceInfo == null || !pieceInfo.color.equals(currentTurn)) {
                return; // Không phải lượt của quân cờ này
            }
            
            // Kiểm tra 2: Người chơi chỉ được đi quân của mình
            if (!pieceInfo.color.equals(playerColor)) {
                return; // Không phải quân cờ của người chơi
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
            
            // Lấy vị trí ban đầu từ PieceInfo (vị trí lưới)
            PieceInfo selectedInfo = (PieceInfo) selectedPiece[0].getUserData();
            if (selectedInfo == null) {
                return;
            }
            int fromRow = selectedInfo.row;
            int fromCol = selectedInfo.col;
            
            // Kiểm tra xem có hợp lệ không (theo luật cờ tướng)
            boolean isValidMove = false;
            for (int[] validMove : validMovesList[0]) {
                if (validMove[0] == toRow && validMove[1] == toCol) {
                    isValidMove = true;
                    break;
                }
            }
            
            if (isValidMove) {
                // Kiểm tra xem có quân cờ nào ở vị trí đích không (dùng row/col từ PieceInfo)
                ImageView capturedPiece = null;
                for (javafx.scene.Node node : container.getChildren()) {
                    if (node instanceof ImageView && node != selectedPiece[0] && 
                        node != highlightLayer && node != clickLayer) {
                        ImageView imgView = (ImageView) node;
                        if (imgView.getUserData() instanceof PieceInfo) {
                            PieceInfo pieceInfo = (PieceInfo) imgView.getUserData();
                            
                            // So sánh bằng row/col từ PieceInfo
                            if (pieceInfo.row == toRow && pieceInfo.col == toCol) {
                                // Tìm thấy quân cờ ở vị trí đích
                                capturedPiece = imgView;
                                
                                // Kiểm tra màu của quân cờ bị ăn
                                PieceInfo capturedInfo = (PieceInfo) capturedPiece.getUserData();
                                
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
                
                // Tính toán vị trí mới (dùng công thức đặt quân cờ)
                double intersectionX = startX + toCol * intersectionSpacingX;
                double intersectionY = startY + toRow * intersectionSpacingY;
                
                // Tính offset dựa trên màu quân cờ
                double offsetX = 0;
                double offsetY = 0;
                if ("red".equals(selectedInfo.color)) {
                    offsetY = -10;  // Dịch lên trên 10px
                    if (toCol >= 5) {
                        offsetX = 5;
                    } else {
                        offsetX = -4;
                    }
                } else if ("black".equals(selectedInfo.color)) {
                    if (toCol < 5) {
                        offsetX = -5;
                    } else {
                        offsetX = 9;
                    }
                }
                
                // Đặt tâm quân cờ tại giao điểm (trừ đi một nửa kích thước) + offset
                double newX = intersectionX - pieceWidth / 2.0 + offsetX;
                double newY = intersectionY - pieceHeight / 2.0 + offsetY;
                
                // Di chuyển quân cờ
                selectedPiece[0].setLayoutX(newX);
                selectedPiece[0].setLayoutY(newY);
                
                // CẬP NHẬT row/col trong PieceInfo (quan trọng!)
                selectedInfo.row = toRow;
                selectedInfo.col = toCol;
                
                // Lấy thông tin quân cờ
                PieceInfo pieceInfo = selectedInfo;
                if (pieceInfo != null && (fromRow != toRow || fromCol != toCol)) {
                    // Thêm nước đi vào history với thông tin quân cờ bị ăn (nếu có)
                    String capturedInfo = "";
                    String capturedPieceType = null;
                    if (capturedPiece != null) {
                        PieceInfo capInfo = (PieceInfo) capturedPiece.getUserData();
                        if (capInfo != null) {
                            capturedInfo = String.format(" (captured %s %s)", 
                                capInfo.color, capInfo.pieceType);
                            capturedPieceType = capInfo.pieceType;
                        }
                    }
                    
                    // Xóa suggest highlights khi user thực hiện move
                    clearSuggestHighlights();
                    
                    // GỬI MOVE MESSAGE ĐẾN SERVER TRƯỚC KHI CẬP NHẬT LOCAL
                    if (onMoveMade != null) {
                        onMoveMade.onMove(fromRow, fromCol, toRow, toCol, pieceInfo.pieceType, capturedPieceType);
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
                        boolean playerIsRed = state.isPlayerRed();
                        String playerColor = playerIsRed ? "red" : "black";
                        
                        if (selectedPieceInfo == null || !selectedPieceInfo.color.equals(currentTurn)) {
                            e.consume();
                            return; // Không phải lượt của quân cờ đã chọn
                        }
                        
                        // Kiểm tra: Người chơi chỉ được đi quân của mình
                        if (!selectedPieceInfo.color.equals(playerColor)) {
                            e.consume();
                            return; // Không phải quân cờ của người chơi
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
        // Nhận thêm row và col để lưu vị trí lưới
        java.util.function.Function<int[], java.util.function.BiFunction<String, String, ImageView>> createPieceFactory = (pos) -> {
            return (color, pieceType) -> {
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
                
                // Lưu thông tin quân cờ vào userData với row/col
                piece.setUserData(new PieceInfo(color, pieceType, imagePath, pos[0], pos[1]));
                
                return piece;
            };
        };
        
        // Wrapper để tương thích với code cũ (không có row/col, sẽ set sau)
        java.util.function.BiFunction<String, String, ImageView> createPiece = (color, pieceType) -> {
            // Tạo với row/col tạm thời (-1, -1), sẽ được cập nhật khi placePiece
            String colorCapitalized = color.substring(0, 1).toUpperCase() + color.substring(1);
            String imagePath = "pieces/" + color + "/Chinese-" + pieceType + "-" + colorCapitalized + ".png";
            ImageView piece = new ImageView(AssetHelper.image(imagePath));
            piece.setFitWidth(pieceWidth);
            piece.setFitHeight(pieceHeight);
            piece.setPreserveRatio(true);
            piece.setSmooth(true);
            piece.setVisible(true);
            piece.setManaged(true);
            piece.setMouseTransparent(false);
            piece.setPickOnBounds(true);
            
            // Lưu thông tin quân cờ vào userData (row/col sẽ được cập nhật khi placePiece)
            piece.setUserData(new PieceInfo(color, pieceType, imagePath, -1, -1));
            
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
                // Kiểm tra row/col hợp lệ (phải >= 0)
                if (pieceInfo != null && (pieceInfo.row < 0 || pieceInfo.col < 0)) {
                    System.err.println("[ChessBoardManager] Piece has invalid row/col: " + pieceInfo.row + "," + pieceInfo.col);
                    e.consume();
                    return;
                }
                String currentTurn = gamePanel.getCurrentTurn();
                boolean playerIsRed = state.isPlayerRed();
                String playerColor = playerIsRed ? "red" : "black";
                
                // Debug log để theo dõi vấn đề
                System.out.println("[ChessBoardManager] Click on piece: color=" + 
                    (pieceInfo != null ? pieceInfo.color : "null") + 
                    ", pieceType=" + (pieceInfo != null ? pieceInfo.pieceType : "null") +
                    ", playerIsRed=" + playerIsRed + 
                    ", playerColor=" + playerColor +
                    ", currentTurn=" + currentTurn);
                
                // Nếu đã có quân cờ được chọn, kiểm tra xem có thể di chuyển đến quân cờ này không
                if (selectedPiece[0] != null && selectedPiece[0] != piece) {
                    // Kiểm tra lượt: chỉ cho phép di chuyển nếu đang là lượt của quân cờ đã chọn
                    PieceInfo selectedPieceInfo = (PieceInfo) selectedPiece[0].getUserData();
                    if (selectedPieceInfo == null || !selectedPieceInfo.color.equals(currentTurn)) {
                        e.consume();
                        return; // Không phải lượt của quân cờ đã chọn
                    }
                    
                    // Kiểm tra: Người chơi chỉ được đi quân của mình
                    if (!selectedPieceInfo.color.equals(playerColor)) {
                        e.consume();
                        return; // Không phải quân cờ của người chơi
                    }
                    
                    // Lấy row và col từ PieceInfo (vị trí lưới)
                    if (pieceInfo != null && selectedPieceInfo != null) {
                        boolean clickedIsRed = pieceInfo.color.equals("red");
                        boolean selectedIsRed = selectedPieceInfo.color.equals("red");
                        
                        // Nếu khác màu, thử di chuyển và ăn quân cờ này
                        if (clickedIsRed != selectedIsRed) {
                            movePieceTo.accept(pieceInfo.row, pieceInfo.col);
                            e.consume();
                            return;
                        }
                    }
                }
                
                // Kiểm tra lượt: chỉ cho phép chọn quân cờ của lượt hiện tại VÀ quân cờ của người chơi
                if (pieceInfo == null || !pieceInfo.color.equals(currentTurn)) {
                    e.consume();
                    return; // Không phải lượt của quân cờ này
                }
                
                // Kiểm tra: Người chơi chỉ được chọn quân của mình
                if (!pieceInfo.color.equals(playerColor)) {
                    e.consume();
                    return; // Không phải quân cờ của người chơi
                }
                
                // Xóa highlights cũ nếu có (khi click vào quân cờ khác)
                clearHighlights.accept(null);
                
                // Lấy row và col từ PieceInfo (vị trí lưới)
                int pieceRow = pieceInfo.row;
                int pieceCol = pieceInfo.col;
                
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
                // CẬP NHẬT row/col trong PieceInfo
                PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
                if (pieceInfo != null) {
                    pieceInfo.row = pos[0];
                    pieceInfo.col = pos[1];
                }
                
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
                    } else {
                        offsetX = -4;
                    }
                } else if ("black".equals(color)) {
                    if (pos[1] < 5) {
                        offsetX = -5;
                    } else {
                        offsetX = 9;
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
            // Standard starting positions - Sắp xếp quân cờ ĐỎ (hàng 0 = top)
            // Frontend: row 0 = top (Đỏ), row 9 = bottom (Đen)
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
            
            // Sắp xếp quân cờ ĐEN (hàng 9 = bottom)
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
        
        // Lưu reference để có thể highlight suggest move
        this.piecesContainerRef = container;
        
        return container;
    }
    
    /**
     * Highlight suggest move: highlight quân cờ được suggest và chấm vàng ở nước đi
     * @param fromRow Frontend row (0-9)
     * @param fromCol Frontend col (0-8)
     * @param toRow Frontend row (0-9)
     * @param toCol Frontend col (0-8)
     */
    public void highlightSuggestMove(int fromRow, int fromCol, int toRow, int toCol) {
        // Tìm highlight layer trong piecesContainer
        if (piecesContainerRef == null) {
            System.err.println("[ChessBoardManager] piecesContainerRef is null, cannot highlight suggest move");
            return;
        }
        
        // Tìm highlight layer bằng ID
        Pane highlightLayer = null;
        for (javafx.scene.Node node : piecesContainerRef.getChildren()) {
            if (node instanceof Pane) {
                Pane pane = (Pane) node;
                if ("highlightLayer".equals(pane.getId())) {
                    highlightLayer = pane;
                    break;
                }
            }
        }
        
        if (highlightLayer == null) {
            System.err.println("[ChessBoardManager] Highlight layer not found");
            // Debug: in ra tất cả children
            System.err.println("[ChessBoardManager] Available children in piecesContainer:");
            for (javafx.scene.Node node : piecesContainerRef.getChildren()) {
                System.err.println("  - " + node.getClass().getSimpleName() + 
                    (node instanceof Pane ? " (id=" + ((Pane)node).getId() + ")" : ""));
            }
            return;
        }
        
        // Xóa highlights cũ (chỉ xóa suggest highlights, giữ lại valid moves nếu có)
        java.util.List<javafx.scene.Node> toRemove = new java.util.ArrayList<>();
        for (javafx.scene.Node node : highlightLayer.getChildren()) {
            if (node.getUserData() != null && "suggest".equals(node.getUserData())) {
                toRemove.add(node);
            }
        }
        highlightLayer.getChildren().removeAll(toRemove);
        
        // Tìm và highlight quân cờ được suggest
        ImageView suggestedPiece = null;
        for (javafx.scene.Node node : piecesContainerRef.getChildren()) {
            if (node instanceof ImageView) {
                ImageView imgView = (ImageView) node;
                if (imgView.getUserData() instanceof PieceInfo) {
                    PieceInfo info = (PieceInfo) imgView.getUserData();
                    if (info.row == fromRow && info.col == fromCol) {
                        suggestedPiece = imgView;
                        // Highlight quân cờ với màu vàng đậm
                        DropShadow suggestShadow = new DropShadow();
                        suggestShadow.setColor(Color.web("#FFD700", 1.0)); // Màu vàng đậm
                        suggestShadow.setRadius(20);
                        suggestShadow.setOffsetX(0);
                        suggestShadow.setOffsetY(0);
                        imgView.setEffect(suggestShadow);
                        break;
                    }
                }
            }
        }
        
        // Tính toán vị trí giao điểm (giống như trong createChessPieces)
        double boardSize = 923.0;
        double startX = 45.0;
        double startY = 45.0;
        double endX = boardSize - 45.0;
        double endY = boardSize - 45.0;
        double intersectionSpacingX = (endX - startX) / 8.0;
        double intersectionSpacingY = (endY - startY) / 9.0;
        
        // Tính vị trí giao điểm cho to
        double toIntersectionX = startX + toCol * intersectionSpacingX;
        double toIntersectionY = startY + toRow * intersectionSpacingY;
        
        // Tính offset (giống như trong createChessPieces)
        double offsetX = 0;
        double offsetY = 0;
        if (suggestedPiece != null && suggestedPiece.getUserData() instanceof PieceInfo) {
            PieceInfo pieceInfo = (PieceInfo) suggestedPiece.getUserData();
            if ("red".equals(pieceInfo.color)) {
                offsetY = -10;
                if (toCol >= 5) {
                    offsetX = 5;
                } else {
                    offsetX = -4;
                }
            } else if ("black".equals(pieceInfo.color)) {
                if (toCol < 5) {
                    offsetX = -5;
                } else {
                    offsetX = 9;
                }
            }
        }
        
        // Vẽ chấm vàng ở nước đi được suggest
        Circle suggestDot = new Circle();
        double dotRadius = Math.min(intersectionSpacingX, intersectionSpacingY) * 0.2; // 20% kích thước intersection spacing
        suggestDot.setRadius(dotRadius);
        suggestDot.setFill(Color.web("#FFD700")); // Màu vàng
        suggestDot.setStroke(Color.web("#FFA500")); // Viền cam đậm
        suggestDot.setStrokeWidth(3);
        
        // Đặt vị trí tại giao điểm với offset
        double x = toIntersectionX + offsetX;
        double y = toIntersectionY + offsetY;
        suggestDot.setLayoutX(x);
        suggestDot.setLayoutY(y);
        suggestDot.setUserData("suggest"); // Đánh dấu là suggest highlight
        
        highlightLayer.getChildren().add(suggestDot);
        
        System.out.println("[ChessBoardManager] Highlighted suggest move: from(" + fromRow + "," + fromCol + 
            ") to(" + toRow + "," + toCol + ")");
    }
    
    /**
     * Xóa tất cả suggest highlights (chấm vàng và shadow trên quân cờ)
     */
    public void clearSuggestHighlights() {
        if (piecesContainerRef == null) {
            return;
        }
        
        // Tìm highlight layer
        Pane highlightLayer = null;
        for (javafx.scene.Node node : piecesContainerRef.getChildren()) {
            if (node instanceof Pane) {
                Pane pane = (Pane) node;
                if ("highlightLayer".equals(pane.getId())) {
                    highlightLayer = pane;
                    break;
                }
            }
        }
        
        if (highlightLayer != null) {
            // Xóa tất cả suggest highlights (chấm vàng)
            java.util.List<javafx.scene.Node> toRemove = new java.util.ArrayList<>();
            for (javafx.scene.Node node : highlightLayer.getChildren()) {
                if (node.getUserData() != null && "suggest".equals(node.getUserData())) {
                    toRemove.add(node);
                }
            }
            highlightLayer.getChildren().removeAll(toRemove);
        }
        
        // Xóa shadow effect trên tất cả quân cờ (khôi phục về shadow bình thường)
        for (javafx.scene.Node node : piecesContainerRef.getChildren()) {
            if (node instanceof ImageView) {
                ImageView imgView = (ImageView) node;
                if (imgView.getEffect() != null && imgView.getEffect() instanceof DropShadow) {
                    DropShadow shadow = (DropShadow) imgView.getEffect();
                    // Kiểm tra nếu là suggest shadow (màu vàng #FFD700)
                    if (shadow.getColor().equals(Color.web("#FFD700", 1.0)) && 
                        shadow.getRadius() == 20 && 
                        shadow.getOffsetX() == 0 && 
                        shadow.getOffsetY() == 0) {
                        // Khôi phục shadow bình thường
                        DropShadow normalShadow = new DropShadow();
                        normalShadow.setColor(Color.color(0, 0, 0, 0.5));
                        normalShadow.setRadius(8);
                        normalShadow.setOffsetX(3);
                        normalShadow.setOffsetY(3);
                        imgView.setEffect(normalShadow);
                    }
                }
            }
        }
        
        System.out.println("[ChessBoardManager] Cleared suggest highlights");
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
    
    /**
     * Parse x-fen string và trả về thông tin quân cờ tại mỗi vị trí
     * X-fen format: rnb1aabnr/9/1c5c1/p1p1p1p1p/9/4P4/P1P3P1P/1C5C1/9/RNBAKABNR w - - 0 3
     * @param xfen X-fen string
     * @return Map với key là "row_col" và value là "color_pieceType"
     */
    public static java.util.Map<String, String> parseXfen(String xfen) {
        java.util.Map<String, String> pieces = new java.util.HashMap<>();
        
        if (xfen == null || xfen.trim().isEmpty()) {
            return pieces;
        }
        
        // Lấy phần board (trước dấu space đầu tiên)
        String boardPart = xfen.split("\\s+")[0];
        
        int row = 0;
        int col = 0;
        
        for (char c : boardPart.toCharArray()) {
            if (c == '/') {
                // Kết thúc hàng, chuyển sang hàng tiếp theo
                row++;
                col = 0;
            } else if (c >= '1' && c <= '9') {
                // Số = số ô trống, bỏ qua
                col += (c - '0');
            } else {
                // Quân cờ
                if (row < 10 && col < 9) {
                    String color = Character.isUpperCase(c) ? "red" : "black";
                    String pieceType = getPieceTypeFromChar(c);
                    
                    if (pieceType != null) {
                        String key = row + "_" + col;
                        String value = color + "_" + pieceType;
                        pieces.put(key, value);
                    }
                }
                col++;
            }
        }
        
        return pieces;
    }
    
    /**
     * Convert ký tự x-fen thành piece type
     */
    private static String getPieceTypeFromChar(char c) {
        char lower = Character.toLowerCase(c);
        switch (lower) {
            case 'r': return "Rook";
            case 'n': return "Horse";
            case 'b': return "Elephant";
            case 'a': return "Advisor";
            case 'k': return "King";
            case 'c': return "Cannon";
            case 'p': return "Pawn";
            default: return null;
        }
    }
    
    /**
     * Vẽ bàn cờ từ x-fen string (đơn giản - chỉ hiển thị)
     * @param container Container chứa các quân cờ
     * @param xfen X-fen string
     */
    public void drawFromXfen(Pane container, String xfen) {
        if (container == null || xfen == null || xfen.trim().isEmpty()) {
            return;
        }
        
        // Xóa tất cả quân cờ hiện tại (chỉ xóa ImageView, giữ lại clickLayer và highlightLayer)
        java.util.List<javafx.scene.Node> toRemove = new java.util.ArrayList<>();
        for (javafx.scene.Node node : container.getChildren()) {
            if (node instanceof ImageView) {
                toRemove.add(node);
            }
        }
        container.getChildren().removeAll(toRemove);
        
        // Parse x-fen
        java.util.Map<String, String> pieces = parseXfen(xfen);
        
        // Tính toán kích thước và vị trí (giống như trong createChessPieces)
        double boardSize = 923.0;
        double startX = 45.0;
        double startY = 45.0;
        double endX = boardSize - 45.0;
        double endY = boardSize - 45.0;
        double intersectionSpacingX = (endX - startX) / 8.0;
        double intersectionSpacingY = (endY - startY) / 9.0;
        double pieceWidth = intersectionSpacingX * 0.8;
        double pieceHeight = intersectionSpacingY * 0.8;
        
        // Hàm tạo quân cờ (giống như trong createChessPieces)
        // Nhận thêm row và col để set vào PieceInfo
        java.util.function.Function<int[], java.util.function.BiFunction<String, String, ImageView>> createPieceFactory = (pos) -> {
            return (color, pieceType) -> {
                String colorCapitalized = color.substring(0, 1).toUpperCase() + color.substring(1);
                String imagePath = "pieces/" + color + "/Chinese-" + pieceType + "-" + colorCapitalized + ".png";
                ImageView piece = new ImageView(AssetHelper.image(imagePath));
                piece.setFitWidth(pieceWidth);
                piece.setFitHeight(pieceHeight);
                piece.setPreserveRatio(true);
                piece.setSmooth(true);
                piece.setVisible(true);
                piece.setManaged(true);
                piece.setMouseTransparent(false);
                piece.setPickOnBounds(true);
                piece.setUserData(new PieceInfo(color, pieceType, imagePath, pos[0], pos[1]));
                
                return piece;
            };
        };
        
        // Hàm đặt quân cờ vào vị trí
        java.util.function.BiConsumer<ImageView, int[]> placePiece = (piece, pos) -> {
            // CẬP NHẬT row/col trong PieceInfo
            PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
            if (pieceInfo != null) {
                pieceInfo.row = pos[0];
                pieceInfo.col = pos[1];
            }
            
            double intersectionX = startX + pos[1] * intersectionSpacingX;
            double intersectionY = startY + pos[0] * intersectionSpacingY;
            
            double offsetX = 0;
            double offsetY = 0;
            String color = pieceInfo != null ? pieceInfo.color : "";
            
            if ("red".equals(color)) {
                offsetY = -10;
                if (pos[1] >= 5) {
                    offsetX = 5;
                } else {
                    offsetX = -4;
                }
            } else if ("black".equals(color)) {
                if (pos[1] < 5) {
                    offsetX = -5;
                } else {
                    offsetX = 9;
                }
            }
            
            double x = intersectionX - pieceWidth / 2.0 + offsetX;
            double y = intersectionY - pieceHeight / 2.0 + offsetY;
            piece.setLayoutX(x);
            piece.setLayoutY(y);
            container.getChildren().add(piece);
        };
        
        // Vẽ các quân cờ từ x-fen
        for (java.util.Map.Entry<String, String> entry : pieces.entrySet()) {
            String[] posParts = entry.getKey().split("_");
            int row = Integer.parseInt(posParts[0]);
            int col = Integer.parseInt(posParts[1]);
            String[] pieceParts = entry.getValue().split("_");
            String color = pieceParts[0];
            String pieceType = pieceParts[1];
            
            // Tạo quân cờ với row/col đúng ngay từ đầu
            String colorCapitalized = color.substring(0, 1).toUpperCase() + color.substring(1);
            String imagePath = "pieces/" + color + "/Chinese-" + pieceType + "-" + colorCapitalized + ".png";
            ImageView piece = new ImageView(AssetHelper.image(imagePath));
            piece.setFitWidth(pieceWidth);
            piece.setFitHeight(pieceHeight);
            piece.setPreserveRatio(true);
            piece.setSmooth(true);
            piece.setVisible(true);
            piece.setManaged(true);
            piece.setMouseTransparent(false);
            piece.setPickOnBounds(true);
            piece.setUserData(new PieceInfo(color, pieceType, imagePath, row, col));
            
            DropShadow shadow = new DropShadow();
            shadow.setColor(Color.color(0, 0, 0, 0.5));
            shadow.setRadius(8);
            shadow.setOffsetX(3);
            shadow.setOffsetY(3);
            piece.setEffect(shadow);
            piece.setCursor(Cursor.HAND);
            
            placePiece.accept(piece, new int[]{row, col});
        }
    }
}


