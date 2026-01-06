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
    private Timeline[] countdownTimers = new Timeline[4];  // 4 timers
    private int[] remainingSeconds = new int[4];  // Thời gian còn lại (giây)
    private boolean isUpdatingFromCountdown = false;  // Flag để tránh vòng lặp
    
    // Chat UI components
    private StackPane chatInputContainer = null;
    private StackPane chatPopup = null;
    private Pane rootPane = null;  // Lưu reference đến root pane để thêm chat UI
    private StackPane movePanel = null;  // Panel "Move" bên phải
    private javafx.scene.control.ScrollPane moveHistoryScrollPane = null;  // ScrollPane cho lịch sử nước đi
    private VBox moveHistoryContainer = null;  // Container chứa danh sách nước đi
    private java.util.List<String> moveHistory = new java.util.ArrayList<>();  // Lưu lịch sử nước đi
    private StackPane surrenderDialog = null;  // Dialog xác nhận surrender
    private StackPane gameResultPanel = null;  // Panel hiển thị kết quả game
    private StackPane gameResultOverlay = null;  // Overlay để khóa mọi action
    private StackPane drawRequestDialog = null;  // Dialog xác nhận request draw
    private StackPane drawReceivedDialog = null;  // Dialog nhận draw request từ đối phương
    private int eloChange = 10;  // Điểm thay đổi khi win/lose (có thể thay đổi được)
    private Pane piecesContainer = null;  // Lưu reference đến container chứa quân cờ
    private StackPane boardContainer = null;  // Lưu reference đến board container
    private String currentTurn = "Red";  // Lượt hiện tại: "Red" đi trước, sau đó "Black"
    
    // Track captured pieces for each player
    private java.util.Map<String, Integer> redCapturedPieces = new java.util.HashMap<>();  // Quân cờ đỏ đã ăn được (của người chơi đen)
    private java.util.Map<String, Integer> blackCapturedPieces = new java.util.HashMap<>();  // Quân cờ đen đã ăn được (của người chơi đỏ)
    
    // UI components for captured pieces display
    private VBox topLeftCapturedPieces = null;  // Hiển thị quân cờ đã ăn của người chơi đỏ (top-left)
    private VBox bottomRightCapturedPieces = null;  // Hiển thị quân cờ đã ăn của người chơi đen (bottom-right)

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
        
        StackPane gameContent = createGameContent();
        gameContent.setLayoutX(0);
        gameContent.setLayoutY(0);
        gameContent.setPickOnBounds(true);
        gameContent.setMouseTransparent(false);
        
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
                });
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.IN_GAME && state.isGameVisible()) {
                // Reset tất cả các panel trước khi fade in
                Platform.runLater(() -> {
                    resetAllGamePanels();
                    ensurePiecesVisible();
                });
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
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
        
        // Top left: User profile
        HBox topLeftProfile = createUserProfile("ySern", "do 100", true);
        topLeftProfile.setLayoutX(50);
        topLeftProfile.setLayoutY(50);
        
        // Top left: Captured pieces display (dưới avatar)
        topLeftCapturedPieces = createCapturedPiecesDisplay(true);
        topLeftCapturedPieces.setLayoutX(50);
        topLeftCapturedPieces.setLayoutY(50 + 120);  // Dưới avatar (120 là height của avatar)
        
        // Bottom right: Opponent profile
        HBox bottomRightProfile = createUserProfile("ySern", "do 100", false);
        bottomRightProfile.setLayoutX(1920 - 450);  // Giảm từ 500 xuống 550 (sang phải 50px)
        bottomRightProfile.setLayoutY(1080 - 200);
        
        // Bottom right: Captured pieces display (dưới avatar)
        bottomRightCapturedPieces = createCapturedPiecesDisplay(false);
        bottomRightCapturedPieces.setLayoutX(1920 - 450);
        bottomRightCapturedPieces.setLayoutY(1080 - 200 + 120);  // Dưới avatar
        
        // Left side: Timers - đặt cạnh khung đen bên trái
        VBox timersContainer = createTimersContainer();
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
        
        // Tạo và thêm các quân cờ vào bàn cờ
<<<<<<< Updated upstream
        piecesContainer = createChessPieces();
=======
        // createChessPieces() trả về container có highlight layer và các quân cờ
        Pane piecesContainer = createChessPieces();
>>>>>>> Stashed changes
        boardContainer.getChildren().add(piecesContainer);
        
        root.getChildren().addAll(background, topLeftProfile, topLeftCapturedPieces, bottomRightProfile, bottomRightCapturedPieces, 
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
        
        // Chọn avatar ngẫu nhiên từ thư mục ava (1.jpg, 2.jpg, hoặc 3.jpg) để đặt trong ô vuông
        Random random = new Random();
        int avatarNumber = random.nextInt(3) + 1;  // Random từ 1 đến 3
        ImageView avatarInSquare = new ImageView(AssetHelper.image("ava/" + avatarNumber + ".jpg"));
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
        
        // Username - bind với state
        Label usernameLabel = new Label();
        usernameLabel.textProperty().bind(state.usernameProperty());
        usernameLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 60px; -fx-text-fill: white; -fx-background-color: transparent;");
        usernameLabel.setLayoutX(-310);
        usernameLabel.setLayoutY(-10);
        
        // Elo/Level
        Label eloLabel = new Label();
        // Bind với elo từ state theo currentGameMode và format thành "do [elo]"
        eloLabel.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                () -> "do " + state.getElo(),  // getElo() tự động lấy theo currentGameMode
                state.currentGameModeProperty(),
                state.classicalEloProperty(),
                state.blitzEloProperty()
            )
        );
        eloLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 50px; -fx-text-fill: white; -fx-background-color: transparent;");
        eloLabel.setLayoutX(-310);
        eloLabel.setLayoutY(35);
        
        textSection.getChildren().addAll(brushStroke, usernameLabel, eloLabel);

        profile.getChildren().addAll(avatarContainer, textSection);
        return profile;
    }
    
    /**
     * Tạo UI để hiển thị các quân cờ đã ăn được
     * @param isTopLeft true nếu là người chơi top-left (Red), false nếu là bottom-right (Black)
     */
    private VBox createCapturedPiecesDisplay(boolean isTopLeft) {
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
        return container;
    }
    
    /**
     * Thêm quân cờ đã bị ăn vào danh sách và cập nhật UI
     * @param capturedPieceColor Màu của quân cờ bị ăn ("Red" hoặc "Black")
     * @param pieceType Loại quân cờ (King, Advisor, Elephant, Horse, Rook, Cannon, Pawn)
     */
    private void addCapturedPiece(String capturedPieceColor, String pieceType) {
        // Xác định người chơi nào đã ăn (người chơi đối lập với màu quân cờ bị ăn)
        boolean isRedPlayer = capturedPieceColor.equals("Black");  // Nếu ăn quân Black thì là Red player
        
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
        if (pieceColor.equals("Red")) {
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
        pieceChars.put("King", pieceColor.equals("Red") ? "帥" : "將");
        pieceChars.put("Advisor", pieceColor.equals("Red") ? "仕" : "士");
        pieceChars.put("Elephant", pieceColor.equals("Red") ? "相" : "象");
        pieceChars.put("Horse", pieceColor.equals("Red") ? "傌" : "馬");
        pieceChars.put("Rook", pieceColor.equals("Red") ? "俥" : "車");
        pieceChars.put("Cannon", pieceColor.equals("Red") ? "炮" : "砲");
        pieceChars.put("Pawn", pieceColor.equals("Red") ? "兵" : "卒");
        
        pieceLabel.setText(pieceChars.getOrDefault(pieceType, "?"));
        
        // Label hiển thị số lượng (luôn hiển thị nếu > 1) - đặt ở góc dưới bên phải
        Label countLabel = null;
        if (count > 1) {
            countLabel = new Label(String.valueOf(count));
            // Màu chữ cùng với màu quân cờ bị ăn
            String textColor = pieceColor.equals("Red") ? "#DC143C" : "#1C1C1C";
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
    private void resetCapturedPieces() {
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
    
    private VBox createTimersContainer() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.TOP_LEFT);
        
        // Tạo 4 timer riêng biệt và bind với state
        // Timer 1: bind với timer1Value
        StackPane timer1Pane = createTimerPane(state.timer1ValueProperty(), "2:00", 0);
        
        // Timer 2: bind với timer2Value
        StackPane timer2Pane = createTimerPane(state.timer2ValueProperty(), "10:00", 1);
        
        // Timer 3: bind với timer3Value
        StackPane timer3Pane = createTimerPane(state.timer3ValueProperty(), "10:00", 2);
        
        // Timer 4: bind với timer4Value
        StackPane timer4Pane = createTimerPane(state.timer4ValueProperty(), "2:00", 3);
        
        container.getChildren().addAll(timer1Pane, timer2Pane, timer3Pane, timer4Pane);
        
        // Bắt đầu countdown khi giá trị thay đổi (chỉ khi không phải từ countdown)
        state.timer1ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(0, newVal);
            }
        });
        state.timer2ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(1, newVal);
            }
        });
        state.timer3ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(2, newVal);
            }
        });
        state.timer4ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(3, newVal);
            }
        });
        
        // Bắt đầu countdown khi game được mở (để đảm bảo timers bắt đầu đếm)
        state.gameVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.IN_GAME) {
                // Sử dụng Platform.runLater để đảm bảo giá trị đã được set
                javafx.application.Platform.runLater(() -> {
                    // Force start countdown cho tất cả timers
                    String timer1 = state.getTimer1Value();
                    String timer2 = state.getTimer2Value();
                    String timer3 = state.getTimer3Value();
                    String timer4 = state.getTimer4Value();
                    
                    if (timer1 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(0, timer1);
                        isUpdatingFromCountdown = false;
                    }
                    if (timer2 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(1, timer2);
                        isUpdatingFromCountdown = false;
                    }
                    if (timer3 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(2, timer3);
                        isUpdatingFromCountdown = false;
                    }
                    if (timer4 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(3, timer4);
                        isUpdatingFromCountdown = false;
                    }
                });
            } else {
                // Dừng tất cả timers khi game đóng
                for (int i = 0; i < 4; i++) {
                    if (countdownTimers[i] != null) {
                        countdownTimers[i].stop();
                    }
                }
            }
        });
        
        // Thêm listener cho appState để đảm bảo timers bắt đầu khi vào game
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.IN_GAME && state.isGameVisible()) {
                javafx.application.Platform.runLater(() -> {
                    String timer1 = state.getTimer1Value();
                    String timer2 = state.getTimer2Value();
                    String timer3 = state.getTimer3Value();
                    String timer4 = state.getTimer4Value();
                    
                    if (timer1 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(0, timer1);
                        isUpdatingFromCountdown = false;
                    }
                    if (timer2 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(1, timer2);
                        isUpdatingFromCountdown = false;
                    }
                    if (timer3 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(2, timer3);
                        isUpdatingFromCountdown = false;
                    }
                    if (timer4 != null) {
                        isUpdatingFromCountdown = true;
                        startCountdown(3, timer4);
                        isUpdatingFromCountdown = false;
                    }
                });
            }
        });
        
        return container;
    }
    
    private void startCountdown(int timerIndex, String initialValue) {
        // Dừng timer cũ nếu có
        if (countdownTimers[timerIndex] != null) {
            countdownTimers[timerIndex].stop();
        }
        
        // Parse giá trị ban đầu
        int seconds = parseTimeToSeconds(initialValue);
        
        if (seconds < 0) {
            // "Unlimited time" - không đếm
            return;
        }
        
        remainingSeconds[timerIndex] = seconds;
        
        // Cập nhật label ban đầu (không trigger listener)
        isUpdatingFromCountdown = true;
        updateTimerLabel(timerIndex, seconds);
        isUpdatingFromCountdown = false;
        
        // Tạo Timeline đếm ngược mỗi giây
        countdownTimers[timerIndex] = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                remainingSeconds[timerIndex]--;
                if (remainingSeconds[timerIndex] >= 0) {
                    // Update label mà không trigger listener
                    isUpdatingFromCountdown = true;
                    updateTimerLabel(timerIndex, remainingSeconds[timerIndex]);
                    isUpdatingFromCountdown = false;
                } else {
                    countdownTimers[timerIndex].stop();
                }
            })
        );
        countdownTimers[timerIndex].setCycleCount(Timeline.INDEFINITE);
        countdownTimers[timerIndex].play();
    }
    
    private int parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.contains("Unlimited")) {
            return -1;  // Unlimited
        }
        
        // Parse "X mins" hoặc "X min"
        if (timeStr.contains("min")) {
            try {
                String number = timeStr.replaceAll("[^0-9]", "");
                int minutes = Integer.parseInt(number);
                return minutes * 60;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        
        // Parse "MM:SS"
        if (timeStr.contains(":")) {
            try {
                String[] parts = timeStr.split(":");
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60 + seconds;
            } catch (Exception e) {
                return -1;
            }
        }
        
        return -1;
    }
    
    private void updateTimerLabel(int timerIndex, int totalSeconds) {
        String formattedTime = formatSecondsToTime(totalSeconds);
        
        switch (timerIndex) {
            case 0:
                state.setTimer1Value(formattedTime);
                break;
            case 1:
                state.setTimer2Value(formattedTime);
                break;
            case 2:
                state.setTimer3Value(formattedTime);
                break;
            case 3:
                state.setTimer4Value(formattedTime);
                break;
        }
    }
    
    private String formatSecondsToTime(int totalSeconds) {
        if (totalSeconds < 0) {
            return "Unlimited time";
        }
        
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        return String.format("%d:%02d", minutes, seconds);
    }
    
    private StackPane createTimerPane(javafx.beans.property.StringProperty valueProperty, String defaultValue, int index) {
        Rectangle timerBg = new Rectangle(120, 50);
        timerBg.setArcWidth(10);
        timerBg.setArcHeight(10);
        timerBg.setStroke(Color.color(0.3, 0.3, 0.3));
        timerBg.setStrokeWidth(1);
        
        // Timer 2 và 3 (index 1, 2) = #A8A4A4, còn lại = xám
        if (index == 1 || index == 2) {
            timerBg.setFill(Color.web("#A8A4A4"));
        } else {
            timerBg.setFill(Color.color(0.85, 0.85, 0.85));
        }
        
        Label timerLabel = new Label(defaultValue);
        timerLabel.textProperty().bind(valueProperty);  // Bind text với state
        timerLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 35px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        timerLabel.setAlignment(Pos.CENTER);
        
        StackPane timerPane = new StackPane();
        timerPane.setPrefSize(120, 50);
        timerPane.setAlignment(Pos.CENTER);
        timerPane.getChildren().addAll(timerBg, timerLabel);
        
        return timerPane;
    }
    
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
            showSurrenderConfirmation();
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
            showDrawRequestConfirmation();
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
            if (movePanel != null && rootPane != null && rootPane.getChildren().contains(movePanel)) {
                // Nếu panel đang hiện, đóng nó
                hideMovePanel();
            } else {
                // Nếu chưa hiện, mở nó
                if (rootPane != null) {
                    showMovePanel();
                }
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
            if (chatInputContainer != null && rootPane != null && rootPane.getChildren().contains(chatInputContainer)) {
                rootPane.getChildren().remove(chatInputContainer);
            } else {
                // Nếu chưa hiện, mở nó
                if (rootPane != null) {
                    showChatInput();
                }
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
        
<<<<<<< Updated upstream
        // Khoảng cách giữa các giao điểm
        double intersectionSpacingX = (endX - startX) / 8.0;  // 8 khoảng cách cho 9 giao điểm
        double intersectionSpacingY = (endY - startY) / 9.0;  // 9 khoảng cách cho 10 giao điểm
        
        // Kích thước quân cờ
        double pieceWidth = intersectionSpacingX * 0.8;
        double pieceHeight = intersectionSpacingY * 0.8;
        
        // Hàm helper để snap vào giao điểm gần nhất
        java.util.function.BiFunction<Double, Double, double[]> snapToCell = (x, y) -> {
            // x, y là vị trí góc trên trái của quân cờ
            // Tính tâm quân cờ để tìm giao điểm gần nhất
            double pieceCenterX = x + pieceWidth / 2.0;
            double pieceCenterY = y + pieceHeight / 2.0;
            
            // Tính col và row dựa trên tâm quân cờ và vị trí giao điểm
            int col = (int) Math.round((pieceCenterX - startX) / intersectionSpacingX);
            int row = (int) Math.round((pieceCenterY - startY) / intersectionSpacingY);
            
            // Giới hạn trong phạm vi bàn cờ
            col = Math.max(0, Math.min(8, col));
            row = Math.max(0, Math.min(9, row));
            
            // Snap vào giao điểm gần nhất, đặt tâm quân cờ tại giao điểm
            // Tính vị trí giao điểm từ startX, startY
            double intersectionX = startX + col * intersectionSpacingX;
            double intersectionY = startY + row * intersectionSpacingY;
            double snappedX = intersectionX - pieceWidth / 2.0;
            double snappedY = intersectionY - pieceHeight / 2.0;
            return new double[]{snappedX, snappedY, row, col};
=======
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
>>>>>>> Stashed changes
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
                        char pieceChar = GamePanel.getPieceChar(info.pieceType, info.color.equals("Red"));
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
            boolean isRedPiece = pieceInfo != null && pieceInfo.color.equals("Red");
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
                    // Kiểm tra màu: Red pieces là uppercase (K, A, B, N, R, C, P)
                    // Black pieces là lowercase (k, a, b, n, r, c, p)
                    boolean targetIsRed = Character.isUpperCase(targetPiece);
                    boolean selectedIsRed = pieceInfo != null && pieceInfo.color.equals("Red");
                    
                    // Nếu khác màu, đây là quân cờ địch có thể bị ăn
                    if (targetIsRed != selectedIsRed) {
                        hasEnemyPiece = true;
                    }
                }
                
                if (hasEnemyPiece) {
                    // Nếu có thể ăn quân cờ địch: vẽ vòng tròn có viền lớn bao quanh quân cờ địch
                    Circle captureCircle = new Circle();
                    // Vòng tròn lớn hơn để bao quanh quân cờ (khoảng 45% kích thước ô)
                    double circleRadius = Math.min(cellWidth, cellHeight) * 0.45;
                    captureCircle.setRadius(circleRadius);
                    captureCircle.setFill(Color.TRANSPARENT); // Trong suốt
                    captureCircle.setStroke(dotColor); // Viền cùng màu với quân cờ đang chọn
                    captureCircle.setStrokeWidth(4.5); // Viền dày để nổi bật
                    
                    // Đặt vị trí ở giữa ô (nơi quân cờ địch đang đứng)
                    double x = toCol * cellWidth + cellWidth / 2;
                    double y = toRow * cellHeight + cellHeight / 2;
                    captureCircle.setLayoutX(x);
                    captureCircle.setLayoutY(y);
                    
                    highlightLayer.getChildren().add(captureCircle);
                    highlightRects[0].add(captureCircle);
                } else {
                    // Nếu ô trống: dấu chấm tròn đầy
                    Circle dot = new Circle();
                    double dotRadius = Math.min(cellWidth, cellHeight) * 0.15; // 15% kích thước ô
                    dot.setRadius(dotRadius);
                    dot.setFill(dotColor);
                    dot.setStroke(Color.WHITE); // Viền trắng để nổi bật
                    dot.setStrokeWidth(1.5);
                    
                    // Đặt vị trí ở giữa ô
                    double x = toCol * cellWidth + cellWidth / 2;
                    double y = toRow * cellHeight + cellHeight / 2;
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
                                    boolean capturedIsRed = capturedInfo.color.equals("Red");
                                    boolean selectedIsRed = selectedInfo.color.equals("Red");
                                    
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
                    currentTurn = currentTurn.equals("Red") ? "Black" : "Red";
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
            
<<<<<<< Updated upstream
            piece.setOnMousePressed(e -> {
                // Lưu vị trí ban đầu của quân cờ và chuột
                initialX[0] = piece.getLayoutX();
                initialY[0] = piece.getLayoutY();
                mouseX[0] = e.getSceneX();
                mouseY[0] = e.getSceneY();
                
                // Tính toán row và col ban đầu dựa trên giao điểm
                // Tính tâm quân cờ: layoutX + pieceWidth/2
                double pieceCenterX = initialX[0] + pieceWidth / 2.0;
                double pieceCenterY = initialY[0] + pieceHeight / 2.0;
                // Trừ startX, startY để tính vị trí trong vùng giao điểm
                initialCol[0] = (int) Math.round((pieceCenterX - startX) / intersectionSpacingX);
                initialRow[0] = (int) Math.round((pieceCenterY - startY) / intersectionSpacingY);
                // Giới hạn trong phạm vi bàn cờ
                initialRow[0] = Math.max(0, Math.min(9, initialRow[0]));
                initialCol[0] = Math.max(0, Math.min(8, initialCol[0]));
                
                // Đưa quân cờ lên trên cùng khi bắt đầu kéo
                piece.toFront();
                
                // Tăng shadow khi đang kéo
                DropShadow dragShadow = new DropShadow();
                dragShadow.setColor(Color.color(0, 0, 0, 0.7));
                dragShadow.setRadius(12);
                dragShadow.setOffsetX(5);
                dragShadow.setOffsetY(5);
                piece.setEffect(dragShadow);
                
                e.consume();
            });
            
            piece.setOnMouseDragged(e -> {
                // Tính toán offset từ vị trí chuột ban đầu
                double offsetX = e.getSceneX() - mouseX[0];
                double offsetY = e.getSceneY() - mouseY[0];
                
                // Cập nhật vị trí quân cờ
                double newX = initialX[0] + offsetX;
                double newY = initialY[0] + offsetY;
                
                // Giới hạn trong phạm vi bàn cờ
                newX = Math.max(0, Math.min(923 - piece.getFitWidth(), newX));
                newY = Math.max(0, Math.min(923 - piece.getFitHeight(), newY));
                
                piece.setLayoutX(newX);
                piece.setLayoutY(newY);
                
                e.consume();
            });
            
            piece.setOnMouseReleased(e -> {
                // Kiểm tra xem quân cờ có di chuyển thực sự không
                double currentX = piece.getLayoutX();
                double currentY = piece.getLayoutY();
                double moveDistance = Math.sqrt(Math.pow(currentX - initialX[0], 2) + Math.pow(currentY - initialY[0], 2));
                
                // Nếu quân cờ không di chuyển (chỉ bấm và thả), giữ nguyên vị trí
                if (moveDistance < 5.0) {
                    // Khôi phục shadow ban đầu
                    DropShadow normalShadow = new DropShadow();
                    normalShadow.setColor(Color.color(0, 0, 0, 0.5));
                    normalShadow.setRadius(8);
                    normalShadow.setOffsetX(3);
                    normalShadow.setOffsetY(3);
                    piece.setEffect(normalShadow);
                    e.consume();
                    return;
                }
                
                // Snap vào ô gần nhất
                double[] snapped = snapToCell.apply(piece.getLayoutX(), piece.getLayoutY());
                
                // Lấy thông tin quân cờ để áp dụng offset
                PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
                String pieceColor = pieceInfo != null ? pieceInfo.color : "";
                
                // Áp dụng offset điều chỉnh giống như placePiece
                double offsetX = 0;
                double offsetY = 0;
                int col = (int) snapped[3];  // Lấy cột để điều chỉnh
                if ("Red".equals(pieceColor)) {
                    offsetY = -10;  // Dịch lên trên 10px
                    // Điều chỉnh thêm cho các cột bên phải
                    if (col >= 5) {  // Cột 5, 6, 7, 8
                        offsetX = 5;  // Dịch sang phải thêm 5px
                    }
                } else if ("Black".equals(pieceColor)) {
                    offsetX = 4;   // Dịch sang phải 4px
                    // Điều chỉnh thêm cho các cột bên phải
                    if (col >= 5) {  // Cột 5, 6, 7, 8
                        offsetX += 5;  // Dịch sang phải thêm 5px
                    }
                }
                
                // Tính vị trí giao điểm và áp dụng offset
                double intersectionX = startX + snapped[3] * intersectionSpacingX;
                double intersectionY = startY + snapped[2] * intersectionSpacingY;
                double finalX = intersectionX - pieceWidth / 2.0 + offsetX;
                double finalY = intersectionY - pieceHeight / 2.0 + offsetY;
                
                piece.setLayoutX(finalX);
                piece.setLayoutY(finalY);
                
                // Tính toán vị trí mới (row, col)
                int newRow = (int) snapped[2];
                int newCol = (int) snapped[3];
                
                if (pieceInfo != null) {
                    // Chỉ thêm nước đi nếu vị trí thay đổi
                    if (initialRow[0] != newRow || initialCol[0] != newCol) {
                        addMove(pieceInfo.color, pieceInfo.pieceType, initialRow[0], initialCol[0], newRow, newCol);
=======
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
                        boolean clickedIsRed = pieceInfo.color.equals("Red");
                        boolean selectedIsRed = selectedPieceInfo.color.equals("Red");
                        
                        // Nếu khác màu, thử di chuyển và ăn quân cờ này
                        if (clickedIsRed != selectedIsRed) {
                            movePieceTo.accept(pieceRow, pieceCol);
                e.consume();
                            return;
                        }
>>>>>>> Stashed changes
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
                if ("Red".equals(color)) {
                    offsetY = -10;  // Dịch lên trên 10px
                    // Điều chỉnh thêm cho các cột bên phải
                    if (pos[1] >= 5) {  // Cột 5, 6, 7, 8
                        offsetX = 5;  // Dịch sang phải thêm 5px
                    }
                } else if ("Black".equals(color)) {
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
        
<<<<<<< Updated upstream
        // Sắp xếp quân cờ ĐỎ (hàng 0-4, dưới cùng)
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
=======
        // Thêm highlight layer và click layer
        // Thứ tự: click layer (dưới cùng, invisible) -> quân cờ -> highlight layer (trên cùng)
        // Click layer được thêm trước để không chặn click vào quân cờ
        // Nhưng vì nó transparent và ở dưới, click vào quân cờ sẽ được xử lý bởi quân cờ trước
        container.getChildren().add(0, clickLayer); // Thêm vào đầu để ở dưới cùng
        container.getChildren().add(highlightLayer); // Highlight layer ở trên cùng
        
        // Check xem có custom board setup không
        java.util.Map<String, String> customSetup = state.getCustomBoardSetup();
        if (customSetup != null && !customSetup.isEmpty() && state.isUseCustomBoard()) {
            // Áp dụng custom board setup
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
            // Standard starting positions
            // Sắp xếp quân cờ ĐỎ (hàng 0-4, dưới cùng)
            // Hàng 0: Xe, Mã, Tượng, Sĩ, Tướng, Sĩ, Tượng, Mã, Xe
            placePiece.accept(createPiece.apply("Red", "Rook"), new int[]{0, 0});
            placePiece.accept(createPiece.apply("Red", "Horse"), new int[]{0, 1});
            placePiece.accept(createPiece.apply("Red", "Elephant"), new int[]{0, 2});
            placePiece.accept(createPiece.apply("Red", "Advisor"), new int[]{0, 3});
            placePiece.accept(createPiece.apply("Red", "King"), new int[]{0, 4});
            placePiece.accept(createPiece.apply("Red", "Advisor"), new int[]{0, 5});
            placePiece.accept(createPiece.apply("Red", "Elephant"), new int[]{0, 6});
            placePiece.accept(createPiece.apply("Red", "Horse"), new int[]{0, 7});
            placePiece.accept(createPiece.apply("Red", "Rook"), new int[]{0, 8});
            
            // Hàng 2: Pháo ở cột 1 và 7
            placePiece.accept(createPiece.apply("Red", "Cannon"), new int[]{2, 1});
            placePiece.accept(createPiece.apply("Red", "Cannon"), new int[]{2, 7});
            
            // Hàng 3: Tốt ở cột 0, 2, 4, 6, 8
            placePiece.accept(createPiece.apply("Red", "Pawn"), new int[]{3, 0});
            placePiece.accept(createPiece.apply("Red", "Pawn"), new int[]{3, 2});
            placePiece.accept(createPiece.apply("Red", "Pawn"), new int[]{3, 4});
            placePiece.accept(createPiece.apply("Red", "Pawn"), new int[]{3, 6});
            placePiece.accept(createPiece.apply("Red", "Pawn"), new int[]{3, 8});
            
            // Sắp xếp quân cờ ĐEN (hàng 5-9, trên cùng)
            // Hàng 9: Xe, Mã, Tượng, Sĩ, Tướng, Sĩ, Tượng, Mã, Xe
            placePiece.accept(createPiece.apply("Black", "Rook"), new int[]{9, 0});
            placePiece.accept(createPiece.apply("Black", "Horse"), new int[]{9, 1});
            placePiece.accept(createPiece.apply("Black", "Elephant"), new int[]{9, 2});
            placePiece.accept(createPiece.apply("Black", "Advisor"), new int[]{9, 3});
            placePiece.accept(createPiece.apply("Black", "King"), new int[]{9, 4});
            placePiece.accept(createPiece.apply("Black", "Advisor"), new int[]{9, 5});
            placePiece.accept(createPiece.apply("Black", "Elephant"), new int[]{9, 6});
            placePiece.accept(createPiece.apply("Black", "Horse"), new int[]{9, 7});
            placePiece.accept(createPiece.apply("Black", "Rook"), new int[]{9, 8});
            
            // Hàng 7: Pháo ở cột 1 và 7
            placePiece.accept(createPiece.apply("Black", "Cannon"), new int[]{7, 1});
            placePiece.accept(createPiece.apply("Black", "Cannon"), new int[]{7, 7});
            
            // Hàng 6: Tốt ở cột 0, 2, 4, 6, 8
            placePiece.accept(createPiece.apply("Black", "Pawn"), new int[]{6, 0});
            placePiece.accept(createPiece.apply("Black", "Pawn"), new int[]{6, 2});
            placePiece.accept(createPiece.apply("Black", "Pawn"), new int[]{6, 4});
            placePiece.accept(createPiece.apply("Black", "Pawn"), new int[]{6, 6});
            placePiece.accept(createPiece.apply("Black", "Pawn"), new int[]{6, 8});
        }
>>>>>>> Stashed changes
        
        return container;
    }
    
    private void resetChessPieces() {
        // Xóa quân cờ cũ nếu có
        if (piecesContainer != null && boardContainer != null) {
            boardContainer.getChildren().remove(piecesContainer);
        }
        
        // Tạo lại quân cờ mới
        piecesContainer = createChessPieces();
        
        // Thêm vào board container
        if (boardContainer != null) {
            boardContainer.getChildren().add(piecesContainer);
        }
    }

    private void showChatInput() {
        // Nếu đã có input field, ẩn nó trước
        if (chatInputContainer != null && rootPane != null && rootPane.getChildren().contains(chatInputContainer)) {
            rootPane.getChildren().remove(chatInputContainer);
        }
        
        // Tạo chat input field
        chatInputContainer = new StackPane();
        chatInputContainer.setLayoutX((1920 - 400) / 2 + 750);  // Dịch sang phải 100px
        chatInputContainer.setLayoutY(100);
        chatInputContainer.setPrefSize(400, 60);    
        
        // Background cho input
        Rectangle inputBg = new Rectangle(400, 60);
        inputBg.setFill(Color.WHITE);
        inputBg.setStroke(Color.color(0.3, 0.3, 0.3));
        inputBg.setStrokeWidth(2);
        inputBg.setArcWidth(10);
        inputBg.setArcHeight(10);
        
        // TextField để nhập chat
        javafx.scene.control.TextField chatInput = new javafx.scene.control.TextField();
        chatInput.setPrefSize(380, 40);
        chatInput.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 24px; " +
            "-fx-background-color: transparent; " +
            "-fx-border-color: transparent;"
        );
        chatInput.setPromptText("Nhập tin nhắn...");
        
        // Xử lý khi nhấn Enter
        chatInput.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                String message = chatInput.getText().trim();
                if (!message.isEmpty()) {
                    showChatPopup(message);
                    chatInput.clear();
                    // Ẩn input field
                    if (rootPane.getChildren().contains(chatInputContainer)) {
                        rootPane.getChildren().remove(chatInputContainer);
                    }
                }
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                // Ẩn input field khi nhấn ESC
                if (rootPane.getChildren().contains(chatInputContainer)) {
                    rootPane.getChildren().remove(chatInputContainer);
                }
            }
        });
        
        chatInputContainer.getChildren().addAll(inputBg, chatInput);
        chatInputContainer.setAlignment(Pos.CENTER);
        
        // Thêm vào root pane
        rootPane.getChildren().add(chatInputContainer);
        
        // Focus vào input field
        Platform.runLater(() -> chatInput.requestFocus());
    }
    
    private void showChatPopup(String message) {
        // Nếu đã có popup, xóa nó trước
        if (chatPopup != null && rootPane != null && rootPane.getChildren().contains(chatPopup)) {
            rootPane.getChildren().remove(chatPopup);
        }
        
        // Vị trí avatar của đối thủ (bottom-right)
        double avatarX = 1920 - 525;  // 1470
        double avatarY = 1080 - 200;  // 880
        double avatarWidth = 450;  // Width của profile container
        double avatarHeight = 120;  // Height của profile container
        
        // Tính toán vị trí popup - đặt phía trên avatar, căn chỉnh để không tràn ra ngoài
        double popupWidth = 300;
        double popupHeight = 120;
        double popupX = avatarX + (avatarWidth - popupWidth) / 2;  // Căn giữa theo avatar
        // Đảm bảo không tràn ra ngoài màn hình bên phải
        if (popupX + popupWidth > 1920) {
            popupX = 1920 - popupWidth - 20;  // Cách lề phải 20px
        }
        // Đảm bảo không tràn ra ngoài màn hình bên trái
        if (popupX < 0) {
            popupX = 20;  // Cách lề trái 20px
        }
        double popupY = avatarY - popupHeight - 20;  // Phía trên avatar, cách 20px
        
        // Tạo popup container
        Pane popupContainer = new Pane();
        popupContainer.setLayoutX(popupX);
        popupContainer.setLayoutY(popupY);
        popupContainer.setPrefSize(popupWidth, popupHeight);
        
        // Background chính cho popup (nền trắng, viền đỏ nâu)
        Rectangle popupBg = new Rectangle(popupWidth, popupHeight);
        popupBg.setFill(Color.WHITE);
        popupBg.setStroke(Color.color(0.6, 0.4, 0.3));  // Màu đỏ nâu giống border của avatar
        popupBg.setStrokeWidth(2);
        popupBg.setArcWidth(20);
        popupBg.setArcHeight(20);
        popupBg.setLayoutX(0);
        popupBg.setLayoutY(0);
        
        // Tạo tail (đuôi nhọn) trỏ xuống về phía avatar
        // Tính toán vị trí tail để trỏ vào giữa avatar
        double avatarCenterX = avatarX + avatarWidth / 2;  // Giữa avatar: 1470 + 225 = 1695
        // Vị trí tail tương đối với popupContainer (không phải popupX vì đã wrap trong StackPane)
        double tailX = avatarCenterX - popupX - 90;  // Dịch sang trái 30px
        
        // Giới hạn tail trong phạm vi popup (tránh tail ra ngoài)
        tailX = Math.max(30, Math.min(popupWidth - 70, tailX));
        
        javafx.scene.shape.Polygon tail = new javafx.scene.shape.Polygon();
        tail.getPoints().addAll(
            tailX, popupHeight,           // Điểm bắt đầu từ bubble (góc dưới)
            tailX - 15, popupHeight + 20, // Điểm nhọn trỏ xuống
            tailX + 15, popupHeight + 20  // Điểm kết thúc
        );
        tail.setFill(Color.WHITE);
        tail.setStroke(Color.color(0.6, 0.4, 0.3));
        tail.setStrokeWidth(2);
        
        // Label để hiển thị message
        Label messageLabel = new Label(message);
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 36px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        messageLabel.setLayoutX(20);  // Padding trái
        messageLabel.setLayoutY(40);  // Căn giữa theo chiều dọc
        messageLabel.setPrefWidth(popupWidth - 40);  // Chiều rộng còn lại
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.CENTER_LEFT);
        
        popupContainer.getChildren().addAll(popupBg, tail, messageLabel);
        
        // Lưu vào chatPopup - KHÔNG wrap trong StackPane, dùng trực tiếp Pane
        chatPopup = new StackPane();
        // Set vị trí cho chatPopup
        chatPopup.setLayoutX(popupX);
        chatPopup.setLayoutY(popupY);
        chatPopup.getChildren().add(popupContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(chatPopup);
        
        // Fade in animation
        chatPopup.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), chatPopup);
        fadeIn.setToValue(1.0);
        fadeIn.play();
        
        // Tự động ẩn sau 5 giây
        Timeline hideTimer = new Timeline(
            new KeyFrame(Duration.seconds(5), e -> {
                // Fade out animation
                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), chatPopup);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(event -> {
                    if (rootPane.getChildren().contains(chatPopup)) {
                        rootPane.getChildren().remove(chatPopup);
                    }
                });
                fadeOut.play();
            })
        );
        hideTimer.play();
    }

    private void showMovePanel() {
        // Nếu đã có panel, xóa nó trước
        if (movePanel != null && rootPane != null && rootPane.getChildren().contains(movePanel)) {
            rootPane.getChildren().remove(movePanel);
        }
        
        // Tạo panel "Move" bên phải - kích thước 450x755
        movePanel = new StackPane();
        movePanel.setLayoutX(1920 - 450);  // Bên phải, rộng 450px
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
        moveHistoryScrollPane = new javafx.scene.control.ScrollPane();
        moveHistoryScrollPane.setPrefSize(450, 675);  // Chiều cao = 755 - 80 (header)
        moveHistoryScrollPane.setStyle(
            "-fx-background: rgba(230, 230, 230, 0.95); " +  // Dùng -fx-background thay vì -fx-background-color
            "-fx-background-color: rgba(230, 230, 230, 0.95); " +
            "-fx-border-color: transparent;"
        );
        moveHistoryScrollPane.setFitToWidth(true);
        moveHistoryScrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        moveHistoryScrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        
        // Container cho danh sách nước đi
        moveHistoryContainer = new VBox(10);
        moveHistoryContainer.setPrefWidth(430);  // Nhỏ hơn scrollPane một chút để tránh scroll ngang
        moveHistoryContainer.setStyle(
            "-fx-padding: 20; " +
            "-fx-background-color: transparent;"  // Trong suốt để hiển thị màu của ScrollPane
        );
        moveHistoryContainer.setAlignment(Pos.TOP_LEFT);
        
        // Hiển thị các nước đi đã lưu
        for (String move : moveHistory) {
            Label moveHistoryLabel = createMoveLabel(move);
            moveHistoryContainer.getChildren().add(moveHistoryLabel);
        }
        
        moveHistoryScrollPane.setContent(moveHistoryContainer);
        
        // Thêm ScrollPane vào panel container
        panelContainer.getChildren().add(moveHistoryScrollPane);
        
        movePanel.getChildren().addAll(panelBg, panelContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(movePanel);
        
        // Fade in animation
        movePanel.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), movePanel);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    private void hideMovePanel() {
        if (movePanel != null && rootPane != null && rootPane.getChildren().contains(movePanel)) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), movePanel);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(movePanel)) {
                    rootPane.getChildren().remove(movePanel);
                }
            });
            fadeOut.play();
        }
    }
    
    // Helper class để lưu thông tin quân cờ
    private static class PieceInfo {
        String color;
        String pieceType;
        String imagePath;
        
        PieceInfo(String color, String pieceType, String imagePath) {
            this.color = color;
            this.pieceType = pieceType;
            this.imagePath = imagePath;
        }
    }
    
    // Helper method để convert piece type và color thành character
    private static char getPieceChar(String pieceType, boolean isRed) {
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
    
    // Method để tạo label cho mỗi nước đi
    private Label createMoveLabel(String moveText) {
        Label moveLabel = new Label(moveText);
        moveLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 24px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        moveLabel.setPrefWidth(410);
        moveLabel.setAlignment(Pos.CENTER_LEFT);
        return moveLabel;
    }
    
    // Method để thêm nước đi mới vào lịch sử
    public void addMove(String color, String pieceType, int fromRow, int fromCol, int toRow, int toCol) {
        addMove(color, pieceType, fromRow, fromCol, toRow, toCol, "");
    }
    
    // Method để thêm nước đi mới vào lịch sử (với thông tin quân cờ bị ăn)
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
            moveHistoryContainer.getChildren().add(newMoveLabel);
            
            // Scroll xuống nước đi mới nhất
            Platform.runLater(() -> {
                if (moveHistoryScrollPane != null) {
                    moveHistoryScrollPane.setVvalue(1.0);
                }
            });
        }
    }
    
    // Method để xóa lịch sử nước đi (khi bắt đầu game mới)
    public void clearMoveHistory() {
        moveHistory.clear();
        if (moveHistoryContainer != null) {
            moveHistoryContainer.getChildren().clear();
        }
    }

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
    
    // Method để reset tất cả các panel và dialog của game
    private void resetAllGamePanels() {
        if (rootPane == null) {
            return; // rootPane chưa được khởi tạo
        }
        
        // Reset game result panels
        resetGameResultPanels();
        
        // Reset các dialog khác
        if (surrenderDialog != null) {
            if (rootPane.getChildren().contains(surrenderDialog)) {
                rootPane.getChildren().remove(surrenderDialog);
            }
            surrenderDialog = null;
        }
        if (drawRequestDialog != null) {
            if (rootPane.getChildren().contains(drawRequestDialog)) {
                rootPane.getChildren().remove(drawRequestDialog);
            }
            drawRequestDialog = null;
        }
        if (drawReceivedDialog != null) {
            if (rootPane.getChildren().contains(drawReceivedDialog)) {
                rootPane.getChildren().remove(drawReceivedDialog);
            }
            drawReceivedDialog = null;
        }
        
        // Reset chat và move panel
        if (chatInputContainer != null) {
            if (rootPane.getChildren().contains(chatInputContainer)) {
                rootPane.getChildren().remove(chatInputContainer);
            }
            chatInputContainer = null;
        }
        if (chatPopup != null) {
            if (rootPane.getChildren().contains(chatPopup)) {
                rootPane.getChildren().remove(chatPopup);
            }
            chatPopup = null;
        }
        if (movePanel != null) {
            if (rootPane.getChildren().contains(movePanel)) {
                rootPane.getChildren().remove(movePanel);
            }
            movePanel = null;
        }
        
        // Reset move history
        if (moveHistory != null) {
            moveHistory.clear();
        }
        if (moveHistoryContainer != null) {
            moveHistoryContainer.getChildren().clear();
        }
        
<<<<<<< Updated upstream
        // Reset quân cờ về vị trí ban đầu
        if (boardContainer != null) {
            Platform.runLater(() -> {
                resetChessPieces();
            });
        }
=======
        // Reset captured pieces
        resetCapturedPieces();
>>>>>>> Stashed changes
        
        // Reset các biến state
        eloChange = 10; // Reset về giá trị mặc định
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
        }
    }
}
