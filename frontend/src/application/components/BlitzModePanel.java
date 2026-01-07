package application.components;

import application.state.UIState;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.Cursor;

public class BlitzModePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private StackPane selectedTimeOption = null; // Track thời gian được chọn
    private StackPane selectedPlayWithOption = null; // Track đối thủ được chọn

    public BlitzModePanel(UIState state) {
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
        
        StackPane blitzModeContent = createBlitzModeContent();
        blitzModeContent.setLayoutX(0);
        blitzModeContent.setLayoutY(0);
        blitzModeContent.setPickOnBounds(true);
        blitzModeContent.setMouseTransparent(false);
        
        container.getChildren().add(blitzModeContent);
        getChildren().add(container);
        
        // Bind visibility - ẩn khi friendsVisible = true (để PlayWithFriendPanel hiển thị)
        // Bind visibility - ẩn khi playWithFriendMode = true (chọn Friend + bấm Play)
        // KHÔNG ẩn khi chỉ bấm icon friend (để FriendsPanel hiển thị trên mode panel)
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.blitzModeVisibleProperty())
                .and(state.playWithFriendModeProperty().not())  // Chỉ ẩn khi playWithFriendMode = true
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation - ẩn ngay khi playWithFriendMode = true
        state.blitzModeVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU && !state.isPlayWithFriendMode()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isBlitzModeVisible() && !state.isPlayWithFriendMode()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.playWithFriendModeProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU && state.isBlitzModeVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private StackPane createBlitzModeContent() {
        StackPane mainPanel = new StackPane();
        mainPanel.setPrefSize(1000, 700);
        
        // Background panel
        Rectangle bg = new Rectangle(1000, 700);
        bg.setFill(Color.color(0.85, 0.85, 0.85));
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(2);
        
        Pane contentPane = new Pane();
        contentPane.setPrefSize(1000, 700);
        
        // Title "Blitz mode"
        Label title = new Label("Blitz mode");
        title.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 125px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        title.setLayoutX(775);
        title.setLayoutY(130);
        
        // "Time each player:" text
        Label timeLabel = new Label("Time each player:");
        timeLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        timeLabel.setLayoutX(500);
        timeLabel.setLayoutY(250);
        
        // Time selection buttons - Row 1 (3 buttons: 1 mins, 3 mins, 5 mins)
        HBox timeRow1 = new HBox(20);
        timeRow1.setLayoutX(650);  // Giữa panel (1000/2)
        timeRow1.setLayoutY(370);
        timeRow1.setAlignment(Pos.CENTER);  // Đổi từ CENTER_LEFT sang CENTER
        
        StackPane time1min = createTimeButton("1 mins");
        StackPane time3min = createTimeButton("3 mins");
        StackPane time5min = createTimeButton("5 mins");
        selectTimeOption(time1min); // Set mặc định
        
        timeRow1.getChildren().addAll(time1min, time3min, time5min);
        
        // Time selection buttons - Row 2 (3 buttons: 10 mins, 15 mins, 20 mins)
        HBox timeRow2 = new HBox(20);
        timeRow2.setLayoutX(650);  // Giữa panel (1000/2)
        timeRow2.setLayoutY(480);
        timeRow2.setAlignment(Pos.CENTER);  // Đổi từ CENTER_LEFT sang CENTER
        
        StackPane time10min = createTimeButton("10 mins");
        StackPane time15min = createTimeButton("15 mins");
        StackPane time20min = createTimeButton("20 mins");
        
        timeRow2.getChildren().addAll(time10min, time15min, time20min);
        
        // Back icon
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
            state.closeBlitzMode();
            state.openGameMode();
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
        playWithLabel.setLayoutY(580);
        
        // Play with buttons - Row 1 (Random, Friend)
        HBox playWithRow1 = new HBox(80);
        playWithRow1.setLayoutX(700);
        playWithRow1.setLayoutY(680);
        playWithRow1.setAlignment(Pos.CENTER_LEFT);
        
        StackPane randomButton = createPlayWithButton("Random");
        StackPane friendButton = createPlayWithButton("Friend");
        selectPlayWithOption(randomButton); // Set mặc định
        
        playWithRow1.getChildren().addAll(randomButton, friendButton);
        
        // Play with buttons - Row 2 (AI ở giữa)
        HBox playWithRow2 = new HBox(80);
        playWithRow2.setLayoutX(700 + 140); // Căn giữa (khoảng cách giữa Random và Friend là 80 + button width 200 = 280, chia 2 = 140)
        playWithRow2.setLayoutY(780);  // Giảm từ 800 xuống 780 (lên trên 20px)
        playWithRow2.setAlignment(Pos.CENTER);
        
        StackPane aiButton = createPlayWithButton("AI");
        playWithRow2.getChildren().add(aiButton);
        
        // "Play" button
        StackPane playButton = createPlayButton();
        playButton.setLayoutX(1250);
        playButton.setLayoutY(780);
        
        contentPane.getChildren().addAll(title, backButtonContainer, timeLabel, timeRow1, timeRow2, 
            playWithLabel, playWithRow1, playWithRow2, playButton);
        mainPanel.getChildren().addAll(bg, contentPane);
        StackPane.setAlignment(mainPanel, Pos.CENTER);

        // Overlay layer for social icons & bottom bar
        HBox socialIcons = createSocialIcons();
        socialIcons.setLayoutX(1920 - 350);
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
    
    private StackPane createTimeButton(String text) {
        Rectangle bg = new Rectangle(200, 80);  // Giảm từ 250x100 xuống 200x80
        bg.setFill(Color.TRANSPARENT);
        bg.setStroke(Color.color(0.6, 0.4, 0.3));
        bg.setStrokeWidth(2);
        bg.setArcWidth(30);  // Giảm từ 35 xuống 30
        bg.setArcHeight(30);  // Giảm từ 35 xuống 30
        
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 65px; " +  // Giảm từ 80px xuống 65px
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center;"
        );
        textLabel.setAlignment(Pos.CENTER);
        
        StackPane button = new StackPane();
        button.setPrefSize(200, 80);  // Giảm từ 250x100 xuống 200x80
        button.setAlignment(Pos.CENTER);
        button.getChildren().addAll(bg, textLabel);
        button.setCursor(Cursor.HAND);
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        
        button.setOnMouseEntered(e -> {
            if (button != selectedTimeOption) {
                bg.setFill(Color.color(0.9, 0.9, 0.9, 0.5));
            }
        });
        
        button.setOnMouseExited(e -> {
            if (button != selectedTimeOption) {
                bg.setFill(Color.TRANSPARENT);
            }
        });
        
        button.setOnMouseClicked(e -> selectTimeOption(button));
        
        return button;
    }
    
    private StackPane createPlayWithButton(String text) {
        Rectangle bg = new Rectangle(200, 80);  // Giảm từ 250x100 xuống 200x80
        bg.setFill(Color.TRANSPARENT);
        bg.setStroke(Color.color(0.6, 0.4, 0.3));
        bg.setStrokeWidth(2);
        bg.setArcWidth(30);  // Giảm từ 35 xuống 30
        bg.setArcHeight(30);  // Giảm từ 35 xuống 30
        
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 65px; " +  // Giảm từ 80px xuống 65px
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center;"
        );
        textLabel.setAlignment(Pos.CENTER);
        
        StackPane button = new StackPane();
        button.setPrefSize(200, 80);  // Giảm từ 250x100 xuống 200x80
        button.setAlignment(Pos.CENTER);
        button.getChildren().addAll(bg, textLabel);
        button.setCursor(Cursor.HAND);
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        
        button.setOnMouseEntered(e -> {
            if (button != selectedPlayWithOption) {
                bg.setFill(Color.color(0.9, 0.9, 0.9, 0.5));
            }
        });
        
        button.setOnMouseExited(e -> {
            if (button != selectedPlayWithOption) {
                bg.setFill(Color.TRANSPARENT);
            }
        });
        
        button.setOnMouseClicked(e -> selectPlayWithOption(button));
        
        return button;
    }
    
    private void selectTimeOption(StackPane button) {
        if (selectedTimeOption != null) {
            Rectangle oldBg = (Rectangle) selectedTimeOption.getChildren().get(0);
            oldBg.setFill(Color.TRANSPARENT);
        }
        selectedTimeOption = button;
        Rectangle bg = (Rectangle) button.getChildren().get(0);
        bg.setFill(Color.color(0.85, 0.75, 0.6));
    }
    
    private void selectPlayWithOption(StackPane button) {
        if (selectedPlayWithOption != null) {
            Rectangle oldBg = (Rectangle) selectedPlayWithOption.getChildren().get(0);
            oldBg.setFill(Color.TRANSPARENT);
        }
        selectedPlayWithOption = button;
        Rectangle bg = (Rectangle) button.getChildren().get(0);
        bg.setFill(Color.color(0.85, 0.75, 0.6));
    }
    
    private StackPane createPlayButton() {
        Rectangle bg = new Rectangle(200, 80);
        bg.setFill(Color.web("#A8A4A4"));
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        
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
        button.setCursor(Cursor.HAND);
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        
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
            // Lấy giá trị thời gian đã chọn
            String selectedTime = getSelectedTimeValue();
            
            // Timer 1 và 4 = thời gian đã chọn, Timer 2 và 3 = "10:00"
            state.setTimer1Value(selectedTime);
            state.setTimer2Value("10:00");
            state.setTimer3Value("10:00");
            state.setTimer4Value(selectedTime);
            
            // Kiểm tra xem đã chọn option nào
            String selectedOption = null;
            if (selectedPlayWithOption != null) {
                // Lấy text từ label trong selectedPlayWithOption
                for (javafx.scene.Node node : selectedPlayWithOption.getChildren()) {
                    if (node instanceof Label) {
                        Label label = (Label) node;
                        String text = label.getText();
                        if ("Random".equals(text) || "Friend".equals(text) || "AI".equals(text)) {
                            selectedOption = text;
                            break;
                        }
                    }
                }
            }
            
            if ("Random".equals(selectedOption)) {
                // Mở waiting panel khi chọn Random
                state.closeBlitzMode();
                state.openWaiting();
            } else if ("Friend".equals(selectedOption)) {
                // Mở PlayWithFriendPanel khi chọn Friend và bấm Play
                // Sử dụng openPlayWithFriend() để đánh dấu đang trong play with friend mode
                state.openPlayWithFriend();
            } else {
                // Vào game trực tiếp khi chọn AI
                state.closeBlitzMode();
                state.openGame("blitz");
            }
        });
        
        return button;
    }
    
    private String getSelectedTimeValue() {
        if (selectedTimeOption == null) {
            return "1 mins"; // Default
        }
        // Lấy text từ label trong button
        StackPane button = selectedTimeOption;
        Label label = (Label) button.getChildren().get(1); // Label là phần tử thứ 2 (sau Rectangle)
        return label.getText();
    }
    
    private HBox createSocialIcons() {
        HBox icons = new HBox(15);
        icons.setAlignment(Pos.CENTER);

        StackPane fbContainer = createIconWithHover(AssetHelper.image("icon_fb.png"), 80);
        StackPane igContainer = createIconWithHover(AssetHelper.image("icon_ig.png"), 80);
        StackPane helpContainer = createIconWithHover(AssetHelper.image("icon_rule.png"), 80);

        icons.getChildren().addAll(fbContainer, igContainer, helpContainer);
        return icons;
    }

    private StackPane createIconWithHover(javafx.scene.image.Image image, double size) {
        StackPane container = new StackPane();

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
