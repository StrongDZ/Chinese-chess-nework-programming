package application.components;

import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.effect.DropShadow;

/**
 * Manager để quản lý các dialog trong game
 */
public class DialogManager {
    
    private final UIState state;
    private final GamePanel gamePanel;
    private final Pane rootPane;
    
    private StackPane surrenderDialog = null;
    private StackPane gameResultPanel = null;
    private StackPane gameResultOverlay = null;
    private StackPane drawRequestDialog = null;
    private StackPane drawReceivedDialog = null;
    
    private int eloChange = 10;  // Điểm thay đổi khi win/lose (có thể thay đổi được)
    
    public DialogManager(UIState state, GamePanel gamePanel, Pane rootPane) {
        this.state = state;
        this.gamePanel = gamePanel;
        this.rootPane = rootPane;
    }
    
    /**
     * Hiển thị dialog xác nhận đầu hàng
     */
    public void showSurrenderConfirmation() {
        // Nếu đã có dialog, xóa nó trước
        if (surrenderDialog != null && rootPane != null && rootPane.getChildren().contains(surrenderDialog)) {
            rootPane.getChildren().remove(surrenderDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        surrenderDialog = new StackPane();
        surrenderDialog.setLayoutX((1920 - 500) / 2);  // Căn giữa theo chiều ngang
        surrenderDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        surrenderDialog.setPrefSize(500, 300);
        
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
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");  // Giảm padding top từ 40 xuống 20 để dịch lên
        
        // Label câu hỏi
        Label questionLabel = new Label("Are you sure you want to surrender?");
        questionLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +  // Tăng từ 36px lên 48px
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        questionLabel.setAlignment(Pos.CENTER);
        questionLabel.setPrefWidth(420);
        questionLabel.setTranslateY(-20);  // Dịch lên 20px
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Yes
        StackPane yesButton = createDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Xử lý khi bấm Yes (surrender)
            hideSurrenderConfirmation();
            showGameResult(false);  // false = người chơi thua
            e.consume();
        });
        
        // Nút No
        StackPane noButton = createDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            // Đóng dialog khi bấm No
            hideSurrenderConfirmation();
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        contentContainer.getChildren().addAll(questionLabel, buttonsContainer);
        
        surrenderDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(surrenderDialog);
        
        // Fade in animation
        surrenderDialog.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), surrenderDialog);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    /**
     * Ẩn dialog xác nhận đầu hàng
     */
    public void hideSurrenderConfirmation() {
        if (surrenderDialog != null && rootPane != null && rootPane.getChildren().contains(surrenderDialog)) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), surrenderDialog);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(surrenderDialog)) {
                    rootPane.getChildren().remove(surrenderDialog);
                }
            });
            fadeOut.play();
        }
    }
    
    /**
     * Hiển thị dialog xác nhận đề nghị hòa
     */
    public void showDrawRequestConfirmation() {
        // Nếu đã có dialog, xóa nó trước
        if (drawRequestDialog != null && rootPane != null && rootPane.getChildren().contains(drawRequestDialog)) {
            rootPane.getChildren().remove(drawRequestDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        drawRequestDialog = new StackPane();
        drawRequestDialog.setLayoutX((1920 - 600) / 2);  // Căn giữa theo chiều ngang
        drawRequestDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        drawRequestDialog.setPrefSize(600, 300);
        
        // Background cho dialog
        Rectangle dialogBg = new Rectangle(600, 300);
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
        contentContainer.setPrefSize(600, 300);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");
        
        // Label câu hỏi
        Label questionLabel = new Label("Are you sure you want to request a draw?");
        questionLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        questionLabel.setAlignment(Pos.CENTER);
        questionLabel.setPrefWidth(520);
        questionLabel.setTranslateY(-20);
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Yes
        StackPane yesButton = createDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Gửi draw request đến đối phương
            hideDrawRequestConfirmation();
            // TODO: Gửi draw request qua network
            // Tạm thời hiển thị dialog cho đối phương (sẽ được thay bằng network call)
            showDrawRequestReceived();
            e.consume();
        });
        
        // Nút No
        StackPane noButton = createDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            hideDrawRequestConfirmation();
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        contentContainer.getChildren().addAll(questionLabel, buttonsContainer);
        
        drawRequestDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(drawRequestDialog);
        
        // Fade in animation
        drawRequestDialog.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), drawRequestDialog);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    /**
     * Ẩn dialog xác nhận đề nghị hòa
     */
    public void hideDrawRequestConfirmation() {
        if (drawRequestDialog != null && rootPane != null && rootPane.getChildren().contains(drawRequestDialog)) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), drawRequestDialog);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(drawRequestDialog)) {
                    rootPane.getChildren().remove(drawRequestDialog);
                }
            });
            fadeOut.play();
        }
    }
    
    /**
     * Hiển thị dialog nhận yêu cầu hòa từ đối phương
     */
    public void showDrawRequestReceived() {
        // Nếu đã có dialog, xóa nó trước
        if (drawReceivedDialog != null && rootPane != null && rootPane.getChildren().contains(drawReceivedDialog)) {
            rootPane.getChildren().remove(drawReceivedDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        drawReceivedDialog = new StackPane();
        drawReceivedDialog.setLayoutX((1920 - 600) / 2);  // Căn giữa theo chiều ngang
        drawReceivedDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        drawReceivedDialog.setPrefSize(600, 300);
        
        // Background cho dialog
        Rectangle dialogBg = new Rectangle(600, 300);
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
        contentContainer.setPrefSize(600, 300);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");
        
        // Label thông báo
        Label messageLabel = new Label("The opposing side wants to sue for peace");
        messageLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 48px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent; " +
            "-fx-wrap-text: true;"
        );
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setPrefWidth(520);
        messageLabel.setTranslateY(-20);
        
        // Container cho 2 nút
        HBox buttonsContainer = new HBox(30);
        buttonsContainer.setAlignment(Pos.CENTER);
        
        // Nút Yes (chấp nhận draw)
        StackPane yesButton = createDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Chấp nhận draw - kết thúc game hòa
            hideDrawRequestReceived();
            // TODO: Gửi accept draw qua network
            // Tạm thời hiển thị kết quả hòa
            showGameResultDraw();
            e.consume();
        });
        
        // Nút No (từ chối draw)
        StackPane noButton = createDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            hideDrawRequestReceived();
            // TODO: Gửi reject draw qua network
            e.consume();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        contentContainer.getChildren().addAll(messageLabel, buttonsContainer);
        
        drawReceivedDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm vào root pane
        rootPane.getChildren().add(drawReceivedDialog);
        
        // Fade in animation
        drawReceivedDialog.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), drawReceivedDialog);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }
    
    /**
     * Ẩn dialog nhận yêu cầu hòa
     */
    public void hideDrawRequestReceived() {
        if (drawReceivedDialog != null && rootPane != null && rootPane.getChildren().contains(drawReceivedDialog)) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), drawReceivedDialog);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(drawReceivedDialog)) {
                    rootPane.getChildren().remove(drawReceivedDialog);
                }
            });
            fadeOut.play();
        }
    }
    
    /**
     * Hiển thị kết quả game
     */
    public void showGameResult(boolean isWinner) {
        int eloDelta = isWinner ? eloChange : -eloChange;
        String resultText = isWinner ? "You win" : "You lose";
        showGameResultWithCustomText(resultText, eloDelta);
    }
    
    /**
     * Hiển thị kết quả game với text và elo delta tùy chỉnh
     */
    public void showGameResultWithCustomText(String resultText, int eloDelta) {
        // Nếu eloDelta = 0, không hiển thị elo change
        if (gameResultPanel != null && rootPane != null && rootPane.getChildren().contains(gameResultPanel)) {
            rootPane.getChildren().remove(gameResultPanel);
        }
        if (gameResultOverlay != null && rootPane != null && rootPane.getChildren().contains(gameResultOverlay)) {
            rootPane.getChildren().remove(gameResultOverlay);
        }
        
        // Cập nhật elo nếu có thay đổi (theo currentGameMode)
        if (eloDelta != 0) {
            state.addElo(eloDelta);  // addElo tự động dùng currentGameMode
        }
        
        // Tạo overlay để khóa mọi action
        gameResultOverlay = new StackPane();
        gameResultOverlay.setLayoutX(0);
        gameResultOverlay.setLayoutY(0);
        gameResultOverlay.setPrefSize(1920, 1080);
        gameResultOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");
        gameResultOverlay.setPickOnBounds(true);
        gameResultOverlay.setMouseTransparent(false);
        gameResultOverlay.setOnMouseClicked(e -> {
            state.closeGame();
            state.navigateToMainMenu();
            e.consume();
        });
        
        // Tạo result panel
        gameResultPanel = new StackPane();
        gameResultPanel.setLayoutX((1920 - 500) / 2);
        gameResultPanel.setLayoutY((1080 - 300) / 2);
        gameResultPanel.setPrefSize(500, 300);
        gameResultPanel.setPickOnBounds(true);
        gameResultPanel.setMouseTransparent(false);
        gameResultPanel.setOnMouseClicked(e -> {
            state.closeGame();
            state.navigateToMainMenu();
            e.consume();
        });
        
        // Background cho panel
        Rectangle panelBg = new Rectangle(500, 300);
        panelBg.setFill(Color.WHITE);
        panelBg.setStroke(Color.color(0.3, 0.3, 0.3));
        panelBg.setStrokeWidth(2);
        panelBg.setArcWidth(20);
        panelBg.setArcHeight(20);
        
        DropShadow panelShadow = new DropShadow();
        panelShadow.setColor(Color.color(0, 0, 0, 0.5));
        panelShadow.setRadius(20);
        panelShadow.setOffsetX(5);
        panelShadow.setOffsetY(5);
        panelBg.setEffect(panelShadow);
        
        // Container chính cho nội dung
        VBox contentContainer = new VBox(15);
        contentContainer.setPrefSize(500, 300);
        contentContainer.setAlignment(Pos.CENTER);
        contentContainer.setStyle("-fx-padding: 20 40 40 40;");
        contentContainer.setMouseTransparent(true);
        
        // Label kết quả
        Label resultLabel = new Label(resultText);
        // Xác định màu dựa vào resultText
        String textColor = resultText.equals("Draw") ? "#4CAF50" : 
                          (resultText.equals("You win") ? "#4CAF50" : "#A65252");
        resultLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 72px; " +
            "-fx-text-fill: " + textColor + "; " +
            "-fx-background-color: transparent;"
        );
        resultLabel.setAlignment(Pos.CENTER);
        resultLabel.setTranslateY(-30);
        resultLabel.setMouseTransparent(true);
        
        contentContainer.getChildren().add(resultLabel);
        
        // Chỉ thêm elo change label nếu có thay đổi
        if (eloDelta != 0) {
            String eloChangeText = (eloDelta > 0 ? "+" : "") + eloDelta;
            Label eloChangeLabel = new Label(eloChangeText);
            eloChangeLabel.setStyle(
                "-fx-font-family: 'Kolker Brush'; " +
                "-fx-font-size: 48px; " +
                "-fx-text-fill: " + textColor + "; " +  // Dùng cùng màu với result label
                "-fx-background-color: transparent;"
            );
            eloChangeLabel.setAlignment(Pos.CENTER);
            eloChangeLabel.setTranslateY(-30);
            eloChangeLabel.setMouseTransparent(true);
            contentContainer.getChildren().add(eloChangeLabel);
        }
        
        gameResultPanel.getChildren().addAll(panelBg, contentContainer);
        
        rootPane.getChildren().add(gameResultOverlay);
        rootPane.getChildren().add(gameResultPanel);
        
        gameResultOverlay.setOpacity(0);
        gameResultPanel.setOpacity(0);
        FadeTransition overlayFadeIn = new FadeTransition(Duration.millis(300), gameResultOverlay);
        overlayFadeIn.setToValue(1.0);
        FadeTransition panelFadeIn = new FadeTransition(Duration.millis(300), gameResultPanel);
        panelFadeIn.setToValue(1.0);
        overlayFadeIn.play();
        panelFadeIn.play();
    }
    
    /**
     * Hiển thị kết quả hòa
     */
    public void showGameResultDraw() {
        // Hiển thị kết quả hòa (không thay đổi elo)
        showGameResultWithCustomText("Draw", 0);  // 0 = không thay đổi elo
    }
    
    /**
     * Ẩn kết quả game
     */
    public void hideGameResult() {
        if (gameResultPanel != null && rootPane != null && rootPane.getChildren().contains(gameResultPanel)) {
            // Fade out animation
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), gameResultPanel);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                if (rootPane.getChildren().contains(gameResultPanel)) {
                    rootPane.getChildren().remove(gameResultPanel);
                }
                if (gameResultOverlay != null && rootPane.getChildren().contains(gameResultOverlay)) {
                    rootPane.getChildren().remove(gameResultOverlay);
                }
            });
            fadeOut.play();
        } else if (gameResultOverlay != null && rootPane != null && rootPane.getChildren().contains(gameResultOverlay)) {
            rootPane.getChildren().remove(gameResultOverlay);
        }
    }
    
    /**
     * Reset các dialog kết quả ngay lập tức (không cần animation)
     */
    public void resetGameResultPanels() {
        if (rootPane == null) {
            return; // rootPane chưa được khởi tạo
        }
        
        if (gameResultPanel != null) {
            if (rootPane.getChildren().contains(gameResultPanel)) {
                rootPane.getChildren().remove(gameResultPanel);
            }
            gameResultPanel = null;
        }
        if (gameResultOverlay != null) {
            if (rootPane.getChildren().contains(gameResultOverlay)) {
                rootPane.getChildren().remove(gameResultOverlay);
            }
            gameResultOverlay = null;
        }
    }
    
    /**
     * Reset tất cả các dialog
     */
    public void resetAllDialogs() {
        if (rootPane == null) {
            return; // rootPane chưa được khởi tạo
        }
        
        // Reset game result panels
        resetGameResultPanels();
        
        // Reset các dialog khác
        if (surrenderDialog != null) {
            if (rootPane.getChildren().contains(surrenderDialog)) {
                rootPane.getChildren().remove(surrenderDialog);
            }
            surrenderDialog = null;
        }
        if (drawRequestDialog != null) {
            if (rootPane.getChildren().contains(drawRequestDialog)) {
                rootPane.getChildren().remove(drawRequestDialog);
            }
            drawRequestDialog = null;
        }
        if (drawReceivedDialog != null) {
            if (rootPane.getChildren().contains(drawReceivedDialog)) {
                rootPane.getChildren().remove(drawReceivedDialog);
            }
            drawReceivedDialog = null;
        }
        
        // Reset elo change về giá trị mặc định
        eloChange = 10;
    }
    
    /**
     * Tạo nút dialog
     */
    private StackPane createDialogButton(String text, boolean isYes) {
        StackPane button = new StackPane();
        button.setPrefSize(150, 60);
        
        // Background cho nút
        Rectangle buttonBg = new Rectangle(150, 60);
        if (isYes) {
            buttonBg.setFill(Color.web("#A65252"));  // Màu đỏ cho nút Yes
        } else {
            buttonBg.setFill(Color.color(0.7, 0.7, 0.7));  // Màu xám cho nút No
        }
        buttonBg.setArcWidth(15);
        buttonBg.setArcHeight(15);
        
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
            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), button);
            scaleIn.setToX(1.05);
            scaleIn.setToY(1.05);
            scaleIn.play();
        });
        button.setOnMouseExited(e -> {
            ScaleTransition scaleOut = new ScaleTransition(Duration.millis(200), button);
            scaleOut.setToX(1.0);
            scaleOut.setToY(1.0);
            scaleOut.play();
        });
        
        return button;
    }
    
    /**
     * Set điểm thay đổi ELO
     */
    public void setEloChange(int change) {
        this.eloChange = change;
    }
    
    /**
     * Get điểm thay đổi ELO
     */
    public int getEloChange() {
        return eloChange;
    }
}


