package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.Cursor;

public class GameModePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;

    public GameModePanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        setPickOnBounds(false);
        // BỎ setMouseTransparent(true) - để children có thể nhận mouse events
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        container.setMouseTransparent(false);  // Giống InventoryPanel - allow mouse events to reach children
        container.setPickOnBounds(false);
        
        // Tạo game mode content
        StackPane gameModeContent = createGameModeContent();
        gameModeContent.setLayoutX(0);
        gameModeContent.setLayoutY(0);
        gameModeContent.setPickOnBounds(true);  // Giống InventoryPanel
        gameModeContent.setMouseTransparent(false);  // Giống InventoryPanel
        
        container.getChildren().add(gameModeContent);
        getChildren().add(container);
        
        // Bind visibility
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.gameModeVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation
        state.gameModeVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isGameModeVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private StackPane createGameModeContent() {
        StackPane content = new StackPane();
        content.setPrefSize(1920, 1080);
        content.setStyle("-fx-background-color: transparent;");
        content.setPickOnBounds(true);  // Giống InventoryPanel
        content.setMouseTransparent(false);  // Giống InventoryPanel
        
        Pane mainPane = new Pane();
        mainPane.setPrefSize(1920, 1080);
        // Không set pickOnBounds và mouseTransparent - giống InventoryPanel
        
        // Top-right: Social icons - add lại với cùng chức năng
        HBox socialIcons = createSocialIcons();
        socialIcons.setLayoutX(1920 - 350);
        socialIcons.setLayoutY(50);
        
        // Bottom-right: Bottom bar icons - add lại với cùng chức năng
        StackPane bottomBar = createBottomBar();
        bottomBar.setLayoutX(1920 - 300 - 250);
        bottomBar.setLayoutY(980);
        
        // Back arrow icon
        StackPane backButtonContainer = new StackPane();
        backButtonContainer.setLayoutX(50);
        backButtonContainer.setLayoutY(175);
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
        backButton.setCursor(javafx.scene.Cursor.HAND);
        backButton.setMouseTransparent(false);
        backButton.setPickOnBounds(true);
        backButton.setOnMouseClicked(e -> state.closeGameMode());
        
        // Hover effect cho back button
        backButton.setScaleX(1.0);
        backButton.setScaleY(1.0);
        
        backButton.setOnMouseEntered(e -> {
            backButton.setScaleX(1.1);
            backButton.setScaleY(1.1);
            backIcon.setOpacity(0.85);
        });
        backButton.setOnMouseExited(e -> {
            backButton.setScaleX(1.0);
            backButton.setScaleY(1.0);
            backIcon.setOpacity(1.0);
        });
        
        backButtonContainer.getChildren().add(backButton);
        
        // Game mode buttons container
        VBox buttonsContainer = new VBox(20);
        buttonsContainer.setLayoutX((1920 - 400) / 2);
        buttonsContainer.setLayoutY((1080 - 600) / 2);
        buttonsContainer.setAlignment(Pos.CENTER);
        buttonsContainer.setMouseTransparent(false);
        buttonsContainer.setPickOnBounds(true);
        buttonsContainer.setManaged(true);
        
        // Tạo các nút game mode
        String[] gameModes = {
            "Classic mode",
            "Blitz mode",
            // "Puzzle mode",
            // "Watch match",
            "Custom mode"
        };
        
        for (String mode : gameModes) {
            StackPane modeButton = createGameModeButton(mode);
            buttonsContainer.getChildren().add(modeButton);
        }
        
        mainPane.getChildren().addAll(socialIcons, bottomBar, backButtonContainer, buttonsContainer);
        content.getChildren().add(mainPane);
        
        return content;
    }
    
    private StackPane createGameModeButton(String text) {
        // Background khung
        javafx.scene.shape.Rectangle background = new javafx.scene.shape.Rectangle(400, 100);
        background.setFill(javafx.scene.paint.Color.rgb(217, 217, 217, 0.9));
        background.setArcWidth(30);
        background.setArcHeight(30);
        
        // Thêm shadow effect
        DropShadow shadow = new DropShadow();
        shadow.setColor(javafx.scene.paint.Color.color(0, 0, 0, 0.4));
        shadow.setRadius(10);
        shadow.setOffsetX(4);
        shadow.setOffsetY(4);
        background.setEffect(shadow);
        
        // Label chứa text - không có background
        Label textLabel = new Label(text);
        textLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 75px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        textLabel.setTranslateY(-15);
        
        // StackPane chứa background và text
        StackPane button = new StackPane();
        button.setPrefSize(400, 100);
        button.setAlignment(Pos.CENTER);
        button.getChildren().addAll(background, textLabel);
        button.setCursor(javafx.scene.Cursor.HAND);
        
        // Đặt initial scale
        button.setScaleX(1.0);
        button.setScaleY(1.0);
        
        // Hover effect - wrap
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
        
        // Click handler: mở Classic mode và đóng GameMode (với nút Classic)
        button.setOnMouseClicked(e -> {
            if ("Classic mode".equals(text)) {
                state.closeGameMode();
                state.openClassicMode();
            } else if ("Blitz mode".equals(text)) {
                state.closeGameMode();
                state.openBlitzMode();
            } else if ("Custom mode".equals(text)) {
                state.closeGameMode();
                state.openCustomMode();
            }
        });
        
        return button;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }

    // Thêm lại các method createSocialIcons, createIconWithHover, createBottomBar
    private HBox createSocialIcons() {
        HBox icons = new HBox(15);
        icons.setAlignment(Pos.CENTER);

        // Facebook icon với hover effect
        StackPane fbContainer = createIconWithHover(AssetHelper.image("icon_fb.png"), 80);
        
        // Instagram icon với hover effect
        StackPane igContainer = createIconWithHover(AssetHelper.image("icon_ig.png"), 80);
        
        // Help icon với hover effect
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
        container.setMouseTransparent(false);  // Quan trọng: nhận mouse events
        container.setPickOnBounds(true);  // Quan trọng: nhận mouse events
        
        // Tạo scale transition cho hover effect
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), container);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.1);
        scaleTransition.setToY(1.1);
        
        // Hiệu ứng khi trỏ chuột vào
        container.setOnMouseEntered(e -> {
            scaleTransition.setToX(1.1);
            scaleTransition.setToY(1.1);
            scaleTransition.play();
        });
        
        // Hiệu ứng khi trỏ chuột ra ngoài
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
        
        // Background image
        ImageView background = new ImageView(AssetHelper.image("bottom_menu.png"));
        background.setPreserveRatio(true);
        background.setFitWidth(552);
        background.setFitHeight(111);
        
        // Icons container
        HBox icons = new HBox(70);
        icons.setAlignment(Pos.CENTER);
        icons.setPadding(new javafx.geometry.Insets(0, 20, 0, 70));

        // Inventory icon với hover effect và click handler
        StackPane storeContainer = createIconWithHover(AssetHelper.image("icon_inventory.png"), 55);
        storeContainer.setOnMouseClicked(e -> {
            if (state.isInventoryVisible()) {
                state.closeInventory();
            } else {
                state.openInventory();
            }
        });
        
        // Friends icon với hover effect và click handler
        StackPane friendsContainer = createIconWithHover(AssetHelper.image("icon_friend.png"), 100);
        friendsContainer.setOnMouseClicked(e -> {
            if (state.isFriendsVisible()) {
                state.closeFriends();
            } else {
                state.openFriends();
            }
        });
        
        // Settings icon với hover effect và click handler
        StackPane settingsContainer = createIconWithHover(AssetHelper.image("icon_setting.png"), 100);
        settingsContainer.setOnMouseClicked(e -> {
            state.openSettings();
        });

        icons.getChildren().addAll(storeContainer, friendsContainer, settingsContainer);
        
        // Thêm background và icons vào StackPane
        bottomBar.getChildren().addAll(background, icons);
        bottomBar.setAlignment(Pos.CENTER);
        
        return bottomBar;
    }
}
