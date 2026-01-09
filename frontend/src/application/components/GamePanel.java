package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import javafx.scene.Cursor;
import java.util.Random;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.effect.DropShadow;

public class GamePanel extends StackPane implements IGamePanel {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    
    // Managers
    private ChessBoardManager chessBoardManager;
    private TimerManager timerManager;
    private CapturedPiecesManager capturedPiecesManager;
    private MoveHistoryManager moveHistoryManager;
    private DialogManager dialogManager;
    private ChatManager chatManager;
    
    // Game End Checker
    private application.game.GameEndChecker gameEndChecker;
    
    // Flag để tránh gửi AI_QUIT nhiều lần
    private boolean aiQuitSent = false;
    
    // Core fields
    private Pane rootPane = null;  // Lưu reference đến root pane để thêm UI
    private Pane piecesContainer = null;  // Lưu reference đến container chứa quân cờ
    private StackPane boardContainer = null;  // Lưu reference đến board container
    private ImageView boardImage = null;  // Reference đến board image để xoay riêng
    private String currentTurn = "red";  // Lượt hiện tại: "red" đi trước, sau đó "black"
    private boolean isBoardFlipped = false;  // True nếu player là black (board cần flip)
    private VBox leftIcons = null;  // Lưu reference đến left icons container để cập nhật khi game mode thay đổi

    public GamePanel(UIState state) {
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
        
        // Khởi tạo các Manager KHÔNG cần rootPane trước
        // (cần cho createGameContent(): capturedPiecesManager, timerManager)
        this.capturedPiecesManager = new CapturedPiecesManager(state, this);
        this.timerManager = new TimerManager(state, this);
        this.chessBoardManager = new ChessBoardManager(state, this);
        
        // Tạo gameContent - rootPane được set bên trong createGameContent()
        StackPane gameContent = createGameContent();
        gameContent.setLayoutX(0);
        gameContent.setLayoutY(0);
        gameContent.setPickOnBounds(true);
        gameContent.setMouseTransparent(false);
        
        // Bây giờ rootPane đã được set đúng trong createGameContent()
        // Khởi tạo các Manager CẦN rootPane
        this.moveHistoryManager = new MoveHistoryManager(state, this, rootPane);
        this.chatManager = new ChatManager(state, this, rootPane);
        this.dialogManager = new DialogManager(state, this, rootPane);
        
        // Khởi tạo GameEndChecker
        this.gameEndChecker = new application.game.GameEndChecker();
        
        // Listener để cập nhật leftIcons khi game mode thay đổi (sau khi dialogManager được khởi tạo)
        state.currentGameModeProperty().addListener((obs, oldVal, newVal) -> {
            updateLeftIcons();
        });
        
        // Listener để cập nhật leftIcons khi opponent username thay đổi (quan trọng cho AI game)
        state.opponentUsernameProperty().addListener((obs, oldVal, newVal) -> {
            updateLeftIcons();
        });
        
        // Cập nhật leftIcons lần đầu để đảm bảo UI đúng khi game được mở
        Platform.runLater(() -> {
            updateLeftIcons();
        });
        
        // Thiết lập callbacks giữa các Manager
        setupManagerCallbacks();
        
        container.getChildren().add(gameContent);
        getChildren().add(container);
        
        // Bind visibility
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.IN_GAME)
                .and(state.gameVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation
        state.gameVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.IN_GAME) {
                // Reset tất cả các panel trước khi fade in
                Platform.runLater(() -> {
                    resetAllGamePanels();
                    ensurePiecesVisible();
                    timerManager.initializeTimers();
                    updateLeftIcons(); // Đảm bảo leftIcons được cập nhật khi game becomes visible
                });
                fadeTo(1);
            } else {
                timerManager.stopAllTimers();
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.IN_GAME && state.isGameVisible()) {
                // Reset tất cả các panel trước khi fade in
                Platform.runLater(() -> {
                    resetAllGamePanels();
                    ensurePiecesVisible();
                    timerManager.initializeTimers();
                    updateLeftIcons(); // Đảm bảo leftIcons được cập nhật khi vào game
                });
                fadeTo(1);
            } else {
                timerManager.stopAllTimers();
                fadeTo(0);
            }
        });
    }
    
    /**
     * Thiết lập callbacks giữa các Manager
     */
    private void setupManagerCallbacks() {
        // ChessBoardManager callbacks
        chessBoardManager.setOnPieceCaptured((color, pieceType) -> {
            capturedPiecesManager.addCapturedPiece(color, pieceType);
        });
        
        chessBoardManager.setOnMoveAddedWithDetails((moveDetails) -> {
            // Parse moveDetails: "color|pieceType|fromRow|fromCol|toRow|toCol|capturedInfo"
            // capturedInfo có thể rỗng nếu không có captured piece
            String[] parts = moveDetails.split("\\|");
            if (parts.length >= 6) {
                String color = parts[0];
                String pieceType = parts[1];
                int fromRow = Integer.parseInt(parts[2]);
                int fromCol = Integer.parseInt(parts[3]);
                int toRow = Integer.parseInt(parts[4]);
                int toCol = Integer.parseInt(parts[5]);
                // capturedInfo có thể là phần thứ 7 hoặc rỗng nếu không có
                String capturedInfo = (parts.length >= 7) ? parts[6] : "";
                moveHistoryManager.addMove(color, pieceType, fromRow, fromCol, toRow, toCol, capturedInfo);
                
                // Check game end after move
                Platform.runLater(() -> {
                    checkGameEndAfterMove(capturedInfo.contains("captured"));
                });
            } else {
                System.err.println("[GamePanel] Invalid moveDetails format: " + moveDetails + " (expected at least 6 parts, got " + parts.length + ")");
            }
        });
        
        // TimerManager timeout callback
        timerManager.setTimeoutCallback((losingPlayerColor) -> {
            // losingPlayerColor là "red" hoặc "black" - người chơi hết thời gian
            System.out.println("[GamePanel] Timeout callback: losingPlayerColor=" + losingPlayerColor);
            
            // Xác định người chơi hiện tại
            boolean currentPlayerIsRed = state.isPlayerRed();
            String currentPlayerColor = currentPlayerIsRed ? "red" : "black";
            
            // Nếu người chơi hiện tại là người hết thời gian → thua
            // Nếu đối thủ hết thời gian → thắng
            boolean isCurrentPlayerLosing = losingPlayerColor.equals(currentPlayerColor);
            
            boolean isAIGame = isAIGame();
            if (isCurrentPlayerLosing) {
                // Người chơi hiện tại hết thời gian → thua
                System.out.println("[GamePanel] Current player (" + currentPlayerColor + ") ran out of time - LOSE");
                if (isAIGame) {
                    dialogManager.showGameResultWithCustomText("You lose", 0);
                } else {
                    dialogManager.showGameResult(false);
                }
            } else {
                // Đối thủ hết thời gian → thắng
                System.out.println("[GamePanel] Opponent (" + losingPlayerColor + ") ran out of time - WIN");
                if (isAIGame) {
                    dialogManager.showGameResultWithCustomText("You win", 0);
                } else {
                    dialogManager.showGameResult(true);
                }
            }
            
            // TODO: Có thể gửi message đến server để đối thủ cũng nhận được thông báo
            // networkManager.game().sendTimeoutLoss(losingPlayerColor);
        });
        
        // Listen to game action triggers from UIState
        state.gameActionTriggerProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("[GamePanel] gameActionTrigger changed: oldVal=" + oldVal + ", newVal=" + newVal);
            
            if (newVal == null || newVal.isEmpty()) {
                System.out.println("[GamePanel] Ignoring empty/null trigger");
                return;
            }
            
            String result = state.getGameActionResult();
            System.out.println("[GamePanel] Processing trigger=" + newVal + ", result=" + result);
            
            switch (newVal) {
                case "game_result":
                    System.out.println("[GamePanel] Handling game_result case");
                    boolean isAIGame = isAIGame();
                    if ("win".equals(result)) {
                        System.out.println("[GamePanel] Calling showGameResult - WIN");
                        if (isAIGame) {
                            dialogManager.showGameResultWithCustomText("You win", 0);
                        } else {
                            dialogManager.showGameResult(true);
                        }
                    } else if ("lose".equals(result)) {
                        System.out.println("[GamePanel] Calling showGameResult - LOSE");
                        if (isAIGame) {
                            dialogManager.showGameResultWithCustomText("You lose", 0);
                        } else {
                            dialogManager.showGameResult(false);
                        }
                    } else if ("draw".equals(result)) {
                        System.out.println("[GamePanel] Calling showGameResult - DRAW");
                        if (isAIGame) {
                            dialogManager.showGameResultWithCustomText("Draw", 0);
                        } else {
                            dialogManager.showGameResultDraw();
                        }
                    } else {
                        System.out.println("[GamePanel] Unknown result: " + result);
                    }
                    break;
                case "draw_request":
                    System.out.println("[GamePanel] Handling draw_request case");
                    if ("received".equals(result)) {
                        System.out.println("[GamePanel] Calling showDrawRequestReceived()");
                        dialogManager.showDrawRequestReceived();
                    } else if ("hide".equals(result)) {
                        System.out.println("[GamePanel] Calling hideDrawRequestReceived()");
                        dialogManager.hideDrawRequestReceived();
                    }
                    break;
                case "chat_message":
                    if (result != null && !result.isEmpty()) {
                        // Message từ opponent (nhận từ server), hiển thị ở avatar opponent
                        chatManager.showChatPopup(result, false);
                    }
                    break;
                case "game_restore":
                    System.out.println("[GamePanel] Handling game_restore case");
                    if (result != null && !result.isEmpty()) {
                        // Format: "|currentTurn|xfen|movesJson"
                        // Parse: opponent|gameMode|isRed|currentTurn|xfen|movesJson
                        String[] parts = result.split("\\|", -1);
                        if (parts.length >= 5) {
                            String opponent = parts.length > 0 ? parts[0] : "";
                            String gameMode = parts.length > 1 ? parts[1] : "classical";
                            boolean isRed = parts.length > 2 && "true".equals(parts[2]);
                            String currentTurn = parts.length > 3 ? parts[3] : "red";
                            String xfen = parts.length > 4 ? parts[4] : "";
                            String movesJson = parts.length > 5 ? parts[5] : "";
                            
                            System.out.println("[GamePanel] Restoring game: opponent=" + opponent + 
                                ", gameMode=" + gameMode + ", isRed=" + isRed + ", currentTurn=" + currentTurn);
                            
                            // Set current turn
                            setCurrentTurn(currentTurn);
                            
                            // Restore board from xfen and moves if available
                            // Note: ChessBoardManager will restore from moves when game state is updated
                            if (!xfen.isEmpty() && !movesJson.isEmpty()) {
                                System.out.println("[GamePanel] Restoring board from xfen and moves");
                                // Board will be restored when moves are applied
                            }
                            
                            // Update timers based on current turn
                            timerManager.updateTimersOnTurnChange();
                        }
                    }
                    break;
                case "suggest_move":
                    if (result != null && !result.isEmpty()) {
                        // Format: "fromRow_fromCol_toRow_toCol"
                        String[] parts = result.split("_");
                        if (parts.length == 4) {
                            int fromRow = Integer.parseInt(parts[0]);
                            int fromCol = Integer.parseInt(parts[1]);
                            int toRow = Integer.parseInt(parts[2]);
                            int toCol = Integer.parseInt(parts[3]);
                            // Highlight suggest move
                            chessBoardManager.highlightSuggestMove(fromRow, fromCol, toRow, toCol);
                        }
                    }
                    break;
                default:
                    System.out.println("[GamePanel] Unknown trigger: " + newVal);
            }
        });
        
        chessBoardManager.setOnTurnChanged(() -> {
            timerManager.updateTimersOnTurnChange();
        });
        
        // Callback để gửi MOVE message đến server khi người chơi di chuyển quân cờ
        chessBoardManager.setOnMoveMade((fromRow, fromCol, toRow, toCol, piece, captured) -> {
            try {
                // Gửi MOVE message đến server
                // Convert: Frontend row (0=top đỏ) → Backend row (0=top đen)
                // Frontend: row 0 = top (đỏ), row 9 = bottom (đen)
                // Backend: row 0 = top (đen), row 9 = bottom (đỏ)
                // Công thức: backendRow = 9 - frontendRow
                int backendFromRow = 9 - fromRow;
                int backendToRow = 9 - toRow;
                
                application.network.NetworkManager networkManager = application.network.NetworkManager.getInstance();
                if (networkManager != null) {
                    networkManager.game().sendMove(fromCol, backendFromRow, toCol, backendToRow, piece, captured, null);
                    System.out.println("[GamePanel] Sent MOVE to server: " + piece + 
                        " from FE(" + fromRow + "," + fromCol + ") BE(" + backendFromRow + "," + fromCol + 
                        ") to FE(" + toRow + "," + toCol + ") BE(" + backendToRow + "," + toCol + ")");
                }
            } catch (Exception e) {
                System.err.println("[GamePanel] Error sending MOVE: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Register callback trong UIState để nhận move từ server
        state.setOpponentMoveCallback((fromCol, fromRow, toCol, toRow) -> {
            applyOpponentMove(fromCol, fromRow, toCol, toRow);
        });
    }
    
    // Lưu reference đến profile containers để có thể đổi vị trí
    private HBox playerProfile = null;
    private HBox opponentProfile = null;
    private VBox playerCapturedPieces = null;
    private VBox opponentCapturedPieces = null;
    
    private StackPane createGameContent() {
        Pane root = new Pane();
        root.setPrefSize(1920, 1080);
        root.setStyle("-fx-background-color: transparent;");
        
        // Lưu reference để dùng cho chat UI
        this.rootPane = root;
        
        // Background (có thể dùng background2.png nếu có)
        ImageView background = new ImageView(AssetHelper.image("bg.jpg"));
        background.setFitWidth(1920);
        background.setFitHeight(1080);
        background.setPreserveRatio(false);
        
        // Tạo profile containers (vị trí sẽ được cập nhật dựa trên playerIsRed)
        playerProfile = createUserProfile("ySern", "do 100", true);
        opponentProfile = createUserProfile("ySern", "do 100", false);
        
        // Tạo captured pieces displays
        playerCapturedPieces = capturedPiecesManager.createCapturedPiecesDisplay(true);
        opponentCapturedPieces = capturedPiecesManager.createCapturedPiecesDisplay(false);
        
        // Cập nhật vị trí profile ban đầu
        updateProfilePositions(state.isPlayerRed());
        
        // Listener để cập nhật vị trí profile khi playerIsRed thay đổi
        state.playerIsRedProperty().addListener((obs, oldVal, newVal) -> {
            updateProfilePositions(newVal);
        });
        
        // Left side: Timers - đặt cạnh khung đen bên trái
        VBox timersContainer = timerManager.createTimersContainer();
        double boardX = (1920 - 923) / 2;  // Vị trí X của khung đen
        timersContainer.setLayoutX(boardX - 125);  // Đặt bên trái khung đen, cách 150px
        timersContainer.setLayoutY((1080 - 923) / 2 + 350);  // Tăng từ 50 lên 150 (lùi xuống 100px)
        
        // Left side: Icons (resign, draw hoặc quit) - đặt dưới timers, cạnh khung đen
        this.leftIcons = createLeftIcons();
        this.leftIcons.setLayoutX(boardX - 65);  // Cùng vị trí X với timers
        this.leftIcons.setLayoutY((1080 - 923) / 2 + 750);  // Đặt dưới timers
        
        // Top right: Chat and token icons
        HBox topRightIcons = createTopRightIcons();
        topRightIcons.setLayoutX(1920 - 200);  // Dịch sang trái (từ 150 xuống 200)
        topRightIcons.setLayoutY(20);
        getChildren().add(topRightIcons);
        
        // Center: Game board area - hiển thị ảnh bàn cờ đã chọn
        this.boardImage = new ImageView();
        this.boardImage.setFitWidth(923);
        this.boardImage.setFitHeight(923);
        this.boardImage.setPreserveRatio(true);
        this.boardImage.setSmooth(true);
        this.boardImage.setLayoutX((1920 - 923) / 2);
        this.boardImage.setLayoutY((1080 - 923) / 2);
        
        // Bind image với selectedBoardImagePath từ state
        state.selectedBoardImagePathProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                try {
                    this.boardImage.setImage(AssetHelper.image(newVal));
                } catch (Exception e) {
                    this.boardImage.setImage(null);
                }
            } else {
                this.boardImage.setImage(null);
            }
        });
        
        // Load ảnh ban đầu nếu đã có
        String initialBoardPath = state.getSelectedBoardImagePath();
        if (initialBoardPath != null && !initialBoardPath.isEmpty()) {
            try {
                this.boardImage.setImage(AssetHelper.image(initialBoardPath));
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
        StackPane boardContainer = new StackPane();
        boardContainer.setLayoutX((1920 - 923) / 2);
        boardContainer.setLayoutY((1080 - 923) / 2);
        boardContainer.setPrefSize(923, 923);
        boardContainer.setAlignment(Pos.CENTER);
        boardContainer.getChildren().addAll(boardPlaceholder, this.boardImage);
        
        // Lưu reference để có thể reset quân cờ
        this.boardContainer = boardContainer;
        
        // Xoay bàn cờ theo hướng người chơi: nếu player là black, xoay 180 độ
        // để phía cờ của người chơi luôn ở dưới
        state.playerIsRedProperty().addListener((obs, oldVal, newVal) -> {
            updateBoardRotation(newVal);
        });
        // Set rotation ban đầu
        updateBoardRotation(state.isPlayerRed());
        
        // Tạo và thêm các quân cờ vào bàn cờ
        // createChessPieces() trả về container có highlight layer và các quân cờ
        piecesContainer = chessBoardManager.createChessPieces();
        boardContainer.getChildren().add(piecesContainer);
        
        // Listener để reset quân cờ khi đổi bàn cờ
        state.selectedBoardImagePathProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty() && boardContainer != null) {
                Platform.runLater(() -> {
                    resetChessPieces();
                });
            }
        });
        
        root.getChildren().addAll(background, playerProfile, playerCapturedPieces, opponentProfile, opponentCapturedPieces, 
            timersContainer, leftIcons, topRightIcons, boardContainer);
        
        StackPane content = new StackPane();
        content.setPrefSize(1920, 1080);
        content.getChildren().add(root);
        
        return content;
    }
    
    private HBox createUserProfile(String username, String level, boolean isTopLeft) {
        HBox profile = new HBox(15);
        profile.setAlignment(Pos.CENTER_LEFT);

        // Avatar với hình vuông xám viền đỏ đè lên
        StackPane avatarContainer = new StackPane();
        
        // ava_profile.png - giữ nguyên kích thước và vị trí
        ImageView avaProfile = new ImageView(AssetHelper.image("ava_profile.png"));
        avaProfile.setFitWidth(450);
        avaProfile.setFitHeight(120);
        avaProfile.setPreserveRatio(true);
        
        // Avatar image - sẽ được cập nhật từ state
        ImageView avatarInSquare = new ImageView();
        avatarInSquare.setFitWidth(130);
        avatarInSquare.setFitHeight(130);
        avatarInSquare.setPreserveRatio(false);  // Đổi thành false để resize chính xác về 130x130
        avatarInSquare.setSmooth(true);
        
        // Clip ảnh thành hình vuông 130x130 để vừa khít trong ô vuông
        Rectangle clip = new Rectangle(130, 130);
        clip.setArcWidth(18);  // Bo góc giống squareFrame
        clip.setArcHeight(18);
        avatarInSquare.setClip(clip);
        
        // Hình vuông trong suốt với viền đỏ 130x130
        Rectangle squareFrame = new Rectangle(130, 130);
        squareFrame.setFill(Color.TRANSPARENT);  // Trong suốt hoàn toàn
        squareFrame.setStroke(Color.web("#A65252"));  // Viền đỏ
        squareFrame.setStrokeWidth(6);
        squareFrame.setArcWidth(18);  // Bo góc
        squareFrame.setArcHeight(18);
        
        // Thứ tự: avaProfile (dưới) -> avatarInSquare (giữa) -> squareFrame (trên, có viền đỏ)
        avatarContainer.getChildren().addAll(avaProfile, avatarInSquare, squareFrame);
        avatarContainer.setAlignment(Pos.CENTER_LEFT);  // Căn trái để square đè lên avatar

        // Text section với background trong suốt
        Pane textSection = new Pane();
        textSection.setPrefSize(250, 60);
        
        // Brush stroke background - trong suốt
        Rectangle brushStroke = new Rectangle(200, 50);
        brushStroke.setFill(Color.TRANSPARENT);  // Trong suốt
        brushStroke.setArcWidth(10);
        brushStroke.setArcHeight(10);
        brushStroke.setLayoutY(-5);
        
        // Username và Elo labels
        Label usernameLabel = new Label();
        Label eloLabel = new Label();
        
        if (isTopLeft) {
            // Player profile (top-left) - bind với state
            usernameLabel.textProperty().bind(state.usernameProperty());
            eloLabel.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                    () -> "do " + state.getElo(),
                    state.currentGameModeProperty(),
                    state.classicalEloProperty(),
                    state.blitzEloProperty()
                )
            );
            
            // Avatar của player: dùng hash của username để chọn avatar nhất quán
            state.usernameProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isEmpty()) {
                    int avatarNumber = (newVal.hashCode() % 10 + 10) % 10 + 1;  // 1-10
                    avatarNumber = Math.min(avatarNumber, 10);  // Giới hạn 1-10
                    try {
                        avatarInSquare.setImage(AssetHelper.image("ava/" + avatarNumber + ".jpg"));
                    } catch (Exception e) {
                        // Fallback to default
                        avatarInSquare.setImage(AssetHelper.image("ava/1.jpg"));
                    }
                }
            });
            // Set initial avatar
            String currentUsername = state.getUsername();
            if (currentUsername != null && !currentUsername.isEmpty()) {
                int avatarNumber = (currentUsername.hashCode() % 10 + 10) % 10 + 1;
                avatarNumber = Math.min(avatarNumber, 10);
                try {
                    avatarInSquare.setImage(AssetHelper.image("ava/" + avatarNumber + ".jpg"));
                } catch (Exception e) {
                    avatarInSquare.setImage(AssetHelper.image("ava/1.jpg"));
                }
            }
        } else {
            // Opponent profile (bottom-right) - bind với opponent state
            // Custom binding để format AI name và elo
            usernameLabel.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                    () -> {
                        String opponent = state.getOpponentUsername();
                        if (opponent != null && (opponent.startsWith("AI") || opponent.startsWith("AI_"))) {
                            // Parse difficulty từ opponent username
                            String difficulty = parseAIDifficulty(opponent);
                            return "AI_" + difficulty;
                        }
                        return opponent != null ? opponent : "";
                    },
                    state.opponentUsernameProperty()
                )
            );
            
            eloLabel.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                    () -> {
                        String opponent = state.getOpponentUsername();
                        if (opponent != null && (opponent.startsWith("AI") || opponent.startsWith("AI_"))) {
                            // Get elo từ difficulty
                            String difficulty = parseAIDifficulty(opponent);
                            int elo = getAIElo(difficulty);
                            return "do " + elo;
                        }
                        return "do " + state.getOpponentElo();
                    },
                    state.opponentUsernameProperty(),
                    state.opponentEloProperty()
                )
            );
            
            // Avatar của opponent: dùng hash của opponent username để chọn avatar nhất quán
            state.opponentUsernameProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isEmpty() && !"AI".equals(newVal)) {
                    int avatarNumber = (newVal.hashCode() % 10 + 10) % 10 + 1;  // 1-10
                    avatarNumber = Math.min(avatarNumber, 10);  // Giới hạn 1-10
                    try {
                        avatarInSquare.setImage(AssetHelper.image("ava/" + avatarNumber + ".jpg"));
                    } catch (Exception e) {
                        // Fallback to default
                        avatarInSquare.setImage(AssetHelper.image("ava/1.jpg"));
                    }
                } else {
                    // Default avatar for AI or empty
                    avatarInSquare.setImage(AssetHelper.image("ava/1.jpg"));
                }
            });
            // Set initial avatar
            String opponentUsername = state.getOpponentUsername();
            if (opponentUsername != null && !opponentUsername.isEmpty() && !"AI".equals(opponentUsername)) {
                int avatarNumber = (opponentUsername.hashCode() % 10 + 10) % 10 + 1;
                avatarNumber = Math.min(avatarNumber, 10);
                try {
                    avatarInSquare.setImage(AssetHelper.image("ava/" + avatarNumber + ".jpg"));
                } catch (Exception e) {
                    avatarInSquare.setImage(AssetHelper.image("ava/1.jpg"));
                }
            } else {
                avatarInSquare.setImage(AssetHelper.image("ava/1.jpg"));
            }
        }
        
        usernameLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 60px; -fx-text-fill: white; -fx-background-color: transparent;");
        usernameLabel.setLayoutX(-310);
        usernameLabel.setLayoutY(-10);
        
        eloLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 50px; -fx-text-fill: white; -fx-background-color: transparent;");
        eloLabel.setLayoutX(-310);
        eloLabel.setLayoutY(35);
        
        textSection.getChildren().addAll(brushStroke, usernameLabel, eloLabel);

        profile.getChildren().addAll(avatarContainer, textSection);
        return profile;
    }
    
    /**
     * Tạo UI để hiển thị các quân cờ đã ăn được
     * @param isTopLeft true nếu là người chơi top-left (red), false nếu là bottom-right (black)
     */

    
    private VBox createLeftIcons() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.TOP_LEFT);
        // Children sẽ được thêm trong updateLeftIcons()
        return container;
    }
    
    private HBox createTopRightIcons() {
        HBox container = new HBox(15);
        container.setAlignment(Pos.CENTER);
        
        // Move icon (ic_move.png) - thêm vào top right
        ImageView moveIcon = new ImageView(AssetHelper.image("ic_move.png"));
        moveIcon.setFitWidth(85);  // Tăng từ 70 lên 85
        moveIcon.setFitHeight(85);  // Tăng từ 70 lên 85
        moveIcon.setPreserveRatio(true);
        moveIcon.setCursor(Cursor.HAND);
        
        // Click handler để toggle move panel
        moveIcon.setOnMouseClicked(e -> {
            if (moveHistoryManager.isMovePanelVisible()) {
                // Nếu panel đang hiện, đóng nó
                moveHistoryManager.hideMovePanel();
            } else {
                // Nếu chưa hiện, mở nó
                moveHistoryManager.showMovePanel();
            }
            e.consume();
        });
        
        // Chat icon
        ImageView chatIcon = new ImageView(AssetHelper.image("ic_chat.png"));
        chatIcon.setFitWidth(50);
        chatIcon.setFitHeight(50);
        chatIcon.setPreserveRatio(true);
        chatIcon.setCursor(Cursor.HAND);
        
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
        
        ScaleTransition chatScale = new ScaleTransition(Duration.millis(200), chatIcon);
        chatIcon.setOnMouseEntered(e -> {
            chatScale.setToX(1.1);
            chatScale.setToY(1.1);
            chatScale.play();
        });
        chatIcon.setOnMouseExited(e -> {
            chatScale.setToX(1.0);
            chatScale.setToY(1.0);
            chatScale.play();
        });
        
        // Click handler để toggle chat input (mở/đóng)
        chatIcon.setOnMouseClicked(e -> {
            // Nếu chat input đang hiện, đóng nó
            if (chatManager.isChatInputVisible()) {
                chatManager.hideChatInput();
            } else {
                // Nếu chưa hiện, mở nó
                chatManager.showChatInput();
            }
            e.consume();
        });
        
        container.getChildren().addAll(moveIcon, chatIcon);
        
        return container;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }

    
    @Override
    public void resetChessPieces() {
        // Xóa quân cờ cũ nếu có
        if (piecesContainer != null && boardContainer != null) {
            boardContainer.getChildren().remove(piecesContainer);
        }
        
        // Tạo lại quân cờ mới
        piecesContainer = chessBoardManager.createChessPieces();
        
        // Thêm vào board container
        if (boardContainer != null) {
            boardContainer.getChildren().add(piecesContainer);
        }
    }
    
    /**
     * Get current turn
     */
    @Override
    public String getCurrentTurn() {
        return currentTurn;
    }
    
    /**
     * Set current turn
     */
    @Override
    public void setCurrentTurn(String turn) {
        this.currentTurn = turn;
    }
    
    /**
     * Apply opponent's move from server
     * @param fromCol Column of piece to move (0-8) - Backend coordinates
     * @param fromRow Row of piece to move (0-9) - Backend coordinates
     * @param toCol Target column (0-8) - Backend coordinates
     * @param toRow Target row (0-9) - Backend coordinates
     */
    public void applyOpponentMove(int fromCol, int fromRow, int toCol, int toRow) {
        Platform.runLater(() -> {
            if (piecesContainer == null) {
                System.err.println("[GamePanel] piecesContainer is null, cannot apply move");
                return;
            }
            
            // Convert: Backend row (0=top đen) → Frontend row (0=top đỏ)
            // Backend: row 0 = top (đen), row 9 = bottom (đỏ)
            // Frontend: row 0 = top (đỏ), row 9 = bottom (đen)
            // Công thức: frontendRow = 9 - backendRow
            int frontendFromRow = 9 - fromRow;
            int frontendToRow = 9 - toRow;
            
            // Tìm quân cờ tại vị trí from (dùng row/col từ PieceInfo - không dùng pixel)
            ImageView pieceToMove = null;
            ImageView capturedPiece = null;
            
            System.out.println("[GamePanel] Looking for piece at FE(" + frontendFromRow + "," + fromCol + ") BE(" + fromRow + "," + fromCol + ")");
            
            for (javafx.scene.Node node : piecesContainer.getChildren()) {
                if (node instanceof ImageView) {
                    ImageView imgView = (ImageView) node;
                    if (imgView.getUserData() instanceof ChessBoardManager.PieceInfo) {
                        ChessBoardManager.PieceInfo info = (ChessBoardManager.PieceInfo) imgView.getUserData();
                        
                        // So sánh bằng row/col từ PieceInfo (vị trí lưới)
                        if (info.row == frontendFromRow && info.col == fromCol) {
                            pieceToMove = imgView;
                            System.out.println("[GamePanel] Found piece to move: " + info.color + " " + info.pieceType +
                                " at grid position (" + info.row + "," + info.col + ")");
                        }
                        if (info.row == frontendToRow && info.col == toCol) {
                            capturedPiece = imgView;
                        }
                    }
                }
            }
            
            if (pieceToMove == null) {
                System.err.println("[GamePanel] Could not find piece at FE(" + frontendFromRow + "," + fromCol + ") BE(" + fromRow + "," + fromCol + ")");
                // Debug: in ra tất cả pieces với row/col
                System.err.println("[GamePanel] Available pieces:");
                for (javafx.scene.Node node : piecesContainer.getChildren()) {
                    if (node instanceof ImageView) {
                        ImageView imgView = (ImageView) node;
                        if (imgView.getUserData() instanceof ChessBoardManager.PieceInfo) {
                            ChessBoardManager.PieceInfo info = (ChessBoardManager.PieceInfo) imgView.getUserData();
                            System.err.println("  - " + info.color + " " + info.pieceType +
                                " at grid (" + info.row + "," + info.col + ")");
                        }
                    }
                }
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
            
            // Di chuyển quân cờ đến vị trí mới (dùng công thức đặt quân cờ với offset)
            ChessBoardManager.PieceInfo pieceInfo = (ChessBoardManager.PieceInfo) pieceToMove.getUserData();
            
            // Tính toán vị trí giao điểm dựa trên công thức đặt quân cờ trong ChessBoardManager
            double boardSize = 923.0;
            double startX = 45.0;
            double startY = 45.0;
            double endX = boardSize - 45.0;
            double endY = boardSize - 45.0;
            double intersectionSpacingX = (endX - startX) / 8.0;
            double intersectionSpacingY = (endY - startY) / 9.0;
            double pieceWidth = intersectionSpacingX * 0.8;
            double pieceHeight = intersectionSpacingY * 0.8;
            
            // Tính vị trí giao điểm cho to (dùng frontend coordinates)
            double toIntersectionX = startX + toCol * intersectionSpacingX;
            double toIntersectionY = startY + frontendToRow * intersectionSpacingY;
            
            // Tính offset dựa trên màu quân cờ (giống như trong ChessBoardManager)
            double offsetX = 0;
            double offsetY = 0;
            if (pieceInfo != null) {
                if ("red".equals(pieceInfo.color)) {
                    offsetY = -10;  // Dịch lên trên 10px
                    if (toCol >= 5) {  // Cột 5, 6, 7, 8
                        offsetX = 5;  // Dịch sang phải thêm 5px
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
            
            // Đặt tâm quân cờ tại giao điểm (trừ đi một nửa kích thước) + offset
            double newX = toIntersectionX - pieceWidth / 2.0 + offsetX;
            double newY = toIntersectionY - pieceHeight / 2.0 + offsetY;
            pieceToMove.setLayoutX(newX);
            pieceToMove.setLayoutY(newY);
            
            // CẬP NHẬT row/col trong PieceInfo (quan trọng!)
            pieceInfo.row = frontendToRow;
            pieceInfo.col = toCol;
            
            // Lấy thông tin để thêm vào move history
            if (pieceInfo != null) {
                String capturedInfo = "";
                if (capturedPiece != null && capturedPiece != pieceToMove) {
                    ChessBoardManager.PieceInfo capInfo = (ChessBoardManager.PieceInfo) capturedPiece.getUserData();
                    if (capInfo != null) {
                        capturedInfo = String.format(" (captured %s %s)", capInfo.color, capInfo.pieceType);
                    }
                }
                moveHistoryManager.addMove(pieceInfo.color, pieceInfo.pieceType, frontendFromRow, fromCol, frontendToRow, toCol, capturedInfo);
            }
            
            // Đổi lượt
            currentTurn = currentTurn.equals("red") ? "black" : "red";
            
            // Cập nhật timer
            timerManager.updateTimersOnTurnChange();
            
            System.out.println("[GamePanel] Applied opponent move: FE(" + frontendFromRow + "," + fromCol + 
                ") -> FE(" + frontendToRow + "," + toCol + ") BE(" + fromRow + "," + fromCol + 
                ") -> BE(" + toRow + "," + toCol + ")");
            
            // Check game end after opponent move
            Platform.runLater(() -> {
                checkGameEndAfterMove(false); // captured info not available here, will check from board
            });
        });
    }

    
    /**
     * Cập nhật vị trí profile dựa trên playerIsRed
     * Player luôn ở bottom-right, opponent luôn ở top-left
     */
    private void updateProfilePositions(boolean isPlayerRed) {
        if (playerProfile == null || opponentProfile == null || 
            playerCapturedPieces == null || opponentCapturedPieces == null) {
            return;
        }
        
        // Player luôn ở bottom-right, opponent luôn ở top-left
        // Không phụ thuộc vào playerIsRed
        
        // Player profile ở bottom-right
        playerProfile.setLayoutX(1920 - 450);
        playerProfile.setLayoutY(1080 - 200);
        playerCapturedPieces.setLayoutX(1920 - 450);
        playerCapturedPieces.setLayoutY(1080 - 200 + 120);
        
        // Opponent profile ở top-left
        opponentProfile.setLayoutX(50);
        opponentProfile.setLayoutY(50);
        opponentCapturedPieces.setLayoutX(50);
        opponentCapturedPieces.setLayoutY(50 + 120);
    }
    
    /**
     * Xoay bàn cờ theo hướng người chơi
     * Nếu player là black, xoay 180 độ để phía cờ của người chơi luôn ở dưới
     */
    private void updateBoardRotation(boolean isPlayerRed) {
        if (boardContainer == null) {
            return;
        }
        
        // Cập nhật flag
        this.isBoardFlipped = !isPlayerRed;
        
        // Nếu player là black, xoay 180 độ
        // Nếu player là red, không xoay (0 độ)
        double boardRotation = isPlayerRed ? 180.0 : 0.0;
        
        System.out.println("[GamePanel] updateBoardRotation: isPlayerRed=" + isPlayerRed + 
            ", boardRotation=" + boardRotation + ", isBoardFlipped=" + isBoardFlipped);
        
        // Xoay boardContainer - điều này sẽ xoay tất cả children (boardImage, piecesContainer)
        boardContainer.setRotate(boardRotation);
        
        // QUAN TRỌNG: Khi bàn cờ xoay 180 độ, quân cờ cũng bị xoay làm chữ ngược
        // Cần xoay ngược mỗi quân cờ (ImageView) để chữ luôn đúng hướng
        if (piecesContainer != null) {
            double pieceRotation = isPlayerRed ? 180.0 : 0.0;
            for (javafx.scene.Node node : piecesContainer.getChildren()) {
                if (node instanceof ImageView) {
                    ImageView piece = (ImageView) node;
                    // Xoay ngược quân cờ để chữ luôn đúng hướng
                    piece.setRotate(pieceRotation);
                }
            }
        }
        
        // Xoay các bộ đếm (timers) giống như xoay quân cờ để chữ luôn đúng hướng
        if (timerManager != null) {
            timerManager.updateRotation(isPlayerRed);
        }
    }
    
    /**
     * Kiểm tra xem board có đang flipped không (player là black)
     */
    public boolean isBoardFlipped() {
        return isBoardFlipped;
    }
    
    /**
     * Reset tất cả các panel và dialog của game
     */
    private void resetAllGamePanels() {
        if (rootPane == null) {
            return; // rootPane chưa được khởi tạo
        }
        
        // Reset currentTurn về red (mặc định red đi trước)
        currentTurn = "red";
        
        // Reset AI quit flag
        aiQuitSent = false;
        
        // Reset các Manager
        dialogManager.resetAllDialogs();
        moveHistoryManager.clearMoveHistory();
        capturedPiecesManager.resetCapturedPieces();
        
        // Reset GameEndChecker
        if (gameEndChecker != null) {
            gameEndChecker.reset();
        }
        
        // Reset quân cờ về vị trí ban đầu
        if (boardContainer != null) {
            Platform.runLater(() -> {
                resetChessPieces();
                // Quan trọng: Sau khi reset pieces, cần xoay lại để chữ không bị ngược
                updateBoardRotation(state.isPlayerRed());
            });
        }
    }
    
    // Method để đảm bảo pieces luôn hiển thị
    private void ensurePiecesVisible() {
        if (boardContainer != null && piecesContainer != null) {
            // Đảm bảo piecesContainer có trong boardContainer
            if (!boardContainer.getChildren().contains(piecesContainer)) {
                boardContainer.getChildren().add(piecesContainer);
            }
            // Đảm bảo piecesContainer ở trên cùng
            piecesContainer.toFront();
            // Đảm bảo piecesContainer visible
            piecesContainer.setVisible(true);
            piecesContainer.setManaged(true);
            piecesContainer.setMouseTransparent(false);
            piecesContainer.setPickOnBounds(true);
            
            // Đảm bảo rotation được áp dụng cho board và pieces
            updateBoardRotation(state.isPlayerRed());
        }
    }
    
    /**
     * Get UIState reference - for ChessBoardManager to access playerIsRed
     */
    public UIState getState() {
        return state;
    }
    
    /**
     * Get current board state from piecesContainer
     * @return char[10][9] board representation
     */
    private char[][] getCurrentBoardState() {
        char[][] board = new char[10][9];
        // Initialize with empty squares
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                board[i][j] = ' ';
            }
        }
        
        if (piecesContainer == null) {
            return board;
        }
        
        // Extract piece positions from piecesContainer (dùng row/col từ PieceInfo)
        for (javafx.scene.Node node : piecesContainer.getChildren()) {
            if (node instanceof ImageView) {
                ImageView piece = (ImageView) node;
                if (piece.getUserData() instanceof ChessBoardManager.PieceInfo) {
                    ChessBoardManager.PieceInfo info = (ChessBoardManager.PieceInfo) piece.getUserData();
                    
                    // Dùng row/col từ PieceInfo thay vì tính từ pixel
                    int row = info.row;
                    int col = info.col;
                    
                    // Chỉ thêm vào board nếu row/col hợp lệ
                    if (row >= 0 && row < 10 && col >= 0 && col < 9) {
                        // Convert piece type to character
                        char pieceChar = ChessBoardManager.getPieceChar(info.pieceType, info.color.equals("red"));
                        board[row][col] = pieceChar;
                    }
                }
            }
        }
        
        return board;
    }
    
    /**
     * Check game end conditions after a move
     * @param captured Whether a piece was captured in this move
     */
    private void checkGameEndAfterMove(boolean captured) {
        if (gameEndChecker == null || piecesContainer == null) {
            return;
        }
        
        // Get current board state
        char[][] board = getCurrentBoardState();
        
        // Debug: Check if King exists on board
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 9; col++) {
                if (board[row][col] == 'K') hasRedKing = true;
                if (board[row][col] == 'k') hasBlackKing = true;
            }
        }
        System.out.println("[GamePanel] checkGameEndAfterMove: captured=" + captured + 
                          ", hasRedKing=" + hasRedKing + ", hasBlackKing=" + hasBlackKing);
        
        // Record move in history
        gameEndChecker.recordMove(board, captured);
        
        // Check game end conditions
        boolean isRedTurn = currentTurn.equals("red");
        application.game.GameEndChecker.GameEndResult result = gameEndChecker.checkGameEnd(board, isRedTurn);
        
        if (result.isGameOver) {
            System.out.println("[GamePanel] Game Over: " + result.result + " - " + result.termination + " - " + result.message);
            
            // Check if this is an AI game
            boolean isAIGame = isAIGame();
            
            // Determine if player wins/loses/draws
            boolean playerIsRed = state.isPlayerRed();
            String playerResult;
            
            if ("draw".equals(result.result)) {
                playerResult = "draw";
            } else if ((playerIsRed && "red".equals(result.result)) || 
                       (!playerIsRed && "black".equals(result.result))) {
                playerResult = "win";
            } else {
                playerResult = "lose";
            }
            
            // Send game end to server
            try {
                application.network.NetworkManager networkManager = 
                    application.network.NetworkManager.getInstance();
                if (networkManager != null) {
                    if (isAIGame) {
                        // AI game: gửi AI_QUIT thay vì GAME_END (chỉ gửi một lần)
                        if (!aiQuitSent) {
                            networkManager.game().quitAIGame();
                            aiQuitSent = true;
                            System.out.println("[GamePanel] AI game ended - sent AI_QUIT (no rating change)");
                        } else {
                            System.out.println("[GamePanel] AI_QUIT already sent, skipping duplicate");
                        }
                    } else {
                        // Human game: gửi GAME_END như bình thường
                        String winSide = "draw";
                        if ("red".equals(result.result)) {
                            winSide = state.isPlayerRed() ? state.getUsername() : state.getOpponentUsername();
                        } else if ("black".equals(result.result)) {
                            winSide = state.isPlayerRed() ? state.getOpponentUsername() : state.getUsername();
                        }
                        networkManager.game().sendGameEnd(winSide);
                        System.out.println("[GamePanel] Game ended - result: " + result.result + 
                            ", termination: " + result.termination + ", winSide: " + winSide);
                    }
                }
            } catch (Exception e) {
                System.err.println("[GamePanel] Error sending game end: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Show result dialog
            Platform.runLater(() -> {
                if (isAIGame) {
                    // AI game: hiển thị dialog không có điểm (eloDelta = 0)
                    if ("draw".equals(playerResult)) {
                        dialogManager.showGameResultWithCustomText("Draw", 0);
                    } else if ("win".equals(playerResult)) {
                        dialogManager.showGameResultWithCustomText("You win", 0);
                    } else {
                        dialogManager.showGameResultWithCustomText("You lose", 0);
                    }
                } else {
                    // Human game: hiển thị dialog có điểm như bình thường
                    if ("draw".equals(playerResult)) {
                        dialogManager.showGameResultDraw();
                    } else if ("win".equals(playerResult)) {
                        dialogManager.showGameResult(true);
                    } else {
                        dialogManager.showGameResult(false);
                    }
                }
            });
        } else {
            // Check if king is in check (for UI indication)
            boolean redInCheck = application.game.GameEndChecker.isKingInCheck(board, true);
            boolean blackInCheck = application.game.GameEndChecker.isKingInCheck(board, false);
            
            if (redInCheck || blackInCheck) {
                System.out.println("[GamePanel] Check! Red: " + redInCheck + ", Black: " + blackInCheck);
                // TODO: Show "Check!" indicator in UI
            }
        }
    }
    
    /**
     * Cập nhật leftIcons dựa trên currentGameMode và AI game
     */
    private void updateLeftIcons() {
        if (leftIcons == null || dialogManager == null) {
            return;
        }
        
        // Xóa tất cả children cũ
        leftIcons.getChildren().clear();
        
        // Check if playing with AI
        String opponentUsername = state.getOpponentUsername();
        boolean isAIGame = opponentUsername != null && 
                          (opponentUsername.startsWith("AI") || 
                           opponentUsername.startsWith("AI_") ||
                           opponentUsername.equals("AI") ||
                           opponentUsername.startsWith("AI ("));
        
        System.out.println("[GamePanel] updateLeftIcons: opponentUsername=" + opponentUsername + 
                          ", isAIGame=" + isAIGame + ", leftIcons.children.size=" + leftIcons.getChildren().size());
        
        if (isAIGame) {
            // AI game: chỉ hiển thị quit và suggest buttons
            StackPane quitContainer = new StackPane();
            ImageView quitIcon = new ImageView(AssetHelper.image("ic_quit.png"));
            quitIcon.setFitWidth(60);
            quitIcon.setFitHeight(60);
            quitIcon.setPreserveRatio(true);
            quitContainer.getChildren().add(quitIcon);
            quitContainer.setCursor(Cursor.HAND);
            quitContainer.setMouseTransparent(false);
            quitContainer.setPickOnBounds(true);
            
            // Hover effects cho quit icon
            ScaleTransition quitScaleIn = new ScaleTransition(Duration.millis(200), quitContainer);
            quitScaleIn.setToX(1.1);
            quitScaleIn.setToY(1.1);
            
            ScaleTransition quitScaleOut = new ScaleTransition(Duration.millis(200), quitContainer);
            quitScaleOut.setToX(1.0);
            quitScaleOut.setToY(1.0);
            
            quitContainer.setOnMouseEntered(e -> {
                quitScaleOut.stop();
                quitScaleIn.setFromX(quitContainer.getScaleX());
                quitScaleIn.setFromY(quitContainer.getScaleY());
                quitScaleIn.play();
            });
            quitContainer.setOnMouseExited(e -> {
                quitScaleIn.stop();
                quitScaleOut.setFromX(quitContainer.getScaleX());
                quitScaleOut.setFromY(quitContainer.getScaleY());
                quitScaleOut.play();
            });
            
            // Click handler để hiển thị quit confirmation dialog
            quitContainer.setOnMouseClicked(e -> {
                // Show quit confirmation dialog với callback để gửi message về BE
                // Lấy thông tin opponent trước khi vào lambda để tránh conflict
                final String oppUsername = state.getOpponentUsername();
                final boolean isAI = oppUsername != null && 
                                    (oppUsername.startsWith("AI") || 
                                     oppUsername.startsWith("AI_") ||
                                     oppUsername.equals("AI") ||
                                     oppUsername.startsWith("AI ("));
                
                dialogManager.showQuitConfirmation(() -> {
                    // Callback khi user confirm quit
                    try {
                        application.network.NetworkManager networkManager = 
                            application.network.NetworkManager.getInstance();
                        if (networkManager != null) {
                            if (isAI) {
                                // AI game: gửi AI_QUIT (không tính là resign, không mất điểm)
                                networkManager.game().quitAIGame();
                                System.out.println("[GamePanel] Quit AI game - sent AI_QUIT");
                            } else {
                                // Human game: gửi RESIGN
                                networkManager.game().resign();
                                System.out.println("[GamePanel] Quit human game - sent RESIGN");
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("[GamePanel] Error sending quit message: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
                e.consume();
            });
            
            leftIcons.getChildren().add(quitContainer);
            
            // Thêm suggest button
            StackPane suggestContainer = createSuggestButton();
            leftIcons.getChildren().add(suggestContainer);
        } else {
            // Normal mode (không phải AI): hiển thị resign và draw buttons
            // Flag icon (ic_signup.png - có thể là resign/surrender) - wrap trong StackPane
            StackPane resignContainer = new StackPane();
            ImageView resignIcon = new ImageView(AssetHelper.image("ic_signup.png"));
            resignIcon.setFitWidth(60);
            resignIcon.setFitHeight(60);
            resignIcon.setPreserveRatio(true);
            resignContainer.getChildren().add(resignIcon);
            resignContainer.setCursor(Cursor.HAND);
            resignContainer.setMouseTransparent(false);
            resignContainer.setPickOnBounds(true);
            
            // Draw icon (ic_draw.png - đề nghị hòa) - wrap trong StackPane
            StackPane drawContainer = new StackPane();
            ImageView drawIcon = new ImageView(AssetHelper.image("ic_draw.png"));
            drawIcon.setFitWidth(60);
            drawIcon.setFitHeight(60);
            drawIcon.setPreserveRatio(true);
            drawContainer.getChildren().add(drawIcon);
            drawContainer.setCursor(Cursor.HAND);
            drawContainer.setMouseTransparent(false);
            drawContainer.setPickOnBounds(true);
            
            // Hover effects cho các icon - scale StackPane thay vì ImageView
            ScaleTransition resignScaleIn = new ScaleTransition(Duration.millis(200), resignContainer);
            resignScaleIn.setToX(1.1);
            resignScaleIn.setToY(1.1);
            
            ScaleTransition resignScaleOut = new ScaleTransition(Duration.millis(200), resignContainer);
            resignScaleOut.setToX(1.0);
            resignScaleOut.setToY(1.0);
            
            resignContainer.setOnMouseEntered(e -> {
                resignScaleOut.stop();
                resignScaleIn.setFromX(resignContainer.getScaleX());
                resignScaleIn.setFromY(resignContainer.getScaleY());
                resignScaleIn.play();
            });
            resignContainer.setOnMouseExited(e -> {
                resignScaleIn.stop();
                resignScaleOut.setFromX(resignContainer.getScaleX());
                resignScaleOut.setFromY(resignContainer.getScaleY());
                resignScaleOut.play();
            });
            
            // Click handler để hiển thị confirmation dialog
            resignContainer.setOnMouseClicked(e -> {
                dialogManager.showSurrenderConfirmation();
                e.consume();
            });
            
            ScaleTransition drawScaleIn = new ScaleTransition(Duration.millis(200), drawContainer);
            drawScaleIn.setToX(1.1);
            drawScaleIn.setToY(1.1);
            
            ScaleTransition drawScaleOut = new ScaleTransition(Duration.millis(200), drawContainer);
            drawScaleOut.setToX(1.0);
            drawScaleOut.setToY(1.0);
            
            drawContainer.setOnMouseEntered(e -> {
                drawScaleOut.stop();
                drawScaleIn.setFromX(drawContainer.getScaleX());
                drawScaleIn.setFromY(drawContainer.getScaleY());
                drawScaleIn.play();
            });
            drawContainer.setOnMouseExited(e -> {
                drawScaleIn.stop();
                drawScaleOut.setFromX(drawContainer.getScaleX());
                drawScaleOut.setFromY(drawContainer.getScaleY());
                drawScaleOut.play();
            });
            
            // Click handler để hiển thị draw request confirmation
            drawContainer.setOnMouseClicked(e -> {
                dialogManager.showDrawRequestConfirmation();
                e.consume();
            });
            
            leftIcons.getChildren().add(resignContainer);
            leftIcons.getChildren().add(drawContainer);
        }
    }
    
    /**
     * Tạo nút suggest move (chỉ hiện khi chơi với AI)
     */
    private StackPane createSuggestButton() {
        StackPane suggestContainer = new StackPane();
        
        // Sử dụng icon bóng đèn
        ImageView suggestIcon = new ImageView(AssetHelper.image("ic_bulb.png"));
        suggestIcon.setFitWidth(60);
        suggestIcon.setFitHeight(60);
        suggestIcon.setPreserveRatio(true);
        suggestContainer.getChildren().add(suggestIcon);
        
        suggestContainer.setCursor(Cursor.HAND);
        suggestContainer.setMouseTransparent(false);
        suggestContainer.setPickOnBounds(true);
        
        // Hover effects
        ScaleTransition suggestScaleIn = new ScaleTransition(Duration.millis(200), suggestContainer);
        suggestScaleIn.setToX(1.1);
        suggestScaleIn.setToY(1.1);
        
        ScaleTransition suggestScaleOut = new ScaleTransition(Duration.millis(200), suggestContainer);
        suggestScaleOut.setToX(1.0);
        suggestScaleOut.setToY(1.0);
        
        suggestContainer.setOnMouseEntered(e -> {
            suggestScaleOut.stop();
            suggestScaleIn.setFromX(suggestContainer.getScaleX());
            suggestScaleIn.setFromY(suggestContainer.getScaleY());
            suggestScaleIn.play();
        });
        suggestContainer.setOnMouseExited(e -> {
            suggestScaleIn.stop();
            suggestScaleOut.setFromX(suggestContainer.getScaleX());
            suggestScaleOut.setFromY(suggestContainer.getScaleY());
            suggestScaleOut.play();
        });
        
        // Click handler để gửi SUGGEST_MOVE request
        suggestContainer.setOnMouseClicked(e -> {
            try {
                application.network.NetworkManager.getInstance().game().requestSuggestMove();
                System.out.println("[GamePanel] Sent SUGGEST_MOVE request");
            } catch (Exception ex) {
                System.err.println("[GamePanel] Failed to request suggest move: " + ex.getMessage());
                ex.printStackTrace();
            }
            e.consume();
        });
        
        return suggestContainer;
    }
    
    /**
     * Parse AI difficulty từ opponent username
     * Hỗ trợ các format: "AI (Easy)", "AI_easy", "AI_medium", "AI_hard", "AI"
     * @param opponentUsername Username của opponent
     * @return Difficulty string: "easy", "medium", "hard" (default: "medium")
     */
    private String parseAIDifficulty(String opponentUsername) {
        if (opponentUsername == null || opponentUsername.isEmpty()) {
            return "medium";
        }
        
        String lower = opponentUsername.toLowerCase();
        
        // Format: "AI (Easy)" hoặc "AI (Medium)" hoặc "AI (Hard)"
        if (lower.contains("(easy)")) {
            return "easy";
        } else if (lower.contains("(medium)")) {
            return "medium";
        } else if (lower.contains("(hard)")) {
            return "hard";
        }
        
        // Format: "AI_easy", "AI_medium", "AI_hard"
        if (lower.contains("_easy")) {
            return "easy";
        } else if (lower.contains("_medium")) {
            return "medium";
        } else if (lower.contains("_hard")) {
            return "hard";
        }
        
        // Format: "AI" hoặc không tìm thấy → default medium
        return "medium";
    }
    
    /**
     * Get elo rating cho AI dựa trên difficulty
     * @param difficulty "easy", "medium", hoặc "hard"
     * @return Elo rating: easy=800, medium=1800, hard=2500
     */
    private int getAIElo(String difficulty) {
        if (difficulty == null) {
            return 1800; // Default medium
        }
        
        switch (difficulty.toLowerCase()) {
            case "easy":
                return 800;
            case "hard":
                return 2500;
            case "medium":
            default:
                return 1800;
        }
    }
    
    /**
     * Kiểm tra xem có đang chơi với AI không
     * @return true nếu là AI game
     */
    private boolean isAIGame() {
        String opponentUsername = state.getOpponentUsername();
        return opponentUsername != null && 
               (opponentUsername.startsWith("AI") || 
                opponentUsername.startsWith("AI_") ||
                opponentUsername.equals("AI") ||
                opponentUsername.startsWith("AI ("));
    }
}