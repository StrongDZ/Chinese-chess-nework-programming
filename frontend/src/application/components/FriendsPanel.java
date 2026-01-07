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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Friends panel that appears when clicking the friends icon.
 */
public class FriendsPanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;

    public FriendsPanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        setMouseTransparent(true);  // Đặt mouseTransparent cho chính FriendsPanel
        setPickOnBounds(false);
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        container.setMouseTransparent(true);
        
        // Friends panel - đặt ở bên phải, chiều cao từ đầu màn hình đến bottom menu
        VBox friendsContent = createFriendsContent();
        friendsContent.setLayoutX(1920 - 430);
        friendsContent.setLayoutY(-45);
        friendsContent.setMouseTransparent(false);  // friendsContent vẫn nhận sự kiện chuột
        friendsContent.setPickOnBounds(true);
        
        container.getChildren().add(friendsContent);
        getChildren().add(container);
        
        // Bind visibility - hiển thị khi bấm icon friend (KHÔNG phải play with friend mode)
        // Có thể hiển thị trong MAIN_MENU hoặc khi đang trong mode panel
        // Hiển thị khi: friendsVisible = true AND playWithFriendMode = false
        // (KHÔNG cần kiểm tra appState, vì mode panel vẫn ở trong MAIN_MENU)
        visibleProperty().bind(
            state.friendsVisibleProperty()
                .and(state.playWithFriendModeProperty().not())  // KHÔNG hiển thị khi play with friend mode
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation khi friendsVisible thay đổi
        state.friendsVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && !state.isPlayWithFriendMode()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        state.playWithFriendModeProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && state.isFriendsVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private VBox createFriendsContent() {
        VBox content = new VBox(20);
        content.setPrefWidth(400);
        content.setPrefHeight(980);  // Chiều cao từ đầu màn hình đến bottom menu
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(30));
        
        // Background cho friends panel
        javafx.scene.shape.Rectangle bg = new javafx.scene.shape.Rectangle(400, 980);
        bg.setFill(Color.color(0.75, 0.75, 0.75, 0.9));
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(2);
        
        // Header với "Friends" title và Add Friend icon
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 20, 0));
        
        // "Friends" title
        Label friendsTitle = new Label("Friends");
        friendsTitle.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Add Friend icon
        ImageView addFriendIcon = new ImageView(AssetHelper.image("ic_friend.png"));
        addFriendIcon.setFitWidth(40);
        addFriendIcon.setFitHeight(40);
        addFriendIcon.setPreserveRatio(true);
        addFriendIcon.setSmooth(true);
        
        StackPane addFriendButton = new StackPane(addFriendIcon);
        addFriendButton.setCursor(javafx.scene.Cursor.HAND);
        addFriendButton.setOnMouseEntered(e -> {
            addFriendIcon.setOpacity(0.7);
        });
        addFriendButton.setOnMouseExited(e -> {
            addFriendIcon.setOpacity(1.0);
        });
        
        header.getChildren().addAll(friendsTitle, addFriendButton);
        
        // Friends list
        VBox friendsList = new VBox(15);
        friendsList.setAlignment(Pos.TOP_LEFT);
        
        // Tạo 8 friend entries
        for (int i = 0; i < 8; i++) {
            HBox friendEntry = createFriendEntry("Username", "Online");
            friendsList.getChildren().add(friendEntry);
        }
        
        // Stack background và content
        StackPane friendsPanel = new StackPane();
        VBox innerContent = new VBox(20, header, friendsList);
        innerContent.setAlignment(Pos.TOP_LEFT);
        innerContent.setPadding(new Insets(20));
        
        friendsPanel.getChildren().addAll(bg, innerContent);
        StackPane.setAlignment(bg, Pos.CENTER);
        StackPane.setAlignment(innerContent, Pos.TOP_LEFT);
        
        content.getChildren().add(friendsPanel);
        
        return content;
    }
    
    private HBox createFriendEntry(String username, String status) {
        HBox entry = new HBox(15);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(10, 0, 10, 0));
        
        // Avatar circle với viền đỏ
        Circle avatarCircle = new Circle(25);
        avatarCircle.setFill(Color.TRANSPARENT);
        avatarCircle.setStroke(Color.web("#A65252"));  // Viền đỏ
        avatarCircle.setStrokeWidth(2);
        
        StackPane avatarContainer = new StackPane(avatarCircle);
        avatarContainer.setPrefSize(50, 50);
        
        // Username và status
        VBox textInfo = new VBox(5);
        textInfo.setAlignment(Pos.CENTER_LEFT);
        
        Label usernameLabel = new Label(username);
        usernameLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 18px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        Label statusLabel = new Label(status);
        statusLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 14px; " +
            "-fx-text-fill: rgba(0, 0, 0, 0.7); " +
            "-fx-background-color: transparent;"
        );
        
        textInfo.getChildren().addAll(usernameLabel, statusLabel);
        
        entry.getChildren().addAll(avatarContainer, textInfo);
        
        return entry;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
