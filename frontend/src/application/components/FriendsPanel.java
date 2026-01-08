package application.components;

import application.network.NetworkManager;
import application.state.UIState;
import application.util.AssetHelper;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;
import javafx.scene.Cursor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Friends panel that appears when clicking the friends icon.
 */
public class FriendsPanel extends Pane {
    
    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final UIState state;
    private final NetworkManager networkManager = NetworkManager.getInstance();
    private VBox friendsListContainer;
    private VBox searchResultsContainer;
    private VBox friendRequestsContainer; // Container for friend requests list
    private ScrollPane searchScrollPane; // Reference để control visibility
    private ScrollPane friendsScrollPane; // Reference để control visibility của friends list
    private ScrollPane friendRequestsScrollPane; // Scroll pane for friend requests
    private Pane rootPane; // Reference to root pane for showing dialogs
    private TextField searchField;
    private VBox searchContainer; // Reference để control visibility
    private ObservableList<String> onlinePlayers = FXCollections.observableArrayList();
    private ObservableList<String> filteredPlayers = FXCollections.observableArrayList();
    
    // Friend requests data
    private ObservableList<FriendRequestInfo> pendingRequests = FXCollections.observableArrayList();
    private ObservableList<FriendRequestInfo> acceptedRequests = FXCollections.observableArrayList();
    private ObservableList<FriendRequestInfo> rejectedRequests = FXCollections.observableArrayList();
    private int totalRequestsCount = 0; // Total pending + accepted requests
    private int badgeResetCount = 0; // Số lượng requests tại thời điểm reset badge (khi click notification)
    
    // Buttons
    private StackPane addFriendButton; // Reference to Add Friend button (for search)
    private StackPane notificationButton; // Reference to Notification button (for friend requests)
    private Circle badgeCircle;
    private Label badgeLabel;
    
    // Inner class for friend request info (public so InfoHandler can access it)
    public static class FriendRequestInfo {
        public String fromUsername;
        public String status; // "pending", "accepted", or "rejected"
        
        public FriendRequestInfo(String fromUsername, String status) {
            this.fromUsername = fromUsername;
            this.status = status;
        }
    }
    

    public FriendsPanel(UIState state) {
        this.state = state;
        
        // Set layout cho FriendsPanel để đặt ở bên phải màn hình
        setLayoutX(1920 - 430);
        setLayoutY(50);
        setPrefSize(400, 850);
        setStyle("-fx-background-color: transparent;");
        setMouseTransparent(false);  // Cho phép FriendsPanel nhận click events
        setPickOnBounds(true);  // Chỉ nhận events trong bounds của panel
        
        // Friends panel - đặt ở bên phải, chỉ chiếm đúng kích thước của panel
        VBox friendsContent = createFriendsContent();
        // Không cần set layoutX/layoutY cho friendsContent vì nó sẽ nằm trong FriendsPanel
        friendsContent.setMouseTransparent(false);  // friendsContent vẫn nhận sự kiện chuột
        friendsContent.setPickOnBounds(true);
        
        // THÊM friendsContent vào FriendsPanel - QUAN TRỌNG!
        getChildren().add(friendsContent);
        System.out.println("[FriendsPanel] Added friendsContent. Children count: " + getChildren().size());
        
        // Không bind visibility - tự quản lý
        setVisible(false);
        setOpacity(0);
        
        // Listener cho friendsVisible - tự quản lý visibility và opacity
        state.friendsVisibleProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("[FriendsPanel] friendsVisible changed: " + newVal + ", appState: " + state.appStateProperty().get());
            System.out.println("[FriendsPanel] Current layout: X=" + getLayoutX() + ", Y=" + getLayoutY() + ", PrefWidth=" + getPrefWidth() + ", PrefHeight=" + getPrefHeight());
            System.out.println("[FriendsPanel] Current visible: " + isVisible() + ", opacity: " + getOpacity() + ", managed: " + isManaged());
            System.out.println("[FriendsPanel] Parent: " + (getParent() != null ? getParent().getClass().getSimpleName() : "null"));
            System.out.println("[FriendsPanel] Children count: " + getChildren().size());
            if (newVal && state.appStateProperty().get() == UIState.AppState.MAIN_MENU) {
                System.out.println("[FriendsPanel] Showing panel...");
                setVisible(true);
                setManaged(true);
                setOpacity(1.0); // Set opacity ngay lập tức để test
                System.out.println("[FriendsPanel] After setVisible: visible=" + isVisible() + ", opacity=" + getOpacity() + ", managed=" + isManaged());
            } else {
                System.out.println("[FriendsPanel] Hiding panel...");
                setOpacity(0);
                setVisible(false);
            }
        });
        
        state.appStateProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("[FriendsPanel] appState changed: " + newVal + ", friendsVisible: " + state.isFriendsVisible());
            if (newVal == UIState.AppState.MAIN_MENU && state.isFriendsVisible()) {
                System.out.println("[FriendsPanel] Showing panel...");
                setVisible(true);
                setOpacity(1.0); // Set opacity ngay lập tức để test
                // fadeTo(1);
            } else {
                System.out.println("[FriendsPanel] Hiding panel...");
                setOpacity(0);
                setVisible(false);
            }
        });
        
        // Đóng FriendsPanel khi Settings được mở
        state.settingsVisibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && state.isFriendsVisible()) {
                System.out.println("[FriendsPanel] Settings opened, closing FriendsPanel...");
                state.closeFriends();
            }
        });
        
        // Bind friend list changes
        state.getFriendsList().addListener((ListChangeListener<String>) change -> {
            refreshFriendsList();
            // Also refresh search results to update Add/Unfriend buttons
            refreshSearchResults();
        });
        
        // Load online players and friend requests when panel becomes visible
        state.friendsVisibleProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("[FriendsPanel] friendsVisibleProperty changed: " + oldVal + " -> " + newVal);
            if (newVal) {
                System.out.println("[FriendsPanel] Friends panel opened, showing friends list...");
                loadOnlinePlayers();
                loadFriendRequests();
                loadFriends(); // Load friends list from server
                // Mặc định hiển thị friends list (không phải friend requests)
                javafx.application.Platform.runLater(() -> {
                    // Đảm bảo ẩn tất cả view khác trước
                    hideFriendRequests();
                    hideSearchBox();
                    // Hiện friends list
                    showFriendsList();
                    // Refresh để hiển thị friends list hiện tại
                    refreshFriendsList();
                    System.out.println("[FriendsPanel] Friends list should be visible now. Container visible: " + 
                        (friendsListContainer != null ? friendsListContainer.isVisible() : "null") + 
                        ", ScrollPane visible: " + 
                        (friendsScrollPane != null ? friendsScrollPane.isVisible() : "null") +
                        ", Friends count: " + state.getFriendsList().size());
                });
            }
        });
        
        // Register callbacks to receive updates
        state.setOnlinePlayersUpdateCallback(this::updateOnlinePlayers);
        state.setSearchResultsUpdateCallback(this::updateSearchResults);
        state.setFriendRequestsUpdateCallback(this::updateFriendRequests);
        
        // Register callbacks to receive updates
        state.setOnlinePlayersUpdateCallback(this::updateOnlinePlayers);
        state.setSearchResultsUpdateCallback(this::updateSearchResults);
        
        // Initial load
        refreshFriendsList();
    }
    
    /**
     * Create search box for finding and adding friends.
     */
    private VBox createSearchBox() {
        VBox container = new VBox(8);
        container.setAlignment(Pos.TOP_LEFT);
        
        // Search label
        Label searchLabel = new Label("Search by username:");
        searchLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 16px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Search input field
        searchField = new TextField();
        searchField.setPrefWidth(360);
        searchField.setPrefHeight(45);
        searchField.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 16px; " +
            "-fx-background-color: white; " +
            "-fx-border-color: #A65252; " +
            "-fx-border-width: 2px; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px;"
        );
        searchField.setPromptText("Enter username...");
        
        // Real-time search on text change
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("[FriendsPanel] Search field changed: '" + oldVal + "' -> '" + newVal + "'");
            
            if (newVal == null || newVal.trim().isEmpty()) {
                // Hide search results, show friends list
                System.out.println("[FriendsPanel] Search field empty, hiding search results");
                if (searchResultsContainer != null) {
                    searchResultsContainer.setVisible(false);
                }
                if (searchScrollPane != null) {
                    searchScrollPane.setVisible(false);
                    searchScrollPane.setManaged(false);
                }
                // Ẩn friend requests
                hideFriendRequests();
                // Hiện friends list
                showFriendsList();
                // Clear filtered results
                filteredPlayers.clear();
            } else {
                // Ensure search container is visible when user types
                if (searchContainer != null) {
                    if (!searchContainer.isVisible()) {
                        System.out.println("[FriendsPanel] Search container was hidden, making it visible...");
                    }
                    searchContainer.setVisible(true);
                    searchContainer.setManaged(true);
                }
                // Ẩn friend requests khi user gõ search
                hideFriendRequests();
                // Ẩn friends list khi user gõ search
                hideFriendsList();
                // Show search results
                System.out.println("[FriendsPanel] Search field has text, sending search request to server...");
                String query = newVal.trim();
                if (query.length() >= 1) { // Search if at least 1 character
                    try {
                        networkManager.info().searchUsers(query);
                    } catch (IOException e) {
                        System.err.println("[FriendsPanel] Failed to search users: " + e.getMessage());
                    }
                }
            }
        });
        
        container.getChildren().addAll(searchLabel, searchField);
        return container;
    }
    
    /**
     * Update search results from server response.
     * Called when server returns search results.
     */
    public void updateSearchResults(List<String> results) {
        javafx.application.Platform.runLater(() -> {
            System.out.println("[FriendsPanel] updateSearchResults called with " + (results != null ? results.size() : 0) + " users");
        filteredPlayers.clear();
            if (results != null && !results.isEmpty()) {
                filteredPlayers.addAll(results);
                System.out.println("[FriendsPanel] Updated search results. Total: " + filteredPlayers.size());
        if (!filteredPlayers.isEmpty()) {
            System.out.println("[FriendsPanel] Sample results: " + filteredPlayers.subList(0, Math.min(3, filteredPlayers.size())));
                }
            } else {
                System.out.println("[FriendsPanel] No search results received");
        }
        
            // Ensure search container is visible when we have results
            if (searchContainer != null && !filteredPlayers.isEmpty()) {
            searchContainer.setVisible(true);
            searchContainer.setManaged(true);
            System.out.println("[FriendsPanel] Search container made visible");
        }
        
        // Update UI
        refreshSearchResults();
        
            // Show search results, hide friends list (only if we have results or search field has text)
            if (searchField != null && searchField.getText() != null && !searchField.getText().trim().isEmpty()) {
                System.out.println("[FriendsPanel] Search field has text, showing search results");
        if (searchResultsContainer != null) {
            searchResultsContainer.setVisible(true);
            System.out.println("[FriendsPanel] Search results container made visible");
        }
        if (searchScrollPane != null) {
            searchScrollPane.setVisible(true);
            searchScrollPane.setManaged(true);
            System.out.println("[FriendsPanel] Search scroll pane made visible. Visible: " + searchScrollPane.isVisible() + ", Managed: " + searchScrollPane.isManaged());
        }
        if (friendsListContainer != null) {
            friendsListContainer.setVisible(false);
        }
        if (friendsScrollPane != null) {
            friendsScrollPane.setVisible(false);
            friendsScrollPane.setManaged(false);
        }
            }
        });
    }
    
    /**
     * Refresh search results display.
     */
    private void refreshSearchResults() {
        if (searchResultsContainer == null) {
            System.err.println("[FriendsPanel] searchResultsContainer is null!");
            return;
        }
        
        System.out.println("[FriendsPanel] Refreshing search results. Filtered players: " + filteredPlayers.size());
        
        searchResultsContainer.getChildren().clear();
        
        if (filteredPlayers.isEmpty()) {
            Label noResultsLabel = new Label("No players found");
            noResultsLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 16px; " +
                "-fx-text-fill: rgba(0, 0, 0, 0.5); " +
                "-fx-background-color: transparent; " +
                "-fx-alignment: center;"
            );
            noResultsLabel.setAlignment(Pos.CENTER);
            noResultsLabel.setPrefWidth(360);
            noResultsLabel.setPrefHeight(50);
            searchResultsContainer.getChildren().add(noResultsLabel);
            System.out.println("[FriendsPanel] Showing 'No players found' message");
        } else {
            // Display filtered players
            System.out.println("[FriendsPanel] Adding " + filteredPlayers.size() + " player entries to UI");
            for (String username : filteredPlayers) {
                HBox playerEntry = createSearchResultEntry(username);
                searchResultsContainer.getChildren().add(playerEntry);
            }
        }
    }
    
    /**
     * Create a search result entry with "Add" button.
     */
    private HBox createSearchResultEntry(String username) {
        HBox entry = new HBox(15);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(12, 15, 12, 15));
        entry.setCursor(Cursor.HAND);
        entry.setStyle("-fx-background-radius: 8px;");
        
        // Avatar circle với fill và initial letter
        Circle avatarCircle = new Circle(22);
        avatarCircle.setFill(Color.web("#F5E6E6")); // Light pink fill
        avatarCircle.setStroke(Color.web("#A65252"));
        avatarCircle.setStrokeWidth(2.5);
        
        // Initial letter trong avatar
        Label initialLabel = new Label(username.substring(0, 1).toUpperCase());
        initialLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 18px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #A65252;"
        );
        
        StackPane avatarContainer = new StackPane(avatarCircle, initialLabel);
        avatarContainer.setPrefSize(44, 44);
        
        // Username label
        Label usernameLabel = new Label(username);
        usernameLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 18px; " +
            "-fx-text-fill: #2C2C2C; " +
            "-fx-background-color: transparent;"
        );
        
        // Check if user is already a friend
        boolean isFriend = state.getFriendsList().contains(username);
        
        // Button container (Add or Unfriend)
        StackPane buttonContainer = new StackPane();
        buttonContainer.setPrefWidth(90);
        buttonContainer.setPrefHeight(36);
        
        Rectangle buttonBg = new Rectangle(90, 36);
        buttonBg.setArcWidth(8);
        buttonBg.setArcHeight(8);
        buttonBg.setEffect(new javafx.scene.effect.DropShadow(4, Color.color(0.65, 0.32, 0.32, 0.4)));
        
        Label buttonLabel = new Label(isFriend ? "Unfriend" : "Add");
        buttonLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        
        // Set button color based on action
        if (isFriend) {
            buttonBg.setFill(Color.web("#8B4242")); // Darker red for unfriend
        } else {
            buttonBg.setFill(Color.web("#A65252")); // Red for add
        }
        
        buttonContainer.getChildren().addAll(buttonBg, buttonLabel);
        buttonContainer.setCursor(Cursor.HAND);
        
        // Click handler
        buttonContainer.setOnMouseClicked(e -> {
            if (isFriend) {
                // Unfriend
                handleUnfriendFromSearch(username, buttonContainer, buttonBg, buttonLabel);
            } else {
                // Send friend request
                handleSendFriendRequestFromSearch(username, buttonContainer, buttonBg, buttonLabel);
            }
            e.consume();
        });
        
        // Hover effect cho button (only if not disabled)
        buttonContainer.setOnMouseEntered(e -> {
            if (!buttonContainer.isDisable()) {
                if (isFriend) {
                    buttonBg.setFill(Color.web("#6B3232")); // Darker shade on hover for unfriend
                } else {
                    buttonBg.setFill(Color.web("#B85C5C")); // Lighter shade on hover for add
                }
                buttonBg.setEffect(new javafx.scene.effect.DropShadow(6, Color.color(0.65, 0.32, 0.32, 0.5)));
            }
        });
        buttonContainer.setOnMouseExited(e -> {
            if (!buttonContainer.isDisable()) {
                if (isFriend) {
                    buttonBg.setFill(Color.web("#8B4242"));
                } else {
                    buttonBg.setFill(Color.web("#A65252"));
                }
                buttonBg.setEffect(new javafx.scene.effect.DropShadow(4, Color.color(0.65, 0.32, 0.32, 0.4)));
            }
        });
        buttonContainer.setOnMousePressed(e -> {
            if (!buttonContainer.isDisable()) {
                if (isFriend) {
                    buttonBg.setFill(Color.web("#5B2222")); // Even darker on press for unfriend
                } else {
                    buttonBg.setFill(Color.web("#8B4242")); // Darker shade on press for add
                }
            }
        });
        buttonContainer.setOnMouseReleased(e -> {
            if (!buttonContainer.isDisable()) {
                if (isFriend) {
                    buttonBg.setFill(Color.web("#8B4242"));
                } else {
                    buttonBg.setFill(Color.web("#A65252"));
                }
            }
        });
        
        entry.getChildren().addAll(avatarContainer, usernameLabel);
        
        // Button to the right
        HBox.setHgrow(usernameLabel, javafx.scene.layout.Priority.ALWAYS);
        entry.getChildren().add(buttonContainer);
        
        // Hover effect cho entry
        entry.setOnMouseEntered(e -> {
            entry.setStyle(
                "-fx-background-color: rgba(166, 82, 82, 0.08); " +
                "-fx-background-radius: 8px;"
            );
        });
        entry.setOnMouseExited(e -> {
            entry.setStyle("-fx-background-color: transparent;");
        });
        
        return entry;
    }
    
    /**
     * Show friend requests list (không toggle, luôn hiện friend requests).
     */
    private void showFriendRequestsDirectly() {
        // Luôn hiện friend requests, ẩn tất cả khác
        showFriendRequests();
    }
    
    /**
     * Toggle search box visibility.
     * Nếu đang ẩn thì hiện search box, nếu đang hiện thì ẩn và quay về friends list.
     */
    private void toggleSearchBox() {
        if (searchContainer == null) {
            return;
        }
        
        boolean isVisible = searchContainer.isVisible();
        
        if (!isVisible) {
            // Hiện search box, ẩn tất cả khác
            showSearchBox();
        } else {
            // Ẩn search box, quay về friends list
            hideSearchBox();
            showFriendsList();
        }
    }
    
    /**
     * Show friends list.
     */
    private void showFriendsList() {
        // Ẩn friend requests
        hideFriendRequests();
        
        // Ẩn search box và search results
        hideSearchBox();
        
        // Hiện friends list
        if (friendsListContainer != null) {
            friendsListContainer.setVisible(true);
        }
        if (friendsScrollPane != null) {
            friendsScrollPane.setVisible(true);
            friendsScrollPane.setManaged(true);
        }
    }
    
    /**
     * Hide friends list.
     */
    private void hideFriendsList() {
        if (friendsListContainer != null) {
            friendsListContainer.setVisible(false);
        }
        if (friendsScrollPane != null) {
            friendsScrollPane.setVisible(false);
            friendsScrollPane.setManaged(false);
        }
    }
    
    /**
     * Show friend requests list.
     */
    private void showFriendRequests() {
        // Load friend requests from server
        loadFriendRequests();
        
        // Ẩn friends list
        hideFriendsList();
        
        // Ẩn search box và search results
        hideSearchBox();
        
        // Hiện friend requests
        if (friendRequestsScrollPane != null) {
            friendRequestsScrollPane.setVisible(true);
            friendRequestsScrollPane.setManaged(true);
        }
        if (friendRequestsContainer != null) {
            friendRequestsContainer.setVisible(true);
        }
    }
    
    /**
     * Hide friend requests list.
     */
    private void hideFriendRequests() {
        if (friendRequestsScrollPane != null) {
            friendRequestsScrollPane.setVisible(false);
            friendRequestsScrollPane.setManaged(false);
        }
        if (friendRequestsContainer != null) {
            friendRequestsContainer.setVisible(false);
        }
    }
    
    /**
     * Show search box.
     */
    private void showSearchBox() {
        // Ẩn friends list
        hideFriendsList();
        
        // Ẩn friend requests
        hideFriendRequests();
        
            // Hiện search box
        if (searchContainer != null) {
            searchContainer.setVisible(true);
            searchContainer.setManaged(true);
        }
        
        // Hiện search results container (nếu có kết quả)
        if (searchResultsContainer != null && !searchResultsContainer.getChildren().isEmpty()) {
            if (searchScrollPane != null) {
                searchScrollPane.setVisible(true);
                searchScrollPane.setManaged(true);
            }
        }
        
            if (searchField != null) {
                javafx.application.Platform.runLater(() -> {
                    searchField.requestFocus();
                });
            }
    }
    
    /**
     * Hide search box.
     */
    private void hideSearchBox() {
        if (searchContainer != null) {
            searchContainer.setVisible(false);
            searchContainer.setManaged(false);
        }
            if (searchScrollPane != null) {
                searchScrollPane.setVisible(false);
                searchScrollPane.setManaged(false);
            }
            if (searchResultsContainer != null) {
                searchResultsContainer.setVisible(false);
            }
        if (searchField != null) {
            searchField.clear();
        }
    }
    
    /**
     * Load online players from server.
     */
    private void loadOnlinePlayers() {
        try {
            System.out.println("[FriendsPanel] Requesting player list from server...");
            networkManager.info().requestPlayerList();
        } catch (IOException e) {
            System.err.println("[FriendsPanel] Failed to request player list: " + e.getMessage());
            // Clear online players if server is not available
            onlinePlayers.clear();
        }
    }
    
    /**
     * Load friend requests from server.
     */
    private void loadFriendRequests() {
        try {
            System.out.println("[FriendsPanel] Requesting friend requests from server...");
            networkManager.friend().requestAllReceivedRequests();
        } catch (IOException e) {
            System.err.println("[FriendsPanel] Failed to request friend requests: " + e.getMessage());
        }
    }
    
    /**
     * Load friends list from server.
     */
    private void loadFriends() {
        try {
            System.out.println("[FriendsPanel] Requesting friends list from server...");
            networkManager.friend().requestFriendsList();
        } catch (IOException e) {
            System.err.println("[FriendsPanel] Failed to request friends list: " + e.getMessage());
        }
    }
    
    /**
     * Update online players list (called from InfoHandler).
     * Only uses real data from server, no mock data.
     * Note: InfoHandler already excludes current user, so we just use the list as-is.
     */
    public void updateOnlinePlayers(List<String> players) {
        javafx.application.Platform.runLater(() -> {
            System.out.println("[FriendsPanel] updateOnlinePlayers called with " + (players != null ? players.size() : 0) + " players");
            onlinePlayers.clear();
            if (players != null && !players.isEmpty()) {
                onlinePlayers.addAll(players);
                System.out.println("[FriendsPanel] Updated online players list. Total: " + onlinePlayers.size());
                if (!onlinePlayers.isEmpty()) {
                    System.out.println("[FriendsPanel] Sample players: " + onlinePlayers.subList(0, Math.min(5, onlinePlayers.size())));
                }
            } else {
                System.out.println("[FriendsPanel] No players received from server");
            }
            // Refresh friends list để cập nhật online status
            refreshFriendsList();
        });
    }
    
    /**
     * Update friend requests list from server response.
     */
    public void updateFriendRequests(List<FriendRequestInfo> pending, List<FriendRequestInfo> accepted) {
        javafx.application.Platform.runLater(() -> {
            System.out.println("[FriendsPanel] updateFriendRequests called with " + 
                (pending != null ? pending.size() : 0) + " pending, " + 
                (accepted != null ? accepted.size() : 0) + " accepted");
            
            pendingRequests.clear();
            acceptedRequests.clear();
            rejectedRequests.clear(); // Clear rejected when reloading from server
            
            // Chỉ lưu pending requests (không lưu accepted/rejected)
            if (pending != null) {
                pendingRequests.addAll(pending);
            }
            
            // Badge chỉ đếm pending requests MỚI (sau khi reset)
            totalRequestsCount = Math.max(0, pendingRequests.size() - badgeResetCount);
            updateBadge();
            // Update badge ở bottom menu
            state.updateFriendsBadge(totalRequestsCount);
            System.out.println("[FriendsPanel] Badge updated from server. Pending: " + pendingRequests.size() + ", Baseline: " + badgeResetCount + ", New requests: " + totalRequestsCount);
            refreshFriendRequestsUI();
        });
    }
    
    /**
     * Update badge on Add Friend button.
     */
    private void updateBadge() {
        if (badgeCircle == null || badgeLabel == null) {
            return;
        }
        // Badge chỉ đếm pending requests (không đếm accepted/rejected)
        int count = pendingRequests.size();
        if (count > 0) {
            badgeCircle.setVisible(true);
            badgeLabel.setVisible(true);
            String countText = count > 99 ? "99+" : String.valueOf(count);
            badgeLabel.setText(countText);
        } else {
            badgeCircle.setVisible(false);
            badgeLabel.setVisible(false);
        }
    }
    
    /**
     * Refresh friend requests UI.
     */
    private void refreshFriendRequestsUI() {
        if (friendRequestsContainer == null) {
            return;
        }
        
        friendRequestsContainer.getChildren().clear();
        
        // Chỉ hiển thị pending requests (không hiển thị accepted/rejected)
        if (!pendingRequests.isEmpty()) {
            Label pendingLabel = new Label("Pending Requests");
            pendingLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 18px; " +
                "-fx-text-fill: #A65252; " +
                "-fx-font-weight: bold;"
            );
            friendRequestsContainer.getChildren().add(pendingLabel);
            
            for (FriendRequestInfo req : pendingRequests) {
                friendRequestsContainer.getChildren().add(createFriendRequestEntry(req, true, false));
            }
        }
        
        // Show empty message if no pending requests
        if (pendingRequests.isEmpty()) {
            Label emptyLabel = new Label("No pending friend requests");
            emptyLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 16px; " +
                "-fx-text-fill: #666;"
            );
            friendRequestsContainer.getChildren().add(emptyLabel);
        }
    }
    
    /**
     * Create a friend request entry UI.
     * @param req The friend request info
     * @param isPending Whether this is a pending request (shows Accept/Decline buttons)
     * @param isRejected Whether this is a rejected request (shows Rejected button)
     */
    private HBox createFriendRequestEntry(FriendRequestInfo req, boolean isPending, boolean isRejected) {
        HBox entry = new HBox(15);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(10));
        entry.setPrefWidth(340);
        entry.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5); -fx-background-radius: 8px;");
        
        // Avatar
        Circle avatar = new Circle(25);
        avatar.setFill(Color.web("#E0E0E0"));
        avatar.setStroke(Color.web("#A65252"));
        avatar.setStrokeWidth(2);
        
        Label avatarLabel = new Label(req.fromUsername.length() > 0 ? 
            String.valueOf(req.fromUsername.charAt(0)).toUpperCase() : "?");
        avatarLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 20px; " +
            "-fx-text-fill: #A65252; " +
            "-fx-font-weight: bold;"
        );
        StackPane avatarContainer = new StackPane(avatar, avatarLabel);
        
        // Username
        Label usernameLabel = new Label(req.fromUsername);
        usernameLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 16px; " +
            "-fx-text-fill: black; " +
            "-fx-font-weight: bold;"
        );
        
        entry.getChildren().addAll(avatarContainer, usernameLabel);
        HBox.setHgrow(usernameLabel, javafx.scene.layout.Priority.ALWAYS);
        
        if (isPending) {
            // Accept button - màu đen
            Rectangle acceptBg = new Rectangle(80, 35);
            acceptBg.setFill(Color.web("#000000")); // Màu đen
            acceptBg.setArcWidth(8);
            acceptBg.setArcHeight(8);
            acceptBg.setEffect(new javafx.scene.effect.DropShadow(4, Color.color(0.0, 0.0, 0.0, 0.4)));
            
            Label acceptLabel = new Label("Accept");
            acceptLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 14px; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold;"
            );
            StackPane acceptButton = new StackPane(acceptBg, acceptLabel);
            acceptButton.setCursor(Cursor.HAND);
            acceptButton.setOnMouseClicked(e -> {
                e.consume();
                handleAcceptRequest(req.fromUsername);
            });
            
            // Hover effect cho Accept button
            acceptButton.setOnMouseEntered(e -> acceptBg.setFill(Color.web("#333333")));
            acceptButton.setOnMouseExited(e -> acceptBg.setFill(Color.web("#000000")));
            
            // Decline button - màu đỏ
            Rectangle declineBg = new Rectangle(80, 35);
            declineBg.setFill(Color.web("#F44336")); // Màu đỏ
            declineBg.setArcWidth(8);
            declineBg.setArcHeight(8);
            declineBg.setEffect(new javafx.scene.effect.DropShadow(4, Color.color(0.8, 0.3, 0.3, 0.4)));
            
            Label declineLabel = new Label("Decline");
            declineLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 14px; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold;"
            );
            StackPane declineButton = new StackPane(declineBg, declineLabel);
            declineButton.setCursor(Cursor.HAND);
            declineButton.setOnMouseClicked(e -> {
                e.consume();
                handleDeclineRequest(req.fromUsername);
            });
            
            // Hover effect cho Decline button
            declineButton.setOnMouseEntered(e -> declineBg.setFill(Color.web("#D32F2F")));
            declineButton.setOnMouseExited(e -> declineBg.setFill(Color.web("#F44336")));
            
            HBox buttons = new HBox(10, acceptButton, declineButton);
            entry.getChildren().add(buttons);
        } else if (isRejected) {
            // Show "Rejected" button (màu xám)
            Rectangle rejectedBg = new Rectangle(100, 35);
            rejectedBg.setFill(Color.web("#757575")); // Màu xám
            rejectedBg.setArcWidth(8);
            rejectedBg.setArcHeight(8);
            rejectedBg.setEffect(new javafx.scene.effect.DropShadow(4, Color.color(0.5, 0.5, 0.5, 0.4)));
            
            Label rejectedLabel = new Label("Rejected");
            rejectedLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 14px; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold;"
            );
            StackPane rejectedButton = new StackPane(rejectedBg, rejectedLabel);
            rejectedButton.setMouseTransparent(true); // Disable interaction
            entry.getChildren().add(rejectedButton);
        } else {
            // Show "Accepted" button (màu xám)
            Rectangle acceptedBg = new Rectangle(100, 35);
            acceptedBg.setFill(Color.web("#757575")); // Màu xám
            acceptedBg.setArcWidth(8);
            acceptedBg.setArcHeight(8);
            acceptedBg.setEffect(new javafx.scene.effect.DropShadow(4, Color.color(0.5, 0.5, 0.5, 0.4)));
            
            Label acceptedLabel = new Label("Accepted");
            acceptedLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 14px; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold;"
            );
            StackPane acceptedButton = new StackPane(acceptedBg, acceptedLabel);
            acceptedButton.setMouseTransparent(true); // Disable interaction
            entry.getChildren().add(acceptedButton);
        }
        
        return entry;
    }
    
    /**
     * Handle accept friend request.
     */
    private void handleAcceptRequest(String fromUsername) {
        try {
            System.out.println("[FriendsPanel] Accepting friend request from: " + fromUsername);
            
            // Optimistic update: Update UI immediately
            javafx.application.Platform.runLater(() -> {
                // Xóa khỏi pending (không thêm vào accepted/rejected)
                pendingRequests.removeIf(req -> req.fromUsername.equals(fromUsername));
                
                // Update badge count (chỉ đếm pending requests)
                totalRequestsCount = Math.max(0, pendingRequests.size() - badgeResetCount);
                updateBadge();
                // Update badge ở bottom menu
                state.updateFriendsBadge(totalRequestsCount);
                System.out.println("[FriendsPanel] Badge updated after Accept. Pending: " + pendingRequests.size() + ", Badge: " + totalRequestsCount);
                
                // Refresh UI
                refreshFriendRequestsUI();
            });
            
            // Send request to server
            networkManager.friend().respondFriendRequest(fromUsername, true);
            
            // Reload friend requests and friends list after a delay to sync with server
            javafx.application.Platform.runLater(() -> {
                javafx.util.Duration delay = javafx.util.Duration.millis(500);
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(delay);
                pause.setOnFinished(e -> {
                    loadFriendRequests(); // Reload friend requests
                    loadFriends(); // Reload friends list để cập nhật danh sách bạn bè
                });
                pause.play();
            });
        } catch (IOException e) {
            System.err.println("[FriendsPanel] Failed to accept friend request: " + e.getMessage());
            // Reload on error to restore correct state
            loadFriendRequests();
        }
    }
    
    /**
     * Handle decline friend request.
     */
    private void handleDeclineRequest(String fromUsername) {
        try {
            System.out.println("[FriendsPanel] Declining friend request from: " + fromUsername);
            
            // Optimistic update: Update UI immediately
            javafx.application.Platform.runLater(() -> {
                // Xóa khỏi pending (không thêm vào accepted/rejected)
                pendingRequests.removeIf(req -> req.fromUsername.equals(fromUsername));
                
                // Update badge count (chỉ đếm pending requests)
                totalRequestsCount = Math.max(0, pendingRequests.size() - badgeResetCount);
                updateBadge();
                // Update badge ở bottom menu
                state.updateFriendsBadge(totalRequestsCount);
                System.out.println("[FriendsPanel] Badge updated after Decline. Pending: " + pendingRequests.size() + ", Badge: " + totalRequestsCount);
                
                // Refresh UI
                refreshFriendRequestsUI();
            });
            
            // Send request to server
            networkManager.friend().respondFriendRequest(fromUsername, false);
            
            // Reload friend requests after a delay to sync with server
            javafx.application.Platform.runLater(() -> {
                javafx.util.Duration delay = javafx.util.Duration.millis(500);
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(delay);
                pause.setOnFinished(e -> loadFriendRequests());
                pause.play();
            });
        } catch (IOException e) {
            System.err.println("[FriendsPanel] Failed to decline friend request: " + e.getMessage());
            // Reload on error to restore correct state
            loadFriendRequests();
        }
    }
    
    /**
     * Handle send friend request from search results.
     */
    private void handleSendFriendRequestFromSearch(String username, StackPane buttonContainer, Rectangle buttonBg, Label buttonLabel) {
        try {
            networkManager.friend().sendFriendRequest(username);
            // Change button to "Sent" and disable it
            javafx.application.Platform.runLater(() -> {
                buttonLabel.setText("Sent");
                buttonLabel.setStyle(
                    "-fx-font-family: 'Kumar One'; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-text-fill: rgba(255, 255, 255, 0.7); " +
                    "-fx-background-color: transparent;"
                );
                buttonBg.setFill(Color.web("#8B8B8B")); // Gray color for sent
                buttonContainer.setCursor(Cursor.DEFAULT);
                buttonContainer.setDisable(true);
                // Remove hover effects
                buttonContainer.setOnMouseEntered(null);
                buttonContainer.setOnMouseExited(null);
                buttonContainer.setOnMousePressed(null);
                buttonContainer.setOnMouseReleased(null);
                // Remove click handler to prevent multiple clicks
                buttonContainer.setOnMouseClicked(null);
                System.out.println("[FriendsPanel] Friend request sent to: " + username + ", button changed to 'Sent'");
            });
        } catch (IOException ex) {
            System.err.println("[FriendsPanel] Failed to send friend request: " + ex.getMessage());
        }
    }
    
    /**
     * Handle unfriend from search results.
     */
    private void handleUnfriendFromSearch(String username, StackPane buttonContainer, Rectangle buttonBg, Label buttonLabel) {
        try {
            System.out.println("[FriendsPanel] Unfriending: " + username);
            
            // Optimistic update: Remove from friends list immediately
            javafx.application.Platform.runLater(() -> {
                state.removeFriend(username);
                // Disable button
                buttonLabel.setText("Unfriended");
                buttonLabel.setStyle(
                    "-fx-font-family: 'Kumar One'; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-text-fill: rgba(255, 255, 255, 0.7); " +
                    "-fx-background-color: transparent;"
                );
                buttonBg.setFill(Color.web("#8B8B8B")); // Gray color
                buttonContainer.setCursor(Cursor.DEFAULT);
                buttonContainer.setDisable(true);
                // Remove hover effects
                buttonContainer.setOnMouseEntered(null);
                buttonContainer.setOnMouseExited(null);
                buttonContainer.setOnMousePressed(null);
                buttonContainer.setOnMouseReleased(null);
                buttonContainer.setOnMouseClicked(null);
                System.out.println("[FriendsPanel] Unfriended: " + username);
            });
            
            // Send unfriend request to server
            networkManager.friend().unfriend(username);
            
            // Reload friends list and refresh search results after a delay to sync with server
            javafx.application.Platform.runLater(() -> {
                javafx.util.Duration delay = javafx.util.Duration.millis(500);
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(delay);
                pause.setOnFinished(e -> {
                    loadFriends(); // Reload friends list from server
                    // Refresh search results to update buttons (Add/Unfriend)
                    refreshSearchResults();
                });
                pause.play();
            });
        } catch (IOException e) {
            System.err.println("[FriendsPanel] Failed to unfriend: " + e.getMessage());
            // Reload on error to restore correct state
            loadFriends();
            refreshSearchResults();
        }
    }
    
    /**
     * Set root pane reference (called from Main.java after initialization).
     */
    public void setRootPane(Pane rootPane) {
        this.rootPane = rootPane;
    }
    
    private VBox createFriendsContent() {
        VBox content = new VBox(15);
        content.setPrefWidth(400);
        content.setPrefHeight(850);  // Giảm chiều cao từ 980 xuống 850
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(20));
        content.setPickOnBounds(true);
        content.setMouseTransparent(false); // Ensure content can receive mouse events
        
        // Background cho friends panel
        javafx.scene.shape.Rectangle bg = new javafx.scene.shape.Rectangle(400, 850);
        bg.setFill(Color.color(0.75, 0.75, 0.75, 0.9));
        bg.setArcWidth(15);
        bg.setArcHeight(15);
        bg.setStroke(Color.color(0.3, 0.3, 0.3));
        bg.setStrokeWidth(2);
        
        // Header với "Friends" title và Add Friend icon
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 15, 0));
        
        // "Friends" title
        Label friendsTitle = new Label("Friends");
        friendsTitle.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 60px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        
        // Notification button (để xem friend requests) - có badge - dùng icon chuông (bell)
        StackPane bellIcon = createBellIcon();
        bellIcon.setMouseTransparent(true);
        
        Rectangle notificationButtonBg = new Rectangle(50, 50);
        notificationButtonBg.setFill(Color.TRANSPARENT);
        notificationButtonBg.setMouseTransparent(true);
        
        StackPane notificationButton = new StackPane(notificationButtonBg, bellIcon);
        notificationButton.setPrefSize(50, 50);
        notificationButton.setCursor(Cursor.HAND);
        notificationButton.setPickOnBounds(true);
        notificationButton.setMouseTransparent(false);
        this.notificationButton = notificationButton; // Store reference
        
        // Badge for friend requests count trên notification button
        badgeCircle = new Circle(10);
        badgeCircle.setFill(Color.web("#FF4444")); // Red badge
        badgeCircle.setStroke(Color.WHITE);
        badgeCircle.setStrokeWidth(2);
        badgeCircle.setVisible(false); // Hidden by default
        
        badgeLabel = new Label("0");
        badgeLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: white; " +
            "-fx-background-color: transparent;"
        );
        badgeLabel.setVisible(false);
        
        StackPane badgeContainer = new StackPane(badgeCircle, badgeLabel);
        badgeContainer.setLayoutX(35); // Position at top-right of button
        badgeContainer.setLayoutY(5);
        badgeContainer.setMouseTransparent(true);
        
        notificationButton.getChildren().add(badgeContainer);
        
        notificationButton.setOnMouseEntered(e -> {
            if (bellIcon.getChildren().size() > 0) {
                bellIcon.setOpacity(0.7);
            }
        });
        notificationButton.setOnMouseExited(e -> {
            if (bellIcon.getChildren().size() > 0) {
                bellIcon.setOpacity(1.0);
            }
        });
        
        // Click handler để hiện friend requests list
        notificationButton.setOnMouseClicked(e -> {
            // Reset badge: lưu số lượng pending hiện tại làm baseline
            badgeResetCount = pendingRequests.size();
            totalRequestsCount = 0;
            updateBadge();
            state.updateFriendsBadge(0); // Reset badge ở bottom menu
            System.out.println("[FriendsPanel] Badge reset. New baseline: " + badgeResetCount);
            
            // Luôn hiện friend requests list, ẩn tất cả khác
            showFriendRequestsDirectly();
            e.consume();
        });
        
        // Add Friend button (để search/add friends) - không có badge
        ImageView addFriendIcon = new ImageView(AssetHelper.image("ic_friend.png"));
        addFriendIcon.setFitWidth(40);
        addFriendIcon.setFitHeight(40);
        addFriendIcon.setPreserveRatio(true);
        addFriendIcon.setSmooth(true);
        addFriendIcon.setMouseTransparent(true);
        
        Rectangle addFriendButtonBg = new Rectangle(50, 50);
        addFriendButtonBg.setFill(Color.TRANSPARENT);
        addFriendButtonBg.setMouseTransparent(true);
        
        StackPane addFriendButton = new StackPane(addFriendButtonBg, addFriendIcon);
        addFriendButton.setPrefSize(50, 50);
        addFriendButton.setCursor(Cursor.HAND);
        addFriendButton.setPickOnBounds(true);
        addFriendButton.setMouseTransparent(false);
        this.addFriendButton = addFriendButton; // Store reference
        
        addFriendButton.setOnMouseEntered(e -> addFriendIcon.setOpacity(0.7));
        addFriendButton.setOnMouseExited(e -> addFriendIcon.setOpacity(1.0));
        
        // Click handler để toggle search box
        addFriendButton.setOnMouseClicked(e -> {
            toggleSearchBox();
            e.consume();
        });
        
        header.getChildren().addAll(friendsTitle, notificationButton, addFriendButton);
        header.setPickOnBounds(true);
        header.setMouseTransparent(false);
        
        // Friends list container với scroll - khởi tạo trước
        friendsListContainer = new VBox(10);
        friendsListContainer.setAlignment(Pos.TOP_LEFT);
        friendsListContainer.setVisible(true); // Hiện mặc định
        friendsListContainer.setVisible(true); // Hiện mặc định
        
        // Search results container - khởi tạo trước createSearchBox()
        searchResultsContainer = new VBox(5);
        searchResultsContainer.setAlignment(Pos.TOP_LEFT);
        searchResultsContainer.setVisible(false); // Ẩn ban đầu
        
        // Friend requests container
        friendRequestsContainer = new VBox(5);
        friendRequestsContainer.setAlignment(Pos.TOP_LEFT);
        friendRequestsContainer.setVisible(false); // Ẩn ban đầu
        
        // Search box - thay thế dialog (sau khi searchResultsContainer đã được khởi tạo)
        // Ẩn ban đầu, chỉ hiện khi click icon "+"
        searchContainer = createSearchBox();
        searchContainer.setVisible(false); // Ẩn ban đầu
        searchContainer.setManaged(false); // Không chiếm space khi ẩn
        
        friendsScrollPane = new ScrollPane(friendsListContainer);
        friendsScrollPane.setPrefWidth(360);
        friendsScrollPane.setPrefHeight(650);
        friendsScrollPane.setStyle(
            "-fx-background: transparent; " +
            "-fx-background-color: rgba(255, 255, 255, 0.3); " +
            "-fx-background-radius: 8px;"
        );
        friendsScrollPane.setFitToWidth(true);
        friendsScrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        friendsScrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        friendsScrollPane.setVisible(true); // Hiện mặc định
        friendsScrollPane.setManaged(true); // Quản lý mặc định
        
        // Search results scroll pane - tăng chiều cao để tận dụng không gian
        // Panel height: 850px, header: ~80px, search box: ~100px, padding: ~40px
        // Còn lại: ~630px cho search results
        searchScrollPane = new ScrollPane(searchResultsContainer);
        searchScrollPane.setPrefWidth(360);
        searchScrollPane.setPrefHeight(630); // Tận dụng tối đa không gian còn lại
        searchScrollPane.setStyle(
            "-fx-background: transparent; " +
            "-fx-background-color: rgba(255, 255, 255, 0.4); " +
            "-fx-background-radius: 8px; " +
            "-fx-border-color: rgba(166, 82, 82, 0.3); " +
            "-fx-border-width: 1px; " +
            "-fx-border-radius: 8px;"
        );
        searchScrollPane.setFitToWidth(true);
        searchScrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        searchScrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Ẩn ban đầu, sẽ được control bởi filterAndShowResults()
        searchScrollPane.setVisible(false);
        searchScrollPane.setManaged(false); // Không chiếm space khi ẩn
        
        // Friend requests scroll pane
        friendRequestsScrollPane = new ScrollPane(friendRequestsContainer);
        friendRequestsScrollPane.setPrefWidth(360);
        friendRequestsScrollPane.setPrefHeight(630);
        friendRequestsScrollPane.setStyle(
            "-fx-background: transparent; " +
            "-fx-background-color: rgba(255, 255, 255, 0.4); " +
            "-fx-background-radius: 8px; " +
            "-fx-border-color: rgba(166, 82, 82, 0.3); " +
            "-fx-border-width: 1px; " +
            "-fx-border-radius: 8px;"
        );
        friendRequestsScrollPane.setFitToWidth(true);
        friendRequestsScrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        friendRequestsScrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        friendRequestsScrollPane.setVisible(false);
        friendRequestsScrollPane.setManaged(false);
        
        // Stack background và content
        StackPane friendsPanel = new StackPane();
        VBox innerContent = new VBox(15, header, searchContainer, searchScrollPane, friendRequestsScrollPane, friendsScrollPane);
        innerContent.setAlignment(Pos.TOP_LEFT);
        innerContent.setPadding(new Insets(20));
        innerContent.setPickOnBounds(true);
        innerContent.setMouseTransparent(false);
        
        friendsPanel.getChildren().addAll(bg, innerContent);
        StackPane.setAlignment(bg, Pos.CENTER);
        StackPane.setAlignment(innerContent, Pos.TOP_LEFT);
        friendsPanel.setPickOnBounds(true);
        friendsPanel.setMouseTransparent(false);
        
        content.getChildren().add(friendsPanel);
        
        return content;
    }
    
    
    /**
     * Refresh friends list from UIState.
     */
    private void refreshFriendsList() {
        if (friendsListContainer == null) {
            return;
        }
        
        friendsListContainer.getChildren().clear();
        
        javafx.collections.ObservableList<String> friends = state.getFriendsList();
        
        if (friends.isEmpty()) {
            // Show empty message
            Label emptyLabel = new Label("No friends yet.\nClick + to add friends");
            emptyLabel.setStyle(
                "-fx-font-family: 'Kumar One'; " +
                "-fx-font-size: 18px; " +
                "-fx-text-fill: rgba(0, 0, 0, 0.5); " +
                "-fx-background-color: transparent; " +
                "-fx-alignment: center;"
            );
            emptyLabel.setAlignment(Pos.CENTER);
            emptyLabel.setPrefWidth(360);
            emptyLabel.setPrefHeight(100);
            friendsListContainer.getChildren().add(emptyLabel);
        } else {
            // Display friends với online status thực tế
            for (String username : friends) {
                // Check if friend is in online players list
                boolean isOnline = onlinePlayers.contains(username);
                String status = isOnline ? "Online" : "Offline";
                HBox friendEntry = createFriendEntry(username, status);
                friendsListContainer.getChildren().add(friendEntry);
            }
        }
    }
    
    private HBox createFriendEntry(String username, String status) {
        HBox entry = new HBox(15);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(12, 15, 12, 15));
        entry.setCursor(Cursor.HAND);
        entry.setStyle("-fx-background-radius: 8px;");
        
        // Avatar circle với fill và initial letter
        Circle avatarCircle = new Circle(25);
        avatarCircle.setFill(Color.web("#F5E6E6")); // Light pink fill
        avatarCircle.setStroke(Color.web("#A65252"));
        avatarCircle.setStrokeWidth(2.5);
        
        // Initial letter trong avatar
        Label initialLabel = new Label(username.substring(0, 1).toUpperCase());
        initialLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 20px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #A65252;"
        );
        
        StackPane avatarContainer = new StackPane(avatarCircle, initialLabel);
        avatarContainer.setPrefSize(50, 50);
        
        // Text info container
        VBox textInfo = new VBox(5);
        textInfo.setAlignment(Pos.CENTER_LEFT);
        
        Label usernameLabel = new Label(username);
        usernameLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 18px; " +
            "-fx-text-fill: #2C2C2C; " +
            "-fx-background-color: transparent;"
        );
        
        // Online status indicator
        Circle statusIndicator = new Circle(6);
        if ("Online".equals(status)) {
            statusIndicator.setFill(Color.web("#4CAF50")); // Green for online
        } else {
            statusIndicator.setFill(Color.web("#9E9E9E")); // Gray for offline
        }
        
        HBox statusContainer = new HBox(8);
        statusContainer.setAlignment(Pos.CENTER_LEFT);
        statusContainer.getChildren().addAll(statusIndicator);
        
        Label statusLabel = new Label(status);
        statusLabel.setStyle(
            "-fx-font-family: 'Kumar One'; " +
            "-fx-font-size: 14px; " +
            "-fx-text-fill: rgba(44, 44, 44, 0.7); " +
            "-fx-background-color: transparent;"
        );
        statusContainer.getChildren().add(statusLabel);
        
        textInfo.getChildren().addAll(usernameLabel, statusContainer);
        
        entry.getChildren().addAll(avatarContainer, textInfo);
        
        // Hover effect
        entry.setOnMouseEntered(e -> {
            entry.setStyle(
                "-fx-background-color: rgba(166, 82, 82, 0.08); " +
                "-fx-background-radius: 8px;"
            );
        });
        entry.setOnMouseExited(e -> {
            entry.setStyle("-fx-background-color: transparent;");
        });
        
        // TODO: Click handler để challenge friend hoặc view profile
        
        return entry;
    }
    
    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
    
    /**
     * Create bell icon using JavaFX shapes.
     */
    private StackPane createBellIcon() {
        StackPane bellContainer = new StackPane();
        bellContainer.setPrefSize(40, 40);
        
        // Bell body (simplified bell shape using SVG-like path)
        javafx.scene.shape.Path bell = new javafx.scene.shape.Path();
        
        // Top handle (small rectangle at top)
        javafx.scene.shape.MoveTo move1 = new javafx.scene.shape.MoveTo(18, 8);
        javafx.scene.shape.LineTo line1 = new javafx.scene.shape.LineTo(22, 8);
        javafx.scene.shape.LineTo line2 = new javafx.scene.shape.LineTo(22, 12);
        javafx.scene.shape.LineTo line3 = new javafx.scene.shape.LineTo(18, 12);
        javafx.scene.shape.ClosePath close1 = new javafx.scene.shape.ClosePath();
        
        // Bell body (trapezoid shape)
        javafx.scene.shape.MoveTo move2 = new javafx.scene.shape.MoveTo(12, 12);
        javafx.scene.shape.LineTo line4 = new javafx.scene.shape.LineTo(28, 12);
        javafx.scene.shape.LineTo line5 = new javafx.scene.shape.LineTo(26, 28);
        javafx.scene.shape.LineTo line6 = new javafx.scene.shape.LineTo(14, 28);
        javafx.scene.shape.ClosePath close2 = new javafx.scene.shape.ClosePath();
        
        bell.getElements().addAll(move1, line1, line2, line3, close1, move2, line4, line5, line6, close2);
        bell.setFill(Color.BLACK);
        bell.setStroke(Color.BLACK);
        bell.setStrokeWidth(1.5);
        
        // Clapper (small circle inside bell)
        Circle clapper = new Circle(20, 26, 2);
        clapper.setFill(Color.BLACK);
        
        bellContainer.getChildren().addAll(bell, clapper);
        
        return bellContainer;
    }
}
