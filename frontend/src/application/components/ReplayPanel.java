package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.Cursor;
import javafx.application.Platform;
import javafx.animation.ScaleTransition;
import application.network.NetworkManager;

/**
 * Replay panel for viewing game replays.
 * Similar to GamePanel but without timers, draw/signup buttons.
 * Move history is always visible.
 */
public class ReplayPanel extends StackPane implements IGamePanel {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    
    // Managers
    private ChessBoardManager chessBoardManager;
    private CapturedPiecesManager capturedPiecesManager;
    private MoveHistoryManager moveHistoryManager;
    
    // Core fields
    private Pane rootPane = null;
    private Pane piecesContainer = null;
    private StackPane boardContainer = null;
    private String currentGameId = "";  // Current game being replayed
    private int currentMoveIndex = -1;  // Current move index in replay (-1 = initial position)
    private java.util.List<ReplayMove> replayMoves = new java.util.ArrayList<>();  // List of moves to replay
    private String currentTurn = "red";  // Current turn for replay
    private boolean replayPlayerIsRed = true;  // Màu quân cờ của người chơi trong trận đấu này (từ database)
    
    // Inner class để lưu thông tin move (phải public để InfoHandler có thể tạo)
    public static class ReplayMove {
        public int fromRow, fromCol, toRow, toCol;
        public String color;
        public String pieceType;
        public String capturedColor;
        public String capturedPieceType;
        
        public ReplayMove(int fromRow, int fromCol, int toRow, int toCol, String color, String pieceType, 
                   String capturedColor, String capturedPieceType) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.color = color;
            this.pieceType = pieceType;
            this.capturedColor = capturedColor;
            this.capturedPieceType = capturedPieceType;
        }
    }
    
    // Profile containers
    private HBox playerProfile = null;
    private HBox opponentProfile = null;
    private VBox playerCapturedPieces = null;
    private VBox opponentCapturedPieces = null;
    
    // Replay control buttons (để có thể update state)
    private StackPane backButton = null;
    private StackPane nextButton = null;
    
    public ReplayPanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        setPickOnBounds(false);
        setMouseTransparent(false);
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        container.setMouseTransparent(false);
        
        // Khởi tạo các Manager
        this.capturedPiecesManager = new CapturedPiecesManager(state, this);
        this.chessBoardManager = new ChessBoardManager(state, this);
        
        // Tạo replay content
        StackPane replayContent = createReplayContent();
        replayContent.setLayoutX(0);
        replayContent.setLayoutY(0);
        replayContent.setPickOnBounds(true);
        replayContent.setMouseTransparent(false);
        
        // Khởi tạo MoveHistoryManager sau khi rootPane được set
        this.moveHistoryManager = new MoveHistoryManager(state, this, rootPane);
        
        // Set supplier để tạo replay control buttons trong move panel
        // CHỈ ReplayPanel mới set supplier này, GamePanel sẽ KHÔNG có các nút này
        moveHistoryManager.setReplayControlsSupplier(() -> {
            return createReplayControls();
        });
        
        container.getChildren().add(replayContent);
        getChildren().add(container);
        
        // Hiển thị move panel sau khi mọi thứ đã được thêm vào scene graph
        // Move panel sẽ được thêm vào ReplayPanel (this) thông qua MoveHistoryManager
        Platform.runLater(() -> {
            if (moveHistoryManager != null) {
                moveHistoryManager.showMovePanel();
                // Đảm bảo move panel ở trên cùng
                javafx.scene.Node movePanelNode = moveHistoryManager.getMovePanel();
                if (movePanelNode != null && getChildren().contains(movePanelNode)) {
                    movePanelNode.toFront();
                }
            }
        });
        
        // Bind visibility
        visibleProperty().bind(state.replayVisibleProperty());
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation
        state.replayVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                Platform.runLater(() -> {
                    resetReplayState();
                    // Load game data when replay panel opens
                    String gameId = state.getReplayGameId();
                    if (gameId != null && !gameId.isEmpty()) {
                        loadReplay(gameId);
                    }
                });
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        // Listen to gameId changes
        state.replayGameIdProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty() && state.isReplayVisible()) {
                loadReplay(newVal);
            }
        });
        
        // Listen to replay player color changes (từ database)
        state.replayPlayerIsRedProperty().addListener((obs, oldVal, newVal) -> {
            this.replayPlayerIsRed = newVal;
            Platform.runLater(() -> {
                updateProfilePositions(replayPlayerIsRed);
                updateBoardRotation(replayPlayerIsRed);
            });
        });
        
        // Register callback để nhận moves từ InfoHandler
        state.setReplayMovesCallback((moves) -> {
            Platform.runLater(() -> {
                setReplayMoves(moves);
            });
        });
        
        // Listen to replay player color changes (từ database)
        state.replayPlayerIsRedProperty().addListener((obs, oldVal, newVal) -> {
            this.replayPlayerIsRed = newVal;
            Platform.runLater(() -> {
                updateProfilePositions(replayPlayerIsRed);
                updateBoardRotation(replayPlayerIsRed);
            });
        });
    }
    
    private StackPane createReplayContent() {
        Pane root = new Pane();
        root.setPrefSize(1920, 1080);
        root.setStyle("-fx-background-color: transparent;");
        
        this.rootPane = root;
        
        // Background
        ImageView background = new ImageView(AssetHelper.image("bg.jpg"));
        background.setFitWidth(1920);
        background.setFitHeight(1080);
        background.setPreserveRatio(false);
        
        // Tạo profile containers
        playerProfile = createUserProfile("ySern", "do 100", true);
        opponentProfile = createUserProfile("ySern", "do 100", false);
        
        // Tạo captured pieces displays
        playerCapturedPieces = capturedPiecesManager.createCapturedPiecesDisplay(true);
        opponentCapturedPieces = capturedPiecesManager.createCapturedPiecesDisplay(false);
        
        // Cập nhật vị trí profile ban đầu (sẽ được cập nhật lại khi load game data)
        updateProfilePositions(replayPlayerIsRed);
        
        // Center: Game board area - setup giống GamePanel
        ImageView boardImage = new ImageView();
        boardImage.setFitWidth(923);
        boardImage.setFitHeight(923);
        boardImage.setPreserveRatio(true);
        boardImage.setSmooth(true);
        boardImage.setLayoutX((1920 - 923) / 2);
        boardImage.setLayoutY((1080 - 923) / 2);
        
        // Bind image với selectedBoardImagePath từ state
        state.selectedBoardImagePathProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                try {
                    boardImage.setImage(AssetHelper.image(newVal));
                } catch (Exception e) {
                    boardImage.setImage(null);
                }
            } else {
                boardImage.setImage(null);
            }
        });
        
        // Load ảnh ban đầu nếu đã có
        String initialBoardPath = state.getSelectedBoardImagePath();
        if (initialBoardPath != null && !initialBoardPath.isEmpty()) {
            try {
                boardImage.setImage(AssetHelper.image(initialBoardPath));
            } catch (Exception e) {
                // Ignore error
            }
        }
        
        // Placeholder background nếu chưa có ảnh
        Rectangle boardPlaceholder = new Rectangle(923, 923);
        boardPlaceholder.setFill(Color.color(0.9, 0.9, 0.9, 0.3));
        boardPlaceholder.setStroke(Color.color(0.3, 0.3, 0.3));
        boardPlaceholder.setStrokeWidth(2);
        boardPlaceholder.setArcWidth(20);
        boardPlaceholder.setArcHeight(20);
        boardPlaceholder.setLayoutX((1920 - 923) / 2);
        boardPlaceholder.setLayoutY((1080 - 923) / 2);
        
        // StackPane để đặt placeholder và image lên nhau
        boardContainer = new StackPane();
        boardContainer.setLayoutX((1920 - 923) / 2);
        boardContainer.setLayoutY((1080 - 923) / 2);
        boardContainer.setPrefSize(923, 923);
        boardContainer.setAlignment(Pos.CENTER);
        boardContainer.getChildren().addAll(boardPlaceholder, boardImage);
        
        // Xoay bàn cờ theo hướng người chơi trong trận đấu này
        // Sẽ được cập nhật lại khi load game data từ database
        // Sử dụng replayPlayerIsRed thay vì state.isPlayerRed() để hiển thị đúng theo trận đấu
        updateBoardRotation(replayPlayerIsRed);
        
        // Tạo và thêm các quân cờ vào bàn cờ
        // createChessPieces() trả về container có highlight layer và các quân cờ
        piecesContainer = chessBoardManager.createChessPieces();
        
        // Tắt khả năng di chuyển quân cờ trong replay (chỉ xem, không tương tác)
        disablePieceInteraction(piecesContainer);
        
        boardContainer.getChildren().add(piecesContainer);
        
        // Listener để reset quân cờ khi đổi bàn cờ (giống GamePanel)
        state.selectedBoardImagePathProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty() && boardContainer != null) {
                Platform.runLater(() -> {
                    resetChessPieces();
                    // Quan trọng: Sau khi reset pieces, cần xoay lại để chữ không bị ngược
                    updateBoardRotation(state.isPlayerRed());
                });
            }
        });
        
        // Top right: Move icon (ic_move.png) - luôn hiển thị để chỉ ra move history đang mở
        HBox topRightIcons = createTopRightIcons();
        topRightIcons.setLayoutX(1920 - 200);
        topRightIcons.setLayoutY(20);
        
        // Replay controls sẽ được thêm vào move panel thông qua MoveHistoryManager
        
        root.getChildren().addAll(background, playerProfile, playerCapturedPieces, opponentProfile, 
            opponentCapturedPieces, boardContainer, topRightIcons);
        
        StackPane content = new StackPane();
        content.setPrefSize(1920, 1080);
        content.getChildren().add(root);
        
        return content;
    }
    
    /**
     * Tạo top right icons (ic_move - luôn hiển thị)
     */
    private HBox createTopRightIcons() {
        HBox container = new HBox(15);
        container.setAlignment(Pos.CENTER_RIGHT);
        
        // Move icon (ic_move.png) - luôn hiển thị vì move history luôn mở trong replay
        ImageView moveIcon = new ImageView(AssetHelper.image("ic_move.png"));
        moveIcon.setFitWidth(85);
        moveIcon.setFitHeight(85);
        moveIcon.setPreserveRatio(true);
        moveIcon.setCursor(Cursor.HAND);
        
        // Hover effects
        ScaleTransition moveScale = new ScaleTransition(Duration.millis(200), moveIcon);
        moveIcon.setOnMouseEntered(e -> {
            moveScale.setToX(1.1);
            moveScale.setToY(1.1);
            moveScale.play();
        });
        moveIcon.setOnMouseExited(e -> {
            moveScale.setToX(1.0);
            moveScale.setToY(1.0);
            moveScale.play();
        });
        
        // Click handler - không làm gì vì move history luôn mở
        moveIcon.setOnMouseClicked(e -> {
            // Move history luôn mở trong replay, không cần toggle
            e.consume();
        });
        
        container.getChildren().add(moveIcon);
        
        return container;
    }
    
    /**
     * Tạo các nút điều khiển replay (back, next, quit) - nằm thẳng hàng
     */
    private HBox createReplayControls() {
        HBox container = new HBox(20);
        container.setAlignment(Pos.CENTER);
        container.setMouseTransparent(false);  // Đảm bảo container không chặn mouse events
        container.setPickOnBounds(true);
        
        // Back move button (ic_backmove.png)
        StackPane backButton = createReplayControlButton("ic_backmove.png", "Back");
        backButton.setOnMouseClicked(e -> {
            System.out.println("[ReplayPanel] Back button clicked, disabled=" + backButton.isDisabled() + ", currentMoveIndex=" + currentMoveIndex);
            if (!backButton.isDisabled()) {
                goToPreviousMove();
            } else {
                System.out.println("[ReplayPanel] Back button is disabled, ignoring click");
            }
            e.consume();
        });
        
        // Next move button (ic_nextMove.png)
        StackPane nextButton = createReplayControlButton("ic_nextMove.png", "Next");
        nextButton.setOnMouseClicked(e -> {
            System.out.println("[ReplayPanel] Next button clicked, disabled=" + nextButton.isDisabled() + ", currentMoveIndex=" + currentMoveIndex + ", moves.size()=" + replayMoves.size());
            if (!nextButton.isDisabled()) {
                goToNextMove();
            } else {
                System.out.println("[ReplayPanel] Next button is disabled, ignoring click");
            }
            e.consume();
        });
        
        // Quit button (ic_quit.png)
        StackPane quitButton = createReplayControlButton("ic_quit.png", "Quit");
        quitButton.setOnMouseClicked(e -> {
            System.out.println("[ReplayPanel] Quit button clicked");
            state.closeReplay();
            e.consume();
        });
        
        // Lưu reference để có thể update state sau này
        this.backButton = backButton;
        this.nextButton = nextButton;
        
        container.getChildren().addAll(backButton, nextButton, quitButton);
        
        // Update button states lần đầu - nhưng không disable nếu chưa có moves
        // Chỉ disable khi thực sự cần (sau khi có moves)
        if (replayMoves.isEmpty()) {
            // Chưa có moves, enable next button để có thể bấm (sẽ load moves)
            nextButton.setDisable(false);
            nextButton.setOpacity(1.0);
            backButton.setDisable(true);  // Back luôn disable khi ở đầu
            backButton.setOpacity(0.5);
        } else {
            updateButtonStates();
        }
        
        return container;
    }
    
    /**
     * Cập nhật trạng thái enable/disable của các nút replay
     */
    private void updateButtonStates() {
        Platform.runLater(() -> {
            if (backButton != null) {
                boolean shouldDisable = currentMoveIndex <= -1;
                backButton.setDisable(shouldDisable);
                backButton.setOpacity(shouldDisable ? 0.5 : 1.0);
                System.out.println("[ReplayPanel] Back button: disabled=" + shouldDisable + ", currentMoveIndex=" + currentMoveIndex);
            }
            if (nextButton != null) {
                boolean shouldDisable = (replayMoves.isEmpty() || currentMoveIndex >= replayMoves.size() - 1);
                nextButton.setDisable(shouldDisable);
                nextButton.setOpacity(shouldDisable ? 0.5 : 1.0);
                System.out.println("[ReplayPanel] Next button: disabled=" + shouldDisable + ", currentMoveIndex=" + currentMoveIndex + ", moves.size()=" + replayMoves.size());
            }
        });
    }
    
    /**
     * Tạo nút điều khiển replay
     */
    private StackPane createReplayControlButton(String iconPath, String tooltip) {
        StackPane button = new StackPane();
        button.setPrefSize(80, 80);
        
        ImageView icon = new ImageView(AssetHelper.image(iconPath));
        icon.setFitWidth(70);
        icon.setFitHeight(70);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        icon.setMouseTransparent(true);  // Icon không nhận mouse events, chỉ button nhận
        
        button.getChildren().add(icon);
        button.setCursor(Cursor.HAND);
        button.setPickOnBounds(true);
        button.setMouseTransparent(false);
        button.setDisable(false);  // Mặc định enable
        
        // Hover effect với scale
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), button);
        scaleIn.setToX(1.1);
        scaleIn.setToY(1.1);
        
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), button);
        scaleOut.setToX(1.0);
        scaleOut.setToY(1.0);
        
        button.setOnMouseEntered(e -> {
            scaleOut.stop();
            scaleIn.setFromX(button.getScaleX());
            scaleIn.setFromY(button.getScaleY());
            scaleIn.play();
        });
        button.setOnMouseExited(e -> {
            scaleIn.stop();
            scaleOut.setFromX(button.getScaleX());
            scaleOut.setFromY(button.getScaleY());
            scaleOut.play();
        });
        
        return button;
    }
    
    /**
     * Lùi lại một nước đi
     */
    private void goToPreviousMove() {
        if (currentMoveIndex > -1) {
            currentMoveIndex--;
            applyMoveToPosition(currentMoveIndex);
            updateMoveHistoryDisplay();
            updateButtonStates();
            System.out.println("[ReplayPanel] Moved to move index: " + currentMoveIndex);
        }
    }
    
    /**
     * Tiến tới một nước đi
     */
    private void goToNextMove() {
        if (currentMoveIndex < replayMoves.size() - 1) {
            currentMoveIndex++;
            applyMoveToPosition(currentMoveIndex);
            updateMoveHistoryDisplay();
            updateButtonStates();
            System.out.println("[ReplayPanel] Moved to move index: " + currentMoveIndex);
        }
    }
    
    /**
     * Áp dụng nước đi đến vị trí chỉ định
     * Reset bàn cờ về vị trí ban đầu, rồi apply tất cả moves từ đầu đến moveIndex
     * LƯU Ý: KHÔNG clear move history vì đã hiển thị sẵn từ setReplayMoves()
     */
    private void applyMoveToPosition(int moveIndex) {
        if (piecesContainer == null || boardContainer == null) {
            System.err.println("[ReplayPanel] piecesContainer or boardContainer is null");
            return;
        }
        
        // Reset board to initial position (KHÔNG clear move history)
        resetChessPieces();
        capturedPiecesManager.resetCapturedPieces();
        // KHÔNG gọi moveHistoryManager.clearMoveHistory() - giữ nguyên để user thấy tất cả moves
        
        // Apply all moves up to moveIndex (chỉ cập nhật board, không thêm vào history)
        for (int i = 0; i <= moveIndex && i < replayMoves.size(); i++) {
            ReplayMove move = replayMoves.get(i);
            applySingleMoveToBoard(move);  // Dùng method không thêm vào history
        }
    }
    
    /**
     * Áp dụng một nước đi lên bàn cờ MÀ KHÔNG thêm vào move history
     * (Dùng khi navigate - move history đã hiển thị sẵn)
     */
    private void applySingleMoveToBoard(ReplayMove move) {
        if (piecesContainer == null) return;
        
        double boardSize = 923.0;
        double cellWidth = boardSize / 9.0;
        double cellHeight = boardSize / 10.0;
        
        // Tìm quân cờ tại vị trí from
        ImageView pieceToMove = null;
        ImageView capturedPiece = null;
        
        for (javafx.scene.Node node : piecesContainer.getChildren()) {
            if (node instanceof ImageView) {
                ImageView imgView = (ImageView) node;
                if (imgView.getUserData() instanceof ChessBoardManager.PieceInfo) {
                    int pieceRow = (int) Math.round(imgView.getLayoutY() / cellHeight);
                    int pieceCol = (int) Math.round(imgView.getLayoutX() / cellWidth);
                    pieceRow = Math.max(0, Math.min(9, pieceRow));
                    pieceCol = Math.max(0, Math.min(8, pieceCol));
                    
                    if (pieceRow == move.fromRow && pieceCol == move.fromCol) {
                        pieceToMove = imgView;
                    }
                    if (pieceRow == move.toRow && pieceCol == move.toCol) {
                        capturedPiece = imgView;
                    }
                }
            }
        }
        
        if (pieceToMove == null) {
            return;  // Silent fail - quân cờ có thể đã bị ăn
        }
        
        // Nếu có quân cờ ở vị trí đích, xóa nó (ăn quân)
        if (capturedPiece != null && capturedPiece != pieceToMove) {
            ChessBoardManager.PieceInfo capInfo = (ChessBoardManager.PieceInfo) capturedPiece.getUserData();
            if (capInfo != null) {
                capturedPiecesManager.addCapturedPiece(capInfo.color, capInfo.pieceType);
            }
            piecesContainer.getChildren().remove(capturedPiece);
        }
        
        // Di chuyển quân cờ đến vị trí mới
        double newX = move.toCol * cellWidth + (cellWidth - pieceToMove.getFitWidth()) / 2;
        double newY = move.toRow * cellHeight + (cellHeight - pieceToMove.getFitHeight()) / 2;
        pieceToMove.setLayoutX(newX);
        pieceToMove.setLayoutY(newY);
        
        // KHÔNG thêm vào move history - đã hiển thị sẵn từ setReplayMoves()
    }
    
    /**
     * Áp dụng một nước đi lên bàn cờ (tham khảo từ GamePanel.applyOpponentMove)
     */
    private void applySingleMove(ReplayMove move) {
        if (piecesContainer == null) return;
        
        double boardSize = 923.0;
        double cellWidth = boardSize / 9.0;
        double cellHeight = boardSize / 10.0;
        
        // Tìm quân cờ tại vị trí from
        ImageView pieceToMove = null;
        ImageView capturedPiece = null;
        
        for (javafx.scene.Node node : piecesContainer.getChildren()) {
            if (node instanceof ImageView) {
                ImageView imgView = (ImageView) node;
                if (imgView.getUserData() instanceof ChessBoardManager.PieceInfo) {
                    int pieceRow = (int) Math.round(imgView.getLayoutY() / cellHeight);
                    int pieceCol = (int) Math.round(imgView.getLayoutX() / cellWidth);
                    pieceRow = Math.max(0, Math.min(9, pieceRow));
                    pieceCol = Math.max(0, Math.min(8, pieceCol));
                    
                    if (pieceRow == move.fromRow && pieceCol == move.fromCol) {
                        pieceToMove = imgView;
                    }
                    if (pieceRow == move.toRow && pieceCol == move.toCol) {
                        capturedPiece = imgView;
                    }
                }
            }
        }
        
        if (pieceToMove == null) {
            System.err.println("[ReplayPanel] Could not find piece at (" + move.fromRow + "," + move.fromCol + ")");
            return;
        }
        
        // Nếu có quân cờ ở vị trí đích, xóa nó (ăn quân)
        if (capturedPiece != null && capturedPiece != pieceToMove) {
            ChessBoardManager.PieceInfo capInfo = (ChessBoardManager.PieceInfo) capturedPiece.getUserData();
            if (capInfo != null) {
                // Thêm vào captured pieces display
                capturedPiecesManager.addCapturedPiece(capInfo.color, capInfo.pieceType);
            }
            piecesContainer.getChildren().remove(capturedPiece);
        }
        
        // Di chuyển quân cờ đến vị trí mới
        double newX = move.toCol * cellWidth + (cellWidth - pieceToMove.getFitWidth()) / 2;
        double newY = move.toRow * cellHeight + (cellHeight - pieceToMove.getFitHeight()) / 2;
        pieceToMove.setLayoutX(newX);
        pieceToMove.setLayoutY(newY);
        
        // Thêm vào move history
        String capturedInfo = "";
        if (capturedPiece != null && capturedPiece != pieceToMove && move.capturedPieceType != null) {
            capturedInfo = String.format(" (captured %s %s)", move.capturedColor, move.capturedPieceType);
        }
        moveHistoryManager.addMove(move.color, move.pieceType, move.fromRow, move.fromCol, 
            move.toRow, move.toCol, capturedInfo);
    }
    
    /**
     * Cập nhật hiển thị move history để highlight nước đi hiện tại
     */
    private void updateMoveHistoryDisplay() {
        // Highlight nước đi hiện tại trong move history panel
        // currentMoveIndex = -1 nghĩa là ở vị trí ban đầu (chưa có nước nào)
        // currentMoveIndex = 0 nghĩa là đã chơi nước đầu tiên
        if (moveHistoryManager != null) {
            // Highlight move tại index (currentMoveIndex + 1 - 1 = currentMoveIndex)
            // Vì move index trong history là 0-based và tương ứng với currentMoveIndex
            int highlightIndex = currentMoveIndex;
            if (highlightIndex >= 0 && highlightIndex < replayMoves.size()) {
                moveHistoryManager.highlightMove(highlightIndex);
            } else {
                // Nếu ở vị trí ban đầu (-1), không highlight gì
                moveHistoryManager.highlightMove(-1);
            }
        }
    }
    
    /**
     * Reset replay state
     */
    private void resetReplayState() {
        currentMoveIndex = -1;
        replayMoves.clear();
        resetChessPieces();
        capturedPiecesManager.resetCapturedPieces();
        moveHistoryManager.clearMoveHistory();
    }
    
    /**
     * Set moves cho replay (được gọi từ InfoHandler khi fetch được moves từ backend)
     */
    public void setReplayMoves(java.util.List<ReplayMove> moves) {
        System.out.println("[ReplayPanel] ========================================");
        System.out.println("[ReplayPanel] setReplayMoves() called with " + (moves != null ? moves.size() : 0) + " moves");
        
        if (moves == null || moves.isEmpty()) {
            System.err.println("[ReplayPanel] WARNING: No moves provided!");
            return;
        }
        
        this.replayMoves.clear();
        this.replayMoves.addAll(moves);
        this.currentMoveIndex = -1;  // Reset về vị trí ban đầu
        
        // Reset board về trạng thái ban đầu
        resetChessPieces();
        capturedPiecesManager.resetCapturedPieces();
        moveHistoryManager.clearMoveHistory();
        
        // Thêm TẤT CẢ moves vào move history panel để người dùng thấy toàn bộ lịch sử
        System.out.println("[ReplayPanel] Adding all " + replayMoves.size() + " moves to history panel...");
        for (int i = 0; i < replayMoves.size(); i++) {
            ReplayMove move = replayMoves.get(i);
            String capturedInfo = "";
            if (move.capturedPieceType != null && !move.capturedPieceType.isEmpty()) {
                capturedInfo = String.format(" (captured %s %s)", move.capturedColor, move.capturedPieceType);
            }
            moveHistoryManager.addMove(move.color, move.pieceType, move.fromRow, move.fromCol, 
                move.toRow, move.toCol, capturedInfo);
        }
        
        // Cập nhật button states sau khi có moves
        updateButtonStates();
        
        // Highlight move hiện tại (nếu có)
        updateMoveHistoryDisplay();
        
        System.out.println("[ReplayPanel] Set " + moves.size() + " moves for replay, currentMoveIndex=" + currentMoveIndex);
        System.out.println("[ReplayPanel] ========================================");
    }
    
    /**
     * Load game data for replay
     * Fetch từ backend để biết người chơi chơi màu gì và các nước đi
     */
    public void loadReplay(String gameId) {
        this.currentGameId = gameId;
        resetReplayState();
        
        // Request replay data từ backend (REPLAY_REQUEST)
        // InfoHandler sẽ tự động cập nhật replayPlayerIsRed và gọi setReplayMoves() khi parse response
        try {
            NetworkManager networkManager = NetworkManager.getInstance();
            if (networkManager != null && networkManager.info() != null) {
                // Gọi REPLAY_REQUEST để lấy full game data với moves
                networkManager.info().requestReplayData(gameId);
            }
        } catch (Exception e) {
            System.err.println("[ReplayPanel] Error requesting replay data: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[ReplayPanel] Loading replay for game: " + gameId);
    }
    
    // Helper methods from GamePanel
    private HBox createUserProfile(String username, String level, boolean isTopLeft) {
        // Copy from GamePanel - simplified version without timer
        HBox profile = new HBox(15);
        profile.setAlignment(Pos.CENTER_LEFT);

        StackPane avatarContainer = new StackPane();
        
        ImageView avaProfile = new ImageView(AssetHelper.image("ava_profile.png"));
        avaProfile.setFitWidth(450);
        avaProfile.setFitHeight(120);
        avaProfile.setPreserveRatio(true);
        
        ImageView avatarInSquare = new ImageView();
        avatarInSquare.setFitWidth(130);
        avatarInSquare.setFitHeight(130);
        avatarInSquare.setPreserveRatio(false);
        avatarInSquare.setSmooth(true);
        
        Rectangle clip = new Rectangle(130, 130);
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        avatarInSquare.setClip(clip);
        
        Rectangle squareFrame = new Rectangle(130, 130);
        squareFrame.setFill(Color.TRANSPARENT);
        squareFrame.setStroke(Color.web("#A65252"));
        squareFrame.setStrokeWidth(6);
        squareFrame.setArcWidth(18);
        squareFrame.setArcHeight(18);
        
        avatarContainer.getChildren().addAll(avaProfile, avatarInSquare, squareFrame);
        avatarContainer.setAlignment(Pos.CENTER_LEFT);

        Pane textSection = new Pane();
        textSection.setPrefSize(250, 60);
        
        Rectangle brushStroke = new Rectangle(200, 50);
        brushStroke.setFill(Color.TRANSPARENT);
        brushStroke.setArcWidth(10);
        brushStroke.setArcHeight(10);
        brushStroke.setLayoutY(-5);
        
        Label usernameLabel = new Label();
        Label eloLabel = new Label();
        
        if (isTopLeft) {
            usernameLabel.textProperty().bind(state.usernameProperty());
            eloLabel.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                    () -> "elo " + state.getClassicalElo(),
                    state.classicalEloProperty()
                )
            );
        } else {
            usernameLabel.textProperty().bind(state.opponentUsernameProperty());
            eloLabel.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                    () -> "elo " + state.getOpponentElo(),
                    state.opponentEloProperty()
                )
            );
        }
        
        usernameLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 50px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        usernameLabel.setLayoutX(10);
        usernameLabel.setLayoutY(0);
        
        eloLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 40px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        eloLabel.setLayoutX(10);
        eloLabel.setLayoutY(35);
        
        textSection.getChildren().addAll(brushStroke, usernameLabel, eloLabel);
        
        profile.getChildren().addAll(avatarContainer, textSection);
        
        return profile;
    }
    
    private void updateProfilePositions(boolean playerIsRed) {
        if (playerProfile == null || opponentProfile == null) return;
        
        if (playerIsRed) {
            // Player is red (top-left), opponent is black (bottom-right)
            playerProfile.setLayoutX(50);
            playerProfile.setLayoutY(50);
            opponentProfile.setLayoutX(1920 - 500);
            opponentProfile.setLayoutY(1080 - 170);
            
            playerCapturedPieces.setLayoutX(50);
            playerCapturedPieces.setLayoutY(180);
            opponentCapturedPieces.setLayoutX(1920 - 500);
            opponentCapturedPieces.setLayoutY(1080 - 340);
        } else {
            // Player is black (bottom-right), opponent is red (top-left)
            playerProfile.setLayoutX(1920 - 500);
            playerProfile.setLayoutY(1080 - 170);
            opponentProfile.setLayoutX(50);
            opponentProfile.setLayoutY(50);
            
            playerCapturedPieces.setLayoutX(1920 - 500);
            playerCapturedPieces.setLayoutY(1080 - 340);
            opponentCapturedPieces.setLayoutX(50);
            opponentCapturedPieces.setLayoutY(180);
        }
    }
    
    /**
     * Xoay bàn cờ để quân cờ của người chơi luôn ở phía dưới
     * Logic giống GamePanel: 
     * - Nếu player là red: boardRotation = 180.0, pieceRotation = 180.0
     * - Nếu player là black: boardRotation = 0.0, pieceRotation = 0.0
     */
    private void updateBoardRotation(boolean playerIsRed) {
        if (boardContainer == null) return;
        
        System.out.println("[ReplayPanel] updateBoardRotation: playerIsRed=" + playerIsRed);
        
        // Xoay boardContainer - điều này sẽ xoay tất cả children (boardImage, piecesContainer)
        // Logic: nếu player là red, xoay 180 độ; nếu là black, không xoay
        double boardRotation = playerIsRed ? 180.0 : 0.0;
        boardContainer.setRotate(boardRotation);
        
        // QUAN TRỌNG: Khi bàn cờ xoay 180 độ, quân cờ cũng bị xoay làm chữ ngược
        // Cần xoay ngược lại các quân cờ để chữ luôn đọc được
        // Logic: nếu board xoay 180 (player red), thì quân cờ cũng xoay 180 để chữ đúng
        if (piecesContainer != null) {
            double pieceRotation = playerIsRed ? 180.0 : 0.0;
            System.out.println("[ReplayPanel] Setting piece rotation: " + pieceRotation);
            for (javafx.scene.Node node : piecesContainer.getChildren()) {
                if (node instanceof ImageView) {
                    ImageView piece = (ImageView) node;
                    piece.setRotate(pieceRotation);
                }
            }
        }
    }
    
    @Override
    public void resetChessPieces() {
        // Xóa quân cờ cũ nếu có
        if (piecesContainer != null && boardContainer != null) {
            boardContainer.getChildren().remove(piecesContainer);
        }
        
        // Tạo lại quân cờ ở vị trí ban đầu
        if (chessBoardManager != null && boardContainer != null) {
            piecesContainer = chessBoardManager.createChessPieces();
            // Tắt khả năng tương tác (chỉ xem replay)
            disablePieceInteraction(piecesContainer);
            boardContainer.getChildren().add(piecesContainer);
            
            // QUAN TRỌNG: Phải gọi updateBoardRotation() sau khi tạo mới quân cờ
            // để xoay các quân cờ đúng hướng (chữ không bị ngược)
            updateBoardRotation(replayPlayerIsRed);
        }
    }
    
    @Override
    public String getCurrentTurn() {
        return currentTurn;
    }
    
    @Override
    public void setCurrentTurn(String turn) {
        this.currentTurn = turn;
    }
    
    /**
     * Tắt khả năng tương tác với quân cờ (chỉ xem replay, không cho di chuyển)
     */
    private void disablePieceInteraction(Pane piecesContainer) {
        if (piecesContainer == null) return;
        
        // Tắt mouse events cho container chính
        piecesContainer.setMouseTransparent(true);
        piecesContainer.setPickOnBounds(false);
        
        // Tắt mouse events cho tất cả các children (quân cờ, click layer, highlight layer)
        for (javafx.scene.Node node : piecesContainer.getChildren()) {
            node.setMouseTransparent(true);
            node.setPickOnBounds(false);
            
            // Nếu là ImageView (quân cờ), tắt cursor và events
            if (node instanceof ImageView) {
                ImageView piece = (ImageView) node;
                piece.setCursor(Cursor.DEFAULT);
                piece.setOnMouseClicked(null);
                piece.setOnMousePressed(null);
                piece.setOnMouseDragged(null);
                piece.setOnMouseReleased(null);
            }
            
            // Nếu là Pane (click layer hoặc highlight layer), tắt events cho children
            if (node instanceof Pane) {
                Pane pane = (Pane) node;
                pane.setMouseTransparent(true);
                pane.setPickOnBounds(false);
                for (javafx.scene.Node child : pane.getChildren()) {
                    child.setMouseTransparent(true);
                    child.setPickOnBounds(false);
                    if (child instanceof Rectangle) {
                        Rectangle rect = (Rectangle) child;
                        rect.setOnMouseClicked(null);
                    }
                    if (child instanceof ImageView) {
                        ((ImageView) child).setCursor(Cursor.DEFAULT);
                    }
                }
            }
        }
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
