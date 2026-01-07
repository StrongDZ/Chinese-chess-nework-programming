package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;

/**
 * Waiting panel that displays when searching for an opponent.
 */
public class WaitingPanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;

    public WaitingPanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        
        // Create waiting content
        StackPane waitingContent = createWaitingContent();
        waitingContent.setLayoutX((1920 - 1200) / 2);  // Center horizontally, width 1200
        waitingContent.setLayoutY((1080 - 800) / 2);    // Center vertically, height 800
        
        container.getChildren().add(waitingContent);
        getChildren().add(container);
        
        // Bind visibility with MAIN_MENU and waitingVisible
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.waitingVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation when waitingVisible changes
        state.waitingVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        // Also listen to appState changes
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isWaitingVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private StackPane createWaitingContent() {
        // Main background panel
        Rectangle bg = new Rectangle(1200, 800);
        bg.setFill(Color.color(0.85, 0.85, 0.85));  // Light grey
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        
        // Shadow effect
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        shadow.setRadius(20);
        shadow.setOffsetX(5);
        shadow.setOffsetY(5);
        bg.setEffect(shadow);
        
        StackPane mainPanel = new StackPane();
        mainPanel.setPrefSize(1200, 800);
        
        Pane contentPane = new Pane();
        contentPane.setPrefSize(1200, 800);
        
        // Back button ở góc trên bên trái
        StackPane backButtonContainer = createBackButton();
        backButtonContainer.setLayoutX(30);
        backButtonContainer.setLayoutY(30);
        
        // "Wait a minutes..." text với hiệu ứng wave animation
        HBox animatedTextContainer = createAnimatedText("Wait a minutes...");
        
        // Tạo StackPane để căn giữa text
        StackPane textContainer = new StackPane();
        textContainer.setPrefSize(1200, 800);
        textContainer.setAlignment(Pos.CENTER);
        textContainer.setMouseTransparent(true);  // Không chặn mouse events để nút back có thể click được
        textContainer.getChildren().add(animatedTextContainer);
        
        // Thêm textContainer trước, backButtonContainer sau để back button ở trên cùng
        contentPane.getChildren().addAll(textContainer, backButtonContainer);
        mainPanel.getChildren().addAll(bg, contentPane);
        StackPane.setAlignment(mainPanel, Pos.CENTER);
        
        return mainPanel;
    }
    
    private StackPane createBackButton() {
        StackPane container = new StackPane();
        container.setPrefSize(180, 180);  // Tăng từ 80x80 lên 120x120
        container.setCursor(Cursor.HAND);
        container.setMouseTransparent(false);
        container.setPickOnBounds(true);
        
        // Back icon (book with left arrow)
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(150);  // Tăng từ 60 lên 90
        backIcon.setFitHeight(150);  // Tăng từ 60 lên 90
        backIcon.setPreserveRatio(true);
        
        container.getChildren().add(backIcon);
        
        // Hover effect
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), container);
        scaleIn.setToX(1.1);
        scaleIn.setToY(1.1);
        
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), container);
        scaleOut.setToX(1.0);
        scaleOut.setToY(1.0);
        
        container.setOnMouseEntered(e -> {
            scaleOut.stop();
            scaleIn.setFromX(container.getScaleX());
            scaleIn.setFromY(container.getScaleY());
            scaleIn.play();
        });
        
        container.setOnMouseExited(e -> {
            scaleIn.stop();
            scaleOut.setFromX(container.getScaleX());
            scaleOut.setFromY(container.getScaleY());
            scaleOut.play();
        });
        
        container.setOnMouseClicked(e -> {
            // Gửi CANCEL_QM đến server trước khi đóng waiting panel
            try {
                application.network.NetworkManager.getInstance().game().cancelQuickMatching();
            } catch (java.io.IOException ex) {
                System.err.println("[WaitingPanel] Error sending CANCEL_QM: " + ex.getMessage());
                ex.printStackTrace();
            }
            
            // Đóng waiting panel
            state.closeWaiting();
            e.consume();
        });
        
        return container;
    }
    
    private HBox createAnimatedText(String text) {
        HBox container = new HBox(5);  // Spacing giữa các phần tử
        container.setAlignment(Pos.CENTER);
        
        // Tách text thành "Wait a minutes" và dấu chấm
        String baseText = "Wait a minutes";
        Label baseTextLabel = new Label(baseText);
        baseTextLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 140px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Label cho dấu chấm (sẽ thay đổi số lượng chấm)
        Label dotsLabel = new Label(".");
        dotsLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 140px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        container.getChildren().addAll(baseTextLabel, dotsLabel);
        
        // Tạo Timeline để thay đổi số lượng chấm: 1 -> 2 -> 3 -> 2 -> 1 -> lặp lại
        Timeline dotsTimeline = new Timeline();
        
        // 1 chấm
        KeyFrame oneDot = new KeyFrame(Duration.millis(0), e -> dotsLabel.setText("."));
        // 2 chấm
        KeyFrame twoDots = new KeyFrame(Duration.millis(500), e -> dotsLabel.setText(".."));
        // 3 chấm
        KeyFrame threeDots = new KeyFrame(Duration.millis(1000), e -> dotsLabel.setText("..."));
        // 2 chấm (quay lại)
        KeyFrame twoDotsBack = new KeyFrame(Duration.millis(1500), e -> dotsLabel.setText(".."));
        // 1 chấm (quay lại)
        KeyFrame oneDotBack = new KeyFrame(Duration.millis(2000), e -> dotsLabel.setText("."));
        
        dotsTimeline.getKeyFrames().addAll(oneDot, twoDots, threeDots, twoDotsBack, oneDotBack);
        dotsTimeline.setCycleCount(Timeline.INDEFINITE);
        
        // Bắt đầu animation khi panel hiển thị
        state.waitingVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                dotsTimeline.play();
            } else {
                dotsTimeline.stop();
            }
        });
        
        // Cũng lắng nghe appState changes
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isWaitingVisible()) {
                dotsTimeline.play();
            } else {
                dotsTimeline.stop();
            }
        });
        
        return container;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}

