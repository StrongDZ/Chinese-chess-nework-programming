package application.components;

import application.network.NetworkManager;
import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
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
import java.io.IOException;

/**
 * Profile panel that displays user profile information.
 */
public class ProfilePanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private final NetworkManager networkManager = NetworkManager.getInstance();
    private final javafx.beans.property.SimpleStringProperty selectedTimeControl = new javafx.beans.property.SimpleStringProperty("classical");  // Default to classical
    
    // Lưu toàn bộ lịch sử (tất cả modes) để filter ở frontend
    private final java.util.List<application.components.HistoryPanel.HistoryEntry> allHistory = new java.util.ArrayList<>();
    
    // Lưu callback cũ (HistoryPanel) để có thể gọi khi filter lại
    private java.util.function.BiConsumer<
        java.util.List<application.components.HistoryPanel.HistoryEntry>,
        java.util.List<application.components.HistoryPanel.HistoryEntry>> historyPanelCallback;

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
                // Fetch all modes when opening profile
                fetchAllModes();
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        // Also listen to appState changes
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.PROFILE && state.isProfileVisible()) {
                // Fetch all modes when opening profile
                fetchAllModes();
                // Fetch match history when opening profile
                fetchMatchHistory();
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });
        
        // Also fetch match history when profile becomes visible
        state.profileVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.PROFILE) {
                fetchMatchHistory();
            }
        });
        
        // Lắng nghe callback để nhận lịch sử và lưu vào allHistory
        // Lưu ý: HistoryPanel cũng set callback này, nên cần lưu callback cũ trước
        this.historyPanelCallback = state.getGameHistoryUpdateCallback();
        
        state.setGameHistoryUpdateCallback((peopleHistory, aiHistory) -> {
            // Lưu tất cả lịch sử (people + AI) vào allHistory
            allHistory.clear();
            allHistory.addAll(peopleHistory);
            allHistory.addAll(aiHistory);
            System.out.println("[ProfilePanel] Received game history - total: " + allHistory.size() + 
                " (people: " + peopleHistory.size() + ", AI: " + aiHistory.size() + ")");
            
            // Filter data theo mode hiện tại và update HistoryPanel
            // Không gọi filterAndUpdateHistory() để tránh vòng lặp, mà filter trực tiếp và gọi callback cũ
            updateHistoryPanelWithFilteredData(historyPanelCallback);
        });
        
        // Lắng nghe thay đổi selectedTimeControl để filter lại lịch sử
        selectedTimeControl.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                System.out.println("[ProfilePanel] Time control changed to: " + newVal);
                filterAndUpdateHistory();
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
        avatarPlaceholder.setStroke(Color.web("#A65252"));  // red border
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
        
        Label eloLabel = new Label("elo 0"); // Set initial text để đảm bảo hiển thị
        eloLabel.textProperty().bind(
            javafx.beans.binding.Bindings.createStringBinding(
                () -> {
                    // Lấy elo theo selectedTimeControl
                    String mode = selectedTimeControl.get();
                    int eloValue = state.getElo(mode);
                    System.out.println("[ProfilePanel] Elo display - mode=" + mode + ", eloValue=" + eloValue + ", classical=" + state.getClassicalElo() + ", blitz=" + state.getBlitzElo());
                    return "elo " + eloValue;
                },
                selectedTimeControl,
                state.classicalEloProperty(),
                state.blitzEloProperty()
            )
        );
        eloLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 70px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        eloLabel.setVisible(true);
        eloLabel.setManaged(true);
        eloLabel.setMinHeight(80); // Đảm bảo có chiều cao để hiển thị
        eloLabel.setPrefHeight(80);
        
        userInfo.getChildren().addAll(usernameLabel);
        
        // ELO label và Time control buttons - cùng dòng, ELO bên trái, buttons bên phải
        HBox eloAndButtonsRow = new HBox(20);
        eloAndButtonsRow.setLayoutX(300);
        eloAndButtonsRow.setLayoutY(210); // Dưới username (130 + 70 + 10 = 210)
        eloAndButtonsRow.setAlignment(Pos.CENTER_LEFT);
        eloAndButtonsRow.setPrefWidth(900); // Đủ rộng để chứa ELO và buttons
        
        // ELO label - bên trái
        eloLabel.setLayoutX(0);
        eloLabel.setLayoutY(0);
        
        // Spacer để đẩy buttons sang phải
        javafx.scene.layout.Region eloSpacer = new javafx.scene.layout.Region();
        HBox.setHgrow(eloSpacer, javafx.scene.layout.Priority.ALWAYS);
        
        // Time control buttons (Classical, Blitz) - bên phải, dịch sang trái một chút
        HBox timeControlButtons = createTimeControlButtons();
        timeControlButtons.setLayoutX(0);
        timeControlButtons.setLayoutY(0);
        timeControlButtons.setTranslateX(-50); // Dịch sang trái 50px
        
        eloAndButtonsRow.getChildren().addAll(eloLabel, eloSpacer, timeControlButtons);
        
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
                () -> {
                    // Lấy total matches theo selectedTimeControl
                    String mode = selectedTimeControl.get();
                    int totalMatches = state.getTotalMatches(mode);
                    System.out.println("[ProfilePanel] TotalMatches display - mode=" + mode + ", totalMatches=" + totalMatches);
                    return "Total match: " + totalMatches;
                },
                selectedTimeControl,
                state.classicalTotalMatchesProperty(),
                state.blitzTotalMatchesProperty()
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
                () -> {
                    // Lấy win matches theo selectedTimeControl
                    String mode = selectedTimeControl.get();
                    int winMatches = state.getWinMatches(mode);
                    System.out.println("[ProfilePanel] WinMatches display - mode=" + mode + ", winMatches=" + winMatches);
                    return "Win matches: " + winMatches;
                },
                selectedTimeControl,
                state.classicalWinMatchesProperty(),
                state.blitzWinMatchesProperty()
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
                    // Lấy winrate theo selectedTimeControl
                    String mode = selectedTimeControl.get();
                    double rate = state.getWinRate(mode);
                    System.out.println("[ProfilePanel] WinRate display - mode=" + mode + ", winRate=" + rate + ", classical=" + state.getClassicalWinRate() + ", blitz=" + state.getBlitzWinRate());
                    return String.format("Win rates: %.1f%%", rate);
                },
                selectedTimeControl,
                state.classicalWinRateProperty(),
                state.blitzWinRateProperty()
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
        
        contentPane.getChildren().addAll(header, avatarContainer, userInfo, eloAndButtonsRow, statisticsContainer);
        mainPanel.getChildren().addAll(bg, contentPane);
        
        return mainPanel;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
    
    private StackPane classicalButton;
    private StackPane blitzButton;
    
    /**
     * Create time control buttons (Classical, Blitz).
     */
    private HBox createTimeControlButtons() {
        HBox container = new HBox(15);
        container.setAlignment(Pos.CENTER_LEFT);
        
        // Classical button
        this.classicalButton = createTimeControlButton("Classical", "classical");
        this.classicalButton.setOnMouseClicked(e -> {
            selectedTimeControl.set("classical");
            updateButtonStyles(this.classicalButton, true);
            updateButtonStyles(this.blitzButton, false);
            // Chỉ filter data đã có ở frontend, không request lại từ backend
            filterAndUpdateHistory();
        });
        
        // Blitz button
        this.blitzButton = createTimeControlButton("Blitz", "blitz");
        this.blitzButton.setOnMouseClicked(e -> {
            selectedTimeControl.set("blitz");
            updateButtonStyles(this.classicalButton, false);
            updateButtonStyles(this.blitzButton, true);
            // Chỉ filter data đã có ở frontend, không request lại từ backend
            filterAndUpdateHistory();
        });
        
        // Set initial style (classical selected by default)
        updateButtonStyles(this.classicalButton, true);
        updateButtonStyles(this.blitzButton, false);
        
        container.getChildren().addAll(this.classicalButton, this.blitzButton);
        return container;
    }
    
    /**
     * Create a time control button.
     */
    private StackPane createTimeControlButton(String text, String timeControl) {
        StackPane button = new StackPane();
        button.setPrefSize(200, 60);
        
        Rectangle bg = new Rectangle(200, 60);
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        bg.setFill(Color.color(0.7, 0.7, 0.7));  // Default grey
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(2);
        
        Label label = new Label(text);
        label.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 40px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        button.getChildren().addAll(bg, label);
        button.setCursor(Cursor.HAND);
        
        // Store background reference for style updates
        button.setUserData(bg);
        
        return button;
    }
    
    /**
     * Update button style based on selection state.
     */
    private void updateButtonStyles(StackPane button, boolean isSelected) {
        Rectangle bg = (Rectangle) button.getUserData();
        if (bg != null) {
            if (isSelected) {
                bg.setFill(Color.web("#A65252"));  // red when selected
                bg.setStroke(Color.web("#8B3A3A"));
            } else {
                bg.setFill(Color.color(0.7, 0.7, 0.7));  // Grey when not selected
                bg.setStroke(Color.color(0.3, 0.3, 0.3));
            }
        }
    }
    
    /**
     * Fetch all modes when opening profile.
     */
    private void fetchAllModes() {
        try {
            if (networkManager.isConnected()) {
                String username = state.getUsername();
                System.out.println("[ProfilePanel] fetchAllModes - username=" + username);
                if (username != null && !username.isEmpty()) {
                    // Fetch all modes at once to get both classical and blitz elo
                    networkManager.info().requestUserStats(username, "all");
                    System.out.println("[ProfilePanel] Sent requestUserStats with time_control=all");
                } else {
                    System.err.println("[ProfilePanel] Username is null or empty");
                }
            } else {
                System.err.println("[ProfilePanel] NetworkManager is not connected");
            }
        } catch (IOException e) {
            System.err.println("[ProfilePanel] Failed to fetch all modes: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Fetch user profile data from backend.
     * DEPRECATED: Không còn dùng nữa. Tất cả data được lấy một lần khi mở profile với time_control="all".
     * Khi chọn mode, chỉ filter data đã có ở frontend.
     */
    @Deprecated
    private void fetchProfile() {
        // Method này không còn được dùng nữa
        // Tất cả data được lấy một lần khi mở profile với fetchAllModes()
    }
    
    /**
     * Fetch match history from backend.
     * This will request game history and display it in HistoryPanel.
     */
    public void fetchMatchHistory() {
        try {
            if (networkManager.isConnected()) {
                String username = state.getUsername();
                System.out.println("[ProfilePanel] fetchMatchHistory - username=" + username);
                if (username != null && !username.isEmpty()) {
                    // Request game history from backend (limit 50 games, tất cả modes)
                    // InfoHandler will update ProfilePanel via UIState callback
                    networkManager.info().requestGameHistory(50);
                    System.out.println("[ProfilePanel] Sent requestGameHistory request (all modes)");
                } else {
                    System.err.println("[ProfilePanel] Username is null or empty");
                }
            } else {
                System.err.println("[ProfilePanel] NetworkManager is not connected");
            }
        } catch (IOException e) {
            System.err.println("[ProfilePanel] Failed to fetch match history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Filter lịch sử theo mode hiện tại và update HistoryPanel.
     */
    private void filterAndUpdateHistory() {
        if (allHistory.isEmpty()) {
            System.out.println("[ProfilePanel] No history to filter");
            return;
        }
        
        // Gọi method để filter và update HistoryPanel (sử dụng callback cũ để tránh vòng lặp)
        updateHistoryPanelWithFilteredData(historyPanelCallback);
    }
    
    /**
     * Filter data theo mode hiện tại và update HistoryPanel thông qua callback.
     * @param callback Callback để update HistoryPanel (có thể null)
     */
    private void updateHistoryPanelWithFilteredData(
            java.util.function.BiConsumer<
                java.util.List<application.components.HistoryPanel.HistoryEntry>,
                java.util.List<application.components.HistoryPanel.HistoryEntry>> callback) {
        
        if (allHistory.isEmpty()) {
            System.out.println("[ProfilePanel] No history to filter");
            if (callback != null) {
                callback.accept(new java.util.ArrayList<>(), new java.util.ArrayList<>());
            }
            return;
        }
        
        String mode = selectedTimeControl.get();
        System.out.println("[ProfilePanel] Filtering history by mode: " + mode);
        
        // Filter history theo mode
        java.util.List<application.components.HistoryPanel.HistoryEntry> filteredPeopleHistory = new java.util.ArrayList<>();
        java.util.List<application.components.HistoryPanel.HistoryEntry> filteredAiHistory = new java.util.ArrayList<>();
        
        for (application.components.HistoryPanel.HistoryEntry entry : allHistory) {
            String entryMode = entry.getMode();
            boolean matchesMode = false;
            
            // Kiểm tra mode: "Classic Mode" -> "classical", "Blitz Mode" -> "blitz"
            if ("classical".equalsIgnoreCase(mode)) {
                matchesMode = entryMode.contains("Classic") || entryMode.contains("Classical");
            } else if ("blitz".equalsIgnoreCase(mode)) {
                matchesMode = entryMode.contains("Blitz");
            }
            
            if (matchesMode) {
                // Phân biệt people vs AI dựa vào opponent
                String opponent = entry.getOpponent();
                boolean isAI = opponent == null || opponent.isEmpty() || 
                              opponent.toLowerCase().contains("ai") ||
                              opponent.toLowerCase().startsWith("ai");
                
                if (isAI) {
                    filteredAiHistory.add(entry);
                } else {
                    filteredPeopleHistory.add(entry);
                }
            }
        }
        
        System.out.println("[ProfilePanel] Filtered history - people: " + filteredPeopleHistory.size() + 
            ", AI: " + filteredAiHistory.size());
        
        // Update HistoryPanel thông qua callback (nếu có)
        if (callback != null) {
            callback.accept(filteredPeopleHistory, filteredAiHistory);
        }
    }
}
