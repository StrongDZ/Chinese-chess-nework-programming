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

public class GameModePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;

    public GameModePanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        setPickOnBounds(false);  // Quan trọng: không chặn mouse events ở vùng trống
        // Không set mouseTransparent - để children có thể nhận events
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        container.setPickOnBounds(false);  // Quan trọng: không chặn mouse events ở vùng trống
        // Không set mouseTransparent - để children có thể nhận events
        
        StackPane gameModeContent = createGameModeContent();
        gameModeContent.setLayoutX(0);
        gameModeContent.setLayoutY(0);
        gameModeContent.setPickOnBounds(false);  // Quan trọng: không chặn mouse events ở vùng trống
        // Không set mouseTransparent - để children có thể nhận events
        
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
        content.setPickOnBounds(false);  // Quan trọng: không chặn mouse events ở vùng trống
        // Không set mouseTransparent - để children có thể nhận events
        
        Pane mainPane = new Pane();
        mainPane.setPrefSize(1920, 1080);
        mainPane.setPickOnBounds(false);  // Quan trọng: không chặn mouse events ở vùng trống
        // Không set mouseTransparent - để children có thể nhận events
        
        // Back arrow icon
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(130);
        backIcon.setFitHeight(130);
        backIcon.setPreserveRatio(true);
        StackPane backButton = new StackPane(backIcon);
        backButton.setLayoutX(50);
        backButton.setLayoutY(175);
        backButton.setPrefSize(130, 130);
        backButton.setMinSize(130, 130);
        backButton.setMaxSize(130, 130);
        backButton.setCursor(javafx.scene.Cursor.HAND);
        backButton.setMouseTransparent(false);
        backButton.setPickOnBounds(true);  // Chỉ backButton nhận mouse events
        backButton.setOnMouseClicked(e -> state.closeGameMode());
        
        // Hover effect cho back button - giống InventoryPanel (scale trực tiếp)
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
        
        // Game mode buttons container - căn giữa màn hình
        VBox buttonsContainer = new VBox(20);
        buttonsContainer.setLayoutX((1920 - 400) / 2);
        buttonsContainer.setLayoutY((1080 - 600) / 2);
        buttonsContainer.setAlignment(Pos.CENTER);
        buttonsContainer.setMouseTransparent(false);
        buttonsContainer.setPickOnBounds(true);  // Chỉ buttonsContainer nhận mouse events
        
        // Tạo các nút game mode
        String[] gameModes = {
            "Classic mode",
            "Blitz mode",
            "Puzzle mode",
            "Watch match",
            "Custom mode"
        };
        
        for (String mode : gameModes) {
            Label modeButton = createGameModeButton(mode);
            buttonsContainer.getChildren().add(modeButton);
        }
        
        mainPane.getChildren().addAll(backButton, buttonsContainer);
        content.getChildren().add(mainPane);
        
        return content;
    }
    
    private Label createGameModeButton(String text) {
        Label button = new Label(text);
        button.setPrefSize(400, 100);
        button.setAlignment(Pos.CENTER);
        button.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 50px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: rgba(217, 217, 217, 0.9); " +
            "-fx-background-radius: 10; " +
            "-fx-border-radius: 10;"
        );
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
        
        return button;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
