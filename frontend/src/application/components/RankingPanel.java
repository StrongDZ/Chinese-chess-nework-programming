package application.components;

import application.network.NetworkManager;
import application.state.UIState;
import application.util.AssetHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.Cursor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RankingPanel extends StackPane {

    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private final NetworkManager networkManager = NetworkManager.getInstance();

    private String currentMode = "classical";
    private VBox rankingListContainer;
    private Label myEloLabel;

    // Kích thước Panel tổng
    private static final double PANEL_WIDTH = 1500;
    private static final double PANEL_HEIGHT = 900;

    private static final Color RED_ACCENT = Color.web("#A61E1E");

    // Độ rộng các cột
    private static final double COL_AVATAR_WIDTH = 90;
    private static final double COL_NAME_WIDTH = 380;
    private static final double COL_RANK_WIDTH = 150;
    private static final double COL_ELO_WIDTH = 180;

    private Label classicTab;
    private Label blitzTab;

    // Lưu toàn bộ data từ backend (cả classical và blitz)
    private List<UserStatData> allUsersStats = new ArrayList<>();
    // Data đã filter theo mode hiện tại
    private List<LeaderboardEntry> leaderboardData = new ArrayList<>();
    
    // Helper class để lưu user stat data từ backend
    private static class UserStatData {
        String username;
        String timeControl; // "classical" or "blitz"
        int rating;
        
        UserStatData(String username, String timeControl, int rating) {
            this.username = username;
            this.timeControl = timeControl;
            this.rating = rating;
        }
    }

    public RankingPanel(UIState state) {
        this.state = state;

        setPrefSize(1920, 1080);
        setStyle("-fx-background-color: transparent;");

        Pane container = new Pane();
        container.setPrefSize(1920, 1080);

        StackPane rankingContent = createRankingContent();
        // Căn giữa panel
        rankingContent.setLayoutX((1920 - PANEL_WIDTH) / 2);
        rankingContent.setLayoutY((1080 - PANEL_HEIGHT) / 2);

        container.getChildren().add(rankingContent);
        getChildren().add(container);

        visibleProperty().bind(state.rankingVisibleProperty());
        managedProperty().bind(visibleProperty());
        setOpacity(0);

        state.rankingVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                // Chỉ fetch 1 lần khi mở panel (lấy toàn bộ data)
                if (allUsersStats.isEmpty()) {
                    fetchAllUsersStats();
                } else {
                    // Nếu đã có data, chỉ filter và hiển thị lại
                    filterAndDisplay(currentMode);
                }
                updateTabsStyle();
                fadeTo(1);
            } else {
                fadeTo(0);
            }
        });

        registerLeaderboardCallback();
    }

    private StackPane createRankingContent() {
        Rectangle bg = new Rectangle(PANEL_WIDTH, PANEL_HEIGHT);
        bg.setFill(Color.web("#E8E8E8", 0.95));
        bg.setArcWidth(40);
        bg.setArcHeight(40);
        bg.setStroke(Color.TRANSPARENT);

        Pane contentPane = new Pane();
        contentPane.setPrefSize(PANEL_WIDTH, PANEL_HEIGHT);

        // --- HEADER ---
        HBox headerBox = new HBox(30);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setLayoutX(60);
        // Đẩy toàn bộ header lên cao hơn một chút (30 -> 20)
        headerBox.setLayoutY(20); 

        ImageView backIcon = new ImageView(AssetHelper.image("ic_back.png"));
        backIcon.setFitWidth(100);
        backIcon.setFitHeight(100);
        backIcon.setCursor(Cursor.HAND);
        backIcon.setOnMouseClicked(e -> state.closeRanking());

        Label titleLabel = new Label("Ranking");
        titleLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 120px; -fx-text-fill: black;");
        titleLabel.setPadding(Insets.EMPTY);
        
        // --- CHỈNH SỬA: Dịch chữ Ranking lên trên ---
        // Sử dụng TranslateY để đẩy chữ lên cao hơn so với trục giữa của HBox
        titleLabel.setTranslateY(-20); 

        headerBox.getChildren().addAll(backIcon, titleLabel);

        // --- CÁC PHẦN DƯỚI ---
        // 2. Mode Tabs
        HBox modeTabs = createModeTabs();
        modeTabs.setLayoutX(100);
        modeTabs.setLayoutY(180);

        // 3. Table Headers
        HBox tableHeaders = createTableHeaders();
        tableHeaders.setLayoutX(100);
        tableHeaders.setLayoutY(270);

        // 4. Ranking List (Container)
        StackPane listArea = createRankingListArea();
        listArea.setLayoutX(100);
        listArea.setLayoutY(340);

        // 5. My Profile Section
        VBox myProfile = createMyProfileSection();
        double listTotalWidth = COL_AVATAR_WIDTH + COL_NAME_WIDTH + COL_RANK_WIDTH + COL_ELO_WIDTH + 20;
        myProfile.setLayoutX(100 + listTotalWidth + 50);
        myProfile.setLayoutY(300);

        contentPane.getChildren().addAll(headerBox, modeTabs, tableHeaders, listArea, myProfile);

        return new StackPane(bg, contentPane);
    }

    private HBox createModeTabs() {
        HBox tabs = new HBox(70);
        tabs.setAlignment(Pos.CENTER_LEFT);

        classicTab = new Label("Classic Mode");
        classicTab.setCursor(Cursor.HAND);
        classicTab.setOnMouseClicked(e -> switchMode("classical"));

        blitzTab = new Label("Blitz Mode");
        blitzTab.setCursor(Cursor.HAND);
        blitzTab.setOnMouseClicked(e -> switchMode("blitz"));

        updateTabsStyle();

        tabs.getChildren().addAll(classicTab, blitzTab);
        return tabs;
    }

    private void updateTabsStyle() {
        String baseStyle = "-fx-font-family: 'Kolker Brush'; -fx-font-size: 60px; -fx-background-color: transparent; ";

        if (currentMode.equals("classical")) {
            classicTab.setStyle(baseStyle + "-fx-text-fill: #A61E1E; -fx-underline: true;");
            blitzTab.setStyle(baseStyle + "-fx-text-fill: black; -fx-underline: false;");
        } else {
            classicTab.setStyle(baseStyle + "-fx-text-fill: black; -fx-underline: false;");
            blitzTab.setStyle(baseStyle + "-fx-text-fill: #A61E1E; -fx-underline: true;");
        }
    }

    private HBox createTableHeaders() {
        HBox headers = new HBox(0);
        headers.setPrefWidth(COL_AVATAR_WIDTH + COL_NAME_WIDTH + COL_RANK_WIDTH + COL_ELO_WIDTH);

        Region spacer = new Region();
        spacer.setPrefWidth(COL_AVATAR_WIDTH + COL_NAME_WIDTH);

        Label rankHeader = new Label("Rank");
        rankHeader.setPrefWidth(COL_RANK_WIDTH);
        rankHeader.setAlignment(Pos.CENTER);
        rankHeader.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 45px; -fx-text-fill: black;");

        Label eloHeader = new Label("Elo");
        eloHeader.setPrefWidth(COL_ELO_WIDTH);
        eloHeader.setAlignment(Pos.CENTER);
        eloHeader.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 45px; -fx-text-fill: black;");

        headers.getChildren().addAll(spacer, rankHeader, eloHeader);
        return headers;
    }

    private StackPane createRankingListArea() {
        double listWidth = COL_AVATAR_WIDTH + COL_NAME_WIDTH + COL_RANK_WIDTH + COL_ELO_WIDTH + 20;
        double listHeight = 550;

        Rectangle listBg = new Rectangle(listWidth, listHeight);
        listBg.setFill(Color.web("#D3D1D1"));
        listBg.setArcWidth(15);
        listBg.setArcHeight(15);

        rankingListContainer = new VBox(0);
        rankingListContainer.setPadding(new Insets(10, 0, 10, 0));
        rankingListContainer.setStyle("-fx-background-color: #D3D1D1;");

        ScrollPane scrollPane = new ScrollPane(rankingListContainer);
        scrollPane.setPrefSize(listWidth, listHeight);

        scrollPane.setStyle(
            "-fx-background: #D3D1D1; " +
            "-fx-background-color: #D3D1D1; " +
            "-fx-border-color: transparent;" +
            "-fx-viewport-border: transparent;"
        );
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Rectangle clip = new Rectangle(listWidth, listHeight);
        clip.setArcWidth(15);
        clip.setArcHeight(15);
        scrollPane.setClip(clip);

        return new StackPane(listBg, scrollPane);
    }

    private VBox createMyProfileSection() {
        VBox container = new VBox(20);
        container.setPrefWidth(450);
        container.setAlignment(Pos.TOP_CENTER);

        // Avatar circle (red border) - larger size
        Circle avatarCircle = new Circle(160);
        avatarCircle.setFill(Color.TRANSPARENT);
        avatarCircle.setStroke(RED_ACCENT);
        avatarCircle.setStrokeWidth(7);

        // Label with first letter of username
        Label initialLabel = new Label();
        initialLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 180px; -fx-text-fill: #A61E1E;");
        initialLabel.setAlignment(Pos.CENTER);
        
        // Update initial based on username
        Runnable updateInitial = () -> {
            String uname = state.getUsername();
            if (uname != null && !uname.isEmpty()) {
                String initial = uname.substring(0, 1).toUpperCase();
                initialLabel.setText(initial);
            } else {
                initialLabel.setText("?");
            }
        };
        
        // Listen to username changes
        state.usernameProperty().addListener((o, old, n) -> {
            Platform.runLater(updateInitial);
        });
        
        // Set initial letter immediately
        updateInitial.run();

        // Stack: circle border (bottom), then initial label (top)
        StackPane avatarStack = new StackPane();
        avatarStack.setAlignment(Pos.CENTER);
        avatarStack.getChildren().addAll(avatarCircle, initialLabel);
        avatarStack.setPrefSize(320, 320);

        Label myUsernameLabel = new Label();
        myUsernameLabel.textProperty().bind(state.usernameProperty());
        myUsernameLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 70px; -fx-text-fill: black;");

        myEloLabel = new Label("Elo xxxx");
        myEloLabel.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 60px; -fx-text-fill: black;");

        container.getChildren().addAll(avatarStack, myUsernameLabel, myEloLabel);
        return container;
    }

    private void switchMode(String mode) {
        if (currentMode.equals(mode)) return;
        currentMode = mode;
        updateTabsStyle();
        // Filter từ data đã có, không request lại
        filterAndDisplay(mode);
    }

    /**
     * Fetch toàn bộ user stats (cả classical và blitz) từ backend
     */
    private void fetchAllUsersStats() {
        if (networkManager != null) {
            try {
                rankingListContainer.getChildren().clear();
                // Gửi LEADER_BOARD message (không cần payload, backend sẽ trả về tất cả)
                networkManager.info().requestLeaderBoard("", 0); // Empty để backend biết trả về tất cả
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Filter và hiển thị leaderboard theo mode
     */
    private void filterAndDisplay(String mode) {
        Platform.runLater(() -> {
            leaderboardData.clear();
            rankingListContainer.getChildren().clear();
            
            // Filter data theo mode
            List<UserStatData> filtered = new ArrayList<>();
            for (UserStatData stat : allUsersStats) {
                if (stat.timeControl.equals(mode)) {
                    filtered.add(stat);
                }
            }
            
            // Sort theo rating giảm dần
            filtered.sort((a, b) -> Integer.compare(b.rating, a.rating));
            
            // Tạo leaderboard entries với rank
            for (int i = 0; i < filtered.size(); i++) {
                UserStatData stat = filtered.get(i);
                LeaderboardEntry entry = new LeaderboardEntry(stat.username, stat.rating, i + 1);
                leaderboardData.add(entry);
                rankingListContainer.getChildren().add(createRankingRow(entry));
            }
            
            updateMyProfileElo();
        });
    }

    private void registerLeaderboardCallback() {}

    public void updateLeaderboard(JsonObject response) {
        Platform.runLater(() -> {
            // Clear old data
            allUsersStats.clear();
            
            // Parse response - có thể là "all_users_stats" (mới) hoặc "leaderboard" (cũ)
            if (response.has("all_users_stats") && response.get("all_users_stats").isJsonArray()) {
                // Format mới: toàn bộ user stats
                JsonArray allStats = response.getAsJsonArray("all_users_stats");
                for (int i = 0; i < allStats.size(); i++) {
                    JsonObject stat = allStats.get(i).getAsJsonObject();
                    String username = stat.has("username") ? stat.get("username").getAsString() : "Unknown";
                    String timeControl = stat.has("time_control") ? stat.get("time_control").getAsString() : "classical";
                    int rating = stat.has("rating") ? stat.get("rating").getAsInt() : 0;
                    
                    allUsersStats.add(new UserStatData(username, timeControl, rating));
                }
                
                System.out.println("[RankingPanel] Received " + allUsersStats.size() + " user stats (all modes)");
                
                // Filter và hiển thị theo mode hiện tại
                filterAndDisplay(currentMode);
                
            } else if (response.has("leaderboard") && response.get("leaderboard").isJsonArray()) {
                // Format cũ: chỉ 1 mode (backward compatibility)
                JsonArray leaderboard = response.getAsJsonArray("leaderboard");
                String timeControl = response.has("time_control") ? response.get("time_control").getAsString() : currentMode;
                
                for (int i = 0; i < leaderboard.size(); i++) {
                    JsonObject entry = leaderboard.get(i).getAsJsonObject();
                    String username = entry.has("username") ? entry.get("username").getAsString() : "Unknown";
                    int rating = entry.has("rating") ? entry.get("rating").getAsInt() : 0;
                    
                    allUsersStats.add(new UserStatData(username, timeControl, rating));
                }
                
                // Filter và hiển thị
                filterAndDisplay(currentMode);
            }
        });
    }

    private HBox createRankingRow(LeaderboardEntry entry) {
        HBox row = new HBox(0);
        row.setPrefWidth(COL_AVATAR_WIDTH + COL_NAME_WIDTH + COL_RANK_WIDTH + COL_ELO_WIDTH + 10);
        row.setAlignment(Pos.CENTER_LEFT);

        row.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent rgba(0,0,0,0.1) transparent; -fx-border-width: 1;");
        row.setPadding(new Insets(8, 0, 8, 10));

        StackPane avatarPane = new StackPane();
        avatarPane.setPrefWidth(COL_AVATAR_WIDTH);
        Circle c = new Circle(28);
        c.setFill(Color.TRANSPARENT);
        c.setStroke(RED_ACCENT);
        c.setStrokeWidth(2);

        Label initial = new Label(entry.username.substring(0, 1).toUpperCase());
        initial.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 30px; -fx-text-fill: #A61E1E;");

        avatarPane.getChildren().addAll(c, initial);

        Label nameLbl = new Label(entry.username);
        nameLbl.setPrefWidth(COL_NAME_WIDTH);
        nameLbl.setPadding(new Insets(0, 0, 0, 10));
        nameLbl.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 45px; -fx-text-fill: black;");

        Label rankLbl = new Label(String.valueOf(entry.rank));
        rankLbl.setPrefWidth(COL_RANK_WIDTH);
        rankLbl.setAlignment(Pos.CENTER);
        rankLbl.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 45px; -fx-text-fill: black;");

        Label eloLbl = new Label(String.valueOf(entry.rating));
        eloLbl.setPrefWidth(COL_ELO_WIDTH);
        eloLbl.setAlignment(Pos.CENTER);
        eloLbl.setStyle("-fx-font-family: 'Kolker Brush'; -fx-font-size: 45px; -fx-text-fill: black;");

        row.getChildren().addAll(avatarPane, nameLbl, rankLbl, eloLbl);
        return row;
    }

    private void updateMyProfileElo() {
        String currentUsername = state.getUsername();
        if (currentUsername == null) return;

        int elo = currentMode.equals("classical") ? state.getClassicalElo() : state.getBlitzElo();
        for (LeaderboardEntry e : leaderboardData) {
            if (e.username.equals(currentUsername)) {
                elo = e.rating;
                break;
            }
        }
        myEloLabel.setText("Elo " + elo);
    }

    private void fadeTo(double opacity) {
        fade.setToValue(opacity);
        fade.play();
    }

    private static class LeaderboardEntry {
        String username;
        int rating;
        int rank;
        LeaderboardEntry(String u, int r, int rank) { this.username = u; this.rating = r; this.rank = rank; }
    }
}