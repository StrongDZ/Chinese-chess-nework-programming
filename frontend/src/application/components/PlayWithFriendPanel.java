package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import java.util.Random;

/**
 * Play with Friend panel - hiển thị danh sách bạn bè để thách đấu
 */
public class PlayWithFriendPanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private StackPane challengeDialog = null;  // Dialog xác nhận challenge
    private StackPane waitingForResponsePanel = null;  // Panel đếm ngược chờ phản hồi
    private StackPane challengeRequestPanel = null;  // Panel hiển thị challenge request nhận được
    private StackPane rejectNotificationPanel = null;  // Panel thông báo bị reject
    private Pane container;  // Container chính để thêm dialog
    private Timeline countdownTimeline = null;  // Timeline cho countdown
    private int remainingSeconds = 30;  // Thời gian còn lại
    private String currentGameMode = "classical";  // Chế độ game hiện tại (classical/blitz/custom)

    public PlayWithFriendPanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        setPickOnBounds(false);
        
        // Xác định game mode hiện tại
        updateCurrentGameMode();
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        container.setMouseTransparent(false);
        this.container = container;  // Lưu reference để thêm dialog
        
        // Background image
        ImageView bg = new ImageView(AssetHelper.image("bg.png"));
        bg.setFitWidth(1920);
        bg.setFitHeight(1080);
        bg.setPreserveRatio(false);
        
        Pane mainPane = new Pane();
        mainPane.setPrefSize(1920, 1080);
        mainPane.setMouseTransparent(false);
        mainPane.setPickOnBounds(true);
        
        // Top-left: Profile section
        HBox profileSection = createProfileSection();
        profileSection.setLayoutX(50);
        profileSection.setLayoutY(50);
        
        // Top-right: Social icons - ẨN đi vì không cần trong PlayWithFriendPanel
        // HBox socialIcons = createSocialIcons();
        // socialIcons.setLayoutX(1920 - 350);
        // socialIcons.setLayoutY(50);
        
        // Center: Friends panel
        VBox friendsPanel = createFriendsPanel();
        friendsPanel.setLayoutX((1920 - 1200) / 2);  // Center horizontally
        friendsPanel.setLayoutY(150);
        
        // Bottom navigation bar - ẨN đi vì không cần trong PlayWithFriendPanel
        // StackPane bottomBar = createBottomBar();
        // bottomBar.setLayoutX(1920 - 300 - 250);
        // bottomBar.setLayoutY(980);
        
        mainPane.getChildren().addAll(profileSection, friendsPanel);
        
        StackPane content = new StackPane();
        content.getChildren().addAll(bg, mainPane);
        container.getChildren().add(content);
        getChildren().add(container);
        
        // Bind visibility - hiển thị khi playWithFriendMode = true (chọn Friend và bấm Play)
        // (FriendsPanel sẽ hiển thị khi bấm icon friend, PlayWithFriendPanel hiển thị khi chọn play with friend)
        visibleProperty().bind(
            state.friendsVisibleProperty()
                .and(state.playWithFriendModeProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation
        state.friendsVisibleProperty().addListener((obs, oldVal, newVal) -> updateFade());
        state.playWithFriendModeProperty().addListener((obs, oldVal, newVal) -> updateFade());
        
        // Bind mouseTransparent với visibility để không chặn events khi không visible
        mouseTransparentProperty().bind(visibleProperty().not());
        container.mouseTransparentProperty().bind(visibleProperty().not());
        mainPane.mouseTransparentProperty().bind(visibleProperty().not());
    }
    
    private HBox createProfileSection() {
        HBox profile = new HBox(15);
        profile.setAlignment(Pos.CENTER_LEFT);

        // Avatar với hình vuông xám viền đỏ đè lên (giống MainMenuPanel)
        StackPane avatarContainer = new StackPane();
        
        // ava_profile.png
        ImageView avaProfile = new ImageView(AssetHelper.image("ava_profile.png"));
        avaProfile.setFitWidth(450);
        avaProfile.setFitHeight(120);
        avaProfile.setPreserveRatio(true);
        
        // Chọn avatar ngẫu nhiên
        Random random = new Random();
        int avatarNumber = random.nextInt(3) + 1;
        ImageView avatarInSquare = new ImageView(AssetHelper.image("ava/" + avatarNumber + ".jpg"));
        avatarInSquare.setFitWidth(130);
        avatarInSquare.setFitHeight(130);
        avatarInSquare.setPreserveRatio(false);
        avatarInSquare.setSmooth(true);
        
        // Clip ảnh thành hình vuông
        Rectangle clip = new Rectangle(130, 130);
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        avatarInSquare.setClip(clip);
        
        // Hình vuông trong suốt với viền đỏ
        Rectangle squareFrame = new Rectangle(130, 130);
        squareFrame.setFill(Color.TRANSPARENT);
        squareFrame.setStroke(Color.web("#A65252"));
        squareFrame.setStrokeWidth(6);
        squareFrame.setArcWidth(18);
        squareFrame.setArcHeight(18);
        
        avatarContainer.getChildren().addAll(avaProfile, avatarInSquare, squareFrame);
        avatarContainer.setAlignment(Pos.CENTER_LEFT);
        avatarContainer.setCursor(Cursor.HAND);
        avatarContainer.setOnMouseClicked(e -> state.openProfile());
        
        // Text section với username
        Pane textSection = new Pane();
        textSection.setPrefSize(250, 60);
        
        Label username = new Label();
        username.textProperty().bind(state.usernameProperty());
        username.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        username.setLayoutX(-310);
        username.setLayoutY(0);
        
        textSection.getChildren().add(username);
        profile.getChildren().addAll(avatarContainer, textSection);
        
        return profile;
    }
    
    private HBox createSocialIcons() {
        HBox icons = new HBox(15);
        icons.setAlignment(Pos.CENTER);

        // Facebook icon
        StackPane fbContainer = createIconWithHover(AssetHelper.image("icon_fb.png"), 80);
        
        // Instagram icon
        StackPane igContainer = createIconWithHover(AssetHelper.image("icon_ig.png"), 80);
        
        // Help icon
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
        container.setOnMouseEntered(e -> icon.setOpacity(0.7));
        container.setOnMouseExited(e -> icon.setOpacity(1.0));
        
        return container;
    }
    
    private VBox createFriendsPanel() {
        VBox panel = new VBox(20);
        panel.setPrefWidth(1200);
        panel.setPrefHeight(700);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(30));
        
        // Background cho friends panel - làm đậm hơn
        Rectangle bg = new Rectangle(1200, 700);
        bg.setFill(Color.color(0.85, 0.85, 0.85, 1.0));  // Tăng độ đậm và opacity
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        bg.setStroke(Color.color(0.2, 0.2, 0.2));  // Viền đậm hơn
        bg.setStrokeWidth(3);  // Viền dày hơn
        
        // Header với back button và title
        // Sử dụng StackPane để đặt back button ở góc trái và title ở giữa
        StackPane header = new StackPane();
        header.setPadding(new Insets(0, 0, 20, 0));
        header.setPrefHeight(150);
        
        // Back button (book icon với arrow) - to hơn
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(150);  // Tăng từ 60 lên 80
        backIcon.setFitHeight(150);  // Tăng từ 60 lên 80
        backIcon.setPreserveRatio(true);
        StackPane backButton = new StackPane(backIcon);
        backButton.setAlignment(Pos.CENTER_LEFT);
        backButton.setPadding(new Insets(0, 0, 0, 20));  // Padding bên trái
        backButton.setCursor(Cursor.HAND);
        backButton.setOnMouseEntered(e -> backIcon.setOpacity(0.7));
        backButton.setOnMouseExited(e -> backIcon.setOpacity(1.0));
        backButton.setOnMouseClicked(e -> {
            // Chỉ đóng friends, KHÔNG đóng mode panel để quay về mode panel trước đó
            state.closeFriends();
        });
        
        // "Friends" title - căn giữa panel, dịch lên một chút
        Label friendsTitle = new Label("Friends");
        friendsTitle.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 100px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        friendsTitle.setAlignment(Pos.CENTER);
        friendsTitle.setTranslateY(-15);  // Dịch lên 15px
        
        header.getChildren().addAll(backButton, friendsTitle);
        StackPane.setAlignment(backButton, Pos.CENTER_LEFT);
        StackPane.setAlignment(friendsTitle, Pos.CENTER);
        
        // Friends list - 2 cột
        HBox friendsListContainer = new HBox(30);
        friendsListContainer.setAlignment(Pos.TOP_CENTER);
        friendsListContainer.setPadding(new Insets(20));
        
        // Cột trái
        VBox leftColumn = new VBox(15);
        leftColumn.setAlignment(Pos.TOP_LEFT);
        leftColumn.setPrefWidth(550);
        
        // Cột phải
        VBox rightColumn = new VBox(15);
        rightColumn.setAlignment(Pos.TOP_LEFT);
        rightColumn.setPrefWidth(550);
        
        // Tạo 6 friend entries (3 mỗi cột)
        for (int i = 0; i < 6; i++) {
            HBox friendEntry = createFriendEntry("Username", "Online");
            if (i < 3) {
                leftColumn.getChildren().add(friendEntry);
            } else {
                rightColumn.getChildren().add(friendEntry);
            }
        }
        
        friendsListContainer.getChildren().addAll(leftColumn, rightColumn);
        
        // Stack background và content
        StackPane friendsPanelStack = new StackPane();
        VBox innerContent = new VBox(20, header, friendsListContainer);
        innerContent.setAlignment(Pos.TOP_CENTER);
        innerContent.setPadding(new Insets(20));
        
        friendsPanelStack.getChildren().addAll(bg, innerContent);
        StackPane.setAlignment(bg, Pos.CENTER);
        StackPane.setAlignment(innerContent, Pos.TOP_CENTER);
        
        panel.getChildren().add(friendsPanelStack);
        
        return panel;
    }
    
    private HBox createFriendEntry(String username, String status) {
        HBox entry = new HBox(15);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(15));
        entry.setCursor(Cursor.HAND);
        
        // Avatar circle với viền đỏ
        Circle avatarCircle = new Circle(30);
        avatarCircle.setFill(Color.TRANSPARENT);
        avatarCircle.setStroke(Color.web("#A65252"));
        avatarCircle.setStrokeWidth(2);
        
        StackPane avatarContainer = new StackPane(avatarCircle);
        avatarContainer.setPrefSize(60, 60);
        
        // Username và status
        VBox textInfo = new VBox(5);
        textInfo.setAlignment(Pos.CENTER_LEFT);
        
        Label usernameLabel = new Label(username);
        usernameLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 20px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        Label statusLabel = new Label(status);
        statusLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 16px; " +
            "-fx-text-fill: rgba(0, 0, 0, 0.7); " +
            "-fx-background-color: transparent;"
        );
        
        textInfo.getChildren().addAll(usernameLabel, statusLabel);
        
        // Challenge icon (ic_challenge) - chỉ để hiển thị, không có click handler
        ImageView challengeIcon = new ImageView(AssetHelper.image("ic_challenge.png"));
        challengeIcon.setFitWidth(40);
        challengeIcon.setFitHeight(40);
        challengeIcon.setPreserveRatio(true);
        StackPane challengeButton = new StackPane(challengeIcon);
        challengeButton.setMouseTransparent(true);  // Không nhận mouse events
        
        // Spacer để đẩy challenge icon sang phải
        Region spacer = new Region();
        spacer.setPrefWidth(Double.MAX_VALUE);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        entry.getChildren().addAll(avatarContainer, textInfo, spacer, challengeButton);
        
        // Click handler cho toàn bộ entry - hiển thị dialog xác nhận challenge
        entry.setOnMouseClicked(e -> {
            // Hiển thị dialog xác nhận challenge khi bấm vào bất kỳ đâu trong entry
            showChallengeConfirmation(username);
        });
        
        // Hover effect
        entry.setOnMouseEntered(e -> {
            entry.setStyle("-fx-background-color: rgba(0, 0, 0, 0.1); -fx-background-radius: 10;");
        });
        entry.setOnMouseExited(e -> {
            entry.setStyle("-fx-background-color: transparent;");
        });
        
        return entry;
    }
    
    private StackPane createBottomBar() {
        StackPane bottomBar = new StackPane();
        bottomBar.setPrefSize(300, 100);
        
        // Background
        Rectangle bg = new Rectangle(300, 100);
        bg.setFill(Color.color(0.3, 0.3, 0.3, 0.9));
        bg.setArcWidth(10);
        bg.setArcHeight(10);
        
        // Icons container
        HBox icons = new HBox(30);
        icons.setAlignment(Pos.CENTER);
        icons.setPadding(new Insets(10));
        
        // Shopping cart icon
        ImageView storeIcon = new ImageView(AssetHelper.image("icon_store.png"));
        storeIcon.setFitWidth(50);
        storeIcon.setFitHeight(50);
        storeIcon.setPreserveRatio(true);
        StackPane storeButton = new StackPane(storeIcon);
        storeButton.setCursor(Cursor.HAND);
        storeButton.setOnMouseEntered(e -> storeIcon.setOpacity(0.7));
        storeButton.setOnMouseExited(e -> storeIcon.setOpacity(1.0));
        storeButton.setOnMouseClicked(e -> state.openInventory());
        
        // Friends icon
        ImageView friendsIcon = new ImageView(AssetHelper.image("icon_friend.png"));
        friendsIcon.setFitWidth(50);
        friendsIcon.setFitHeight(50);
        friendsIcon.setPreserveRatio(true);
        StackPane friendsButton = new StackPane(friendsIcon);
        friendsButton.setCursor(Cursor.HAND);
        friendsButton.setOnMouseEntered(e -> friendsIcon.setOpacity(0.7));
        friendsButton.setOnMouseExited(e -> friendsIcon.setOpacity(1.0));
        friendsButton.setOnMouseClicked(e -> {
            if (state.isFriendsVisible()) {
                state.closeFriends();
            } else {
                state.openFriends();
            }
        });
        
        // Settings icon
        ImageView settingsIcon = new ImageView(AssetHelper.image("icon_setting.png"));
        settingsIcon.setFitWidth(50);
        settingsIcon.setFitHeight(50);
        settingsIcon.setPreserveRatio(true);
        StackPane settingsButton = new StackPane(settingsIcon);
        settingsButton.setCursor(Cursor.HAND);
        settingsButton.setOnMouseEntered(e -> settingsIcon.setOpacity(0.7));
        settingsButton.setOnMouseExited(e -> settingsIcon.setOpacity(1.0));
        settingsButton.setOnMouseClicked(e -> state.openSettings());
        
        icons.getChildren().addAll(storeButton, friendsButton, settingsButton);
        
        bottomBar.getChildren().addAll(bg, icons);
        bottomBar.setAlignment(Pos.CENTER);
        
        return bottomBar;
    }
    
    private void updateFade() {
        boolean shouldShow = state.isFriendsVisible() && state.isPlayWithFriendMode();
        if (shouldShow) {
            fadeTo(1);
            // Cập nhật game mode khi panel hiển thị
            updateCurrentGameMode();
        } else {
            fadeTo(0);
        }
    }
    
    /**
     * Cập nhật game mode hiện tại dựa trên mode panel đang visible
     */
    private void updateCurrentGameMode() {
        if (state.isClassicModeVisible()) {
            currentGameMode = "classical";
        } else if (state.isBlitzModeVisible()) {
            currentGameMode = "blitz";
        } else if (state.isCustomModeVisible()) {
            currentGameMode = "custom";
        }
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
    
    /**
     * Hiển thị dialog xác nhận challenge
     */
    private void showChallengeConfirmation(String username) {
        // Nếu đã có dialog, xóa nó trước
        if (challengeDialog != null && container != null && container.getChildren().contains(challengeDialog)) {
            container.getChildren().remove(challengeDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        challengeDialog = new StackPane();
        challengeDialog.setLayoutX((1920 - 500) / 2);  // Căn giữa theo chiều ngang
        challengeDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        challengeDialog.setPrefSize(500, 300);
        
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
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");
        
        // Label câu hỏi
        Label questionLabel = new Label("Are you sure you want to challenge?");
        questionLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        questionLabel.setAlignment(Pos.CENTER);
        questionLabel.setPrefWidth(420);
        questionLabel.setTranslateY(-20);
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Yes
        StackPane yesButton = createDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Xử lý khi bấm Yes (challenge)
            hideChallengeConfirmation();
            // Hiển thị panel đếm ngược chờ phản hồi
            showWaitingForResponsePanel(username);
            // TODO: Gửi challenge request đến server
            System.out.println("Challenge " + username);
        });
        
        // Nút No
        StackPane noButton = createDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            // Đóng dialog khi bấm No
            hideChallengeConfirmation();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        
        contentContainer.getChildren().addAll(questionLabel, buttonsContainer);
        
        challengeDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm dialog vào container
        if (container != null) {
            container.getChildren().add(challengeDialog);
        }
    }
    
    /**
     * Ẩn dialog xác nhận challenge
     */
    private void hideChallengeConfirmation() {
        if (challengeDialog != null && container != null && container.getChildren().contains(challengeDialog)) {
            container.getChildren().remove(challengeDialog);
            challengeDialog = null;
        }
    }
    
    /**
     * Tạo nút cho dialog
     */
    private StackPane createDialogButton(String text, boolean isYes) {
        StackPane button = new StackPane();
        button.setPrefSize(120, 50);
        
        // Background cho nút
        Rectangle buttonBg = new Rectangle(120, 50);
        if (isYes) {
            buttonBg.setFill(Color.web("#A65252"));  // Màu đỏ cho nút Yes
        } else {
            buttonBg.setFill(Color.color(0.7, 0.7, 0.7));  // Màu xám cho nút No
        }
        buttonBg.setArcWidth(10);
        buttonBg.setArcHeight(10);
        buttonBg.setStroke(Color.color(0.3, 0.3, 0.3));
        buttonBg.setStrokeWidth(1);
        
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
            buttonBg.setOpacity(0.8);
        });
        button.setOnMouseExited(e -> {
            buttonBg.setOpacity(1.0);
        });
        
        return button;
    }
    
    /**
     * Tạo nút Cancel màu đỏ và to hơn
     */
    private StackPane createCancelButton() {
        StackPane button = new StackPane();
        button.setPrefSize(150, 60);  // To hơn: 150x60 thay vì 120x50
        
        // Background màu đỏ
        Rectangle buttonBg = new Rectangle(150, 60);
        buttonBg.setFill(Color.web("#A65252"));  // Màu đỏ
        buttonBg.setArcWidth(10);
        buttonBg.setArcHeight(10);
        buttonBg.setStroke(Color.color(0.3, 0.3, 0.3));
        buttonBg.setStrokeWidth(1);
        
        // Label text
        Label buttonLabel = new Label("Cancel");
        buttonLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 36px; " +  // To hơn một chút
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        
        button.getChildren().addAll(buttonBg, buttonLabel);
        button.setCursor(Cursor.HAND);
        
        // Hover effect
        button.setOnMouseEntered(e -> {
            buttonBg.setOpacity(0.8);
        });
        button.setOnMouseExited(e -> {
            buttonBg.setOpacity(1.0);
        });
        
        return button;
    }
    
    /**
     * Hiển thị panel đếm ngược chờ phản hồi từ đối thủ
     */
    private void showWaitingForResponsePanel(String username) {
        // Nếu đã có panel, xóa nó trước
        if (waitingForResponsePanel != null && container != null && container.getChildren().contains(waitingForResponsePanel)) {
            container.getChildren().remove(waitingForResponsePanel);
        }
        
        // Reset thời gian
        remainingSeconds = 30;
        
        // Tạo panel ở giữa màn hình - dịch lên trên
        waitingForResponsePanel = new StackPane();
        waitingForResponsePanel.setLayoutX((1920 - 600) / 2);  // Căn giữa theo chiều ngang
        waitingForResponsePanel.setLayoutY((1080 - 600) / 2);  
        waitingForResponsePanel.setPrefSize(600, 600);
        
        // Background xám đậm
        Rectangle bg = new Rectangle(600, 600);
        bg.setFill(Color.color(0.3, 0.3, 0.3, 1));  // Màu xám đậm hơn, opacity cao hơn
        bg.setArcWidth(30);
        bg.setArcHeight(30);
        
        // Container chính
        VBox contentContainer = new VBox(40);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setPrefSize(600, 600);
        
        // Label "Waiting for your friend's response" - dịch lên trên
        Label waitingLabel = new Label("Waiting for your friend's response");
        waitingLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 50px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        waitingLabel.setTranslateY(-50);  // Dịch lên 30px
        
        // Label số đếm ngược - dịch lên một chút
        Label countdownLabel = new Label("30");
        countdownLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 150px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        countdownLabel.setId("countdownLabel");  // Để cập nhật sau
        countdownLabel.setTranslateY(-50);  // Dịch lên 20px
        
        // Nút Cancel - màu đỏ và to hơn
        StackPane cancelButton = createCancelButton();
        cancelButton.setOnMouseClicked(e -> {
            hideWaitingForResponsePanel();
        });
        
        contentContainer.getChildren().addAll(waitingLabel, countdownLabel, cancelButton);
        
        waitingForResponsePanel.getChildren().addAll(bg, contentContainer);
        
        // Thêm panel vào container
        if (container != null) {
            container.getChildren().add(waitingForResponsePanel);
        }
        
        // Bắt đầu countdown timer
        startCountdown(countdownLabel, username);
    }
    
    /**
     * Bắt đầu đếm ngược
     */
    private void startCountdown(Label countdownLabel, String username) {
        // Dừng timer cũ nếu có
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        
        remainingSeconds = 30;
        
        // Tạo timeline đếm ngược mỗi giây
        countdownTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                remainingSeconds--;
                
                // Cập nhật label
                countdownLabel.setText(String.valueOf(remainingSeconds));
                
                // Kiểm tra hết thời gian
                if (remainingSeconds <= 0) {
                    // Hết thời gian - quay lại PlayWithFriendPanel
                    hideWaitingForResponsePanel();
                    // TODO: Thông báo hết thời gian
                    System.out.println("Time out - challenge expired");
                }
            })
        );
        countdownTimeline.setCycleCount(30);  // Chạy 30 lần (30 giây)
        countdownTimeline.play();
    }
    
    /**
     * Ẩn panel đếm ngược
     */
    private void hideWaitingForResponsePanel() {
        // Dừng timer
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
        
        if (waitingForResponsePanel != null && container != null && container.getChildren().contains(waitingForResponsePanel)) {
            container.getChildren().remove(waitingForResponsePanel);
            waitingForResponsePanel = null;
        }
    }
    
    /**
     * Hiển thị panel challenge request nhận được từ đối thủ
     */
    public void showChallengeRequest(String challengerUsername) {
        // Nếu đã có panel, xóa nó trước
        if (challengeRequestPanel != null && container != null && container.getChildren().contains(challengeRequestPanel)) {
            container.getChildren().remove(challengeRequestPanel);
        }
        
        // Tạo panel ở giữa màn hình
        challengeRequestPanel = new StackPane();
        challengeRequestPanel.setLayoutX((1920 - 500) / 2);  // Căn giữa theo chiều ngang
        challengeRequestPanel.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        challengeRequestPanel.setPrefSize(500, 300);
        
        // Background xám đậm
        Rectangle bg = new Rectangle(500, 300);
        bg.setFill(Color.color(0.3, 0.3, 0.3, 1));
        bg.setArcWidth(30);
        bg.setArcHeight(30);
        
        // Container chính
        VBox contentContainer = new VBox(30);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setPrefSize(500, 300);
        contentContainer.setPadding(new Insets(40));
        
        // Label thông báo
        Label messageLabel = new Label(challengerUsername + " want to challenge you");
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 40px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent; " +
            "-fx-text-alignment: center;"
        );
        messageLabel.setWrapText(true);
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(20);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Accept
        StackPane acceptButton = createDialogButton("Accept", true);
        acceptButton.setOnMouseClicked(e -> {
            hideChallengeRequest();
            // Gửi accept response đến server và bắt đầu game với chế độ hiện tại
            // TODO: Gửi accept response đến server
            System.out.println("Accept challenge from " + challengerUsername);
            // Mở game với chế độ hiện tại
            state.openGame(currentGameMode);
        });
        
        // Nút Reject
        StackPane rejectButton = createDialogButton("Reject", false);
        rejectButton.setOnMouseClicked(e -> {
            hideChallengeRequest();
            // Gửi reject response đến server
            // TODO: Gửi reject response đến server
            System.out.println("Reject challenge from " + challengerUsername);
            // Hiển thị thông báo reject và quay lại PlayWithFriendPanel
            showRejectNotification(challengerUsername);
        });
        
        buttonsContainer.getChildren().addAll(acceptButton, rejectButton);
        
        contentContainer.getChildren().addAll(messageLabel, buttonsContainer);
        
        challengeRequestPanel.getChildren().addAll(bg, contentContainer);
        
        // Thêm panel vào container
        if (container != null) {
            container.getChildren().add(challengeRequestPanel);
        }
    }
    
    /**
     * Ẩn panel challenge request
     */
    public void hideChallengeRequest() {
        if (challengeRequestPanel != null && container != null && container.getChildren().contains(challengeRequestPanel)) {
            container.getChildren().remove(challengeRequestPanel);
            challengeRequestPanel = null;
        }
    }
    
    /**
     * Xử lý khi đối thủ chấp nhận challenge - vào trận đấu
     */
    public void onChallengeAccepted() {
        hideWaitingForResponsePanel();
        // Mở game với chế độ hiện tại
        state.openGame(currentGameMode);
        System.out.println("Challenge accepted - starting game with mode: " + currentGameMode);
    }
    
    /**
     * Xử lý khi đối thủ từ chối challenge - quay lại PlayWithFriendPanel
     */
    public void onChallengeRejected(String opponentUsername) {
        hideWaitingForResponsePanel();
        // Hiển thị thông báo reject và quay lại PlayWithFriendPanel
        showRejectNotification(opponentUsername);
        System.out.println("Challenge rejected by " + opponentUsername);
    }
    
    /**
     * Hiển thị thông báo khi đối thủ reject challenge
     */
    private void showRejectNotification(String opponentUsername) {
        // Nếu đã có panel, xóa nó trước
        if (rejectNotificationPanel != null && container != null && container.getChildren().contains(rejectNotificationPanel)) {
            container.getChildren().remove(rejectNotificationPanel);
        }
        
        // Tạo panel ở giữa màn hình
        rejectNotificationPanel = new StackPane();
        rejectNotificationPanel.setLayoutX((1920 - 500) / 2);  // Căn giữa theo chiều ngang
        rejectNotificationPanel.setLayoutY((1080 - 200) / 2);  // Căn giữa theo chiều dọc
        rejectNotificationPanel.setPrefSize(500, 200);
        
        // Background xám đậm
        Rectangle bg = new Rectangle(500, 200);
        bg.setFill(Color.color(0.3, 0.3, 0.3, 1));
        bg.setArcWidth(30);
        bg.setArcHeight(30);
        
        // Container chính
        VBox contentContainer = new VBox(20);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setPrefSize(500, 200);
        contentContainer.setPadding(new Insets(30));
        
        // Label thông báo
        Label messageLabel = new Label(opponentUsername + " rejected");
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 45px; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent; " +
            "-fx-text-alignment: center;"
        );
        messageLabel.setWrapText(true);
        
        contentContainer.getChildren().add(messageLabel);
        
        rejectNotificationPanel.getChildren().addAll(bg, contentContainer);
        
        // Thêm panel vào container
        if (container != null) {
            container.getChildren().add(rejectNotificationPanel);
        }
        
        // Tự động ẩn sau 2 giây và quay lại PlayWithFriendPanel
        Timeline autoHide = new Timeline(
            new KeyFrame(Duration.seconds(2), e -> {
                hideRejectNotification();
            })
        );
        autoHide.setCycleCount(1);
        autoHide.play();
    }
    
    /**
     * Ẩn panel thông báo reject
     */
    private void hideRejectNotification() {
        if (rejectNotificationPanel != null && container != null && container.getChildren().contains(rejectNotificationPanel)) {
            container.getChildren().remove(rejectNotificationPanel);
            rejectNotificationPanel = null;
        }
    }
}

