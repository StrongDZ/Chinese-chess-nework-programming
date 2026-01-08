package application.components;

import application.network.NetworkManager;
import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;
import javafx.scene.Cursor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * History panel that displays game history.
 */
public class HistoryPanel extends StackPane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private final NetworkManager networkManager = NetworkManager.getInstance();
    private boolean isPeopleTabSelected = true;  // Default to "Play with people" tab
    private Label peopleTab;  // Store references to tabs
    private Label aiTab;
    
    // History data storage
    private final List<HistoryEntry> peopleHistory = new ArrayList<>();
    private final List<HistoryEntry> aiHistory = new ArrayList<>();
    private VBox tableContent;
    private ScrollPane scrollPane;
    private Pane container;  // Reference to container for adding dialogs
    private StackPane replayDialog;  // Replay confirmation dialog

    /**
     * Represents a single game history entry.
     */
    public static class HistoryEntry {
        private final String opponent;  // e.g., "username (elo...)"
        private final String result;     // "Win", "Lose", "Draw"
        private final String mode;       // "Classic Mode", "Blitz Mode", etc.
        private final String date;       // "yy/mm/dd"
        private final String gameId;     // Game ID for replay
        
        public HistoryEntry(String opponent, String result, String mode, String date, String gameId) {
            this.opponent = opponent;
            this.result = result;
            this.mode = mode;
            this.date = date;
            this.gameId = gameId;
        }
        
        public String getOpponent() { return opponent; }
        public String getResult() { return result; }
        public String getMode() { return mode; }
        public String getDate() { return date; }
        public String getGameId() { return gameId; }
    }

    public HistoryPanel(UIState state) {
        this.state = state;
        
        setPrefSize(1920, 1080);
        setLayoutX(0);
        setLayoutY(0);
        setStyle("-fx-background-color: transparent;");
        
        this.container = new Pane();
        this.container.setPrefSize(1920, 1080);
        this.container.setStyle("-fx-background-color: transparent;");
        
        // Create history content
        StackPane historyContent = createHistoryContent();
        historyContent.setLayoutX((1920 - 1200) / 2);  // Center horizontally, width 1200
        historyContent.setLayoutY((1080 - 800) / 2);    // Center vertically, height 800
        
        this.container.getChildren().add(historyContent);
        getChildren().add(this.container);
        
        // Bind visibility with MAIN_MENU and historyVisible
        visibleProperty().bind(
            state.appStateProperty().isEqualTo(UIState.AppState.MAIN_MENU)
                .and(state.historyVisibleProperty())
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        
        // Fade animation when historyVisible changes
        state.historyVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                fadeTo(1);
                // Fetch match history when panel opens
                fetchMatchHistory();
            } else {
                fadeTo(0);
            }
        });
        
        // Also listen to appState changes
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == UIState.AppState.MAIN_MENU && state.isHistoryVisible()) {
                fadeTo(1);
                // Fetch match history when panel opens
                fetchMatchHistory();
            } else {
                fadeTo(0);
            }
        });
        
        // Register callback for game history updates from InfoHandler
        state.setGameHistoryUpdateCallback(this::updateGameHistory);
    }
    
    /**
     * Callback method to update game history from InfoHandler.
     * @param peopleHistory List of history entries for "Play with people" tab
     * @param aiHistory List of history entries for "Play with AI" tab
     */
    private void updateGameHistory(List<HistoryEntry> peopleHistory, List<HistoryEntry> aiHistory) {
        updatePeopleHistory(peopleHistory);
        updateAIHistory(aiHistory);
    }
    
    private StackPane createHistoryContent() {
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
        
        // Header with back button and "History" title
        HBox header = new HBox(20);
        header.setLayoutX(30);
        header.setLayoutY(30);
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Back button
        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(100);  // Tăng từ 40 lên 60
        backIcon.setFitHeight(100);  // Tăng từ 40 lên 60
        backIcon.setPreserveRatio(true);
        backIcon.setSmooth(true);
        
        StackPane backButton = new StackPane(backIcon);
        backButton.setCursor(Cursor.HAND);
        backButton.setOnMouseClicked(e -> {
            state.closeHistory();
        });
        backButton.setOnMouseEntered(e -> {
            backIcon.setOpacity(0.7);
        });
        backButton.setOnMouseExited(e -> {
            backIcon.setOpacity(1.0);
        });
        
        // "History" title
        Label historyTitle = new Label("History");
        historyTitle.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +  // Giảm từ 90px xuống 60px
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        header.getChildren().addAll(backButton, historyTitle);
        
        // Tabs: "Play with people" and "Play with AI"
        HBox tabsContainer = new HBox(0);
        tabsContainer.setLayoutX(30);
        tabsContainer.setLayoutY(110);  // Giảm từ 140 xuống 110 để lên trên
        tabsContainer.setAlignment(Pos.CENTER_LEFT);
        
        // "Play with people" tab
        peopleTab = createTab("Play with people", true);
        peopleTab.setOnMouseClicked(e -> {
            if (!isPeopleTabSelected) {
                isPeopleTabSelected = true;
                updateTabs();  // This will call refreshTable()
            }
        });
        
        // "Play with AI" tab
        aiTab = createTab("Play with AI", false);
        aiTab.setOnMouseClicked(e -> {
            if (isPeopleTabSelected) {
                isPeopleTabSelected = false;
                updateTabs();  // This will call refreshTable()
            }
        });
        
        tabsContainer.getChildren().addAll(peopleTab, aiTab);
        
        // Table header
        HBox tableHeader = new HBox();
        tableHeader.setLayoutX(30);
        tableHeader.setLayoutY(200);
        tableHeader.setPrefWidth(1140);
        tableHeader.setPrefHeight(50);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        tableHeader.setPadding(new Insets(0, 20, 0, 20));
        tableHeader.setStyle("-fx-background-color: transparent;");
        
        // Column headers - add Opponent column first to match rows
        Label opponentHeader = createTableHeader("Opponent", 400);
        Label resultHeader = createTableHeader("Result", 200);
        Label modeHeader = createTableHeader("Mode", 250);
        Label dateHeader = createTableHeader("Date", 200);
        Label infoHeader = createTableHeader("Replay", 100);
        
        tableHeader.getChildren().addAll(opponentHeader, resultHeader, modeHeader, dateHeader, infoHeader);
        
        // Table content area with scroll
        scrollPane = new ScrollPane();
        scrollPane.setLayoutX(30);
        scrollPane.setLayoutY(250);
        scrollPane.setPrefWidth(1140);
        scrollPane.setPrefHeight(520);
        // Hide scroll bars but keep scroll functionality
        scrollPane.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-background: transparent; " +
            "-fx-scroll-bar-visible: false; " +
            "-fx-hbar-policy: never; " +
            "-fx-vbar-policy: never;"
        );
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);  // Hide vertical scrollbar but keep scroll
        scrollPane.setFitToWidth(true);  // Fit content to width, prevent horizontal scroll
        scrollPane.setPannable(false);   // Disable panning (drag scrolling)
        
        tableContent = new VBox(0);
        tableContent.setPrefWidth(1140);
        tableContent.setMaxWidth(1140);  // Prevent content from exceeding width
        tableContent.setStyle("-fx-background-color: transparent;");
        
        // Initialize with empty history (will be updated later)
        refreshTable();
        
        scrollPane.setContent(tableContent);
        
        contentPane.getChildren().addAll(header, tabsContainer, tableHeader, scrollPane);
        mainPanel.getChildren().addAll(bg, contentPane);
        
        return mainPanel;
    }
    
    private Label createTab(String text, boolean isSelected) {
        Label tab = new Label(text);
        tab.setPrefSize(300, 50);
        tab.setAlignment(Pos.CENTER);
        tab.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 65px; " +  // Tăng từ 50px lên 65px
            "-fx-text-fill: " + (isSelected ? "#A65252" : "black") + "; " +
            "-fx-background-color: transparent; " +
            "-fx-underline: " + isSelected + ";"
        );
        tab.setCursor(Cursor.HAND);
        return tab;
    }
    
    private void updateTabs() {
        // Update tab styles based on selection
        peopleTab.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 65px; " +  // Tăng từ 50px lên 65px
            "-fx-text-fill: " + (isPeopleTabSelected ? "#A65252" : "black") + "; " +
            "-fx-background-color: transparent; " +
            "-fx-underline: " + isPeopleTabSelected + ";"
        );
        
        aiTab.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 65px; " +  // Tăng từ 50px lên 65px
            "-fx-text-fill: " + (isPeopleTabSelected ? "black" : "#A65252") + "; " +
            "-fx-background-color: transparent; " +
            "-fx-underline: " + (!isPeopleTabSelected) + ";"
        );
        
        // Refresh table when switching tabs
        refreshTable();
    }
    
    private Label createTableHeader(String text, double width) {
        Label header = new Label(text);
        header.setPrefWidth(width);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 40px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        return header;
    }
    
    private HBox createHistoryRow(String opponent, String result, String mode, String date) {
        HBox row = new HBox();
        row.setPrefWidth(1140);
        row.setMaxWidth(1140);  // Prevent row from exceeding width
        row.setPrefHeight(80);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 20, 0, 20));
        row.setStyle("-fx-background-color: transparent;");
        
        // Opponent column (first column, not in header but in rows)
        Label opponentLabel = new Label(opponent);
        opponentLabel.setPrefWidth(400);
        opponentLabel.setAlignment(Pos.CENTER_LEFT);
        opponentLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 35px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Result column
        Label resultLabel = new Label(result);
        resultLabel.setPrefWidth(200);
        resultLabel.setAlignment(Pos.CENTER_LEFT);
        resultLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 35px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Mode column
        Label modeLabel = new Label(mode);
        modeLabel.setPrefWidth(250);
        modeLabel.setAlignment(Pos.CENTER_LEFT);
        modeLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 35px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Date column
        Label dateLabel = new Label(date);
        dateLabel.setPrefWidth(200);
        dateLabel.setAlignment(Pos.CENTER_LEFT);
        dateLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 35px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Info icon column
        ImageView infoIcon = new ImageView(AssetHelper.image("ic_info.png"));
        infoIcon.setFitWidth(30);
        infoIcon.setFitHeight(30);
        infoIcon.setPreserveRatio(true);
        infoIcon.setSmooth(true);
        
        StackPane infoButton = new StackPane(infoIcon);
        infoButton.setPrefWidth(100);
        infoButton.setAlignment(Pos.CENTER);
        infoButton.setCursor(Cursor.HAND);
        infoButton.setOnMouseEntered(e -> {
            infoIcon.setOpacity(0.7);
        });
        infoButton.setOnMouseExited(e -> {
            infoIcon.setOpacity(1.0);
        });
        // Click handler will be added in refreshTable() after row is created
        
        row.getChildren().addAll(opponentLabel, resultLabel, modeLabel, dateLabel, infoButton);
        
        return row;
    }
    
    /**
     * Updates the history entries for "Play with people" tab.
     * @param entries List of history entries
     */
    public void updatePeopleHistory(List<HistoryEntry> entries) {
        peopleHistory.clear();
        peopleHistory.addAll(entries);
        if (isPeopleTabSelected) {
            refreshTable();
        }
    }
    
    /**
     * Updates the history entries for "Play with AI" tab.
     * @param entries List of history entries
     */
    public void updateAIHistory(List<HistoryEntry> entries) {
        aiHistory.clear();
        aiHistory.addAll(entries);
        if (!isPeopleTabSelected) {
            refreshTable();
        }
    }
    
    /**
     * Refreshes the table display based on current tab selection.
     */
    private void refreshTable() {
        if (tableContent == null) {
            System.err.println("[HistoryPanel] refreshTable: tableContent is null!");
            return;
        }
        
        System.out.println("[HistoryPanel] refreshTable called - isPeopleTabSelected: " + isPeopleTabSelected);
        System.out.println("[HistoryPanel] peopleHistory size: " + peopleHistory.size());
        System.out.println("[HistoryPanel] aiHistory size: " + aiHistory.size());
        
        tableContent.getChildren().clear();
        
        List<HistoryEntry> currentHistory = isPeopleTabSelected ? peopleHistory : aiHistory;
        
        System.out.println("[HistoryPanel] currentHistory size: " + currentHistory.size());
        
        if (currentHistory.isEmpty()) {
            System.out.println("[HistoryPanel] No history entries to display");
            // Show empty message or keep empty
            return;
        }
        
        System.out.println("[HistoryPanel] Adding " + currentHistory.size() + " rows to table");
        for (HistoryEntry entry : currentHistory) {
            HBox row = createHistoryRow(
                entry.getOpponent(),
                entry.getResult(),
                entry.getMode(),
                entry.getDate()
            );
            // Store entry in row's userData for click handler
            row.setUserData(entry);
            
            // Add click handler to info button (last child in row)
            if (row.getChildren().size() > 0) {
                javafx.scene.Node lastChild = row.getChildren().get(row.getChildren().size() - 1);
                if (lastChild instanceof StackPane) {
                    StackPane infoButton = (StackPane) lastChild;
                    infoButton.setOnMouseClicked(e -> {
                        showReplayConfirmation(entry.getGameId());
                    });
                }
            }
            
            tableContent.getChildren().add(row);
        }
        System.out.println("[HistoryPanel] ✓ Table refreshed with " + tableContent.getChildren().size() + " rows");
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
    
    /**
     * Fetch match history from backend.
     * This will request game history and display it in HistoryPanel.
     */
    private void fetchMatchHistory() {
        try {
            if (networkManager.isConnected()) {
                String username = state.getUsername();
                System.out.println("[HistoryPanel] ===== fetchMatchHistory called =====");
                System.out.println("[HistoryPanel] username=" + username);
                System.out.println("[HistoryPanel] networkManager.isConnected()=" + networkManager.isConnected());
                if (username != null && !username.isEmpty()) {
                    // Request game history from backend (limit 50 games)
                    // InfoHandler will update HistoryPanel via UIState callback
                    networkManager.info().requestGameHistory(50);
                    System.out.println("[HistoryPanel] ✓ Sent requestGameHistory request");
                } else {
                    System.err.println("[HistoryPanel] ✗ Username is null or empty");
                }
            } else {
                System.err.println("[HistoryPanel] ✗ NetworkManager is not connected");
            }
        } catch (IOException e) {
            System.err.println("[HistoryPanel] ✗ Failed to fetch match history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Hiển thị dialog xác nhận replay
     */
    private void showReplayConfirmation(String gameId) {
        // Nếu đã có dialog, xóa nó trước
        if (replayDialog != null && container != null && container.getChildren().contains(replayDialog)) {
            container.getChildren().remove(replayDialog);
        }
        
        // Tạo dialog panel ở giữa màn hình
        replayDialog = new StackPane();
        replayDialog.setLayoutX((1920 - 500) / 2);  // Căn giữa theo chiều ngang
        replayDialog.setLayoutY((1080 - 300) / 2);  // Căn giữa theo chiều dọc
        replayDialog.setPrefSize(500, 300);
        
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
        Label questionLabel = new Label("Do you want to watch the replay?");
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
        StackPane yesButton = createReplayDialogButton("Yes", true);
        yesButton.setOnMouseClicked(e -> {
            // Xử lý khi bấm Yes (replay)
            hideReplayConfirmation();
            // Set gameId và mở ReplayPanel
            state.setReplayGameId(gameId);
            state.openReplay();
            System.out.println("[HistoryPanel] Opening replay for game: " + gameId);
        });
        
        // Nút No
        StackPane noButton = createReplayDialogButton("No", false);
        noButton.setOnMouseClicked(e -> {
            // Đóng dialog khi bấm No
            hideReplayConfirmation();
        });
        
        buttonsContainer.getChildren().addAll(yesButton, noButton);
        
        contentContainer.getChildren().addAll(questionLabel, buttonsContainer);
        
        replayDialog.getChildren().addAll(dialogBg, contentContainer);
        
        // Thêm dialog vào container
        if (container != null) {
            container.getChildren().add(replayDialog);
        }
    }
    
    /**
     * Ẩn dialog xác nhận replay
     */
    private void hideReplayConfirmation() {
        if (replayDialog != null && container != null && container.getChildren().contains(replayDialog)) {
            container.getChildren().remove(replayDialog);
            replayDialog = null;
        }
    }
    
    /**
     * Tạo nút cho dialog replay
     */
    private StackPane createReplayDialogButton(String text, boolean isYes) {
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
}
