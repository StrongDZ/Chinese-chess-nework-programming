package application.components;

import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
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
import javafx.scene.Cursor;
import javafx.util.Duration;

/**
 * Panel chọn độ khó AI: Easy / Medium / Hard
 */
public class AIDifficultyPanel extends StackPane {

    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    
    // Kích thước Panel
    private static final double PANEL_WIDTH = 1000;
    private static final double PANEL_HEIGHT = 700;
    private static final double BUTTON_WIDTH = 700;

    public AIDifficultyPanel(UIState state) {
        this.state = state;

        setPrefSize(1920, 1080);
        setStyle("-fx-background-color: transparent;");

        // Container chính
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);

        // Tạo nội dung bảng chọn
        StackPane content = createContent();
        
        // Căn giữa panel trong màn hình 1920x1080
        content.setLayoutX((1920 - PANEL_WIDTH) / 2);
        content.setLayoutY((1080 - PANEL_HEIGHT) / 2);

        container.getChildren().add(content);
        getChildren().add(container);

        // Binding visibility
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.aiDifficultyVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);

        // Animation fade in/out
        state.aiDifficultyVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }

    private StackPane createContent() {
        // 1. Overlay tối màu làm nền (che toàn màn hình)
        // Lưu ý: Overlay này nằm trong StackPane content nhưng ta set kích thước lớn để che hết
        // Tuy nhiên để xử lý click đóng, ta nên tách overlay ra hoặc xử lý ở mức root.
        // Ở đây để đơn giản và giống logic cũ, ta tạo BG panel riêng.
        
        // Background của Panel (Màu xám sáng giống Ranking)
        Rectangle bg = new Rectangle(PANEL_WIDTH, PANEL_HEIGHT);
        bg.setFill(Color.web("#D3D1D1", 0.95)); // Màu xám nhẹ, hơi trong suốt
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        bg.setStroke(Color.TRANSPARENT);

        // Pane chứa các phần tử con (Header, Buttons)
        Pane contentPane = new Pane();
        contentPane.setPrefSize(PANEL_WIDTH, PANEL_HEIGHT);

        // --- 2. HEADER (Mũi tên + Tiêu đề) ---
        HBox headerBox = new HBox(30); // Khoảng cách giữa icon và chữ
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setLayoutX(60); // Cách lề trái 60px
        headerBox.setLayoutY(40); // Cách lề trên 40px

        // Mũi tên quay lại (To ra: 100x100)
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(100);
        backIcon.setFitHeight(100);
        backIcon.setCursor(Cursor.HAND);
        backIcon.setOnMouseClicked(e -> {
            // Đóng panel AI difficulty
            state.closeAIDifficulty();
            
            // Quay về mode panel trước đó dựa trên currentGameMode
            String mode = state.getCurrentGameMode();
            if (mode != null) {
                if ("classical".equals(mode)) {
                    // Quay về Classic Mode
                    state.openClassicMode();
                } else if ("blitz".equals(mode)) {
                    // Quay về Blitz Mode
                    state.openBlitzMode();
                }
            }
        });

        // Tiêu đề "AI Level"
        Label title = new Label("AI Level");
        title.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 100px; -fx-text-fill: black;");
        title.setPadding(Insets.EMPTY);
        // Đẩy chữ lên một chút để thẳng tâm với mũi tên
        title.setTranslateY(-10);

        headerBox.getChildren().addAll(backIcon, title);

        // --- 3. CÁC NÚT CHỌN ĐỘ KHÓ ---
        VBox buttonsContainer = new VBox(30); // Khoảng cách giữa các nút
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Căn giữa nút theo chiều ngang: (PanelWidth - ButtonWidth) / 2
        // (1000 - 700) / 2 = 150
        buttonsContainer.setLayoutX((PANEL_WIDTH - BUTTON_WIDTH) / 2); 
        buttonsContainer.setLayoutY(200); // Vị trí Y bắt đầu vẽ nút

        StackPane easyBtn = createDifficultyButton("Easy Level", "easy");
        StackPane mediumBtn = createDifficultyButton("Medium Level", "medium");
        StackPane hardBtn = createDifficultyButton("Hard Level", "hard");

        buttonsContainer.getChildren().addAll(easyBtn, mediumBtn, hardBtn);

        contentPane.getChildren().addAll(headerBox, buttonsContainer);

        return new StackPane(bg, contentPane);
    }

    private StackPane createDifficultyButton(String text, String difficulty) {
        // Nền nút
        Rectangle bg = new Rectangle(BUTTON_WIDTH, 120);
        bg.setArcWidth(30);
        bg.setArcHeight(30);
        bg.setFill(Color.web("#F0F0F0")); // Màu nền nút sáng hơn nền panel chút
        bg.setStroke(Color.web("#999999"));
        bg.setStrokeWidth(1);

        // Chữ trong nút
        Label label = new Label(text);
        label.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );

        StackPane button = new StackPane(bg, label);
        button.setAlignment(Pos.CENTER);
        button.setPrefSize(BUTTON_WIDTH, 120);
        button.setCursor(Cursor.HAND);

        // Hiệu ứng Hover phóng to
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), button);
        scaleIn.setToX(1.05);
        scaleIn.setToY(1.05);
        
        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), button);
        scaleOut.setToX(1.0);
        scaleOut.setToY(1.0);

        button.setOnMouseEntered(e -> {
            scaleOut.stop();
            scaleIn.play();
            bg.setFill(Color.web("#FFFFFF")); // Sáng lên khi hover
            bg.setStroke(Color.web("#A61E1E")); // Viền đỏ khi hover
        });
        
        button.setOnMouseExited(e -> {
            scaleIn.stop();
            scaleOut.play();
            bg.setFill(Color.web("#F0F0F0")); // Trả về màu cũ
            bg.setStroke(Color.web("#999999"));
        });

        // Xử lý Click
        button.setOnMouseClicked(e -> {
            state.setAiDifficulty(difficulty);
            state.setOpponentUsername("AI (" + capitalize(difficulty) + ")");
            state.setPlayerIsRed(true);
            
            String mode = state.getCurrentGameMode();
            if (mode == null || mode.isEmpty()) {
                mode = "classical";
            }
            state.closeAIDifficulty();
            state.openGame(mode);
        });

        return button;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0,1).toUpperCase() + str.substring(1);
    }

    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}