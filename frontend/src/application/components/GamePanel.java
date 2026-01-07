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

public class GamePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    
    // Managers
    private ChessBoardManager chessBoardManager;
    private TimerManager timerManager;
    private CapturedPiecesManager capturedPiecesManager;
    private MoveHistoryManager moveHistoryManager;
    private DialogManager dialogManager;
    private ChatManager chatManager;
    
    // Core fields
    private Pane rootPane = null;  // Lưu reference đến root pane để thêm UI
    private Pane piecesContainer = null;  // Lưu reference đến container chứa quân cờ
    private StackPane boardContainer = null;  // Lưu reference đến board container
    private String currentTurn = "red";  // Lượt hiện tại: "red" đi trước, sau đó "black"

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
            String[] parts = moveDetails.split("\\|");
            if (parts.length >= 7) {
                String color = parts[0];
                String pieceType = parts[1];
                int fromRow = Integer.parseInt(parts[2]);
                int fromCol = Integer.parseInt(parts[3]);
                int toRow = Integer.parseInt(parts[4]);
                int toCol = Integer.parseInt(parts[5]);
                String capturedInfo = parts[6];
                moveHistoryManager.addMove(color, pieceType, fromRow, fromCol, toRow, toCol, capturedInfo);
            }
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
                    if ("win".equals(result)) {
                        System.out.println("[GamePanel] Calling showGameResult(true) - WIN");
                        dialogManager.showGameResult(true);
                    } else if ("lose".equals(result)) {
                        System.out.println("[GamePanel] Calling showGameResult(false) - LOSE");
                        dialogManager.showGameResult(false);
                    } else if ("draw".equals(result)) {
                        System.out.println("[GamePanel] Calling showGameResultDraw() - DRAW");
                        dialogManager.showGameResultDraw();
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
                        chatManager.showChatPopup(result);
                    }
                    break;
                default:
                    System.out.println("[GamePanel] Unknown trigger: " + newVal);
            }
        });
        
        chessBoardManager.setOnTurnChanged(() -> {
            timerManager.updateTimersOnTurnChange();
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
        
        // Left side: Icons (resign, draw) - đặt dưới timers, cạnh khung đen
        VBox leftIcons = createLeftIcons();
        leftIcons.setLayoutX(boardX - 65);  // Cùng vị trí X với timers
        leftIcons.setLayoutY((1080 - 923) / 2 + 750);  // Đặt dưới timers
        
        // Top right: Chat and token icons
        HBox topRightIcons = createTopRightIcons();
        topRightIcons.setLayoutX(1920 - 200);  // Dịch sang trái (từ 150 xuống 200)
        topRightIcons.setLayoutY(20);
        getChildren().add(topRightIcons);
        
        // Center: Game board area - hiển thị ảnh bàn cờ đã chọn
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
        StackPane boardContainer = new StackPane();
        boardContainer.setLayoutX((1920 - 923) / 2);
        boardContainer.setLayoutY((1080 - 923) / 2);
        boardContainer.setPrefSize(923, 923);
        boardContainer.setAlignment(Pos.CENTER);
        boardContainer.getChildren().addAll(boardPlaceholder, boardImage);
        
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
            usernameLabel.textProperty().bind(state.opponentUsernameProperty());
            eloLabel.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                    () -> "do " + state.getOpponentElo(),
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
    // Methods createCapturedPiecesDisplay, addCapturedPiece, updateCapturedPiecesDisplay, 
    // createCapturedPieceIcon, resetCapturedPieces đã được chuyển sang CapturedPiecesManager
    
    // Methods createTimersContainer, startCountdown, updateTimersOnTurnChange, parseTimeToSeconds,
    // formatSecondsToTime, updateTimerLabel, createTimerPane đã được chuyển sang TimerManager
    
    private VBox createLeftIcons() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.TOP_LEFT);
        
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
        
        container.getChildren().addAll(resignContainer, drawContainer);
        
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
                // ChatManager sẽ tự xử lý việc đóng
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

    // Method createChessPieces() đã được chuyển sang ChessBoardManager
    // Giữ lại method cũ để tham khảo (sẽ xóa sau khi kiểm tra)
    /*
    private Pane createChessPieces() {
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
                        char pieceChar = ChessBoardManager.getPieceChar(info.pieceType, info.color.equals("red"));
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
                        // Thêm quân cờ đã bị ăn vào danh sách
                        addCapturedPiece(capInfo.color, capInfo.pieceType);
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
                    addMove(pieceInfo.color, pieceInfo.pieceType, fromRow, fromCol, toRow, toCol, capturedInfo);
                    
                    // Đổi lượt sau khi đi xong
                    currentTurn = currentTurn.equals("red") ? "black" : "red";
                    
                    // Cập nhật timers khi đổi lượt (trong blitz/custom mode)
                    updateTimersOnTurnChange();
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
            String imagePath = "pieces/" + color + "/Chinese-" + pieceType + "-" + color + ".png";
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
            piece.setUserData(new ChessBoardManager.PieceInfo(color, pieceType, imagePath));
            
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
     * Reset quân cờ về vị trí ban đầu
     */
    private void resetChessPieces() {
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
    public String getCurrentTurn() {
        return currentTurn;
    }
    
    /**
     * Set current turn
     */
    public void setCurrentTurn(String turn) {
        this.currentTurn = turn;
    }

    // Methods showChatInput, showChatPopup đã được chuyển sang ChatManager
    // Methods showMovePanel, hideMovePanel, createMoveLabel, addMove, clearMoveHistory đã được chuyển sang MoveHistoryManager

    // Methods showSurrenderConfirmation, hideSurrenderConfirmation, showDrawRequestConfirmation,
    // hideDrawRequestConfirmation, showDrawRequestReceived, hideDrawRequestReceived, showGameResult,
    // showGameResultWithCustomText, showGameResultDraw, hideGameResult, resetGameResultPanels,
    // createDialogButton, setEloChange, getEloChange đã được chuyển sang DialogManager
    /*
    private void showSurrenderConfirmation() {
        // Nếu đã có dialog, xóa nó trước
        if (surrenderDialog != null && rootPane != null && rootPane.getChildren().contains(surrenderDialog)) {
            rootPane.getChildren().remove(surrenderDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        surrenderDialog = new StackPane();
        surrenderDialog.setLayoutX((1920 - 500) / 2);  // Căn giữa theo chiều ngang
        surrenderDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        surrenderDialog.setPrefSize(500, 300);
        
        // Background cho dialog
        Rectangle dialogBg = new Rectangle(500, 300);
        dialogBg.setFill(Color.WHITE);
        dialogBg.setStroke(Color.color(0.3, 0.3, 0.3));
        dialogBg.setStrokeWidth(2);
        dialogBg.setArcWidth(20);
        dialogBg.setArcHeight(20);
        
        // Thêm shadow cho dialog
        DropShadow dialogShadow = new DropShadow();
        dialogShadow.setColor(Color.color(0, 0, 0, 0.5));
        dialogShadow.setRadius(20);
        dialogShadow.setOffsetX(5);
        dialogShadow.setOffsetY(5);
        dialogBg.setEffect(dialogShadow);
        
        // Container chính cho nội dung
        VBox contentContainer = new VBox(30);
        contentContainer.setPrefSize(500, 300);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");  // Giảm padding top từ 40 xuống 20 để dịch lên
        
        // Label câu hỏi
        Label questionLabel = new Label("Are you sure you want to surrender?");
        questionLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +  // Tăng từ 36px lên 48px
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        questionLabel.setAlignment(Pos.CENTER);
        questionLabel.setPrefWidth(420);
        questionLabel.setTranslateY(-20);  // Dịch lên 20px
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Yes
        StackPane yesButton = createDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Xử lý khi bấm Yes (surrender)
            hideSurrenderConfirmation();
            showGameResult(false);  // false = người chơi thua
            e.consume();
        });
        
        // Nút No
        StackPane noButton = createDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            // Đóng dialog khi bấm No
            hideSurrenderConfirmation();
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        contentContainer.getChildren().addAll(questionLabel, buttonsContainer);
        
        surrenderDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(surrenderDialog);
        
        // Fade in animation
        surrenderDialog.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), surrenderDialog);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    private StackPane createDialogButton(String text, boolean isYes) {
        StackPane button = new StackPane();
        button.setPrefSize(150, 60);
        
        // Background cho nút
        Rectangle buttonBg = new Rectangle(150, 60);
        if (isYes) {
            buttonBg.setFill(Color.web("#A65252"));  // Màu đỏ cho nút Yes
        } else {
            buttonBg.setFill(Color.color(0.7, 0.7, 0.7));  // Màu xám cho nút No
        }
        buttonBg.setArcWidth(15);
        buttonBg.setArcHeight(15);
        
        // Label text
        Label buttonLabel = new Label(text);
        buttonLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 32px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        
        button.getChildren().addAll(buttonBg, buttonLabel);
        button.setCursor(Cursor.HAND);
        
        // Hover effect
        button.setOnMouseEntered(e -> {
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), button);
            scaleIn.setToX(1.05);
            scaleIn.setToY(1.05);
            scaleIn.play();
        });
        button.setOnMouseExited(e -> {
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), button);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            scaleOut.play();
        });
        
        return button;
    }
    
    private void hideSurrenderConfirmation() {
        if (surrenderDialog != null && rootPane != null && rootPane.getChildren().contains(surrenderDialog)) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), surrenderDialog);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(surrenderDialog)) {
                    rootPane.getChildren().remove(surrenderDialog);
                }
            });
            fadeOut.play();
        }
    }

    private void showDrawRequestConfirmation() {
        // Nếu đã có dialog, xóa nó trước
        if (drawRequestDialog != null && rootPane != null && rootPane.getChildren().contains(drawRequestDialog)) {
            rootPane.getChildren().remove(drawRequestDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        drawRequestDialog = new StackPane();
        drawRequestDialog.setLayoutX((1920 - 600) / 2);  // Căn giữa theo chiều ngang
        drawRequestDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        drawRequestDialog.setPrefSize(600, 300);
        
        // Background cho dialog
        Rectangle dialogBg = new Rectangle(600, 300);
        dialogBg.setFill(Color.WHITE);
        dialogBg.setStroke(Color.color(0.3, 0.3, 0.3));
        dialogBg.setStrokeWidth(2);
        dialogBg.setArcWidth(20);
        dialogBg.setArcHeight(20);
        
        // Thêm shadow cho dialog
        DropShadow dialogShadow = new DropShadow();
        dialogShadow.setColor(Color.color(0, 0, 0, 0.5));
        dialogShadow.setRadius(20);
        dialogShadow.setOffsetX(5);
        dialogShadow.setOffsetY(5);
        dialogBg.setEffect(dialogShadow);
        
        // Container chính cho nội dung
        VBox contentContainer = new VBox(30);
        contentContainer.setPrefSize(600, 300);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");
        
        // Label câu hỏi
        Label questionLabel = new Label("Are you sure you want to request a draw?");
        questionLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        questionLabel.setAlignment(Pos.CENTER);
        questionLabel.setPrefWidth(520);
        questionLabel.setTranslateY(-20);
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Yes
        StackPane yesButton = createDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Gửi draw request đến đối phương
            hideDrawRequestConfirmation();
            // TODO: Gửi draw request qua network
            // Tạm thời hiển thị dialog cho đối phương (sẽ được thay bằng network call)
            showDrawRequestReceived();
            e.consume();
        });
        
        // Nút No
        StackPane noButton = createDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            hideDrawRequestConfirmation();
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        contentContainer.getChildren().addAll(questionLabel, buttonsContainer);
        
        drawRequestDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(drawRequestDialog);
        
        // Fade in animation
        drawRequestDialog.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), drawRequestDialog);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    private void hideDrawRequestConfirmation() {
        if (drawRequestDialog != null && rootPane != null && rootPane.getChildren().contains(drawRequestDialog)) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), drawRequestDialog);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(drawRequestDialog)) {
                    rootPane.getChildren().remove(drawRequestDialog);
                }
            });
            fadeOut.play();
        }
    }

    private void showDrawRequestReceived() {
        // Nếu đã có dialog, xóa nó trước
        if (drawReceivedDialog != null && rootPane != null && rootPane.getChildren().contains(drawReceivedDialog)) {
            rootPane.getChildren().remove(drawReceivedDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        drawReceivedDialog = new StackPane();
        drawReceivedDialog.setLayoutX((1920 - 600) / 2);  // Căn giữa theo chiều ngang
        drawReceivedDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        drawReceivedDialog.setPrefSize(600, 300);
        
        // Background cho dialog
        Rectangle dialogBg = new Rectangle(600, 300);
        dialogBg.setFill(Color.WHITE);
        dialogBg.setStroke(Color.color(0.3, 0.3, 0.3));
        dialogBg.setStrokeWidth(2);
        dialogBg.setArcWidth(20);
        dialogBg.setArcHeight(20);
        
        // Thêm shadow cho dialog
        DropShadow dialogShadow = new DropShadow();
        dialogShadow.setColor(Color.color(0, 0, 0, 0.5));
        dialogShadow.setRadius(20);
        dialogShadow.setOffsetX(5);
        dialogShadow.setOffsetY(5);
        dialogBg.setEffect(dialogShadow);
        
        // Container chính cho nội dung
        VBox contentContainer = new VBox(30);
        contentContainer.setPrefSize(600, 300);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");
        
        // Label thông báo
        Label messageLabel = new Label("The opposing side wants to sue for peace");
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setPrefWidth(520);
        messageLabel.setTranslateY(-20);
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Yes (chấp nhận draw)
        StackPane yesButton = createDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Chấp nhận draw - kết thúc game hòa
            hideDrawRequestReceived();
            // TODO: Gửi accept draw qua network
            // Tạm thời hiển thị kết quả hòa
            showGameResultDraw();
            e.consume();
        });
        
        // Nút No (từ chối draw)
        StackPane noButton = createDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            hideDrawRequestReceived();
            // TODO: Gửi reject draw qua network
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        contentContainer.getChildren().addAll(messageLabel, buttonsContainer);
        
        drawReceivedDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(drawReceivedDialog);
        
        // Fade in animation
        drawReceivedDialog.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), drawReceivedDialog);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    private void hideDrawRequestReceived() {
        if (drawReceivedDialog != null && rootPane != null && rootPane.getChildren().contains(drawReceivedDialog)) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), drawReceivedDialog);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(drawReceivedDialog)) {
                    rootPane.getChildren().remove(drawReceivedDialog);
                }
            });
            fadeOut.play();
        }
    }
    
    private void showGameResult(boolean isWinner) {
        int eloDelta = isWinner ? eloChange : -eloChange;
        String resultText = isWinner ? "You win" : "You lose";
        showGameResultWithCustomText(resultText, eloDelta);
    }
    
    private void showGameResultWithCustomText(String resultText, int eloDelta) {
        // Tương tự showGameResult nhưng cho phép custom text và elo delta
        // Nếu eloDelta = 0, không hiển thị elo change
        if (gameResultPanel != null && rootPane != null && rootPane.getChildren().contains(gameResultPanel)) {
            rootPane.getChildren().remove(gameResultPanel);
        }
        if (gameResultOverlay != null && rootPane != null && rootPane.getChildren().contains(gameResultOverlay)) {
            rootPane.getChildren().remove(gameResultOverlay);
        }
        
        // Cập nhật elo nếu có thay đổi (theo currentGameMode)
        if (eloDelta != 0) {
            state.addElo(eloDelta);  // addElo tự động dùng currentGameMode
        }
        
        // Tạo overlay để khóa mọi action
        gameResultOverlay = new StackPane();
        gameResultOverlay.setLayoutX(0);
        gameResultOverlay.setLayoutY(0);
        gameResultOverlay.setPrefSize(1920, 1080);
        gameResultOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");
        gameResultOverlay.setPickOnBounds(true);
        gameResultOverlay.setMouseTransparent(false);
        gameResultOverlay.setOnMouseClicked(e -> {
            state.closeGame();
            state.navigateToMainMenu();
            e.consume();
        });
        
        // Tạo result panel
        gameResultPanel = new StackPane();
        gameResultPanel.setLayoutX((1920 - 500) / 2);
        gameResultPanel.setLayoutY((1080 - 300) / 2);
        gameResultPanel.setPrefSize(500, 300);
        gameResultPanel.setPickOnBounds(true);
        gameResultPanel.setMouseTransparent(false);
        gameResultPanel.setOnMouseClicked(e -> {
            state.closeGame();
            state.navigateToMainMenu();
            e.consume();
        });
        
        // Background cho panel
        Rectangle panelBg = new Rectangle(500, 300);
        panelBg.setFill(Color.WHITE);
        panelBg.setStroke(Color.color(0.3, 0.3, 0.3));
        panelBg.setStrokeWidth(2);
        panelBg.setArcWidth(20);
        panelBg.setArcHeight(20);
        
        DropShadow panelShadow = new DropShadow();
        panelShadow.setColor(Color.color(0, 0, 0, 0.5));
        panelShadow.setRadius(20);
        panelShadow.setOffsetX(5);
        panelShadow.setOffsetY(5);
        panelBg.setEffect(panelShadow);
        
        // Container chính cho nội dung
        VBox contentContainer = new VBox(15);
        contentContainer.setPrefSize(500, 300);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");
        contentContainer.setMouseTransparent(true);
        
        // Label kết quả
        Label resultLabel = new Label(resultText);
        // Xác định màu dựa vào resultText
        String textColor = resultText.equals("Draw") ? "#4CAF50" : 
                          (resultText.equals("You win") ? "#4CAF50" : "#A65252");
        resultLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 72px; " +
            "-fx-text-fill: " + textColor + "; " +
            "-fx-background-color: transparent;"
        );
        resultLabel.setAlignment(Pos.CENTER);
        resultLabel.setTranslateY(-30);
        resultLabel.setMouseTransparent(true);
        
        contentContainer.getChildren().add(resultLabel);
        
        // Chỉ thêm elo change label nếu có thay đổi
        if (eloDelta != 0) {
            String eloChangeText = (eloDelta > 0 ? "+" : "") + eloDelta;
            Label eloChangeLabel = new Label(eloChangeText);
            eloChangeLabel.setStyle(
                "-fx-font-family: 'Kolker Brush'; " +
                "-fx-font-size: 48px; " +
                "-fx-text-fill: " + textColor + "; " +  // Dùng cùng màu với result label
                "-fx-background-color: transparent;"
            );
            eloChangeLabel.setAlignment(Pos.CENTER);
            eloChangeLabel.setTranslateY(-30);
            eloChangeLabel.setMouseTransparent(true);
            contentContainer.getChildren().add(eloChangeLabel);
        }
        
        gameResultPanel.getChildren().addAll(panelBg, contentContainer);
        
        rootPane.getChildren().add(gameResultOverlay);
        rootPane.getChildren().add(gameResultPanel);
        
        gameResultOverlay.setOpacity(0);
        gameResultPanel.setOpacity(0);
        FadeTransition overlayFadeIn = new FadeTransition(Duration.millis(300), gameResultOverlay);
        overlayFadeIn.setToValue(1.0);
        FadeTransition panelFadeIn = new FadeTransition(Duration.millis(300), gameResultPanel);
        panelFadeIn.setToValue(1.0);
        overlayFadeIn.play();
        panelFadeIn.play();
    }
    
    private void showGameResultDraw() {
        // Hiển thị kết quả hòa (không thay đổi elo)
        showGameResultWithCustomText("Draw", 0);  // 0 = không thay đổi elo
    }

    // Method để set điểm thay đổi (có thể gọi từ bên ngoài để thay đổi)
    public void setEloChange(int change) {
        this.eloChange = change;
    }
    
    public int getEloChange() {
        return eloChange;
    }

    private void hideGameResult() {
        if (gameResultPanel != null && rootPane != null && rootPane.getChildren().contains(gameResultPanel)) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), gameResultPanel);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(gameResultPanel)) {
                    rootPane.getChildren().remove(gameResultPanel);
                }
                if (gameResultOverlay != null && rootPane.getChildren().contains(gameResultOverlay)) {
                    rootPane.getChildren().remove(gameResultOverlay);
                }
            });
            fadeOut.play();
        } else if (gameResultOverlay != null && rootPane != null && rootPane.getChildren().contains(gameResultOverlay)) {
            rootPane.getChildren().remove(gameResultOverlay);
        }
    }
    
    // Method để reset các panel kết quả ngay lập tức (không cần animation)
    private void resetGameResultPanels() {
        if (rootPane == null) {
            return; // rootPane chưa được khởi tạo
        }
        
        if (gameResultPanel != null) {
            if (rootPane.getChildren().contains(gameResultPanel)) {
                rootPane.getChildren().remove(gameResultPanel);
            }
            gameResultPanel = null;
        }
        if (gameResultOverlay != null) {
            if (rootPane.getChildren().contains(gameResultOverlay)) {
                rootPane.getChildren().remove(gameResultOverlay);
            }
            gameResultOverlay = null;
        }
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
        
        // Nếu player là black, xoay 180 độ
        // Nếu player là red, không xoay (0 độ)
        double rotation = isPlayerRed ? 0.0 : 180.0;
        
        // Xoay boardContainer - điều này sẽ xoay tất cả children (boardImage, piecesContainer)
        // Khi xoay 180 độ, pivot point là center của boardContainer
        boardContainer.setRotate(rotation);
        
        // Đảm bảo piecesContainer cũng được xoay (nếu có)
        if (piecesContainer != null) {
            // piecesContainer sẽ tự động xoay theo boardContainer vì nó là child
            // Nhưng để chắc chắn, set rotation riêng
            piecesContainer.setRotate(rotation);
        }
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
        
        // Reset các Manager
        dialogManager.resetAllDialogs();
        moveHistoryManager.clearMoveHistory();
        capturedPiecesManager.resetCapturedPieces();
        
        // Reset quân cờ về vị trí ban đầu
        if (boardContainer != null) {
            Platform.runLater(() -> {
                resetChessPieces();
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
            
            // Đảm bảo rotation được áp dụng
            updateBoardRotation(state.isPlayerRed());
        }
    }
}