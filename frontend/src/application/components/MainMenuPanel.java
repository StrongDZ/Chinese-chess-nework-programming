package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.Cursor;
import java.util.Random;

/**
 * Main menu panel after login/register.
 */
public class MainMenuPanel extends StackPane {

    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;  // Thêm field state

    public MainMenuPanel(UIState state) {
        this.state = state;  // Lưu state vào field
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);

        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");

        // Top-left: Profile section với avatar và text ngang
        HBox profileSection = createProfileSection();
        profileSection.setLayoutX(50);
        profileSection.setLayoutY(50);
        
        // Không ẩn profileSection khi gameMode mở - luôn hiển thị
        // Bỏ binding visibility hoàn toàn

        // Top-right: Social icons
        HBox socialIcons = createSocialIcons();
        socialIcons.setLayoutX(1920 - 350);
        socialIcons.setLayoutY(50);

        // Ẩn social icons khi friends panel mở hoặc gameMode mở
        socialIcons.visibleProperty().bind(
            state.friendsVisibleProperty().not()
                .and(state.gameModeVisibleProperty().not())  // Thêm điều kiện này
        );
        socialIcons.managedProperty().bind(socialIcons.visibleProperty());

        // Center: Menu buttons với layout đặc biệt - đặt giữa màn hình
        Pane menuButtonsContainer = createMenuButtons();
        // Tính toán để đặt giữa màn hình
        // Container width: 450 (Play now) + 30 (spacing) + 300 (History/Game mode) = 780
        // Container height: 450 (Play now height)
        double containerWidth = 780;
        double containerHeight = 450;
        menuButtonsContainer.setLayoutX((1920 - containerWidth) / 2);  // Giữa theo chiều ngang
        menuButtonsContainer.setLayoutY((1080 - containerHeight) / 2);  // Giữa theo chiều dọc

        // Bind visibility của menuButtonsContainer với settingsVisible và gameModeVisible - ẩn khi settings hoặc gameMode mở
        menuButtonsContainer.visibleProperty().bind(
            state.settingsVisibleProperty().not()
                .and(state.gameModeVisibleProperty().not())  // Thêm dòng này
        );
        menuButtonsContainer.managedProperty().bind(menuButtonsContainer.visibleProperty());

        // Bottom-right: Icons với bottom_menu.png background ở góc phải màn hình
        StackPane bottomBar = createBottomBar();
        bottomBar.setLayoutX(1920 - 300 - 250);
        bottomBar.setLayoutY(980);
        
        // Ẩn bottomBar khi gameMode mở
        bottomBar.visibleProperty().bind(
            state.gameModeVisibleProperty().not()
        );
        bottomBar.managedProperty().bind(bottomBar.visibleProperty());

        container.getChildren().addAll(profileSection, socialIcons, menuButtonsContainer, bottomBar);
        getChildren().add(container);

        visibleProperty().bind(state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU));
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }

    private HBox createProfileSection() {
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
        
        // Thêm click handler để mở profile
        avatarContainer.setCursor(javafx.scene.Cursor.HAND);
        avatarContainer.setOnMouseClicked(e -> {
            state.openProfile();
        });
        
        // Text section với background trong suốt
        Pane textSection = new Pane();
        textSection.setPrefSize(250, 60);
        
        // Brush stroke background - trong suốt (có thể bỏ nếu không cần)
        Rectangle brushStroke = new Rectangle(200, 50);
        brushStroke.setFill(Color.TRANSPARENT);  // Trong suốt
        brushStroke.setArcWidth(10);
        brushStroke.setArcHeight(10);
        brushStroke.setLayoutY(-5);
        
        // Username - bind với state
        Label username = new Label();
        username.textProperty().bind(state.usernameProperty());  // Bây giờ có thể truy cập state
        username.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 60px; -fx-text-fill: white; -fx-background-color: transparent;");
        username.setLayoutX(-310);
        username.setLayoutY(0);
        
        textSection.getChildren().addAll(brushStroke, username);

        profile.getChildren().addAll(avatarContainer, textSection);
        return profile;
    }

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
        
        // Tạo scale transition cho hover effect
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), container);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.1);  // Phóng to 10% khi hover
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

    private Pane createMenuButtons() {
        Pane container = new Pane();
        
        // Thêm shadow cho container
        container.setEffect(new DropShadow(15, Color.rgb(0, 0, 0, 0.4)));

        // Play now button - lớn nhất, bên trái, vuông 450x450
        Label playNow = createMenuButton("Play now", 450, 450);
        playNow.setLayoutX(0);
        playNow.setLayoutY(0);
        playNow.setOnMouseClicked(e -> {
            // Set timer values thành "Unlimited time" (classic mode)
            state.setTimer1Value("Unlimited time");
            state.setTimer2Value("Unlimited time");
            state.setTimer3Value("Unlimited time");
            state.setTimer4Value("Unlimited time");
            
            // Vào game trực tiếp với classic mode và random opponent
            state.openGame("classical");
        });

        // History button - 300x215, trên và bên phải Play now
        Label history = createMenuButton("History", 300, 215);
        history.setLayoutX(480);
        history.setLayoutY(0);
        history.setOnMouseClicked(e -> {
            state.openHistory();
        });

        // Game mode button - 300x215, dưới History và bên phải Play now
        Label gameMode = createMenuButton("Game mode", 300, 215);
        gameMode.setLayoutX(480);
        gameMode.setLayoutY(235);
        gameMode.setOnMouseClicked(e -> {
            state.openGameMode();
        });

        container.getChildren().addAll(playNow, history, gameMode);
        return container;
    }

    private Label createMenuButton(String text, double width, double height) {
        Label button = new Label(text);
        button.setPrefSize(width, height);
        button.setAlignment(Pos.CENTER);
        button.setContentDisplay(javafx.scene.control.ContentDisplay.CENTER);
        
        // Font size dựa trên kích thước nút
        String fontSize;
        if (width >= 450 && height >= 450) {
            fontSize = "110px";  // Play now
        } else if (width >= 300 && height >= 200) {
            fontSize = "70px";  // History và Game mode
        } else {
            fontSize = "42px";
        }
        
        button.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: " + fontSize + "; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: rgba(217, 217, 217, 0.9); " +
            "-fx-background-radius: 15; " +
            "-fx-border-radius: 15; " +
            "-fx-alignment: center; " +  // Căn giữa text
            "-fx-text-alignment: center; " +  // Căn giữa text alignment
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 8, 0, 0, 3);"  // Shadow đậm hơn
        );
        button.setCursor(javafx.scene.Cursor.HAND);
        
        // Tạo scale transition cho hover effect
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), button);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.05);  // Phóng to 5% khi hover
        scaleTransition.setToY(1.05);
        
        // Hiệu ứng khi trỏ chuột vào
        button.setOnMouseEntered(e -> {
            scaleTransition.setToX(1.05);
            scaleTransition.setToY(1.05);
            scaleTransition.play();
        });
        
        // Hiệu ứng khi trỏ chuột ra ngoài
        button.setOnMouseExited(e -> {
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);
            scaleTransition.play();
        });
        
        return button;
    }

    private StackPane createBottomBar() {
        // StackPane để chứa background và icons
        StackPane bottomBar = new StackPane();
        bottomBar.setPrefSize(300, 100);  // Set pref size cho StackPane
        
        // Background image
        ImageView background = new ImageView(AssetHelper.image("bottom_menu.png"));
        background.setPreserveRatio(true);
        background.setFitWidth(552);
        background.setFitHeight(111);
        
        // Icons container - điều chỉnh spacing và kích thước cho phù hợp
        HBox icons = new HBox(70);
        icons.setAlignment(Pos.CENTER);
        icons.setPadding(new Insets(0, 20, 0, 70));

        // Inventory icon với hover effect và click handler
        StackPane storeContainer = createIconWithHover(AssetHelper.image("icon_inventory.png"), 55);
        storeContainer.setOnMouseClicked(e -> {
            // Toggle: nếu đang mở thì đóng, nếu đang đóng thì mở
            if (state.isInventoryVisible()) {
                state.closeInventory();
            } else {
                state.openInventory();
            }
        });
        
        // Friends icon với hover effect và click handler
        StackPane friendsContainer = createIconWithHover(AssetHelper.image("icon_friend.png"), 100);
        friendsContainer.setOnMouseClicked(e -> {
            // Toggle: nếu đang mở thì đóng, nếu đang đóng thì mở
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

    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
