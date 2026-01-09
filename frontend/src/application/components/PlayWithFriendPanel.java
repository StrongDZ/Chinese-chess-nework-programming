package application.components;

import application.network.NetworkManager;
import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
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
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

/**
 * Play with Friend panel - hiển thị danh sách bạn bè để thách đấu
 */
public class PlayWithFriendPanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private final NetworkManager networkManager = NetworkManager.getInstance();
    private StackPane challengeDialog = null;  // Dialog xác nhận challenge
    private StackPane waitingForResponsePanel = null;  // Panel đếm ngược chờ phản hồi
    private StackPane challengeRequestPanel = null;  // Panel hiển thị challenge request nhận được
    private StackPane rejectNotificationPanel = null;  // Panel thông báo bị reject
    private Pane container;  // Container chính để thêm dialog
    private javafx.scene.layout.Pane rootPane = null;  // Root pane để hiển thị challenge request dialog (giống FriendRequestNotificationDialog)
    private Timeline countdownTimeline = null;  // Timeline cho countdown
    private int remainingSeconds = 30;  // Thời gian còn lại
    private String currentGameMode = "classical";  // Chế độ game hiện tại (classical/blitz/custom)
    private String currentChallengedUsername = null;  // Username của người đang được challenge
    
    // Friends list data
    private VBox leftColumn;  // Reference to left column
    private VBox rightColumn;  // Reference to right column
    private ObservableList<String> onlinePlayers = FXCollections.observableArrayList();  // Online players list
    private ObservableList<String> onlinePlayersNotInGame = FXCollections.observableArrayList();  // Online players not in game
    private java.util.Map<String, java.util.Map<String, Integer>> friendElos = new java.util.HashMap<>();  // Map username -> Map<mode, elo>
    private boolean isRefreshing = false;  // Flag to prevent concurrent refreshes

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
        
        // Load friends list and online players when panel becomes visible
        visibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                System.out.println("[PlayWithFriendPanel] Panel visible, loading friends list and online players...");
                System.out.println("[PlayWithFriendPanel] Current friends list size: " + state.getFriendsList().size());
                System.out.println("[PlayWithFriendPanel] Current online players size: " + onlinePlayers.size());
                loadFriendsList();
                loadOnlinePlayers();
                // Refresh after a short delay to ensure data is loaded and columns are initialized
                javafx.application.Platform.runLater(() -> {
                    javafx.util.Duration delay = javafx.util.Duration.millis(100);
                    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(delay);
                    pause.setOnFinished(e -> {
                        System.out.println("[PlayWithFriendPanel] Delayed refresh after panel becomes visible");
                        refreshFriendsList();
                    });
                    pause.play();
                });
            }
        });
        
        // Listen to friends list changes
        state.getFriendsList().addListener((ListChangeListener<String>) change -> {
            refreshFriendsList();
        });
        
        // Listen to game mode changes to refresh elo display
        state.classicModeVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                currentGameMode = "classical";
                // Request elo for friends if not cached for this mode
                refreshFriendsList();
            }
        });
        state.blitzModeVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                currentGameMode = "blitz";
                // Request elo for friends if not cached for this mode
                refreshFriendsList();
            }
        });
        
        // Note: Online players callback is registered in Main.java to call both FriendsPanel and PlayWithFriendPanel
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
        leftColumn = new VBox(15);
        leftColumn.setAlignment(Pos.TOP_LEFT);
        leftColumn.setPrefWidth(550);
        
        // Cột phải
        rightColumn = new VBox(15);
        rightColumn.setAlignment(Pos.TOP_LEFT);
        rightColumn.setPrefWidth(550);
        
        // Khởi tạo với empty list, sẽ được refresh khi load data
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
    
    private HBox createFriendEntry(String username, String status, Integer elo) {
        System.out.println("[PlayWithFriendPanel] createFriendEntry called with username: " + username + ", status: " + status + ", elo: " + elo);
        HBox entry = new HBox(15);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(15));
        entry.setCursor(Cursor.HAND);
        entry.setPrefWidth(550);  // Đảm bảo entry có đủ width (column width)
        
        // Avatar circle với fill và initial letter
        Circle avatarCircle = new Circle(30);
        avatarCircle.setFill(Color.web("#F5E6E6")); // Light pink fill giống FriendsPanel
        avatarCircle.setStroke(Color.web("#A65252"));
        avatarCircle.setStrokeWidth(2.5);
        
        // Initial letter trong avatar (chữ cái đầu của username)
        Label initialLabel = new Label(username.substring(0, 1).toUpperCase());
        initialLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 24px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #A65252; " +
            "-fx-background-color: transparent;"
        );
        
        StackPane avatarContainer = new StackPane(avatarCircle, initialLabel);
        avatarContainer.setPrefSize(60, 60);
        
        // Username, status và elo
        VBox textInfo = new VBox(5);
        textInfo.setAlignment(Pos.CENTER_LEFT);
        textInfo.setMinWidth(300);  // Đảm bảo có đủ width tối thiểu
        textInfo.setPrefWidth(400);  // Width ưu tiên
        HBox.setHgrow(textInfo, Priority.ALWAYS);  // Cho phép mở rộng để chiếm hết space còn lại
        
        Label usernameLabel = new Label(username);
        usernameLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 20px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        usernameLabel.setMaxWidth(Double.MAX_VALUE);  // Cho phép mở rộng
        usernameLabel.setWrapText(false);  // Không wrap để hiển thị đầy đủ trên 1 dòng
        usernameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);  // Hiển thị ellipsis nếu quá dài
        VBox.setVgrow(usernameLabel, Priority.NEVER);
        
        // Status và elo trên cùng một dòng
        HBox statusEloContainer = new HBox(10);
        statusEloContainer.setAlignment(Pos.CENTER_LEFT);
        statusEloContainer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusEloContainer, Priority.ALWAYS);
        
        Label statusLabel = new Label(status);
        statusLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 16px; " +
            "-fx-text-fill: rgba(0, 0, 0, 0.7); " +
            "-fx-background-color: transparent;"
        );
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setWrapText(false);
        statusLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        HBox.setHgrow(statusLabel, Priority.NEVER);  // Status không cần grow
        
        // Elo label - luôn hiển thị (nếu chưa có thì hiển thị "Loading...")
        String modeDisplay = "classical".equals(currentGameMode) ? "Classical" : 
                           "blitz".equals(currentGameMode) ? "Blitz" : "Classical";
        String eloText;
        if (elo != null) {
            eloText = modeDisplay + " ELO: " + elo;
        } else {
            eloText = modeDisplay + " ELO: Loading...";
        }
        Label eloLabel = new Label(eloText);
        eloLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 16px; " +
            "-fx-text-fill: #A65252; " +
            "-fx-font-weight: bold; " +
            "-fx-background-color: transparent;"
        );
        eloLabel.setMaxWidth(Double.MAX_VALUE);
        eloLabel.setWrapText(false);
        eloLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        HBox.setHgrow(eloLabel, Priority.ALWAYS);  // Elo label có thể grow để hiển thị đầy đủ
        
        statusEloContainer.getChildren().addAll(statusLabel, eloLabel);
        
        textInfo.getChildren().addAll(usernameLabel, statusEloContainer);
        
        // Challenge icon (ic_challenge) - chỉ để hiển thị, không có click handler
        ImageView challengeIcon = new ImageView(AssetHelper.image("ic_challenge.png"));
        challengeIcon.setFitWidth(40);
        challengeIcon.setFitHeight(40);
        challengeIcon.setPreserveRatio(true);
        StackPane challengeButton = new StackPane(challengeIcon);
        challengeButton.setMouseTransparent(true);  // Không nhận mouse events
        challengeButton.setPrefWidth(40);  // Fixed width cho icon
        
        // Không cần spacer nữa vì textInfo đã có HBox.setHgrow
        entry.getChildren().addAll(avatarContainer, textInfo, challengeButton);
        
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
        System.out.println("[PlayWithFriendPanel] showChallengeConfirmation called for: " + username);
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
            System.out.println("[PlayWithFriendPanel] Yes button clicked for challenge to: " + username);
            // Xử lý khi bấm Yes (challenge)
            hideChallengeConfirmation();
            // Gửi challenge request đến server
            sendChallengeRequest(username);
            // Hiển thị panel đếm ngược chờ phản hồi
            showWaitingForResponsePanel(username);
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
            // Cancel challenge request trước khi ẩn panel
            cancelChallengeRequest();
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
                    // Hết thời gian - cancel challenge và quay lại PlayWithFriendPanel
                    cancelChallengeRequest();
                    hideWaitingForResponsePanel();
                    System.out.println("Time out - challenge expired");
                }
            })
        );
        countdownTimeline.setCycleCount(30);  // Chạy 30 lần (30 giây)
        countdownTimeline.play();
    }
    
    /**
     * Ẩn panel đếm ngược (public để có thể gọi từ GameHandler)
     */
    public void hideWaitingForResponsePanel() {
        // Dừng timer (nếu chưa dừng)
        stopCountdownTimer();
        
        if (waitingForResponsePanel != null) {
            if (rootPane != null && rootPane.getChildren().contains(waitingForResponsePanel)) {
                rootPane.getChildren().remove(waitingForResponsePanel);
            }
            if (container != null && container.getChildren().contains(waitingForResponsePanel)) {
                container.getChildren().remove(waitingForResponsePanel);
            }
            waitingForResponsePanel = null;
        }
        
        // Reset challenged username
        currentChallengedUsername = null;
    }
    
    /**
     * Gửi challenge request đến server
     */
    private void sendChallengeRequest(String targetUsername) {
        try {
            currentChallengedUsername = targetUsername;
            // Get mode and time limit from UIState
            String mode = state.getCurrentGameMode();
            int timeLimit = state.getCurrentTimeLimit();
            
            // Fallback to local currentGameMode if UIState mode is empty
            if (mode == null || mode.isEmpty()) {
                mode = currentGameMode;
            }
            
            System.out.println("[PlayWithFriendPanel] Sending challenge request to: " + targetUsername 
                             + ", mode: " + mode + ", timeLimit: " + timeLimit + "s");
            networkManager.game().sendChallenge(targetUsername, mode, timeLimit);
        } catch (IOException e) {
            System.err.println("[PlayWithFriendPanel] Failed to send challenge request: " + e.getMessage());
            e.printStackTrace();
            // Hide waiting panel on error
            hideWaitingForResponsePanel();
        }
    }
    
    /**
     * Cancel challenge request (khi timeout hoặc user cancel)
     */
    private void cancelChallengeRequest() {
        if (currentChallengedUsername != null) {
            try {
                System.out.println("[PlayWithFriendPanel] Canceling challenge request to: " + currentChallengedUsername);
                networkManager.game().cancelChallenge(currentChallengedUsername);
            } catch (IOException e) {
                System.err.println("[PlayWithFriendPanel] Failed to cancel challenge request: " + e.getMessage());
            }
        }
        currentChallengedUsername = null;
    }
    
    /**
     * Gửi challenge response (accept/reject) đến server
     */
    private void sendChallengeResponse(String challengerUsername, boolean accepted) {
        try {
            System.out.println("[PlayWithFriendPanel] Sending challenge response to: " + challengerUsername + ", accepted: " + accepted);
            networkManager.game().respondChallenge(challengerUsername, accepted);
        } catch (IOException e) {
            System.err.println("[PlayWithFriendPanel] Failed to send challenge response: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Set root pane để hiển thị challenge request dialog (giống FriendRequestNotificationDialog)
     */
    public void setRootPane(javafx.scene.layout.Pane rootPane) {
        this.rootPane = rootPane;
        System.out.println("[PlayWithFriendPanel] Root pane set: " + (rootPane != null ? "set" : "null"));
    }
    
    /**
     * Hiển thị panel challenge request nhận được từ đối thủ (backward compatibility)
     */
    public void showChallengeRequest(String challengerUsername) {
        showChallengeRequest(challengerUsername, "classical", 0);
    }
    
    /**
     * Hiển thị panel challenge request nhận được từ đối thủ với mode và time
     * @param challengerUsername Username của người challenge
     * @param mode Game mode: "classical" hoặc "blitz"
     * @param timeLimit Time limit in seconds (0 = unlimited)
     */
    public void showChallengeRequest(String challengerUsername, String mode, int timeLimit) {
        System.out.println("[PlayWithFriendPanel] showChallengeRequest called for: " + challengerUsername 
                         + ", mode: " + mode + ", timeLimit: " + timeLimit);
        System.out.println("[PlayWithFriendPanel] rootPane is " + (rootPane != null ? "set" : "null"));
        System.out.println("[PlayWithFriendPanel] container is " + (container != null ? "set" : "null"));
        
        // Nếu đã có panel, xóa nó trước
        if (challengeRequestPanel != null) {
            if (rootPane != null && rootPane.getChildren().contains(challengeRequestPanel)) {
                rootPane.getChildren().remove(challengeRequestPanel);
            }
            if (container != null && container.getChildren().contains(challengeRequestPanel)) {
                container.getChildren().remove(challengeRequestPanel);
            }
        }
        
        // Tạo panel ở giữa màn hình - tăng chiều cao để chứa thêm thông tin
        challengeRequestPanel = new StackPane();
        challengeRequestPanel.setLayoutX((1920 - 550) / 2);  // Căn giữa theo chiều ngang
        challengeRequestPanel.setLayoutY((1080 - 350) / 2);  // Căn giữa theo chiều dọc
        challengeRequestPanel.setPrefSize(550, 350);
        
        // Đảm bảo dialog visible và có thể nhận click events
        challengeRequestPanel.setVisible(true);
        challengeRequestPanel.setManaged(true);
        challengeRequestPanel.setMouseTransparent(false);
        challengeRequestPanel.setPickOnBounds(true);
        
        // Background xám đậm
        Rectangle bg = new Rectangle(550, 350);
        bg.setFill(Color.color(0.3, 0.3, 0.3, 1));
        bg.setArcWidth(30);
        bg.setArcHeight(30);
        bg.setMouseTransparent(true);  // Background không chặn click events
        
        // Container chính
        VBox contentContainer = new VBox(20);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setPrefSize(550, 350);
        contentContainer.setPadding(new Insets(30));
        contentContainer.setMouseTransparent(false);  // Container phải nhận click events
        contentContainer.setPickOnBounds(true);
        
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
        
        // Format mode và time để hiển thị
        String modeDisplay = "classical".equals(mode) ? "Classical" : 
                           "blitz".equals(mode) ? "Blitz" : mode;
        String timeDisplay;
        if (timeLimit <= 0) {
            timeDisplay = "Unlimited";
        } else if (timeLimit >= 60) {
            int minutes = timeLimit / 60;
            timeDisplay = minutes + " min" + (minutes > 1 ? "s" : "");
        } else {
            timeDisplay = timeLimit + " sec";
        }
        
        // Label hiển thị mode và time
        Label modeTimeLabel = new Label("Mode: " + modeDisplay + " | Time: " + timeDisplay);
        modeTimeLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 32px; " +
            "-fx-text-fill: #FFD700; " +  // Gold color
            "-fx-background-color: transparent; " +
            "-fx-text-alignment: center;"
        );
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(20);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Accept
        StackPane acceptButton = createDialogButton("Accept", true);
        acceptButton.setOnMouseClicked(e -> {
            hideChallengeRequest();
            // Gửi accept response đến server
            sendChallengeResponse(challengerUsername, true);
            System.out.println("Accept challenge from " + challengerUsername);
            // Game sẽ tự động bắt đầu khi backend gửi GAME_START message
        });
        
        // Nút Reject
        StackPane rejectButton = createDialogButton("Reject", false);
        rejectButton.setOnMouseClicked(e -> {
            hideChallengeRequest();
            // Gửi reject response đến server
            sendChallengeResponse(challengerUsername, false);
            System.out.println("Reject challenge from " + challengerUsername);
        });
        
        buttonsContainer.getChildren().addAll(acceptButton, rejectButton);
        
        contentContainer.getChildren().addAll(messageLabel, modeTimeLabel, buttonsContainer);
        
        challengeRequestPanel.getChildren().addAll(bg, contentContainer);
        
        // Thêm panel vào root pane (giống FriendRequestNotificationDialog)
        // Root pane luôn visible, không phụ thuộc vào PlayWithFriendPanel visibility
        if (rootPane != null) {
            System.out.println("[PlayWithFriendPanel] Adding challenge request panel to rootPane");
            rootPane.getChildren().add(challengeRequestPanel);
            // Đảm bảo dialog ở trên cùng
            challengeRequestPanel.setViewOrder(-1000);
        } else {
            System.err.println("[PlayWithFriendPanel] rootPane is null, cannot show challenge request dialog");
            // Fallback: thêm vào container nếu rootPane không có
            if (container != null) {
                System.out.println("[PlayWithFriendPanel] Fallback: Adding challenge request panel to container");
                container.getChildren().add(challengeRequestPanel);
            }
        }
    }
    
    /**
     * Ẩn panel challenge request
     */
    public void hideChallengeRequest() {
        if (challengeRequestPanel != null) {
            if (rootPane != null && rootPane.getChildren().contains(challengeRequestPanel)) {
                rootPane.getChildren().remove(challengeRequestPanel);
            }
            if (container != null && container.getChildren().contains(challengeRequestPanel)) {
                container.getChildren().remove(challengeRequestPanel);
            }
            challengeRequestPanel = null;
        }
    }
    
    /**
     * Dừng countdown timer (được gọi khi vào trận hoặc challenge được accept/reject)
     */
    public void stopCountdownTimer() {
        if (countdownTimeline != null) {
            System.out.println("[PlayWithFriendPanel] Stopping countdown timer");
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }
    
    /**
     * Xử lý khi đối thủ chấp nhận challenge - vào trận đấu
     * Backend sẽ tự động start game và gửi GAME_START message,
     * GameHandler sẽ xử lý mở game panel.
     */
    public void onChallengeAccepted() {
        System.out.println("[PlayWithFriendPanel] Challenge accepted - stopping countdown and hiding waiting panel");
        stopCountdownTimer();
        hideWaitingForResponsePanel();
    }
    
    /**
     * Xử lý khi đối thủ từ chối challenge - quay lại PlayWithFriendPanel
     */
    public void onChallengeRejected(String opponentUsername) {
        System.out.println("[PlayWithFriendPanel] Challenge rejected by " + opponentUsername + " - stopping countdown");
        stopCountdownTimer();
        hideWaitingForResponsePanel();
        // Hiển thị thông báo reject và quay lại PlayWithFriendPanel
        showRejectNotification(opponentUsername);
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
    
    /**
     * Load friends list from server.
     */
    private void loadFriendsList() {
        try {
            System.out.println("[PlayWithFriendPanel] Requesting friends list from server...");
            networkManager.friend().requestFriendsList();
        } catch (IOException e) {
            System.err.println("[PlayWithFriendPanel] Failed to request friends list: " + e.getMessage());
        }
    }
    
    /**
     * Load online players from server.
     */
    private void loadOnlinePlayers() {
        try {
            System.out.println("[PlayWithFriendPanel] Requesting player list from server...");
            networkManager.info().requestPlayerList();
        } catch (IOException e) {
            System.err.println("[PlayWithFriendPanel] Failed to request player list: " + e.getMessage());
            onlinePlayers.clear();
        }
    }
    
    /**
     * Update online players list (called from UIState callback chain in Main.java).
     */
    public void updateOnlinePlayers(List<String> players) {
        javafx.application.Platform.runLater(() -> {
            System.out.println("[PlayWithFriendPanel] updateOnlinePlayers called with " + (players != null ? players.size() : 0) + " players");
            if (players != null && !players.isEmpty()) {
                System.out.println("[PlayWithFriendPanel] Sample online players: " + players.subList(0, Math.min(5, players.size())));
            }
            onlinePlayers.clear();
            if (players != null && !players.isEmpty()) {
                onlinePlayers.addAll(players);
                System.out.println("[PlayWithFriendPanel] Updated online players list. Total: " + onlinePlayers.size());
            } else {
                System.out.println("[PlayWithFriendPanel] No players received from server");
            }
            // Refresh friends list to update online status
            System.out.println("[PlayWithFriendPanel] Calling refreshFriendsList() after online players update");
            refreshFriendsList();
        });
    }
    
    /**
     * Update online players not in game list (called from UIState callback).
     */
    public void updateOnlinePlayersNotInGame(List<String> players) {
        javafx.application.Platform.runLater(() -> {
            System.out.println("[PlayWithFriendPanel] updateOnlinePlayersNotInGame called with " + (players != null ? players.size() : 0) + " players");
            onlinePlayersNotInGame.clear();
            if (players != null && !players.isEmpty()) {
                onlinePlayersNotInGame.addAll(players);
                System.out.println("[PlayWithFriendPanel] Updated online players not in game list. Total: " + onlinePlayersNotInGame.size());
            } else {
                System.out.println("[PlayWithFriendPanel] No players not in game received from server");
            }
            // Refresh friends list to update filter
            refreshFriendsList();
        });
    }
    
    /**
     * Refresh friends list display.
     * Only shows friends who are online (not in game status will be handled by backend when challenging).
     */
    private void refreshFriendsList() {
        if (leftColumn == null || rightColumn == null) {
            System.out.println("[PlayWithFriendPanel] refreshFriendsList: leftColumn or rightColumn is null, skipping refresh");
            return;
        }
        
        // Prevent concurrent refreshes
        if (isRefreshing) {
            System.out.println("[PlayWithFriendPanel] refreshFriendsList: Already refreshing, skipping");
            return;
        }
        
        javafx.application.Platform.runLater(() -> {
            isRefreshing = true;
            // Clear both columns
            leftColumn.getChildren().clear();
            rightColumn.getChildren().clear();
            
            javafx.collections.ObservableList<String> friends = state.getFriendsList();
            
            // Filter: chỉ hiển thị bạn bè online và không đang trong trận
            List<String> onlineFriends = new ArrayList<>();
            for (String friend : friends) {
                boolean isOnline = onlinePlayers.contains(friend);
                boolean isNotInGame = onlinePlayersNotInGame.contains(friend);
                System.out.println("[PlayWithFriendPanel] Checking friend: " + friend + ", isOnline: " + isOnline + ", isNotInGame: " + isNotInGame);
                if (isOnline && isNotInGame) {
                    onlineFriends.add(friend);
                    // Request elo for this friend if not already cached for current mode
                    if (!friendElos.containsKey(friend) || !friendElos.get(friend).containsKey(currentGameMode)) {
                        requestFriendElo(friend);
                    }
                }
            }
            
            System.out.println("[PlayWithFriendPanel] Refreshing friends list. Total friends: " + friends.size() + ", Online friends: " + onlineFriends.size());
            System.out.println("[PlayWithFriendPanel] Friends list: " + friends);
            System.out.println("[PlayWithFriendPanel] Online players list: " + onlinePlayers);
            System.out.println("[PlayWithFriendPanel] Online players not in game list: " + onlinePlayersNotInGame);
            System.out.println("[PlayWithFriendPanel] Friend elos cache: " + friendElos);
            
            if (onlineFriends.isEmpty()) {
                // Show empty message
                Label emptyLabel = new Label("No online friends available.\nFriends must be online to challenge.");
                emptyLabel.setStyle(
                    "-fx-font-family: 'Kumar One'; " +
                    "-fx-font-size: 24px; " +
                    "-fx-text-fill: rgba(0, 0, 0, 0.6); " +
                    "-fx-background-color: transparent; " +
                    "-fx-alignment: center;"
                );
                emptyLabel.setAlignment(Pos.CENTER);
                emptyLabel.setPrefWidth(1100);
                emptyLabel.setPrefHeight(200);
                leftColumn.getChildren().add(emptyLabel);
            } else {
                // Display online friends in 2 columns
                for (int i = 0; i < onlineFriends.size(); i++) {
                    String username = onlineFriends.get(i);
                    // Get elo from cache for current mode
                    Integer elo = null;
                    if (friendElos.containsKey(username) && friendElos.get(username).containsKey(currentGameMode)) {
                        elo = friendElos.get(username).get(currentGameMode);
                        System.out.println("[PlayWithFriendPanel] Found cached elo for " + username + ", mode: " + currentGameMode + ", elo: " + elo);
                    } else {
                        System.out.println("[PlayWithFriendPanel] No cached elo for " + username + ", mode: " + currentGameMode + ". Cache: " + friendElos);
                    }
                    HBox friendEntry = createFriendEntry(username, "Online", elo);
                    if (i % 2 == 0) {
                        leftColumn.getChildren().add(friendEntry);
                    } else {
                        rightColumn.getChildren().add(friendEntry);
                    }
                }
            }
            isRefreshing = false;
        });
    }
    
    /**
     * Request elo for a friend based on current game mode.
     */
    private void requestFriendElo(String username) {
        try {
            // Convert game mode to time_control format
            String timeControl = currentGameMode;
            if ("classical".equals(currentGameMode)) {
                timeControl = "classical";
            } else if ("blitz".equals(currentGameMode)) {
                timeControl = "blitz";
            } else {
                timeControl = "classical";  // Default to classical
            }
            
            System.out.println("[PlayWithFriendPanel] Requesting elo for friend: " + username + ", mode: " + timeControl);
            networkManager.info().requestUserStats(username, timeControl);
        } catch (IOException e) {
            System.err.println("[PlayWithFriendPanel] Failed to request elo for " + username + ": " + e.getMessage());
        }
    }
    
    /**
     * Update elo for a friend (called from InfoHandler callback).
     * This method will be called by InfoHandler when it receives user stats response.
     * NOTE: Do NOT call refreshFriendsList() here to avoid infinite loop!
     */
    public void updateFriendElo(String username, String timeControl, int elo) {
        javafx.application.Platform.runLater(() -> {
            System.out.println("[PlayWithFriendPanel] Updating elo for friend: " + username + ", mode: " + timeControl + ", elo: " + elo);
            // Cache elo for this friend and mode
            if (!friendElos.containsKey(username)) {
                friendElos.put(username, new java.util.HashMap<>());
            }
            friendElos.get(username).put(timeControl, elo);
            
            // Update the UI label directly for this friend (if displayed)
            // Instead of calling refreshFriendsList() which causes infinite loop
            if (timeControl.equals(currentGameMode)) {
                updateFriendEloLabel(username, elo);
            }
        });
    }
    
    /**
     * Update elo label for a specific friend without refreshing entire list.
     */
    private void updateFriendEloLabel(String username, int elo) {
        // Find and update the label in both columns
        updateEloInColumn(leftColumn, username, elo);
        updateEloInColumn(rightColumn, username, elo);
    }
    
    private void updateEloInColumn(VBox column, String username, int elo) {
        for (javafx.scene.Node node : column.getChildren()) {
            if (node instanceof HBox) {
                HBox entry = (HBox) node;
                // Find the username label and elo label in this entry
                for (javafx.scene.Node child : entry.getChildren()) {
                    if (child instanceof VBox) {
                        VBox textInfo = (VBox) child;
                        String entryUsername = null;
                        for (javafx.scene.Node textChild : textInfo.getChildren()) {
                            if (textChild instanceof Label) {
                                Label label = (Label) textChild;
                                String text = label.getText();
                                // Check if this is the username label
                                if (text != null && !text.contains("ELO") && !text.contains("Online")) {
                                    entryUsername = text;
                                }
                                // Check if this is the elo label and username matches
                                if (text != null && text.contains("ELO") && username.equals(entryUsername)) {
                                    String modeDisplay = "classical".equals(currentGameMode) ? "Classical" : "Blitz";
                                    label.setText(modeDisplay + " ELO: " + elo);
                                    System.out.println("[PlayWithFriendPanel] Updated elo label for " + username + " to " + elo);
                                    return;
                                }
                            }
                            // Also check HBox children (statusEloContainer)
                            if (textChild instanceof HBox) {
                                HBox statusEloContainer = (HBox) textChild;
                                for (javafx.scene.Node statusChild : statusEloContainer.getChildren()) {
                                    if (statusChild instanceof Label) {
                                        Label label = (Label) statusChild;
                                        String text = label.getText();
                                        if (text != null && text.contains("ELO") && username.equals(entryUsername)) {
                                            String modeDisplay = "classical".equals(currentGameMode) ? "Classical" : "Blitz";
                                            label.setText(modeDisplay + " ELO: " + elo);
                                            System.out.println("[PlayWithFriendPanel] Updated elo label for " + username + " to " + elo);
                                            return;
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
}

