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
    private java.util.List<String> replayMoves = new java.util.ArrayList<>();  // List of moves to replay
    private String currentTurn = "red";  // Current turn for replay
    
    // Profile containers
    private HBox playerProfile = null;
    private HBox opponentProfile = null;
    private VBox playerCapturedPieces = null;
    private VBox opponentCapturedPieces = null;
    
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
        
        // Cập nhật vị trí profile ban đầu
        updateProfilePositions(state.isPlayerRed());
        
        // Listener để cập nhật vị trí profile khi playerIsRed thay đổi
        state.playerIsRedProperty().addListener((obs, oldVal, newVal) -> {
            updateProfilePositions(newVal);
        });
        
        // Center: Game board area
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
        
        // Xoay bàn cờ theo hướng người chơi
        state.playerIsRedProperty().addListener((obs, oldVal, newVal) -> {
            updateBoardRotation(newVal);
        });
        updateBoardRotation(state.isPlayerRed());
        
        // Tạo và thêm các quân cờ vào bàn cờ
        piecesContainer = chessBoardManager.createChessPieces();
        boardContainer.getChildren().add(piecesContainer);
        
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
        
        // Back move button (ic_backmove.png)
        StackPane backButton = createReplayControlButton("ic_backmove.png", "Back");
        backButton.setOnMouseClicked(e -> {
            goToPreviousMove();
        });
        
        // Next move button (ic_nextMove.png)
        StackPane nextButton = createReplayControlButton("ic_nextMove.png", "Next");
        nextButton.setOnMouseClicked(e -> {
            goToNextMove();
        });
        
        // Quit button (ic_quit.png)
        StackPane quitButton = createReplayControlButton("ic_quit.png", "Quit");
        quitButton.setOnMouseClicked(e -> {
            state.closeReplay();
        });
        
        container.getChildren().addAll(backButton, nextButton, quitButton);
        
        return container;
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
        
        button.getChildren().add(icon);
        button.setCursor(Cursor.HAND);
        button.setPickOnBounds(true);
        button.setMouseTransparent(false);
        
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
            System.out.println("[ReplayPanel] Moved to move index: " + currentMoveIndex);
        }
    }
    
    /**
     * Áp dụng nước đi đến vị trí chỉ định
     */
    private void applyMoveToPosition(int moveIndex) {
        // TODO: Implement move application logic
        // Reset board to initial position
        resetChessPieces();
        
        // Apply all moves up to moveIndex
        for (int i = 0; i <= moveIndex && i < replayMoves.size(); i++) {
            String move = replayMoves.get(i);
            // TODO: Parse and apply move
            System.out.println("[ReplayPanel] Applying move " + i + ": " + move);
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
     * Load game data for replay
     */
    public void loadReplay(String gameId) {
        this.currentGameId = gameId;
        resetReplayState();
        // TODO: Fetch game data from backend
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
    
    private void updateBoardRotation(boolean playerIsRed) {
        if (boardContainer == null) return;
        
        if (playerIsRed) {
            boardContainer.setRotate(0);
        } else {
            boardContainer.setRotate(180);
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
            boardContainer.getChildren().add(piecesContainer);
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
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
