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
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.Cursor;
import java.util.Random;
import javafx.scene.layout.Region;

/**
 * Profile panel that displays user profile information.
 */
public class ProfilePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;

    public ProfilePanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        
        // Create profile content
        StackPane profileContent = createProfileContent();
        profileContent.setLayoutX((1920 - 1200) / 2);  // Center horizontally, width 1200
        profileContent.setLayoutY((1080 - 800) / 2);    // Center vertically, height 800
        
        container.getChildren().add(profileContent);
        getChildren().add(container);
        
        // Bind visibility with MAIN_MENU/PROFILE and profileVisible
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.PROFILE)
                .and(state.profileVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation when profileVisible changes
        state.profileVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.PROFILE) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        // Also listen to appState changes
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.PROFILE && state.isProfileVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private StackPane createProfileContent() {
        // Main background panel
        Rectangle bg = new Rectangle(1200, 800);
        bg.setFill(Color.color(0.85, 0.85, 0.85));  // Light grey
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));  // Dark grey border
        bg.setStrokeWidth(2);
        
        StackPane mainPanel = new StackPane();
        mainPanel.setPrefSize(1200, 800);
        
        Pane contentPane = new Pane();
        contentPane.setPrefSize(1200, 800);
        
        // Header with back button and "Profile" title
        HBox header = new HBox(20);
        header.setLayoutX(30);
        header.setLayoutY(30);
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Back button
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(100);
        backIcon.setFitHeight(100);
        backIcon.setPreserveRatio(true);
        backIcon.setSmooth(true);
        
        StackPane backButton = new StackPane(backIcon);
        backButton.setCursor(Cursor.HAND);
        backButton.setOnMouseClicked(e -> {
            state.closeProfile();
        });
        backButton.setOnMouseEntered(e -> {
            backIcon.setOpacity(0.7);
        });
        backButton.setOnMouseExited(e -> {
            backIcon.setOpacity(1.0);
        });
        
        // "Profile" title
        Label profileTitle = new Label("Profile");
        profileTitle.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 75px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        header.getChildren().addAll(backButton, profileTitle);
        
        // Avatar placeholder (square with red border)
        Rectangle avatarPlaceholder = new Rectangle(250, 250);  // Tăng từ 200 lên 250
        avatarPlaceholder.setFill(Color.TRANSPARENT);
        avatarPlaceholder.setStroke(Color.web("#A65252"));  // Red border
        avatarPlaceholder.setStrokeWidth(4);
        avatarPlaceholder.setArcWidth(10);
        avatarPlaceholder.setArcHeight(10);
        
        // Load avatar image (random from ava folder)
        Random random = new Random();
        int avatarNumber = random.nextInt(3) + 1;
        ImageView avatarImage = new ImageView(AssetHelper.image("ava/" + avatarNumber + ".jpg"));
        avatarImage.setFitWidth(250);  // Tăng từ 200 lên 250
        avatarImage.setFitHeight(250);  // Tăng từ 200 lên 250
        avatarImage.setPreserveRatio(false);
        avatarImage.setSmooth(true);
        
        // Clip avatar to square
        Rectangle clip = new Rectangle(250, 250);  // Tăng từ 200 lên 250
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        avatarImage.setClip(clip);
        
        StackPane avatarContainer = new StackPane(avatarPlaceholder, avatarImage);
        avatarContainer.setLayoutX(30);
        avatarContainer.setLayoutY(150);
        
        // Username and Elo (to the right of avatar)
        VBox userInfo = new VBox(10);
        userInfo.setLayoutX(300);  // Tăng từ 250 lên 300 để cách avatar xa hơn
        userInfo.setLayoutY(130);  // Giảm từ 150 xuống 130 để dịch lên
        userInfo.setAlignment(Pos.TOP_LEFT);
        
        Label usernameLabel = new Label();
        usernameLabel.textProperty().bind(state.usernameProperty());
        usernameLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        Label eloLabel = new Label();
        eloLabel.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                () -> "elo " + state.getElo(),
                state.eloProperty()
            )
        );
        eloLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        userInfo.getChildren().addAll(usernameLabel, eloLabel);
        
        // Statistics section (centered, below profile info)
        VBox statisticsContainer = new VBox(10);  // Container chính
        statisticsContainer.setLayoutX(30);  // Căn trái
        statisticsContainer.setLayoutY(380);  // Tăng từ 330 lên 380 để dịch xuống
        statisticsContainer.setPrefWidth(1140);
        statisticsContainer.setAlignment(Pos.TOP_LEFT);
        
        // Hàng trên: Total match bên trái, Win matches bên phải
        HBox topRow = new HBox(20);
        topRow.setPrefWidth(1140);
        topRow.setAlignment(Pos.CENTER_LEFT);
        
        // Total matches - bên trái
        Label totalMatchesLabel = new Label();
        totalMatchesLabel.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                () -> "Total match: " + state.getTotalMatches(),
                state.totalMatchesProperty()
            )
        );
        totalMatchesLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        totalMatchesLabel.setWrapText(false);
        totalMatchesLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        
        // Win matches - bên phải
        Label winMatchesLabel = new Label();
        winMatchesLabel.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                () -> "Win matches: " + state.getWinMatches(),
                state.winMatchesProperty()
            )
        );
        winMatchesLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        winMatchesLabel.setWrapText(false);
        winMatchesLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        
        // Spacer để đẩy 2 label ra 2 bên
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        
        topRow.getChildren().addAll(totalMatchesLabel, spacer, winMatchesLabel);
        
        // Win rate - ở giữa, phía dưới, căn trái để thẳng hàng với các chữ trên
        Label winRateLabel = new Label();
        winRateLabel.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                () -> {
                    double rate = state.getWinRate();
                    return String.format("Win rates: %.1f%%", rate);
                },
                state.winRateProperty()
            )
        );
        winRateLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        winRateLabel.setAlignment(Pos.CENTER_LEFT);  // Căn trái để thẳng hàng
        winRateLabel.setWrapText(false);
        winRateLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        winRateLabel.setPrefWidth(1140);  // Cùng width với topRow
        
        statisticsContainer.getChildren().addAll(topRow, winRateLabel);
        
        contentPane.getChildren().addAll(header, avatarContainer, userInfo, statisticsContainer);
        mainPanel.getChildren().addAll(bg, contentPane);
        
        return mainPanel;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
