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
import javafx.scene.shape.Path;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;

/**
 * Settings panel that appears when clicking the settings icon.
 */
public class SettingsPanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;

    public SettingsPanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        
        Pane container = new Pane();
        container.setPrefSize(1920, 1080);
        container.setStyle("-fx-background-color: transparent;");
        
        // Tạo settings content
        StackPane settingsContent = createSettingsContent();
        settingsContent.setLayoutX((1920 - 1000) / 2);  // Giữa màn hình, width 1000
        settingsContent.setLayoutY((1080 - 700) / 2);   // Giữa màn hình, height 700
        
        container.getChildren().add(settingsContent);
        getChildren().add(container);
        
        // Bind visibility với MAIN_MENU và settingsVisible
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.settingsVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation khi settingsVisible thay đổi
        state.settingsVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        // Cũng listen appState thay đổi
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isSettingsVisible()) {
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
    }
    
    private StackPane createSettingsContent() {
        // Main background panel
        Rectangle bg = new Rectangle(1000, 700);
        bg.setFill(Color.color(0.85, 0.85, 0.85));  // Light grey
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));  // Dark grey border
        bg.setStrokeWidth(2);
        
        StackPane mainPanel = new StackPane();
        mainPanel.setPrefSize(1000, 700);
        
        Pane contentPane = new Pane();
        contentPane.setPrefSize(1000, 700);
        
        // Top bar với "Setting" title
        HBox topBar = new HBox();
        topBar.setPrefWidth(1000);
        topBar.setPrefHeight(80);
        topBar.setLayoutX(0);
        topBar.setLayoutY(0);
        topBar.setPadding(new Insets(0, 0, 20, 0));  // Bỏ padding trái/phải để khớp với border
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: transparent;");
        
        // "Setting" title
        Label settingTitle = new Label("Setting");
        settingTitle.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 90px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        settingTitle.setTranslateY(-20);
        settingTitle.setTranslateX(30);  // Thêm padding trái bằng cách dịch chữ
        
        topBar.getChildren().add(settingTitle);
        
        // Close button - đặt ở góc phải trên
        ImageView closeIcon = new ImageView(AssetHelper.image("ic_close.png"));
        closeIcon.setFitWidth(60);
        closeIcon.setFitHeight(60);
        closeIcon.setPreserveRatio(true);
        closeIcon.setSmooth(true);
        
        StackPane closeButton = new StackPane(closeIcon);
        closeButton.setPrefSize(50, 50);
        closeButton.setLayoutX(1000 - 50 - 20);  // Góc phải, cách viền 20px
        closeButton.setLayoutY(20);  // Cách trên 20px
        closeButton.setAlignment(Pos.CENTER);
        closeButton.setCursor(javafx.scene.Cursor.HAND);
        closeButton.setOnMouseClicked(e -> {
            state.closeSettings();
        });
        
        // Hover effect cho close button
        closeButton.setOnMouseEntered(e -> {
            closeIcon.setOpacity(0.7);
        });
        closeButton.setOnMouseExited(e -> {
            closeIcon.setOpacity(1.0);
        });
        
        // Main content area - chia làm 2 phần: left navigation và right content
        Pane mainContent = new Pane();
        mainContent.setLayoutX(0);
        mainContent.setLayoutY(80);
        mainContent.setPrefWidth(1000);
        mainContent.setPrefHeight(620);
        
        // Left navigation pane - đảm bảo khớp với border
        VBox leftNav = new VBox(0);
        leftNav.setLayoutX(0);  // Khớp với border trái
        leftNav.setLayoutY(0);
        leftNav.setPrefWidth(250);
        leftNav.setPrefHeight(620);
        leftNav.setStyle("-fx-background-color: transparent;");
        
        // Đường kẻ ngang kéo dài toàn bộ chiều rộng panel - trùng với khung đầu của Account
        Rectangle dividerLine = new Rectangle(1000, 2);  // Kéo dài toàn bộ chiều rộng (1000px)
        dividerLine.setFill(Color.color(0.5, 0.5, 0.5));  // Dark grey
        dividerLine.setLayoutX(0);
        dividerLine.setLayoutY(80 + 40);  // 80 (mainContent) + 40 (spacer) = 120, trùng với khung đầu Account
        
        // Spacer để đẩy Account xuống
        Rectangle spacer = new Rectangle(250, 40);  // Khoảng trống 40px
        spacer.setFill(Color.TRANSPARENT);
        
        // "Account" navigation item - highlighted
        // Dùng StackPane để có thể đặt text lên trên Rectangle
        StackPane accountContainer = new StackPane();
        accountContainer.setPrefWidth(250);
        accountContainer.setPrefHeight(80);
        
        // Background rectangle cho Account
        Rectangle accountBg = new Rectangle(250, 80);
        accountBg.setFill(Color.color(255.0/255.0, 248.0/255.0, 220.0/255.0, 0.8));  // Light yellow/beige
        accountBg.setStroke(Color.color(0.3, 0.3, 0.3));  // Dark grey border
        accountBg.setStrokeWidth(2);
        
        // Text label
        Label accountNav = new Label("Account");
        accountNav.setStyle(
            "-fx-font-family: 'Kristi'; " +
            "-fx-font-size: 50px; " +  // Tăng từ 40px lên 50px
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        accountNav.setAlignment(Pos.CENTER);  // Đổi từ CENTER_LEFT sang CENTER
        accountNav.setPadding(new Insets(0));  // Bỏ padding trái
        
        accountContainer.getChildren().addAll(accountBg, accountNav);
        StackPane.setAlignment(accountBg, Pos.CENTER);
        StackPane.setAlignment(accountNav, Pos.CENTER);  // Đổi từ CENTER_LEFT sang CENTER
        
        // Tính số box cần thiết để vừa khít với khung
        // LeftNav height: 620px
        // Spacer: 40px, Account: 80px
        // Còn lại: 620 - 40 - 80 = 500px
        // Mỗi box: 80px
        // Số box nguyên: 500 / 80 = 6 boxes (480px)
        // Còn dư: 500 - 480 = 20px - bỏ qua, chỉ dùng 6 boxes
        int remainingHeight = 620 - 40 - 80;  // 500px
        int boxHeight = 80;
        int fullBoxesCount = remainingHeight / boxHeight;  // 6 boxes
        int lastBoxHeight = remainingHeight % boxHeight;  // 20px dư   
        // Placeholder navigation items - box cuối cùng có viền dưới và trái trong suốt
        for (int i = 0; i < fullBoxesCount; i++) {
            boolean isLastBox = (i == fullBoxesCount - 1);
            
            if (isLastBox) {
                // Box cuối cùng - viền dưới và viền trái trong suốt
                // Background rectangle (không có viền)
                Rectangle lastBoxBg = new Rectangle(250, boxHeight);
                lastBoxBg.setFill(Color.color(0.85, 0.85, 0.85));  // Light grey
                lastBoxBg.setStroke(Color.TRANSPARENT);  // Không có viền
                
                // Vẽ chỉ viền trên và viền phải
                javafx.scene.shape.Path lastBoxBorder = new javafx.scene.shape.Path();
                double width = 250;
                double height = boxHeight;
                
                // Viền trên (từ trái sang phải)
                javafx.scene.shape.MoveTo moveToTop = new javafx.scene.shape.MoveTo(0, 0);
                javafx.scene.shape.LineTo lineToTop = new javafx.scene.shape.LineTo(width, 0);
                
                // Viền phải (từ trên xuống dưới)
                javafx.scene.shape.LineTo lineToRight = new javafx.scene.shape.LineTo(width, height);
                
                lastBoxBorder.getElements().addAll(moveToTop, lineToTop, lineToRight);
                lastBoxBorder.setStroke(Color.color(0.3, 0.3, 0.3));  // Dark grey border
                lastBoxBorder.setStrokeWidth(2);
                lastBoxBorder.setFill(Color.TRANSPARENT);
                
                // Stack background và border
                StackPane lastBoxContainer = new StackPane();
                lastBoxContainer.getChildren().addAll(lastBoxBg, lastBoxBorder);
                StackPane.setAlignment(lastBoxBg, Pos.TOP_LEFT);
                StackPane.setAlignment(lastBoxBorder, Pos.TOP_LEFT);
                
                leftNav.getChildren().add(lastBoxContainer);
            } else {
                // Các box khác - viền đầy đủ
                Rectangle placeholder = new Rectangle(250, boxHeight);
                placeholder.setFill(Color.color(0.85, 0.85, 0.85));  // Light grey
                placeholder.setStroke(Color.color(0.3, 0.3, 0.3));  // Dark grey border
                placeholder.setStrokeWidth(2);
                leftNav.getChildren().add(placeholder);
            }
        }
        
        leftNav.getChildren().add(0, spacer);  // Thêm spacer ở đầu
        leftNav.getChildren().add(1, accountContainer);  // Thêm Account sau spacer
        
        // Right content area
        Pane rightContent = new Pane();
        rightContent.setLayoutX(250);
        rightContent.setLayoutY(0);
        rightContent.setPrefWidth(750);
        rightContent.setPrefHeight(620);
        rightContent.setStyle("-fx-background-color: transparent;");
        
        // "username" text
        Label usernameLabel = new Label();
        usernameLabel.textProperty().bind(state.usernameProperty());
        usernameLabel.setLayoutX(40);
        usernameLabel.setLayoutY(40);
        usernameLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 50px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Buttons container - 2 buttons cạnh nhau
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setLayoutX(40);
        buttonsContainer.setLayoutY(150);
        buttonsContainer.setAlignment(Pos.CENTER_LEFT);
        
        // Log out button - bên trái
        Label logOutButton = createActionButton("Log out", 300, 70);
        logOutButton.setOnMouseClicked(e -> {
            state.closeSettings();
            state.setAppState(UIState.AppState.LANDING);
        });
        
        // Delete account button - bên phải
        Label deleteAccountButton = createActionButton("Delete account", 300, 70);
        deleteAccountButton.setOnMouseClicked(e -> {
            // TODO: Implement delete account logic
            state.closeSettings();
        });
        
        buttonsContainer.getChildren().addAll(logOutButton, deleteAccountButton);
        
        rightContent.getChildren().addAll(usernameLabel, buttonsContainer);
        mainContent.getChildren().addAll(leftNav, rightContent);
        
        contentPane.getChildren().addAll(topBar, closeButton, dividerLine, mainContent);  // Thêm dividerLine vào contentPane
        
        mainPanel.getChildren().addAll(bg, contentPane);
        
        return mainPanel;
    }
    
    private Label createActionButton(String text, double width, double height) {
        Label button = new Label(text);
        button.setPrefSize(width, height);
        button.setAlignment(Pos.CENTER);
        button.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 20px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: rgba(220, 220, 220, 0.9); " +  // Light grey
            "-fx-background-radius: 10; " +
            "-fx-border-color: rgba(150, 150, 150, 0.8); " +  // Dark grey border
            "-fx-border-width: 2; " +
            "-fx-border-radius: 10; " +
            "-fx-alignment: center; " +
            "-fx-text-alignment: center; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 5, 0, 0, 2);"  // Drop shadow
        );
        button.setCursor(javafx.scene.Cursor.HAND);
        
        // Hover effect
        String originalStyle = button.getStyle();
        button.setOnMouseEntered(e -> {
            button.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 20px; " +
                "-fx-text-fill: black; " +
                "-fx-background-color: rgba(200, 200, 200, 1.0); " +  // Sáng hơn khi hover
                "-fx-background-radius: 10; " +
                "-fx-border-color: rgba(120, 120, 120, 1.0); " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 10; " +
                "-fx-alignment: center; " +
                "-fx-text-alignment: center; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.4), 7, 0, 0, 3);"
            );
        });
        button.setOnMouseExited(e -> {
            button.setStyle(originalStyle);
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
