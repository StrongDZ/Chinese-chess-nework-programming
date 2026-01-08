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