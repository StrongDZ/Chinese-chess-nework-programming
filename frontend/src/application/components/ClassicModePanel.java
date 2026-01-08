package application.components;

import application.state.UIState;
import application.network.NetworkManager;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.scene.Cursor;

public class ClassicModePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private StackPane selectedOption = null; // Để track nút nào đang được chọn

    public ClassicModePanel(UIState state) {
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
        
        StackPane classicModeContent = createClassicModeContent();
        classicModeContent.setLayoutX(0);
        classicModeContent.setLayoutY(0);
        classicModeContent.setPickOnBounds(true);
        classicModeContent.setMouseTransparent(false);
        
        container.getChildren().add(classicModeContent);
        getChildren().add(container);
        
        // Bind visibility - ẩn khi playWithFriendMode = true (chọn Friend + bấm Play)
        // KHÔNG ẩn khi chỉ bấm icon friend (để FriendsPanel hiển thị trên mode panel)
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.classicModeVisibleProperty())
                .and(state.playWithFriendModeProperty().not())  // Chỉ ẩn khi playWithFriendMode = true
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation - ẩn ngay khi playWithFriendMode = true
        state.classicModeVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU && !state.isPlayWithFriendMode()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isClassicModeVisible() && !state.isPlayWithFriendMode()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.playWithFriendModeProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU && state.isClassicModeVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private StackPane createClassicModeContent() {
        StackPane mainPanel = new StackPane();
        mainPanel.setPrefSize(1000, 700);
        
        // Background panel
        Rectangle bg = new Rectangle(1000, 700);
        bg.setFill(Color.color(0.85, 0.85, 0.85));  // Light grey
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(2);
        
        Pane contentPane = new Pane();
        contentPane.setPrefSize(1000, 700);
        
        // Title "Classic mode"
        Label title = new Label("Classic mode");
        title.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 125px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        title.setLayoutX(775);
        title.setLayoutY(130);
        
        // "Time each player: Unlimited time" (no inline icon)
        HBox timeRow = new HBox(15);
        timeRow.setLayoutX(500);
        timeRow.setLayoutY(270);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        
        Label timeLabel = new Label("Time each player: Unlimited time");
        timeLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        timeRow.getChildren().addAll(timeLabel);
        
        // Back icon (separate control) - wrap in StackPane for better hit area
        StackPane backButtonContainer = new StackPane();
        backButtonContainer.setLayoutX(500);
        backButtonContainer.setLayoutY(200);
        backButtonContainer.setPrefSize(130, 130);
        backButtonContainer.setPickOnBounds(true);
        backButtonContainer.setMouseTransparent(false);
        
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(130);
        backIcon.setFitHeight(130);
        backIcon.setPreserveRatio(true);
        
        StackPane backButton = new StackPane(backIcon);
        backButton.setPrefSize(130, 130);
        backButton.setMinSize(130, 130);
        backButton.setMaxSize(130, 130);
        backButton.setCursor(Cursor.HAND);
        backButton.setMouseTransparent(false);
        backButton.setPickOnBounds(true);
        
        // Hover effect for back button
        ScaleTransition backScaleIn = new ScaleTransition(Duration.millis(150), backButton);
        backScaleIn.setToX(1.08);
        backScaleIn.setToY(1.08);
        ScaleTransition backScaleOut = new ScaleTransition(Duration.millis(150), backButton);
        backScaleOut.setToX(1.0);
        backScaleOut.setToY(1.0);
        backButton.setOnMouseEntered(e -> {
            backScaleOut.stop();
            backScaleIn.setFromX(backButton.getScaleX());
            backScaleIn.setFromY(backButton.getScaleY());
            backScaleIn.play();
        });
        backButton.setOnMouseExited(e -> {
            backScaleIn.stop();
            backScaleOut.setFromX(backButton.getScaleX());
            backScaleOut.setFromY(backButton.getScaleY());
            backScaleOut.play();
        });
        
        backButton.setOnMouseClicked(e -> {
            state.closeClassicMode();
            state.openGameMode();  // Sửa từ openGame() thành openGameMode()
        });
        
        backButtonContainer.getChildren().add(backButton);
        
        // "Play with:" text
        Label playWithLabel = new Label("Play with:");
        playWithLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        playWithLabel.setLayoutX(500);
        playWithLabel.setLayoutY(365);
        
        // Container cho 3 nút Random, Friend, AI
        HBox optionsContainer = new HBox(80);  // Tăng từ 20 lên 40
        optionsContainer.setLayoutX(700);
        optionsContainer.setLayoutY(500);
        optionsContainer.setAlignment(Pos.CENTER_LEFT);
        
        // Tạo 3 nút lựa chọn
        StackPane randomButton = createOptionButton("Random");
        StackPane friendButton = createOptionButton("Friend");
        StackPane aiButton = createOptionButton("AI");
        
        // Set Random làm mặc định được chọn
        selectOption(randomButton);
        
        optionsContainer.getChildren().addAll(randomButton, friendButton, aiButton);
        
        // AI button ở dưới
        StackPane aiContainer = new StackPane();
        aiContainer.setLayoutX(830);
        aiContainer.setLayoutY(620);
        aiContainer.getChildren().add(aiButton);
        
        // "Play" button ở góc dưới bên phải
        StackPane playButton = createPlayButton();
        playButton.setLayoutX(1250);  // Giảm margin từ 50 xuống 30 (sang phải 20px)
        playButton.setLayoutY(780);    // Giảm margin từ 50 xuống 30 (xuống dưới 20px)
        
        contentPane.getChildren().addAll(title, backButtonContainer, timeRow, playWithLabel, optionsContainer, aiContainer, playButton);
        mainPanel.getChildren().addAll(bg, contentPane);
        StackPane.setAlignment(mainPanel, Pos.CENTER);

        // Overlay layer for social icons & bottom bar (riêng cho ClassicMode)
        HBox socialIcons = createSocialIcons();
        // 4 icons (80px) + 3 spacing (15px) + padding (50px) = 365px
        socialIcons.setLayoutX(1920 - 365);
        socialIcons.setLayoutY(50);

        StackPane bottomBar = createBottomBar();
        bottomBar.setLayoutX(1920 - 300 - 250);
        bottomBar.setLayoutY(980);

        Pane overlay = new Pane();
        overlay.setPickOnBounds(false);
        overlay.getChildren().addAll(socialIcons, bottomBar);

        StackPane root = new StackPane();
        root.setPrefSize(1920, 1080);
        root.setStyle("-fx-background-color: transparent;");
        root.setPickOnBounds(true);
        root.setMouseTransparent(false);
        root.getChildren().addAll(mainPanel, overlay);

        return root;
    }
    
    private StackPane createOptionButton(String text) {
        Rectangle bg = new Rectangle(250, 100);  // Tăng từ 200x80 lên 250x100
        bg.setFill(Color.TRANSPARENT);  // Mặc định trong suốt
        bg.setStroke(Color.color(0.6, 0.4, 0.3));  // Viền đỏ nâu
        bg.setStrokeWidth(2);
        bg.setArcWidth(35);  // Tăng từ 15 lên 25
        bg.setArcHeight(35);  // Tăng từ 15 lên 25
        
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 80px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center;"
        );
        textLabel.setAlignment(Pos.CENTER);
        
        StackPane button = new StackPane();
        button.setPrefSize(250, 100);
        button.setAlignment(Pos.CENTER);
        button.getChildren().addAll(bg, textLabel);
        button.setCursor(javafx.scene.Cursor.HAND);
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        
        // Hover effect
        button.setOnMouseEntered(e -> {
            if (button != selectedOption) {
                bg.setFill(Color.color(0.9, 0.9, 0.9, 0.5));
            }
        });
        
        button.setOnMouseExited(e -> {
            if (button != selectedOption) {
                bg.setFill(Color.TRANSPARENT);
            }
        });
        
        // Click handler - single selection
        button.setOnMouseClicked(e -> selectOption(button));
        
        return button;
    }
    
    private void selectOption(StackPane button) {
        // Bỏ chọn nút cũ
        if (selectedOption != null) {
            Rectangle oldBg = (Rectangle) selectedOption.getChildren().get(0);
            oldBg.setFill(Color.TRANSPARENT);
        }
        
        // Chọn nút mới
        selectedOption = button;
        Rectangle bg = (Rectangle) button.getChildren().get(0);
        bg.setFill(Color.color(0.85, 0.75, 0.6));  // Màu vàng nâu khi được chọn
    }
    
    private StackPane createPlayButton() {
        Rectangle bg = new Rectangle(200, 80);
        bg.setFill(Color.web("#A8A4A4"));  // Màu A8A4A4
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        // Bỏ stroke - không cần setStroke nữa
        
        Label textLabel = new Label("Play");
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 50px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        StackPane button = new StackPane();
        button.setPrefSize(200, 80);
        button.setAlignment(Pos.CENTER);
        button.getChildren().addAll(bg, textLabel);
        button.setCursor(javafx.scene.Cursor.HAND);
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        
        // Hover effect
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), button);
        scaleIn.setToX(1.05);
        scaleIn.setToY(1.05);
        
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
        
        button.setOnMouseClicked(e -> {
            // Set tất cả 4 timer thành "Unlimited time"
            state.setTimer1Value("Unlimited time");
            state.setTimer2Value("Unlimited time");
            state.setTimer3Value("Unlimited time");
            state.setTimer4Value("Unlimited time");
            
            // Kiểm tra xem đã chọn option nào
            String selectedOptionText = null;
            if (selectedOption != null) {
                // Lấy text từ label trong selectedOption
                for (javafx.scene.Node node : selectedOption.getChildren()) {
                    if (node instanceof Label) {
                        Label label = (Label) node;
                        String text = label.getText();
                        if ("Random".equals(text) || "Friend".equals(text) || "AI".equals(text)) {
                            selectedOptionText = text;
                            break;
                        }
                    }
                }
            }
            
            if ("Random".equals(selectedOptionText)) {
                // Set mode và time limit cho quick matching (classical = unlimited)
                state.setCurrentGameMode("classical");
                state.setCurrentTimeLimit(0);  // 0 = unlimited

                // Gửi QUICK_MATCHING tới backend
                try {
                    NetworkManager.getInstance().game().requestQuickMatching("classical", 0);
                } catch (Exception ex) {
                    System.err.println("[ClassicModePanel] Failed to request quick matching: " + ex.getMessage());
                    ex.printStackTrace();
                    // Không mở waiting nếu gửi message lỗi
                    return;
                }

                // Mở waiting panel khi chọn Random
                state.closeClassicMode();
                state.openWaiting();
            } else if ("Friend".equals(selectedOptionText)) {
                // Set mode và time limit cho challenge
                state.setCurrentGameMode("classical");
                state.setCurrentTimeLimit(0);  // 0 = unlimited
                // Mở PlayWithFriendPanel khi chọn Friend và bấm Play
                // Sử dụng openPlayWithFriend() để đánh dấu đang trong play with friend mode
                state.openPlayWithFriend();
            } else {
                // Chọn AI: mở panel chọn độ khó, không vào game ngay
                state.setCurrentGameMode("classical");
                state.setCurrentTimeLimit(0);
                
                // KHÔNG gửi AI_MATCH ở đây - sẽ gửi sau khi chọn độ khó trong AIDifficultyPanel
                
                // Đóng classic mode và mở AI difficulty panel
                state.closeClassicMode();
                state.openAIDifficulty();
            }
        });
        
        return button;
    }
    
    // Social icons with hover
    private HBox createSocialIcons() {
        HBox icons = new HBox(15);
        icons.setAlignment(Pos.CENTER);

        // Ranking icon với hover effect và click handler
        StackPane rankingContainer = createIconWithHover(AssetHelper.image("icon_ranking.png"), 80);
        rankingContainer.setOnMouseClicked(e -> {
            state.openRanking();
            e.consume();
        });
        
        StackPane fbContainer = createIconWithHover(AssetHelper.image("icon_fb.png"), 80);
        StackPane igContainer = createIconWithHover(AssetHelper.image("icon_ig.png"), 80);
        StackPane helpContainer = createIconWithHover(AssetHelper.image("icon_rule.png"), 80);

        icons.getChildren().addAll(rankingContainer, fbContainer, igContainer, helpContainer);
        return icons;
    }

    private StackPane createIconWithHover(javafx.scene.image.Image image, double size) {
        StackPane container = new StackPane();
        container.setPrefSize(size, size);
        container.setMinSize(size, size);
        container.setMaxSize(size, size);

        ImageView icon = new ImageView(image);
        icon.setFitWidth(size);
        icon.setFitHeight(size);
        icon.setPreserveRatio(true);

        container.getChildren().add(icon);
        container.setCursor(Cursor.HAND);
        container.setMouseTransparent(false);
        container.setPickOnBounds(true);

        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), container);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.1);
        scaleTransition.setToY(1.1);

        container.setOnMouseEntered(e -> {
            scaleTransition.setToX(1.1);
            scaleTransition.setToY(1.1);
            scaleTransition.play();
        });

        container.setOnMouseExited(e -> {
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);
            scaleTransition.play();
        });

        return container;
    }

    // Bottom bar with inventory/friends/settings
    private StackPane createBottomBar() {
        StackPane bottomBar = new StackPane();
        bottomBar.setPrefSize(300, 100);

        ImageView background = new ImageView(AssetHelper.image("bottom_menu.png"));
        background.setPreserveRatio(true);
        background.setFitWidth(552);
        background.setFitHeight(111);

        HBox icons = new HBox(70);
        icons.setAlignment(Pos.CENTER);
        icons.setPadding(new Insets(0, 20, 0, 70));

        StackPane storeContainer = createIconWithHover(AssetHelper.image("icon_inventory.png"), 55);
        storeContainer.setOnMouseClicked(e -> {
            if (state.isInventoryVisible()) {
                state.closeInventory();
            } else {
                state.openInventory();
            }
        });

        StackPane friendsContainer = createIconWithHover(AssetHelper.image("icon_friend.png"), 100);
        friendsContainer.setOnMouseClicked(e -> {
            if (state.isFriendsVisible()) {
                state.closeFriends();
            } else {
                state.openFriends();
            }
        });

        StackPane settingsContainer = createIconWithHover(AssetHelper.image("icon_setting.png"), 100);
        settingsContainer.setOnMouseClicked(e -> state.openSettings());

        icons.getChildren().addAll(storeContainer, friendsContainer, settingsContainer);

        bottomBar.getChildren().addAll(background, icons);
        bottomBar.setAlignment(Pos.CENTER);

        return bottomBar;
    }

    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
