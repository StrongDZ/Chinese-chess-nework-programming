package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
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
import javafx.animation.TranslateTransition;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class CustomModePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private StackPane selectedSideOption = null; // Track side được chọn (White/red)
    private StackPane selectedLevelOption = null; // Track level được chọn (Easy/Medium/Hard)
    private int gameTimerValue = 5; // Default 5
    private int moveTimerValue = 2; // Default 2

    public CustomModePanel(UIState state) {
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
        
        StackPane customModeContent = createCustomModeContent();
        customModeContent.setLayoutX(0);
        customModeContent.setLayoutY(0);
        customModeContent.setPickOnBounds(true);
        customModeContent.setMouseTransparent(false);
        
        container.getChildren().add(customModeContent);
        getChildren().add(container);
        
        // Bind visibility - ẩn khi playWithFriendMode = true (chọn Friend + bấm Play)
        // KHÔNG ẩn khi chỉ bấm icon friend (để FriendsPanel hiển thị trên mode panel)
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.customModeVisibleProperty())
                .and(state.playWithFriendModeProperty().not())  // Chỉ ẩn khi playWithFriendMode = true
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation - ẩn ngay khi playWithFriendMode = true
        state.customModeVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU && !state.isPlayWithFriendMode()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isCustomModeVisible() && !state.isPlayWithFriendMode()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        // Ẩn ngay khi playWithFriendMode = true
        state.playWithFriendModeProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                setOpacity(0);  // Ẩn ngay lập tức khi playWithFriendMode = true
            } else if (state.appStateProperty().get() == UIState.AppState.MAIN_MENU && state.isCustomModeVisible()) {
                fadeTo(1);
            }
        });
    }
    
    private StackPane createCustomModeContent() {
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
        
        // Title "Custom mode"
        Label title = new Label("Custom mode");
        title.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 125px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        title.setLayoutX(775);
        title.setLayoutY(130);
        
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
            state.closeCustomMode();
            state.openGameMode();  // Sửa từ openGame() thành openGameMode()
        });
        
        backButtonContainer.getChildren().add(backButton);
        
        // Game timer section
        Label gameTimerLabel = new Label("Game timer:");
        gameTimerLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        gameTimerLabel.setLayoutX(480);
        gameTimerLabel.setLayoutY(275);
        
        // Game timer value label (hiển thị giá trị hiện tại)
        Label gameTimerValueLabel = new Label("3 mins");
        gameTimerValueLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        gameTimerValueLabel.setLayoutX(750);
        gameTimerValueLabel.setLayoutY(300);
        
        // Game timer slider container (min 3, max 60)
        VBox gameTimerContainer = createTimerSlider(3, 60, 3, gameTimerValueLabel);
        gameTimerContainer.setLayoutX(550);  // Di chuyển sang phải 70px: 480 + 70 = 550
        gameTimerContainer.setLayoutY(380);
        gameTimerContainer.setPrefWidth(1200);  // Đảm bảo container đủ rộng
        
        // Move timer section
        Label moveTimerLabel = new Label("Move timer:");
        moveTimerLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        moveTimerLabel.setLayoutX(480);
        moveTimerLabel.setLayoutY(405);
        
        // Move timer value label (hiển thị giá trị hiện tại)
        Label moveTimerValueLabel = new Label("1 min");
        moveTimerValueLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        moveTimerValueLabel.setLayoutX(750);
        moveTimerValueLabel.setLayoutY(430);
        
        // Move timer slider container (min 1, max 10)
        VBox moveTimerContainer = createTimerSlider(1, 10, 1, moveTimerValueLabel);
        moveTimerContainer.setLayoutX(550);  // Di chuyển sang phải 70px: 480 + 70 = 550
        moveTimerContainer.setLayoutY(510);
        moveTimerContainer.setPrefWidth(1200);  // Đảm bảo container đủ rộng
        
        // Level selection section
        Label levelLabel = new Label("Level:");
        levelLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 80px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        levelLabel.setLayoutX(480);
        levelLabel.setLayoutY(535);
        
        // Level buttons
        HBox levelContainer = new HBox(15);
        levelContainer.setLayoutX(640);
        levelContainer.setLayoutY(575);
        levelContainer.setAlignment(Pos.CENTER_LEFT);
        
        StackPane easyButton = createLevelButton("Easy");
        StackPane mediumButton = createLevelButton("Medium");
        StackPane hardButton = createLevelButton("Hard");
        selectLevelOption(easyButton); // Set Easy làm mặc định
        
        levelContainer.getChildren().addAll(easyButton, mediumButton, hardButton);
        
        // Side selection section
        Label sideLabel = new Label("Side:");
        sideLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 80px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        sideLabel.setLayoutX(480);
        sideLabel.setLayoutY(635);
        
        // Side buttons
        HBox sideContainer = new HBox(15);
        sideContainer.setLayoutX(640);
        sideContainer.setLayoutY(675);
        sideContainer.setAlignment(Pos.CENTER_LEFT);
        
        StackPane whiteButton = createSideButton("White");
        StackPane redButton = createSideButton("Red");
        selectSideOption(whiteButton); // Set White làm mặc định
        
        sideContainer.getChildren().addAll(whiteButton, redButton);
        
        // Custom Board Setup button
        StackPane customizeBoardButton = createCustomizeBoardButton();
        customizeBoardButton.setLayoutX(480);
        customizeBoardButton.setLayoutY(780);
        
        // "Play" button
        StackPane playButton = createPlayButton();
        playButton.setLayoutX(1180);
        playButton.setLayoutY(780);
        
        contentPane.getChildren().addAll(title, backButtonContainer, gameTimerLabel, gameTimerValueLabel, 
            gameTimerContainer, moveTimerLabel, moveTimerValueLabel, moveTimerContainer, 
            levelLabel, levelContainer, sideLabel, sideContainer, customizeBoardButton, playButton);
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
    
    private VBox createTimerSlider(int minValue, int maxValue, int defaultValue, Label valueLabel) {
        VBox container = new VBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        
        // Slider track - kéo dài hết khung
        Pane trackPane = new Pane();
        trackPane.setPrefWidth(800);  // Kéo dài hết khung (1000 - 480 - 120 margin)
        trackPane.setPrefHeight(20);
        trackPane.setCursor(Cursor.HAND);
        
        Rectangle track = new Rectangle(800, 3);
        track.setFill(Color.color(0.6, 0.6, 0.6));
        track.setLayoutY(8.5);
        
        double trackWidth = 800;
        double minValueDouble = minValue;
        double maxValueDouble = maxValue;
        
        // Tính toán vị trí handle dựa trên defaultValue
        double handlePosition = ((defaultValue - minValueDouble) / (maxValueDouble - minValueDouble)) * trackWidth;
        
        // Handle (circular) - nhỏ lại
        javafx.scene.shape.Circle handle = new javafx.scene.shape.Circle(10);
        handle.setFill(Color.color(0.5, 0.5, 0.5));
        handle.setLayoutX(handlePosition);
        handle.setLayoutY(10);
        handle.setCursor(Cursor.HAND);
        
        // Function để cập nhật giá trị và label
        java.util.function.Consumer<Integer> updateValue = (value) -> {
            int clampedValue = Math.max(minValue, Math.min(maxValue, value));
            String text = clampedValue == 1 ? "1 min" : clampedValue + " mins";
            valueLabel.setText(text);
            if (minValue == 3) {
                gameTimerValue = clampedValue;
            } else {
                moveTimerValue = clampedValue;
            }
        };
        
        // Click vào track để di chuyển handle - chính xác và mượt mà
        trackPane.setOnMouseClicked(e -> {
            // Sử dụng local coordinates của trackPane để chính xác
            double clickX = e.getX();
            double clampedX = Math.max(0, Math.min(trackWidth, clickX));
            
            // Tính toán giá trị chính xác
            double valueRatio = clampedX / trackWidth;
            double exactValue = minValueDouble + valueRatio * (maxValueDouble - minValueDouble);
            int newValue = (int) Math.round(exactValue);
            
            // Snap về vị trí chính xác tương ứng với giá trị
            double exactX = ((newValue - minValueDouble) / (maxValueDouble - minValueDouble)) * trackWidth;
            exactX = Math.max(0, Math.min(trackWidth, exactX));  // Đảm bảo trong bounds
            
            updateValue.accept(newValue);
            handle.setLayoutX(exactX);
            handle.setTranslateX(0);  // Reset translate
        });
        
        // Drag handle - sử dụng scene coordinates để chính xác và mượt mà
        final double[] dragStartSceneX = new double[1];
        final double[] handleStartLayoutX = new double[1];
        final double[] trackPaneStartLayoutX = new double[1];
        
        handle.setOnMousePressed(e -> {
            // Lưu vị trí ban đầu - sử dụng scene coordinates để chính xác
            dragStartSceneX[0] = e.getSceneX();
            handleStartLayoutX[0] = handle.getLayoutX();
            trackPaneStartLayoutX[0] = trackPane.localToScene(0, 0).getX();
            handle.setTranslateX(0);  // Reset translate khi bắt đầu drag
            e.consume();
        });
        
        handle.setOnMouseDragged(e -> {
            // Tính toán delta dựa trên scene coordinates để chính xác nhất
            double deltaSceneX = e.getSceneX() - dragStartSceneX[0];
            double newX = handleStartLayoutX[0] + deltaSceneX;
            
            // Clamp vào bounds của track
            newX = Math.max(0, Math.min(trackWidth, newX));
            
            // Cập nhật vị trí handle ngay lập tức - không dùng animation
            handle.setLayoutX(newX);
            handle.setTranslateX(0);
            
            // Tính toán và cập nhật giá trị chính xác trong khi drag
            double valueRatio = newX / trackWidth;
            double exactValue = minValueDouble + valueRatio * (maxValueDouble - minValueDouble);
            int newValue = (int) Math.round(exactValue);
            updateValue.accept(newValue);
            
            e.consume();
        });
        
        handle.setOnMouseReleased(e -> {
            // Khi thả, snap về vị trí chính xác tương ứng với giá trị hiện tại
            double currentX = handle.getLayoutX();
            
            // Tính toán giá trị chính xác từ vị trí hiện tại
            double valueRatio = currentX / trackWidth;
            double exactValue = minValueDouble + valueRatio * (maxValueDouble - minValueDouble);
            int newValue = (int) Math.round(exactValue);
            
            // Snap về vị trí chính xác tương ứng với giá trị đã làm tròn
            double exactX = ((newValue - minValueDouble) / (maxValueDouble - minValueDouble)) * trackWidth;
            exactX = Math.max(0, Math.min(trackWidth, exactX));  // Đảm bảo trong bounds
            
            // Cập nhật giá trị cuối cùng
            updateValue.accept(newValue);
            
            // Di chuyển handle về vị trí chính xác - không dùng animation để mượt mà
            handle.setLayoutX(exactX);
            handle.setTranslateX(0);  // Reset translate
            e.consume();
        });
        
        trackPane.getChildren().addAll(track, handle);
        
        container.getChildren().add(trackPane);
        
        return container;
    }
    
    private StackPane createLevelButton(String text) {
        Rectangle bg = new Rectangle(150, 60);
        bg.setFill(Color.TRANSPARENT);
        bg.setStroke(Color.color(0.6, 0.4, 0.3));
        bg.setStrokeWidth(2);
        bg.setArcWidth(20);
        bg.setArcHeight(20);
        
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 45px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center;"
        );
        textLabel.setAlignment(Pos.CENTER);
        
        StackPane button = new StackPane();
        button.setPrefSize(150, 60);
        button.setAlignment(Pos.CENTER);
        button.getChildren().addAll(bg, textLabel);
        button.setCursor(Cursor.HAND);
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        
        button.setOnMouseEntered(e -> {
            if (button != selectedLevelOption) {
                bg.setFill(Color.color(0.9, 0.9, 0.9, 0.5));
            }
        });
        
        button.setOnMouseExited(e -> {
            if (button != selectedLevelOption) {
                bg.setFill(Color.TRANSPARENT);
            }
        });
        
        button.setOnMouseClicked(e -> selectLevelOption(button));
        
        return button;
    }
    
    private void selectLevelOption(StackPane button) {
        if (selectedLevelOption != null) {
            Rectangle oldBg = (Rectangle) selectedLevelOption.getChildren().get(0);
            oldBg.setFill(Color.TRANSPARENT);
        }
        selectedLevelOption = button;
        Rectangle bg = (Rectangle) button.getChildren().get(0);
        bg.setFill(Color.color(0.85, 0.75, 0.6));
    }
    
    private StackPane createSideButton(String text) {
        Rectangle bg = new Rectangle(150, 60);
        bg.setFill(Color.TRANSPARENT);
        bg.setStroke(Color.color(0.6, 0.4, 0.3));
        bg.setStrokeWidth(2);
        bg.setArcWidth(20);
        bg.setArcHeight(20);
        
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 45px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center;"
        );
        textLabel.setAlignment(Pos.CENTER);
        
        StackPane button = new StackPane();
        button.setPrefSize(150, 60);
        button.setAlignment(Pos.CENTER);
        button.getChildren().addAll(bg, textLabel);
        button.setCursor(Cursor.HAND);
        button.setMouseTransparent(false);
        button.setPickOnBounds(true);
        
        button.setOnMouseEntered(e -> {
            if (button != selectedSideOption) {
                bg.setFill(Color.color(0.9, 0.9, 0.9, 0.5));
            }
        });
        
        button.setOnMouseExited(e -> {
            if (button != selectedSideOption) {
                bg.setFill(Color.TRANSPARENT);
            }
        });
        
        button.setOnMouseClicked(e -> selectSideOption(button));
        
        return button;
    }
    
    private void selectSideOption(StackPane button) {
        if (selectedSideOption != null) {
            Rectangle oldBg = (Rectangle) selectedSideOption.getChildren().get(0);
            oldBg.setFill(Color.TRANSPARENT);
        }
        selectedSideOption = button;
        Rectangle bg = (Rectangle) button.getChildren().get(0);
        bg.setFill(Color.color(0.85, 0.75, 0.6));
    }
    
    private StackPane createPlayButton() {
        Rectangle bg = new Rectangle(220, 85);
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
        button.setPrefSize(220, 85);
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
            // Lấy giá trị game timer và move timer
            String gameTimerText = gameTimerValue == 1 ? "1 min" : gameTimerValue + " mins";
            String moveTimerText = moveTimerValue == 1 ? "1 min" : moveTimerValue + " mins";
            
            // Timer 2 và 3 = game timer, Timer 1 và 4 = move timer
            state.setTimer1Value(moveTimerText);
            state.setTimer2Value(gameTimerText);
            state.setTimer3Value(gameTimerText);
            state.setTimer4Value(moveTimerText);
            
            state.closeCustomMode();
            state.openGame("custom"); // Set mode là "custom" để load custom board
        });
        
        return button;
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

    private StackPane createCustomizeBoardButton() {
        Rectangle bg = new Rectangle(300, 75);
        bg.setFill(Color.web("#A8A4A4"));
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        
        Label textLabel = new Label("Customize Board");
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 40px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        StackPane button = new StackPane();
        button.setPrefSize(300, 75);
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
            showCustomBoardEditor();
            e.consume();
        });
        
        return button;
    }
    
    private void showCustomBoardEditor() {
        // Tạo dialog/panel để customize board
        StackPane editorDialog = createCustomBoardEditor();
        
        // Thêm vào root pane
        Pane rootPane = (Pane) getChildren().get(0);
        rootPane.getChildren().add(editorDialog);
    }
    
    // Biến để lưu quân cờ đang được chọn
    private PieceEntryInfo selectedPieceEntry = null;
    private Pane highlightLayer = null; // Layer để hiển thị các vị trí hợp lệ
    private Pane currentBoardEditor = null; // Reference đến board editor hiện tại
    
    private StackPane createCustomBoardEditor() {
        // Reset selected piece khi mở editor
        selectedPieceEntry = null;
        
        // Background overlay
        Rectangle overlay = new Rectangle(1920, 1080);
        overlay.setFill(Color.color(0, 0, 0, 0.7)); // Semi-transparent black
        
        // Main panel - mở rộng để chứa board và panels bên ngoài
        StackPane mainPanel = new StackPane();
        mainPanel.setPrefSize(1600, 900);
        
        Rectangle bg = new Rectangle(1600, 900);
        bg.setFill(Color.color(0.9, 0.9, 0.9));
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(3);
        
        // Sử dụng VBox và HBox để căn giữa tự động
        VBox mainContent = new VBox(25); // Giảm spacing một chút
        mainContent.setPrefSize(1600, 900);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(60, 0, 80, 0)); // Tăng padding bottom để tránh tràn lề dưới
        
        // Title - căn giữa tự động
        Label title = new Label("Custom Board Setup");
        title.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 100px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        title.setAlignment(Pos.CENTER);
        
        // Container cho board và panels - sử dụng HBox để căn giữa
        HBox boardContainer = new HBox(50); // 50px spacing giữa panels và board
        boardContainer.setAlignment(Pos.CENTER);
        boardContainer.setPrefHeight(600); // Khớp với chiều cao panel (600px)
        
        // Panel quân cờ bên trái (black pieces)
        VBox leftPiecePanel = createPiecePanel("black");
        
        // Board editor ở giữa - wrap trong VBox để căn giữa theo chiều dọc
        Pane boardEditor = createMiniBoardEditor();
        VBox boardEditorWrapper = new VBox();
        boardEditorWrapper.setAlignment(Pos.CENTER);
        boardEditorWrapper.setPrefHeight(600);
        boardEditorWrapper.getChildren().add(boardEditor);
        
        // Panel quân cờ bên phải (red pieces)
        VBox rightPiecePanel = createPiecePanel("red");
        
        boardContainer.getChildren().addAll(leftPiecePanel, boardEditorWrapper, rightPiecePanel);
        
        // Buttons container - căn giữa tự động
        HBox buttonsContainer = new HBox(30); // 30px spacing giữa các buttons
        buttonsContainer.setAlignment(Pos.CENTER);
        buttonsContainer.setPrefHeight(80);
        
        StackPane resetButton = createEditorButton("Reset", 200, 80);
        resetButton.setOnMouseClicked(e -> {
            resetCustomBoard();
            // Refresh board editor
            Pane rootPane = (Pane) getChildren().get(0);
            rootPane.getChildren().removeIf(node -> node instanceof StackPane && 
                node.getUserData() != null && node.getUserData().equals("customBoardEditor"));
            showCustomBoardEditor();
        });
        
        StackPane saveButton = createEditorButton("Save", 200, 80);
        saveButton.setOnMouseClicked(e -> {
            e.consume(); // Ngăn event propagation
            // Validate trước khi save
            String validationError = validateCustomBoard(boardEditor);
            if (validationError != null) {
                showValidationError(validationError);
                // KHÔNG đóng dialog khi có lỗi
            } else {
                saveCustomBoard();
                closeCustomBoardEditor();
            }
        });
        
        StackPane cancelButton = createEditorButton("Cancel", 200, 80);
        cancelButton.setOnMouseClicked(e -> {
            closeCustomBoardEditor();
        });
        
        buttonsContainer.getChildren().addAll(resetButton, saveButton, cancelButton);
        
        mainContent.getChildren().addAll(title, boardContainer, buttonsContainer);
        mainPanel.getChildren().addAll(bg, mainContent);
        
        StackPane dialog = new StackPane();
        dialog.setPrefSize(1920, 1080);
        dialog.getChildren().addAll(overlay, mainPanel);
        dialog.setUserData("customBoardEditor"); // Mark để có thể remove sau
        StackPane.setAlignment(mainPanel, Pos.CENTER);
        
        // Click overlay để đóng
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                closeCustomBoardEditor();
            }
        });
        
        return dialog;
    }
    
    private VBox createPiecePanel(String color) {
        VBox panel = new VBox(10);
        panel.setPrefWidth(200);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(20, 10, 20, 10));
        
        // Background với màu rõ ràng hơn
        Rectangle bg = new Rectangle(200, 600);
        if (color.equals("red")) {
            // Màu đỏ nhạt cho bên ĐỎ
            bg.setFill(Color.color(1.0, 0.85, 0.85)); // Đỏ nhạt
            bg.setStroke(Color.web("#DC143C")); // Đỏ đậm cho border
        } else {
            // Màu xám đậm cho bên ĐEN
            bg.setFill(Color.color(0.7, 0.7, 0.7)); // Xám đậm
            bg.setStroke(Color.web("#1C1C1C")); // Đen cho border
        }
        bg.setStrokeWidth(3); // Tăng độ dày border
        bg.setArcWidth(10);
        bg.setArcHeight(10);
        
        // Title với màu rõ ràng, căn giữa và to hơn
        String titleText = color + " Pieces";
        String titleColor = color.equals("red") ? "#DC143C" : "#1C1C1C";
        Label title = new Label(titleText);
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 40px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + titleColor + "; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center;"
        );
        
        // Tạo các quân cờ với số lượng đầy đủ
        // Số lượng mặc định: King=1, Advisor=2, Elephant=2, Horse=2, Rook=2, Cannon=2, Pawn=5
        java.util.Map<String, Integer> pieceCounts = new java.util.HashMap<>();
        pieceCounts.put("King", 1);
        pieceCounts.put("Advisor", 2);
        pieceCounts.put("Elephant", 2);
        pieceCounts.put("Horse", 2);
        pieceCounts.put("Rook", 2);
        pieceCounts.put("Cannon", 2);
        pieceCounts.put("Pawn", 5);
        
        String[] pieceTypes = {"King", "Advisor", "Elephant", "Horse", "Rook", "Cannon", "Pawn"};
        
        VBox piecesContainer = new VBox(5);
        piecesContainer.setAlignment(Pos.CENTER);
        
        // Tạo một entry cho mỗi loại quân cờ với số lượng
        for (String pieceType : pieceTypes) {
            int count = pieceCounts.get(pieceType);
            StackPane pieceEntry = createPieceEntryWithCount(color, pieceType, count);
            piecesContainer.getChildren().add(pieceEntry);
        }
        
        // Container cho title để căn giữa hoàn hảo
        VBox titleContainer = new VBox();
        titleContainer.setAlignment(Pos.CENTER);
        titleContainer.setPrefWidth(200);
        titleContainer.getChildren().add(title);
        
        StackPane panelContent = new StackPane();
        panelContent.getChildren().addAll(bg, new VBox(15, titleContainer, piecesContainer));
        StackPane.setAlignment(titleContainer, Pos.TOP_CENTER);
        StackPane.setAlignment(piecesContainer, Pos.CENTER);
        
        panel.getChildren().add(panelContent);
        
        return panel;
    }
    
    private StackPane createPieceEntryWithCount(String color, String pieceType, int initialCount) {
        StackPane entry = new StackPane();
        entry.setPrefWidth(150);
        entry.setPrefHeight(60);
        
        // Quân cờ
        String imagePath = "pieces/" + color + "/Chinese-" + pieceType + "-" + color + ".png";
        ImageView piece = new ImageView(AssetHelper.image(imagePath));
        piece.setFitWidth(50);
        piece.setFitHeight(50);
        piece.setPreserveRatio(true);
        piece.setSmooth(true);
        piece.setCursor(Cursor.HAND);
        
        // Label số lượng
        Label countLabel = new Label(String.valueOf(initialCount));
        countLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 24px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + (color.equals("red") ? "#DC143C" : "#1C1C1C") + "; " +
            "-fx-background-color: transparent;"
        );
        
        // Layout: quân cờ bên trái, số lượng bên phải
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER);
        content.getChildren().addAll(piece, countLabel);
        
        // Lưu thông tin vào userData (bao gồm reference đến entry pane)
        PieceEntryInfo entryInfo = new PieceEntryInfo(color, pieceType, imagePath, initialCount, countLabel, piece, entry);
        entry.setUserData(entryInfo);
        
        entry.getChildren().add(content);
        
        // Click để chọn quân cờ (bỏ drag functionality)
        entry.setOnMouseClicked(e -> {
            PieceEntryInfo info = (PieceEntryInfo) entry.getUserData();
            if (info.count <= 0) {
                e.consume();
                return; // Không cho chọn nếu đã hết
            }
            
            // Bỏ highlight của entry cũ (nếu có)
            if (selectedPieceEntry != null && selectedPieceEntry != info) {
                selectedPieceEntry.entryPane.setStyle("-fx-background-color: transparent;");
            }
            
            // Chọn entry mới
            selectedPieceEntry = info;
            entry.setStyle("-fx-background-color: rgba(255, 255, 0, 0.3); -fx-background-radius: 5;");
            
            // Hiển thị các vị trí hợp lệ cho quân cờ này
            showValidPositions(info.color, info.pieceType);
            
            e.consume();
        });
        
        return entry;
    }
    
    private static class PieceEntryInfo {
        String color;
        String pieceType;
        String imagePath;
        int count;
        Label countLabel;
        ImageView pieceView;
        StackPane entryPane;
        
        PieceEntryInfo(String color, String pieceType, String imagePath, int count, Label countLabel, ImageView pieceView, StackPane entryPane) {
            this.color = color;
            this.pieceType = pieceType;
            this.imagePath = imagePath;
            this.count = count;
            this.countLabel = countLabel;
            this.pieceView = pieceView;
            this.entryPane = entryPane;
        }
    }
    
    private Pane createMiniBoardEditor() {
        Pane container = new Pane();
        // Bàn cờ hình vuông: 500x500
        double boardSize = 500;
        container.setPrefSize(boardSize, boardSize);
        container.setUserData("boardEditor"); // Mark để có thể tìm lại
        
        // Board background (mini version) - thử load từ board_final nếu có
        ImageView boardImage = null;
        try {
            // Thử load board từ selectedBoardImagePath hoặc dùng board.png mặc định
            String boardPath = state.getSelectedBoardImagePath();
            if (boardPath != null && !boardPath.isEmpty()) {
                try {
                    boardImage = new ImageView(AssetHelper.image(boardPath));
                } catch (Exception e) {
                    System.err.println("Cannot load custom board: " + boardPath + ", using default");
                    boardImage = new ImageView(AssetHelper.image("board.png"));
                }
            } else {
                boardImage = new ImageView(AssetHelper.image("board.png"));
            }
        } catch (Exception e) {
            System.err.println("Error loading board image: " + e.getMessage());
            // Fallback: tạo rectangle màu nâu làm background
            Rectangle fallbackBg = new Rectangle(boardSize, boardSize);
            fallbackBg.setFill(Color.web("#D4A574"));
            fallbackBg.setStroke(Color.web("#8B4513"));
            fallbackBg.setStrokeWidth(2);
            fallbackBg.setLayoutX(0);
            fallbackBg.setLayoutY(0);
            container.getChildren().add(0, fallbackBg);
            boardImage = null; // Không có image, dùng fallback
        }
        
        // Lưu boardImage vào final variable để dùng trong lambda
        final ImageView finalBoardImage = boardImage;
        
        if (boardImage != null) {
            boardImage.setFitWidth(boardSize);
            boardImage.setFitHeight(boardSize);
            boardImage.setPreserveRatio(false);
            boardImage.setLayoutX(0);
            boardImage.setLayoutY(0);
            container.getChildren().add(0, boardImage);
        }
        
        double cellWidth = boardSize / 9.0;
        double cellHeight = boardSize / 10.0;
        
        // Tạo click layer để detect click vào board
        Pane clickLayer = new Pane();
        clickLayer.setPrefSize(boardSize, boardSize);
        clickLayer.setStyle("-fx-background-color: transparent;");
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 9; col++) {
                Rectangle cell = new Rectangle(cellWidth, cellHeight);
                cell.setFill(Color.TRANSPARENT);
                cell.setLayoutX(col * cellWidth);
                cell.setLayoutY(row * cellHeight);
                cell.setUserData(new int[]{row, col});
                clickLayer.getChildren().add(cell);
            }
        }
        
        // Click handler cho board - đặt quân cờ tại vị trí được click
        clickLayer.setOnMouseClicked(e -> {
            if (selectedPieceEntry == null || selectedPieceEntry.count <= 0) {
                return; // Không có quân cờ được chọn
            }
            
            // Tính toán row, col từ vị trí click
            double clickX = e.getX();
            double clickY = e.getY();
            
            int col = (int) Math.floor(clickX / cellWidth);
            int row = (int) Math.floor(clickY / cellHeight);
            
            col = Math.max(0, Math.min(8, col));
            row = Math.max(0, Math.min(9, row));
            
            // Kiểm tra xem vị trí này đã có quân cờ chưa
            boolean hasPiece = false;
            for (javafx.scene.Node node : container.getChildren()) {
                if (node instanceof ImageView && node != finalBoardImage) {
                    ImageView existingPiece = (ImageView) node;
                    double pieceX = existingPiece.getLayoutX();
                    double pieceY = existingPiece.getLayoutY();
                    int pieceCol = (int) Math.round((pieceX - (cellWidth - existingPiece.getFitWidth()) / 2) / cellWidth);
                    int pieceRow = (int) Math.round((pieceY - (cellHeight - existingPiece.getFitHeight()) / 2) / cellHeight);
                    if (pieceRow == row && pieceCol == col) {
                        hasPiece = true;
                        break;
                    }
                }
            }
            
            // Kiểm tra xem vị trí này có hợp lệ không
            if (isValidPosition(selectedPieceEntry.color, selectedPieceEntry.pieceType, row, col, container)) {
                if (!hasPiece) {
                    // Tạo quân cờ mới trên board
                    ImageView newPiece = createBoardPiece(selectedPieceEntry.color, selectedPieceEntry.pieceType, cellWidth, cellHeight);
                    double x = col * cellWidth + (cellWidth - newPiece.getFitWidth()) / 2;
                    double y = row * cellHeight + (cellHeight - newPiece.getFitHeight()) / 2;
                    newPiece.setLayoutX(x);
                    newPiece.setLayoutY(y);
                    newPiece.setUserData(new PieceInfo(selectedPieceEntry.color, selectedPieceEntry.pieceType, selectedPieceEntry.imagePath));
                    container.getChildren().add(newPiece);
                    
                    // Giảm số lượng
                    selectedPieceEntry.count--;
                    if (selectedPieceEntry.count <= 0) {
                        // Ẩn entry nếu hết
                        selectedPieceEntry.entryPane.setVisible(false);
                        selectedPieceEntry.entryPane.setManaged(false);
                        selectedPieceEntry.entryPane.setStyle("-fx-background-color: transparent;"); // Bỏ highlight
                        selectedPieceEntry = null; // Bỏ chọn
                        clearHighlights(); // Xóa highlights
                    } else {
                        // Cập nhật label
                        selectedPieceEntry.countLabel.setText(String.valueOf(selectedPieceEntry.count));
                        // Cập nhật lại highlights sau khi đặt quân cờ
                        showValidPositions(selectedPieceEntry.color, selectedPieceEntry.pieceType);
                    }
                }
            }
        });
        
        // Bắt đầu với bàn cờ trống - người dùng sẽ click chọn quân cờ và click vào board để đặt
        // Load từ custom setup nếu có
        java.util.Map<String, String> customSetup = state.getCustomBoardSetup();
        System.out.println("[CustomModePanel] Loading custom board setup. customSetup=" + customSetup + ", isEmpty=" + (customSetup == null || customSetup.isEmpty()) + ", useCustomBoard=" + state.isUseCustomBoard());
        if (customSetup != null && !customSetup.isEmpty() && state.isUseCustomBoard()) {
            System.out.println("[CustomModePanel] Loading " + customSetup.size() + " pieces from custom setup");
            // Load từ custom setup
            for (java.util.Map.Entry<String, String> entry : customSetup.entrySet()) {
                String[] posParts = entry.getKey().split("_");
                int row = Integer.parseInt(posParts[0]);
                int col = Integer.parseInt(posParts[1]);
                String[] pieceParts = entry.getValue().split("_");
                String color = pieceParts[0];
                String pieceType = pieceParts[1];
                
                ImageView piece = createBoardPiece(color, pieceType, cellWidth, cellHeight);
                double x = col * cellWidth + (cellWidth - piece.getFitWidth()) / 2;
                double y = row * cellHeight + (cellHeight - piece.getFitHeight()) / 2;
                piece.setLayoutX(x);
                piece.setLayoutY(y);
                piece.setUserData(new PieceInfo(color, pieceType, "pieces/" + color + "/Chinese-" + pieceType + "-" + color + ".png"));
                container.getChildren().add(piece);
                System.out.println("[CustomModePanel] Loaded piece: " + color + " " + pieceType + " at (" + row + "," + col + ")");
            }
        } else {
            System.out.println("[CustomModePanel] Not loading custom setup - starting with empty board");
        }
        
        // Tạo highlight layer để hiển thị các vị trí hợp lệ
        highlightLayer = new Pane();
        highlightLayer.setPrefSize(boardSize, boardSize);
        highlightLayer.setStyle("-fx-background-color: transparent;");
        highlightLayer.setMouseTransparent(true); // Không chặn click events
        
        // Lưu reference đến board editor
        currentBoardEditor = container;
        
        // Thêm các layer vào container theo thứ tự: board image -> click layer -> highlight layer -> pieces
        if (finalBoardImage != null) {
            container.getChildren().add(1, clickLayer);
            container.getChildren().add(2, highlightLayer);
        } else {
            container.getChildren().add(0, clickLayer);
            container.getChildren().add(1, highlightLayer);
        }
        
        return container;
    }
    
    private ImageView createBoardPiece(String color, String pieceType, double cellWidth, double cellHeight) {
        String imagePath = "pieces/" + color + "/Chinese-" + pieceType + "-" + color + ".png";
        ImageView piece = new ImageView(AssetHelper.image(imagePath));
        piece.setFitWidth(cellWidth * 0.7);
        piece.setFitHeight(cellHeight * 0.7);
        piece.setPreserveRatio(true);
        piece.setSmooth(true);
        piece.setCursor(Cursor.HAND);
        
        // Lưu thông tin quân cờ
        piece.setUserData(new PieceInfo(color, pieceType, imagePath));
        
        // Click để xóa quân cờ và tăng lại số lượng trong panel
        piece.setOnMouseClicked(e -> {
            PieceInfo pieceInfo = (PieceInfo) piece.getUserData();
            if (pieceInfo == null) {
                e.consume();
                return;
            }
            
            // Xóa quân cờ khỏi board
            Pane boardEditor = (Pane) piece.getParent();
            boardEditor.getChildren().remove(piece);
            
            // Tìm và tăng lại số lượng trong panel tương ứng
            // Tìm panel chứa entry của quân cờ này
            // boardEditor.getParent() là VBox (boardEditorWrapper), getParent() của nó là HBox (boardContainer)
            VBox boardEditorWrapper = (VBox) boardEditor.getParent();
            HBox boardContainer = (HBox) boardEditorWrapper.getParent();
            VBox targetPanel = pieceInfo.color.equals("red") ? 
                (VBox) boardContainer.getChildren().get(2) : // rightPiecePanel
                (VBox) boardContainer.getChildren().get(0);  // leftPiecePanel
            
            // Tìm entry tương ứng trong panel
            // Cấu trúc: VBox panel -> StackPane panelContent -> VBox (title + piecesContainer) -> VBox piecesContainer -> StackPane entries
            for (javafx.scene.Node panelNode : targetPanel.getChildren()) {
                if (panelNode instanceof StackPane) {
                    StackPane panelContent = (StackPane) panelNode;
                    for (javafx.scene.Node contentNode : panelContent.getChildren()) {
                        if (contentNode instanceof VBox) {
                            VBox contentVBox = (VBox) contentNode;
                            for (javafx.scene.Node vboxNode : contentVBox.getChildren()) {
                                if (vboxNode instanceof VBox) {
                                    // Đây là piecesContainer
                                    VBox piecesContainer = (VBox) vboxNode;
                                    for (javafx.scene.Node entryNode : piecesContainer.getChildren()) {
                                        if (entryNode instanceof StackPane) {
                                            StackPane entry = (StackPane) entryNode;
                                            Object userData = entry.getUserData();
                                            if (userData instanceof PieceEntryInfo) {
                                                PieceEntryInfo entryInfo = (PieceEntryInfo) userData;
                                                if (entryInfo.color.equals(pieceInfo.color) && 
                                                    entryInfo.pieceType.equals(pieceInfo.pieceType)) {
                                                    // Tăng lại số lượng
                                                    entryInfo.count++;
                                                    entryInfo.countLabel.setText(String.valueOf(entryInfo.count));
                                                    
                                                    // Hiện lại entry nếu đã bị ẩn
                                                    if (!entryInfo.entryPane.isVisible()) {
                                                        entryInfo.entryPane.setVisible(true);
                                                        entryInfo.entryPane.setManaged(true);
                                                    }
                                                    
                                                    // Bỏ highlight nếu đang được chọn
                                                    if (selectedPieceEntry == entryInfo) {
                                                        selectedPieceEntry = null;
                                                        entryInfo.entryPane.setStyle("-fx-background-color: transparent;");
                                                    }
                                                    
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            e.consume();
        });
        
        return piece;
    }
    
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
    
    private StackPane createEditorButton(String text, double width, double height) {
        Rectangle bg = new Rectangle(width, height);
        bg.setFill(Color.web("#A8A4A4"));
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 40px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        StackPane button = new StackPane();
        button.setPrefSize(width, height);
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
        
        return button;
    }
    
    private void resetCustomBoard() {
        state.setCustomBoardSetup(new java.util.HashMap<>());
        state.setUseCustomBoard(false);
    }
    
    private void saveCustomBoard() {
        // Lấy tất cả quân cờ từ board editor và lưu vào state
        Pane rootPane = (Pane) getChildren().get(0);
        StackPane editorDialog = null;
        for (javafx.scene.Node node : rootPane.getChildren()) {
            if (node instanceof StackPane && node.getUserData() != null && 
                node.getUserData().equals("customBoardEditor")) {
                editorDialog = (StackPane) node;
                break;
            }
        }
        
        if (editorDialog != null) {
            // Tìm board editor pane
            // Cấu trúc: StackPane dialog -> StackPane mainPanel -> VBox mainContent -> HBox boardContainer -> VBox boardEditorWrapper -> Pane boardEditor
            Pane boardEditor = null;
            for (javafx.scene.Node node : editorDialog.getChildren()) {
                if (node instanceof StackPane) {
                    StackPane mainPanel = (StackPane) node;
                    for (javafx.scene.Node child : mainPanel.getChildren()) {
                        if (child instanceof VBox) {
                            VBox mainContent = (VBox) child;
                            for (javafx.scene.Node contentChild : mainContent.getChildren()) {
                                if (contentChild instanceof HBox) {
                                    HBox boardContainer = (HBox) contentChild;
                                    for (javafx.scene.Node boardNode : boardContainer.getChildren()) {
                                        // boardNode có thể là VBox (boardEditorWrapper) hoặc Pane (boardEditor)
                                        if (boardNode instanceof VBox) {
                                            // Nếu là VBox wrapper, tìm boardEditor bên trong
                                            VBox wrapper = (VBox) boardNode;
                                            for (javafx.scene.Node wrapperChild : wrapper.getChildren()) {
                                                if (wrapperChild instanceof Pane && wrapperChild.getUserData() != null && 
                                                    wrapperChild.getUserData().equals("boardEditor")) {
                                                    boardEditor = (Pane) wrapperChild;
                                                    break;
                                                }
                                            }
                                        } else if (boardNode instanceof Pane && boardNode.getUserData() != null && 
                                            boardNode.getUserData().equals("boardEditor")) {
                                            boardEditor = (Pane) boardNode;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (boardEditor != null) {
                java.util.Map<String, String> customSetup = new java.util.HashMap<>();
                // Kích thước board hiện tại là 500x500 (hình vuông)
                double boardSize = 500.0;
                double cellWidth = boardSize / 9.0;
                double cellHeight = boardSize / 10.0;
                
                // Lấy vị trí của tất cả quân cờ
                for (javafx.scene.Node node : boardEditor.getChildren()) {
                    if (node instanceof ImageView && node.getUserData() instanceof PieceInfo) {
                        ImageView piece = (ImageView) node;
                        PieceInfo info = (PieceInfo) piece.getUserData();
                        
                        // Tính toán row, col từ layoutX, layoutY
                        double x = piece.getLayoutX();
                        double y = piece.getLayoutY();
                        int col = (int) Math.round((x - (cellWidth - piece.getFitWidth()) / 2) / cellWidth);
                        int row = (int) Math.round((y - (cellHeight - piece.getFitHeight()) / 2) / cellHeight);
                        
                        col = Math.max(0, Math.min(8, col));
                        row = Math.max(0, Math.min(9, row));
                        
                        String key = row + "_" + col;
                        String value = info.color + "_" + info.pieceType;
                        customSetup.put(key, value);
                    }
                }
                
                System.out.println("[CustomModePanel] Saving custom board setup: " + customSetup.size() + " pieces");
                state.setCustomBoardSetup(customSetup);
                state.setUseCustomBoard(true);
                System.out.println("[CustomModePanel] useCustomBoard set to: " + state.isUseCustomBoard());
            }
        }
    }
    
    private void closeCustomBoardEditor() {
        Pane rootPane = (Pane) getChildren().get(0);
        rootPane.getChildren().removeIf(node -> 
            node instanceof StackPane && node.getUserData() != null && 
            node.getUserData().equals("customBoardEditor")
        );
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
    
    /**
     * Validate custom board setup according to Chinese Chess rules
     * @param boardEditor The board editor pane containing pieces
     * @return Error message if validation fails, null if valid
     */
    private String validateCustomBoard(Pane boardEditor) {
        if (boardEditor == null) {
            return "Không tìm thấy bàn cờ để kiểm tra!";
        }
        
        // Kích thước board
        double boardSize = 500.0;
        double cellWidth = boardSize / 9.0;
        double cellHeight = boardSize / 10.0;
        
        // Tạo map để lưu vị trí các quân cờ
        java.util.Map<String, PieceInfo> pieceMap = new java.util.HashMap<>();
        int redKingCount = 0;
        int blackKingCount = 0;
        
        // Thu thập tất cả quân cờ
        for (javafx.scene.Node node : boardEditor.getChildren()) {
            if (node instanceof ImageView && node.getUserData() instanceof PieceInfo) {
                ImageView piece = (ImageView) node;
                PieceInfo info = (PieceInfo) piece.getUserData();
                
                // Tính toán row, col
                double x = piece.getLayoutX();
                double y = piece.getLayoutY();
                int col = (int) Math.round((x - (cellWidth - piece.getFitWidth()) / 2) / cellWidth);
                int row = (int) Math.round((y - (cellHeight - piece.getFitHeight()) / 2) / cellHeight);
                
                col = Math.max(0, Math.min(8, col));
                row = Math.max(0, Math.min(9, row));
                
                String key = row + "_" + col;
                
                pieceMap.put(key, info);
                
                // Đếm số lượng King
                if (info.pieceType.equals("King")) {
                    if (info.color.equals("red")) {
                        redKingCount++;
                    } else {
                        blackKingCount++;
                    }
                }
            }
        }
        
        // Kiểm tra 1: Phải có cả 2 quân Vua trên bàn cờ (ít nhất 1 red King và 1 black King)
        if (redKingCount == 0) {
            return "Must have at least 1 red King on the board!";
        }
        if (blackKingCount == 0) {
            return "Must have at least 1 black King on the board!";
        }
        
        // Kiểm tra 2: Hai quân Vua không được đối mặt trực tiếp (cùng cột, không có quân cờ chặn giữa)
        // Thu thập tất cả các vị trí của red Kings và black Kings
        java.util.List<int[]> redKingPositions = new java.util.ArrayList<>();
        java.util.List<int[]> blackKingPositions = new java.util.ArrayList<>();
        
        for (java.util.Map.Entry<String, PieceInfo> entry : pieceMap.entrySet()) {
            String[] posParts = entry.getKey().split("_");
            int row = Integer.parseInt(posParts[0]);
            int col = Integer.parseInt(posParts[1]);
            PieceInfo info = entry.getValue();
            
            if (info.pieceType.equals("King")) {
                if (info.color.equals("red")) {
                    redKingPositions.add(new int[]{row, col});
                } else {
                    blackKingPositions.add(new int[]{row, col});
                }
            }
        }
        
        // Kiểm tra tất cả các cặp red King và black King xem có đối mặt trực tiếp không
        for (int[] redKingPos : redKingPositions) {
            for (int[] blackKingPos : blackKingPositions) {
                int redKingRow = redKingPos[0];
                int redKingCol = redKingPos[1];
                int blackKingRow = blackKingPos[0];
                int blackKingCol = blackKingPos[1];
                
                // Nếu cùng cột
                if (redKingCol == blackKingCol) {
                    // Kiểm tra xem có quân cờ nào chặn giữa không
                    int startRow = Math.min(redKingRow, blackKingRow);
                    int endRow = Math.max(redKingRow, blackKingRow);
                    boolean hasPieceBetween = false;
                    
                    for (int checkRow = startRow + 1; checkRow < endRow; checkRow++) {
                        String checkKey = checkRow + "_" + redKingCol;
                        if (pieceMap.containsKey(checkKey)) {
                            hasPieceBetween = true;
                            break;
                        }
                    }
                    
                    if (!hasPieceBetween) {
                        return "Two Kings cannot face each other directly (same column " + redKingCol + " with no piece blocking between them)!";
                    }
                }
            }
        }
        
        // Tất cả validation đều pass
        return null;
    }
    
    /**
     * Hiển thị thông báo lỗi validation
     */
    private void showValidationError(String errorMessage) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Custom Board Setup Error");
        alert.setHeaderText("Invalid Board Setup!");
        alert.setContentText(errorMessage);
        alert.getDialogPane().setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 16px;"
        );
        // Set owner window để đảm bảo Alert hiển thị đúng trong fullscreen
        if (getScene() != null && getScene().getWindow() != null) {
            alert.initOwner(getScene().getWindow());
        }
        alert.showAndWait();
    }
    
    /**
     * Hiển thị các vị trí hợp lệ cho một quân cờ
     */
    private void showValidPositions(String color, String pieceType) {
        System.out.println("[CustomModePanel] showValidPositions called: color=" + color + ", pieceType=" + pieceType);
        if (highlightLayer == null || currentBoardEditor == null) {
            System.out.println("[CustomModePanel] highlightLayer or currentBoardEditor is null!");
            return;
        }
        
        // Xóa highlights cũ
        clearHighlights();
        
        // Tính toán các vị trí hợp lệ
        java.util.List<int[]> validPositions = getValidPositionsForPiece(color, pieceType, currentBoardEditor);
        System.out.println("[CustomModePanel] Found " + validPositions.size() + " valid positions");
        
        // Hiển thị các vị trí hợp lệ
        double boardSize = 500.0;
        double cellWidth = boardSize / 9.0;
        double cellHeight = boardSize / 10.0;
        double dotSize = Math.min(cellWidth, cellHeight) * 0.3;
        
        String dotColor = color.equals("red") ? "#DC143C" : "#1C1C1C";
        
        for (int[] pos : validPositions) {
            int row = pos[0];
            int col = pos[1];
            
            // Kiểm tra xem vị trí này đã có quân cờ chưa
            boolean hasPiece = false;
            for (javafx.scene.Node node : currentBoardEditor.getChildren()) {
                if (node instanceof ImageView && node.getUserData() instanceof PieceInfo) {
                    ImageView existingPiece = (ImageView) node;
                    double pieceX = existingPiece.getLayoutX();
                    double pieceY = existingPiece.getLayoutY();
                    int pieceCol = (int) Math.round((pieceX - (cellWidth - existingPiece.getFitWidth()) / 2) / cellWidth);
                    int pieceRow = (int) Math.round((pieceY - (cellHeight - existingPiece.getFitHeight()) / 2) / cellHeight);
                    if (pieceRow == row && pieceCol == col) {
                        hasPiece = true;
                        break;
                    }
                }
            }
            
            // Tính toán vị trí center của cell
            double centerX = col * cellWidth + cellWidth / 2;
            double centerY = row * cellHeight + cellHeight / 2;
            
            Circle dot = new Circle(dotSize / 2);
            dot.setFill(Color.web(dotColor));
            dot.setStroke(Color.WHITE);
            dot.setStrokeWidth(2);
            dot.setLayoutX(centerX);
            dot.setLayoutY(centerY);
            dot.setOpacity(0.8);
            
            highlightLayer.getChildren().add(dot);
            System.out.println("[CustomModePanel] Added dot at (" + row + "," + col + ")");
        }
    }
    
    /**
     * Xóa tất cả highlights
     */
    private void clearHighlights() {
        if (highlightLayer != null) {
            highlightLayer.getChildren().clear();
        }
    }
    
    /**
     * Tính toán các vị trí hợp lệ cho một quân cờ
     */
    private java.util.List<int[]> getValidPositionsForPiece(String color, String pieceType, Pane boardEditor) {
        java.util.List<int[]> validPositions = new java.util.ArrayList<>();
        
        // Tạo map các quân cờ hiện có trên board
        java.util.Map<String, PieceInfo> pieceMap = new java.util.HashMap<>();
        double boardSize = 500.0;
        double cellWidth = boardSize / 9.0;
        double cellHeight = boardSize / 10.0;
        
        for (javafx.scene.Node node : boardEditor.getChildren()) {
            if (node instanceof ImageView && node.getUserData() instanceof PieceInfo) {
                ImageView piece = (ImageView) node;
                PieceInfo info = (PieceInfo) piece.getUserData();
                double x = piece.getLayoutX();
                double y = piece.getLayoutY();
                int col = (int) Math.round((x - (cellWidth - piece.getFitWidth()) / 2) / cellWidth);
                int row = (int) Math.round((y - (cellHeight - piece.getFitHeight()) / 2) / cellHeight);
                col = Math.max(0, Math.min(8, col));
                row = Math.max(0, Math.min(9, row));
                String key = row + "_" + col;
                pieceMap.put(key, info);
            }
        }
        
        // Duyệt qua tất cả các vị trí trên board
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 9; col++) {
                String key = row + "_" + col;
                
                // Bỏ qua nếu vị trí này đã có quân cờ
                if (pieceMap.containsKey(key)) {
                    continue;
                }
                
                // Kiểm tra xem vị trí này có hợp lệ không
                if (isValidPosition(color, pieceType, row, col, boardEditor)) {
                    validPositions.add(new int[]{row, col});
                }
            }
        }
        
        return validPositions;
    }
    
    /**
     * Kiểm tra xem một vị trí có hợp lệ cho một quân cờ không
     */
    private boolean isValidPosition(String color, String pieceType, int row, int col, Pane boardEditor) {
        // Kiểm tra các quy tắc validation tương tự như trong validateCustomBoard
        
        // King: chỉ được trong palace
        if (pieceType.equals("King")) {
            if (color.equals("red")) {
                return (row >= 0 && row <= 2 && col >= 3 && col <= 5);
            } else {
                return (row >= 7 && row <= 9 && col >= 3 && col <= 5);
            }
        }
        
        // Advisor: chỉ được trong palace
        if (pieceType.equals("Advisor")) {
            if (color.equals("red")) {
                return (row >= 0 && row <= 2 && col >= 3 && col <= 5);
            } else {
                return (row >= 7 && row <= 9 && col >= 3 && col <= 5);
            }
        }
        
        // Elephant: không được vượt sông và chỉ ở các vị trí hợp lệ
        if (pieceType.equals("Elephant")) {
            if (color.equals("red")) {
                if (row > 4) return false; // Không được vượt sông
                // Các vị trí hợp lệ cho red elephant
                int[][] validPositions = {
                    {0, 2}, {0, 6},
                    {2, 0}, {2, 4}, {2, 8},
                    {4, 2}, {4, 6}
                };
                for (int[] pos : validPositions) {
                    if (row == pos[0] && col == pos[1]) {
                        return true;
                    }
                }
                return false;
            } else {
                if (row < 5) return false; // Không được vượt sông
                // Các vị trí hợp lệ cho black elephant
                int[][] validPositions = {
                    {5, 2}, {5, 6},
                    {7, 0}, {7, 4}, {7, 8},
                    {9, 2}, {9, 6}
                };
                for (int[] pos : validPositions) {
                    if (row == pos[0] && col == pos[1]) {
                        return true;
                    }
                }
                return false;
            }
        }
        
        // Các quân cờ khác (Horse, Rook, Cannon, Pawn) có thể đặt ở bất kỳ đâu trên board
        return true;
    }
}
