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
        
        // "Play" button
        StackPane playButton = createPlayButton();
        playButton.setLayoutX(1250);
        playButton.setLayoutY(780);
        
        contentPane.getChildren().addAll(title, backButtonContainer, gameTimerLabel, gameTimerValueLabel, 
            gameTimerContainer, moveTimerLabel, moveTimerValueLabel, moveTimerContainer, 
            sideLabel, sideContainer, playButton);
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

    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
