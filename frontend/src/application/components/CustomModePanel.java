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
import javafx.util.Duration;
import javafx.scene.Cursor;
import javafx.animation.TranslateTransition;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.binding.Bindings;

public class CustomModePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private StackPane selectedSideOption = null; // Track side được chọn (White/Red)
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
        
        // Bind visibility
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.customModeVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation
        state.customModeVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isCustomModeVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
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
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        gameTimerLabel.setLayoutX(500);
        gameTimerLabel.setLayoutY(270);
        
        // Game timer value label (hiển thị giá trị hiện tại)
        Label gameTimerValueLabel = new Label("3 mins");
        gameTimerValueLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        gameTimerValueLabel.setLayoutX(850);
        gameTimerValueLabel.setLayoutY(270);
        
        // Game timer slider container (min 3, max 60)
        VBox gameTimerContainer = createTimerSlider(3, 60, 3, gameTimerValueLabel);
        gameTimerContainer.setLayoutX(550);  // Căn giữa: (1000 - 800) / 2 = 100, nhưng điều chỉnh thành 150
        gameTimerContainer.setLayoutY(400);  // Lùi xuống từ 370 lên 400
        
        // Move timer section
        Label moveTimerLabel = new Label("Move timer:");
        moveTimerLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        moveTimerLabel.setLayoutX(500);
        moveTimerLabel.setLayoutY(480);
        
        // Move timer value label (hiển thị giá trị hiện tại)
        Label moveTimerValueLabel = new Label("1 min");
        moveTimerValueLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        moveTimerValueLabel.setLayoutX(850);
        moveTimerValueLabel.setLayoutY(480);
        
        // Move timer slider container (min 1, max 10)
        VBox moveTimerContainer = createTimerSlider(1, 10, 1, moveTimerValueLabel);
        moveTimerContainer.setLayoutX(550);  // Căn giữa: (1000 - 800) / 2 = 100, nhưng điều chỉnh thành 150
        moveTimerContainer.setLayoutY(610);  // Lùi xuống từ 580 lên 610
        
        // Side selection section
        Label sideLabel = new Label("Side:");
        sideLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        sideLabel.setLayoutX(500);
        sideLabel.setLayoutY(680);
        
        // Side buttons
        HBox sideContainer = new HBox(20);
        sideContainer.setLayoutX(700);
        sideContainer.setLayoutY(700);
        sideContainer.setAlignment(Pos.CENTER_LEFT);
        
        StackPane whiteButton = createSideButton("White");
        StackPane redButton = createSideButton("Red");
        selectSideOption(whiteButton); // Set White làm mặc định
        
        sideContainer.getChildren().addAll(whiteButton, redButton);
        
        // Custom Board Setup button
        StackPane customizeBoardButton = createCustomizeBoardButton();
        customizeBoardButton.setLayoutX(500);
        customizeBoardButton.setLayoutY(800);
        
        // "Play" button
        StackPane playButton = createPlayButton();
        playButton.setLayoutX(1250);
        playButton.setLayoutY(780);
        
        contentPane.getChildren().addAll(title, backButtonContainer, gameTimerLabel, gameTimerValueLabel, 
            gameTimerContainer, moveTimerLabel, moveTimerValueLabel, moveTimerContainer, 
            sideLabel, sideContainer, customizeBoardButton, playButton);
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
        
        // Slider track - kéo dài ra
        Pane trackPane = new Pane();
        trackPane.setPrefWidth(800);  // Tăng lên 800
        trackPane.setPrefHeight(30);
        trackPane.setCursor(Cursor.HAND);
        
        Rectangle track = new Rectangle(800, 4);
        track.setFill(Color.color(0.6, 0.6, 0.6));
        track.setLayoutY(13);
        
        double trackWidth = 800;
        double minValueDouble = minValue;
        double maxValueDouble = maxValue;
        
        // Tính toán vị trí handle dựa trên defaultValue
        double handlePosition = ((defaultValue - minValueDouble) / (maxValueDouble - minValueDouble)) * trackWidth;
        
        // Handle (circular)
        javafx.scene.shape.Circle handle = new javafx.scene.shape.Circle(15);
        handle.setFill(Color.color(0.5, 0.5, 0.5));
        handle.setLayoutX(handlePosition);
        handle.setLayoutY(15);
        handle.setCursor(Cursor.HAND);
        
        // Animation cho handle - mượt mà hơn
        TranslateTransition transition = new TranslateTransition(Duration.millis(200), handle);
        
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
        
        // Function để di chuyển handle đến vị trí mới với animation
        java.util.function.Consumer<Double> moveHandle = (targetX) -> {
            double clampedX = Math.max(0, Math.min(trackWidth, targetX));
            int newValue = (int) Math.round(minValueDouble + (clampedX / trackWidth) * (maxValueDouble - minValueDouble));
            updateValue.accept(newValue);
            
            // Animation mượt mà
            transition.stop();
            transition.setFromX(handle.getTranslateX());
            transition.setToX(clampedX - handle.getLayoutX());
            transition.play();
            
            handle.setLayoutX(clampedX);
        };
        
        // Click vào track để di chuyển handle
        trackPane.setOnMouseClicked(e -> {
            double clickX = e.getX();
            moveHandle.accept(clickX);
        });
        
        // Drag handle với animation mượt mà
        final double[] dragStartX = new double[1];
        final double[] handleStartX = new double[1];
        
        handle.setOnMousePressed(e -> {
            transition.stop();
            dragStartX[0] = e.getSceneX();
            handleStartX[0] = handle.getLayoutX();
            e.consume();
        });
        
        handle.setOnMouseDragged(e -> {
            // Tính toán vị trí dựa trên offset từ khi bắt đầu drag
            double deltaX = e.getSceneX() - dragStartX[0];
            double newX = Math.max(0, Math.min(trackWidth, handleStartX[0] + deltaX));
            handle.setLayoutX(newX);
            
            // Cập nhật giá trị và label trong khi drag
            int newValue = (int) Math.round(minValueDouble + (newX / trackWidth) * (maxValueDouble - minValueDouble));
            updateValue.accept(newValue);
            e.consume();
        });
        
        handle.setOnMouseReleased(e -> {
            // Khi thả, đảm bảo handle ở vị trí chính xác
            double currentX = handle.getLayoutX();
            int newValue = (int) Math.round(minValueDouble + (currentX / trackWidth) * (maxValueDouble - minValueDouble));
            double exactX = ((newValue - minValueDouble) / (maxValueDouble - minValueDouble)) * trackWidth;
            moveHandle.accept(exactX);
            e.consume();
        });
        
        trackPane.getChildren().addAll(track, handle);
        
        container.getChildren().add(trackPane);
        
        return container;
    }
    
    private StackPane createSideButton(String text) {
        Rectangle bg = new Rectangle(200, 80);
        bg.setFill(Color.TRANSPARENT);
        bg.setStroke(Color.color(0.6, 0.4, 0.3));
        bg.setStrokeWidth(2);
        bg.setArcWidth(30);
        bg.setArcHeight(30);
        
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 65px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-alignment: center;"
        );
        textLabel.setAlignment(Pos.CENTER);
        
        StackPane button = new StackPane();
        button.setPrefSize(200, 80);
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
            // Lấy giá trị game timer và move timer
            String gameTimerText = gameTimerValue == 1 ? "1 min" : gameTimerValue + " mins";
            String moveTimerText = moveTimerValue == 1 ? "1 min" : moveTimerValue + " mins";
            
            // Timer 2 và 3 = game timer, Timer 1 và 4 = move timer
            state.setTimer1Value(moveTimerText);
            state.setTimer2Value(gameTimerText);
            state.setTimer3Value(gameTimerText);
            state.setTimer4Value(moveTimerText);
            
            state.closeCustomMode();
            state.openGame();
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
        Rectangle bg = new Rectangle(300, 80);
        bg.setFill(Color.web("#A8A4A4"));
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        
        Label textLabel = new Label("Customize Board");
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 45px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        StackPane button = new StackPane();
        button.setPrefSize(300, 80);
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
        VBox mainContent = new VBox(20);
        mainContent.setPrefSize(1600, 900);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(50, 0, 50, 0));
        
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
        boardContainer.setPrefHeight(500);
        
        // Panel quân cờ bên trái (Black pieces)
        VBox leftPiecePanel = createPiecePanel("Black");
        
        // Board editor ở giữa
        Pane boardEditor = createMiniBoardEditor();
        
        // Panel quân cờ bên phải (Red pieces)
        VBox rightPiecePanel = createPiecePanel("Red");
        
        boardContainer.getChildren().addAll(leftPiecePanel, boardEditor, rightPiecePanel);
        
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
            saveCustomBoard();
            closeCustomBoardEditor();
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
        
        // Background
        Rectangle bg = new Rectangle(200, 600);
        bg.setFill(color.equals("Red") ? Color.color(0.95, 0.9, 0.9) : Color.color(0.85, 0.85, 0.85));
        bg.setStroke(Color.color(0.5, 0.5, 0.5));
        bg.setStrokeWidth(2);
        bg.setArcWidth(10);
        bg.setArcHeight(10);
        
        // Title
        Label title = new Label(color + " Pieces");
        title.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 30px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
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
        
        StackPane panelContent = new StackPane();
        panelContent.getChildren().addAll(bg, new VBox(10, title, piecesContainer));
        StackPane.setAlignment(title, Pos.TOP_CENTER);
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
            "-fx-text-fill: " + (color.equals("Red") ? "#DC143C" : "#1C1C1C") + "; " +
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
        container.setPrefSize(1000, 500);
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
            Rectangle fallbackBg = new Rectangle(1000, 500);
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
            boardImage.setFitWidth(1000);
            boardImage.setFitHeight(500);
            boardImage.setPreserveRatio(false);
            boardImage.setLayoutX(0);
            boardImage.setLayoutY(0);
            container.getChildren().add(0, boardImage);
        }
        
        double cellWidth = 1000.0 / 9.0;
        double cellHeight = 500.0 / 10.0;
        
        // Tạo click layer để detect click vào board
        Pane clickLayer = new Pane();
        clickLayer.setPrefSize(1000, 500);
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
                } else {
                    // Cập nhật label
                    selectedPieceEntry.countLabel.setText(String.valueOf(selectedPieceEntry.count));
                }
            }
        });
        
        // Bắt đầu với bàn cờ trống - người dùng sẽ click chọn quân cờ và click vào board để đặt
        // Load từ custom setup nếu có
        java.util.Map<String, String> customSetup = state.getCustomBoardSetup();
        if (customSetup != null && !customSetup.isEmpty() && state.isUseCustomBoard()) {
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
            }
        }
        
        // Thêm click layer vào container (sau board image, trước các quân cờ)
        if (finalBoardImage != null) {
            container.getChildren().add(1, clickLayer);
        } else {
            container.getChildren().add(0, clickLayer);
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
        
        // Click để xóa quân cờ
        piece.setOnMouseClicked(e -> {
            Pane boardEditor = (Pane) piece.getParent();
            boardEditor.getChildren().remove(piece);
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
            Pane boardEditor = null;
            for (javafx.scene.Node node : editorDialog.getChildren()) {
                if (node instanceof StackPane) {
                    StackPane mainPanel = (StackPane) node;
                    for (javafx.scene.Node child : mainPanel.getChildren()) {
                        if (child instanceof Pane && child.getUserData() != null && 
                            child.getUserData().equals("boardEditor")) {
                            boardEditor = (Pane) child;
                            break;
                        }
                    }
                }
            }
            
            if (boardEditor != null) {
                java.util.Map<String, String> customSetup = new java.util.HashMap<>();
                double cellWidth = 1000.0 / 9.0;
                double cellHeight = 500.0 / 10.0;
                
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
                
                state.setCustomBoardSetup(customSetup);
                state.setUseCustomBoard(true);
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
}
