package application.state;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Central state container that mimics the custom-event flow in the React code.
 */
public class UIState {

    public enum BoardState { NORMAL, SMALL }
    public enum AppState { LANDING, MAIN_MENU, SETTINGS, IN_GAME, PROFILE }  // Thêm PROFILE

    private final ObjectProperty<BoardState> boardState = new SimpleObjectProperty<>(BoardState.NORMAL);
    private final ObjectProperty<AppState> appState = new SimpleObjectProperty<>(AppState.LANDING);
    private final BooleanProperty authPanelVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty loginVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty registerVisible = new SimpleBooleanProperty(false);
    private final StringProperty username = new SimpleStringProperty("username");  // Thêm username property
    private final BooleanProperty settingsVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty friendsVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty inventoryVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty historyVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final BooleanProperty profileVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final BooleanProperty waitingVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final BooleanProperty gameModeVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final BooleanProperty replayVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final BooleanProperty rankingVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final StringProperty replayGameId = new SimpleStringProperty("");  // Game ID for replay
    private final BooleanProperty replayPlayerIsRed = new SimpleBooleanProperty(true);  // Màu quân cờ của người chơi trong replay (từ database)
    private final BooleanProperty reconnectingVisible = new SimpleBooleanProperty(false);  // Reconnecting overlay visibility
    private final BooleanProperty classicModeVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty blitzModeVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty customModeVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty gameVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final BooleanProperty playWithFriendMode = new SimpleBooleanProperty(false);  // Phân biệt play with friend mode
    private final BooleanProperty aiDifficultyVisible = new SimpleBooleanProperty(false);  // Panel chọn độ khó AI
    
    // Timer values for game panel
    private final StringProperty timer1Value = new SimpleStringProperty("2:00");
    private final StringProperty timer2Value = new SimpleStringProperty("10:00");
    private final StringProperty timer3Value = new SimpleStringProperty("10:00");
    private final StringProperty timer4Value = new SimpleStringProperty("2:00");
    
    // Selected board image path
    private final StringProperty selectedBoardImagePath = new SimpleStringProperty("");
    
    // Elo scores by mode (default to 0, will be loaded from backend)
    private final javafx.beans.property.IntegerProperty classicalElo = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.IntegerProperty blitzElo = new javafx.beans.property.SimpleIntegerProperty(0);
    
    // Current game mode ("classical", "blitz", or null)
    private final StringProperty currentGameMode = new SimpleStringProperty("classical");  // Default to classical
    private int currentTimeLimit = 0;  // Time limit in seconds for matching (0 = unlimited)
    // AI difficulty: "easy", "medium", "hard"
    private final StringProperty aiDifficulty = new SimpleStringProperty("medium");
    
    // Custom board setup - lưu vị trí tùy chỉnh của các quân cờ
    // Format: Map<"row_col", "color_pieceType">, ví dụ: "0_0" -> "Red_Rook"
    private final javafx.beans.property.ObjectProperty<java.util.Map<String, String>> customBoardSetup = 
        new javafx.beans.property.SimpleObjectProperty<>(new java.util.HashMap<>());
    
    // Flag để biết có sử dụng custom board setup không
    private final BooleanProperty useCustomBoard = new SimpleBooleanProperty(false);
    
    // Game action trigger - để trigger các action từ network (game_result, draw_request, chat_message)
    private final StringProperty gameActionTrigger = new SimpleStringProperty("");
    
    // Game action result - kết quả của action (win, lose, draw, received, hide, message text)
    private final StringProperty gameActionResult = new SimpleStringProperty("");
    
    // Player side - true nếu player là red, false nếu là black
    private final BooleanProperty playerIsRed = new SimpleBooleanProperty(true);

    // Profile statistics
    private final javafx.beans.property.IntegerProperty totalMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.IntegerProperty winMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.DoubleProperty winRate = new javafx.beans.property.SimpleDoubleProperty(0.0);
    
    // Statistics by mode (similar to Elo)
    private final javafx.beans.property.IntegerProperty classicalTotalMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.IntegerProperty blitzTotalMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.IntegerProperty classicalWinMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.IntegerProperty blitzWinMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.DoubleProperty classicalWinRate = new javafx.beans.property.SimpleDoubleProperty(0.0);
    private final javafx.beans.property.DoubleProperty blitzWinRate = new javafx.beans.property.SimpleDoubleProperty(0.0);
    
    // Opponent profile (for game panel)
    private final StringProperty opponentUsername = new SimpleStringProperty("");
    private final javafx.beans.property.IntegerProperty opponentElo = new javafx.beans.property.SimpleIntegerProperty(100);
    
    // Friend list management
    private final javafx.collections.ObservableList<String> friendsList = javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.ObservableList<String> pendingFriendRequests = javafx.collections.FXCollections.observableArrayList();

    public ObjectProperty<BoardState> boardStateProperty() {
        return boardState;
    }

    public ObjectProperty<AppState> appStateProperty() {
        return appState;
    }

    public BooleanProperty authPanelVisibleProperty() {
        return authPanelVisible;
    }

    public BooleanProperty loginVisibleProperty() {
        return loginVisible;
    }

    public BooleanProperty registerVisibleProperty() {
        return registerVisible;
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public String getUsername() {
        return username.get();
    }

    public void setUsername(String value) {
        username.set(value);
    }

    public BooleanProperty settingsVisibleProperty() {
        return settingsVisible;
    }

    public boolean isSettingsVisible() {
        return settingsVisible.get();
    }

    public void setSettingsVisible(boolean value) {
        settingsVisible.set(value);
    }

    public void openSettings() {
        setSettingsVisible(true);
    }

    public void closeSettings() {
        setSettingsVisible(false);
    }

    public BooleanProperty friendsVisibleProperty() {
        return friendsVisible;
    }

    public boolean isFriendsVisible() {
        return friendsVisible.get();
    }

    public void setFriendsVisible(boolean value) {
        friendsVisible.set(value);
    }

    public void openFriends() {
        setFriendsVisible(true);
        setPlayWithFriendMode(false);  // Khi bấm icon friend, không phải play with friend mode
    }

    public void closeFriends() {
        setFriendsVisible(false);
        setPlayWithFriendMode(false);  // Reset khi đóng
    }
    
    public void openPlayWithFriend() {
        setPlayWithFriendMode(true);  // Đánh dấu đang trong play with friend mode
        setFriendsVisible(true);
    }
    
    public BooleanProperty playWithFriendModeProperty() {
        return playWithFriendMode;
    }
    
    public boolean isPlayWithFriendMode() {
        return playWithFriendMode.get();
    }
    
    public void setPlayWithFriendMode(boolean value) {
        playWithFriendMode.set(value);
    }

    public BooleanProperty inventoryVisibleProperty() {
        return inventoryVisible;
    }
    public boolean isInventoryVisible() {
        return inventoryVisible.get();
    }
    public void setInventoryVisible(boolean value) {
        inventoryVisible.set(value);
    }
    public void openInventory() {
        setInventoryVisible(true);
    }
    public void closeInventory() {
        setInventoryVisible(false);
    }

    public BooleanProperty historyVisibleProperty() {
        return historyVisible;
    }
    
    public boolean isHistoryVisible() {
        return historyVisible.get();
    }
    
    public void setHistoryVisible(boolean value) {
        historyVisible.set(value);
    }
    
    public void openHistory() {
        setHistoryVisible(true);
    }
    
    public void closeHistory() {
        setHistoryVisible(false);
    }

    public BooleanProperty profileVisibleProperty() {
        return profileVisible;
    }
    
    public BooleanProperty rankingVisibleProperty() {
        return rankingVisible;
    }
    
    public boolean isRankingVisible() {
        return rankingVisible.get();
    }
    
    public void setRankingVisible(boolean value) {
        rankingVisible.set(value);
    }
    
    public void openRanking() {
        setRankingVisible(true);
    }
    
    public void closeRanking() {
        setRankingVisible(false);
    }
    
    public boolean isProfileVisible() {
        return profileVisible.get();
    }
    
    public void setProfileVisible(boolean value) {
        profileVisible.set(value);
    }
    
    public void openProfile() {
        setProfileVisible(true);
        setAppState(AppState.PROFILE);
    }
    
    public void closeProfile() {
        setProfileVisible(false);
        setAppState(AppState.MAIN_MENU);
    }

    public BooleanProperty waitingVisibleProperty() {
        return waitingVisible;
    }

    public boolean isWaitingVisible() {
        return waitingVisible.get();
    }

    public void setWaitingVisible(boolean value) {
        waitingVisible.set(value);
    }
    
    public void openWaiting() {
        setWaitingVisible(true);
        setAppState(AppState.MAIN_MENU);
    }
    
    public void closeWaiting() {
        setWaitingVisible(false);
    }

    public BooleanProperty replayVisibleProperty() {
        return replayVisible;
    }
    
    public boolean isReplayVisible() {
        return replayVisible.get();
    }
    
    public void setReplayVisible(boolean value) {
        replayVisible.set(value);
    }
    
    public void openReplay() {
        setReplayVisible(true);
    }
    
    public void closeReplay() {
        setReplayVisible(false);
    }
    
    public StringProperty replayGameIdProperty() {
        return replayGameId;
    }
    
    public String getReplayGameId() {
        return replayGameId.get();
    }
    
    public void setReplayGameId(String value) {
        replayGameId.set(value);
    }
    
    public BooleanProperty replayPlayerIsRedProperty() {
        return replayPlayerIsRed;
    }
    
    public boolean isReplayPlayerRed() {
        return replayPlayerIsRed.get();
    }
    
    public void setReplayPlayerIsRed(boolean value) {
        replayPlayerIsRed.set(value);
    }
    
    /**
     * Set màu quân cờ của người chơi trong replay (từ database)
     * Được gọi từ InfoHandler khi nhận được game details
     */
    public void setReplayPlayerColor(boolean isRed, String redPlayer, String blackPlayer) {
        setReplayPlayerIsRed(isRed);
        System.out.println("[UIState] Set replay player color: isRed=" + isRed + 
            ", redPlayer=" + redPlayer + ", blackPlayer=" + blackPlayer);
    }
    
    // Callback để ReplayPanel nhận moves từ InfoHandler
    private java.util.function.Consumer<java.util.List<application.components.ReplayPanel.ReplayMove>> replayMovesCallback = null;
    
    public void setReplayMovesCallback(java.util.function.Consumer<java.util.List<application.components.ReplayPanel.ReplayMove>> callback) {
        this.replayMovesCallback = callback;
    }
    
    public java.util.function.Consumer<java.util.List<application.components.ReplayPanel.ReplayMove>> getReplayMovesCallback() {
        return replayMovesCallback;
    }

    public BooleanProperty gameModeVisibleProperty() {
        return gameModeVisible;
    }

    public boolean isGameModeVisible() {
        return gameModeVisible.get();
    }

    public void setGameModeVisible(boolean value) {
        gameModeVisible.set(value);
    }

    public void openGameMode() {
        setGameModeVisible(true);
    }

    public void closeGameMode() {
        setGameModeVisible(false);
    }

    public BooleanProperty reconnectingVisibleProperty() {
        return reconnectingVisible;
    }

    public boolean isReconnectingVisible() {
        return reconnectingVisible.get();
    }

    public void setReconnectingVisible(boolean value) {
        reconnectingVisible.set(value);
    }

    public BooleanProperty classicModeVisibleProperty() {
        return classicModeVisible;
    }

    public boolean isClassicModeVisible() {
        return classicModeVisible.get();
    }

    public void setClassicModeVisible(boolean value) {
        classicModeVisible.set(value);
    }

    public void openClassicMode() {
        setClassicModeVisible(true);
        setAppState(AppState.MAIN_MENU);  // Đảm bảo appState là MAIN_MENU
    }

    public void closeClassicMode() {
        setClassicModeVisible(false);
    }

    public BooleanProperty blitzModeVisibleProperty() {
        return blitzModeVisible;
    }

    public boolean isBlitzModeVisible() {
        return blitzModeVisible.get();
    }

    public void setBlitzModeVisible(boolean value) {
        blitzModeVisible.set(value);
    }

    public void openBlitzMode() {
        setBlitzModeVisible(true);
        setAppState(AppState.MAIN_MENU);  // Đảm bảo appState là MAIN_MENU
    }

    public void closeBlitzMode() {
        setBlitzModeVisible(false);
    }

    public BooleanProperty customModeVisibleProperty() {
        return customModeVisible;
    }

    public boolean isCustomModeVisible() {
        return customModeVisible.get();
    }

    public void setCustomModeVisible(boolean value) {
        customModeVisible.set(value);
    }

    public void openCustomMode() {
        setCustomModeVisible(true);
        setAppState(AppState.MAIN_MENU);  // Đảm bảo appState là MAIN_MENU
    }

    public void closeCustomMode() {
        setCustomModeVisible(false);
    }

    public BooleanProperty gameVisibleProperty() {
        return gameVisible;
    }

    public boolean isGameVisible() {
        return gameVisible.get();
    }

    public void setGameVisible(boolean value) {
        gameVisible.set(value);
    }

    public void openGame() {
        openGame("classical");  // Default to classical
    }
    
    public void openGame(String mode) {
        setCurrentGameMode(mode);  // Set game mode before opening game
        
        // Reset useCustomBoard nếu không phải custom mode
        if (!"custom".equals(mode)) {
            setUseCustomBoard(false);
        }
        // Nếu là custom mode, giữ nguyên useCustomBoard (đã được set khi save custom board)
        
        setGameVisible(true);
        setAppState(AppState.IN_GAME);
        // Đóng tất cả các mode panel
        setClassicModeVisible(false);
        setBlitzModeVisible(false);
        setCustomModeVisible(false);
        setGameModeVisible(false);
        closeAIDifficulty();
    }

    public void closeGame() {
        setGameVisible(false);
        setAppState(AppState.MAIN_MENU);
    }

    // === AI Difficulty Panel ===
    public BooleanProperty aiDifficultyVisibleProperty() {
        return aiDifficultyVisible;
    }

    public boolean isAiDifficultyVisible() {
        return aiDifficultyVisible.get();
    }

    public void setAiDifficultyVisible(boolean value) {
        aiDifficultyVisible.set(value);
    }

    public void openAIDifficulty() {
        setAiDifficultyVisible(true);
        setAppState(AppState.MAIN_MENU);
    }

    public void closeAIDifficulty() {
        setAiDifficultyVisible(false);
    }

    public String getAiDifficulty() {
        return aiDifficulty.get();
    }

    public void setAiDifficulty(String value) {
        if (value != null && !value.isEmpty()) {
            aiDifficulty.set(value);
        }
    }

    public void setBoardState(BoardState newState) {
        boardState.set(newState);
        if (newState == BoardState.NORMAL) {
            authPanelVisible.set(false);
        } else {
            authPanelVisible.set(true);
        }
    }

    public void setAppState(AppState newState) {
        appState.set(newState);
    }

    public void toggleBoardState() {
        if (boardState.get() != BoardState.SMALL) {
            openSmallBoard();
        } else {
            if (loginVisible.get()) {
                closeLoginPanel();
            } else if (registerVisible.get()) {
                closeRegisterPanel();
            } else {
                resetBoard();
            }
        }
    }

    private void openSmallBoard() {
        boardState.set(BoardState.SMALL);
        authPanelVisible.set(true);
    }

    private void resetBoard() {
        boardState.set(BoardState.NORMAL);
        authPanelVisible.set(false);
    }

    public void openLoginPanel() {
        loginVisible.set(true);
        registerVisible.set(false);
        authPanelVisible.set(false);
    }

    public void closeLoginPanel() {
        loginVisible.set(false);
        if (boardState.get() == BoardState.SMALL && !registerVisible.get()) {
            authPanelVisible.set(true);
        }
    }

    public void openRegisterPanel() {
        registerVisible.set(true);
        loginVisible.set(false);
        authPanelVisible.set(false);
    }

    public void closeRegisterPanel() {
        registerVisible.set(false);
        if (boardState.get() == BoardState.SMALL && !loginVisible.get()) {
            authPanelVisible.set(true);
        }
    }

    public void navigateToMainMenu() {
        setAppState(AppState.MAIN_MENU);
        loginVisible.set(false);
        registerVisible.set(false);
        authPanelVisible.set(false);
        boardState.set(BoardState.NORMAL);
    }

    public StringProperty timer1ValueProperty() {
        return timer1Value;
    }

    public String getTimer1Value() {
        return timer1Value.get();
    }

    public void setTimer1Value(String value) {
        timer1Value.set(value);
    }

    public StringProperty timer2ValueProperty() {
        return timer2Value;
    }

    public String getTimer2Value() {
        return timer2Value.get();
    }

    public void setTimer2Value(String value) {
        timer2Value.set(value);
    }

    public StringProperty timer3ValueProperty() {
        return timer3Value;
    }

    public String getTimer3Value() {
        return timer3Value.get();
    }

    public void setTimer3Value(String value) {
        timer3Value.set(value);
    }

    public StringProperty timer4ValueProperty() {
        return timer4Value;
    }

    public String getTimer4Value() {
        return timer4Value.get();
    }

    public void setTimer4Value(String value) {
        timer4Value.set(value);
    }

    // Thêm getter/setter cho selectedBoardImagePath
    public StringProperty selectedBoardImagePathProperty() {
        return selectedBoardImagePath;
    }

    public String getSelectedBoardImagePath() {
        return selectedBoardImagePath.get();
    }

    public void setSelectedBoardImagePath(String value) {
        selectedBoardImagePath.set(value);
    }
    
    // Elo getters/setters by mode
    public javafx.beans.property.IntegerProperty classicalEloProperty() {
        return classicalElo;
    }
    
    public int getClassicalElo() {
        return classicalElo.get();
    }
    
    public void setClassicalElo(int value) {
        classicalElo.set(value);
    }
    
    public javafx.beans.property.IntegerProperty blitzEloProperty() {
        return blitzElo;
    }
    
    public int getBlitzElo() {
        return blitzElo.get();
    }
    
    public void setBlitzElo(int value) {
        blitzElo.set(value);
    }
    
    // Current game mode getters/setters
    public StringProperty currentGameModeProperty() {
        return currentGameMode;
    }
    
    public String getCurrentGameMode() {
        return currentGameMode.get();
    }
    
    public void setCurrentGameMode(String value) {
        currentGameMode.set(value);
    }
    
    public int getCurrentTimeLimit() {
        return currentTimeLimit;
    }
    
    public void setCurrentTimeLimit(int value) {
        this.currentTimeLimit = value;
    }
    
    // Get elo based on current game mode
    public int getElo() {
        String mode = currentGameMode.get();
        if (mode == null || mode.isEmpty()) {
            mode = "classical";  // Default
        }
        return getElo(mode);
    }
    
    // Get elo for specific mode
    public int getElo(String mode) {
        if ("blitz".equalsIgnoreCase(mode)) {
            return getBlitzElo();
        } else {
            return getClassicalElo();  // Default to classical
        }
    }
    
    // Set elo for specific mode
    public void setElo(String mode, int value) {
        if ("blitz".equalsIgnoreCase(mode)) {
            setBlitzElo(value);
        } else {
            setClassicalElo(value);  // Default to classical
        }
    }
    
    // Add elo for current game mode
    public void addElo(int delta) {
        String mode = currentGameMode.get();
        if (mode == null || mode.isEmpty()) {
            mode = "classical";  // Default
        }
        addElo(mode, delta);
    }
    
    // Add elo for specific mode
    public void addElo(String mode, int delta) {
        if ("blitz".equalsIgnoreCase(mode)) {
            setBlitzElo(getBlitzElo() + delta);
        } else {
            setClassicalElo(getClassicalElo() + delta);  // Default to classical
        }
    }
    
    // Property binding for current elo (changes based on currentGameMode)
    // Note: This returns a read-only binding. Use getElo() or getElo(mode) to get values.
    // For binding in UI, bind directly to classicalEloProperty() or blitzEloProperty() based on mode.
    public javafx.beans.binding.IntegerBinding eloProperty() {
        // Return a computed binding that changes based on currentGameMode
        return javafx.beans.binding.Bindings.createIntegerBinding(
            () -> getElo(),
            currentGameMode,
            classicalElo,
            blitzElo
        );
    }

    // Profile statistics getters/setters
    public javafx.beans.property.IntegerProperty totalMatchesProperty() {
        return totalMatches;
    }
    
    public int getTotalMatches() {
        return totalMatches.get();
    }
    
    public void setTotalMatches(int value) {
        totalMatches.set(value);
    }
    
    public javafx.beans.property.IntegerProperty winMatchesProperty() {
        return winMatches;
    }
    
    public int getWinMatches() {
        return winMatches.get();
    }
    
    public void setWinMatches(int value) {
        winMatches.set(value);
    }
    
    public javafx.beans.property.DoubleProperty winRateProperty() {
        return winRate;
    }
    
    public double getWinRate() {
        return winRate.get();
    }
    
    public void setWinRate(double value) {
        winRate.set(value);
    }
    
    // Statistics by mode (similar to Elo)
    // Total matches by mode
    public javafx.beans.property.IntegerProperty classicalTotalMatchesProperty() {
        return classicalTotalMatches;
    }
    
    public int getClassicalTotalMatches() {
        return classicalTotalMatches.get();
    }
    
    public void setClassicalTotalMatches(int value) {
        classicalTotalMatches.set(value);
    }
    
    public javafx.beans.property.IntegerProperty blitzTotalMatchesProperty() {
        return blitzTotalMatches;
    }
    
    public int getBlitzTotalMatches() {
        return blitzTotalMatches.get();
    }
    
    public void setBlitzTotalMatches(int value) {
        blitzTotalMatches.set(value);
    }
    
    // Win matches by mode
    public javafx.beans.property.IntegerProperty classicalWinMatchesProperty() {
        return classicalWinMatches;
    }
    
    public int getClassicalWinMatches() {
        return classicalWinMatches.get();
    }
    
    public void setClassicalWinMatches(int value) {
        classicalWinMatches.set(value);
    }
    
    public javafx.beans.property.IntegerProperty blitzWinMatchesProperty() {
        return blitzWinMatches;
    }
    
    public int getBlitzWinMatches() {
        return blitzWinMatches.get();
    }
    
    public void setBlitzWinMatches(int value) {
        blitzWinMatches.set(value);
    }
    
    // Win rate by mode
    public javafx.beans.property.DoubleProperty classicalWinRateProperty() {
        return classicalWinRate;
    }
    
    public double getClassicalWinRate() {
        return classicalWinRate.get();
    }
    
    public void setClassicalWinRate(double value) {
        classicalWinRate.set(value);
    }
    
    public javafx.beans.property.DoubleProperty blitzWinRateProperty() {
        return blitzWinRate;
    }
    
    public double getBlitzWinRate() {
        return blitzWinRate.get();
    }
    
    public void setBlitzWinRate(double value) {
        blitzWinRate.set(value);
    }
    
    // Get total matches for specific mode
    public int getTotalMatches(String mode) {
        if ("blitz".equalsIgnoreCase(mode)) {
            return getBlitzTotalMatches();
        } else {
            return getClassicalTotalMatches();  // Default to classical
        }
    }
    
    // Set total matches for specific mode
    public void setTotalMatches(String mode, int value) {
        if ("blitz".equalsIgnoreCase(mode)) {
            setBlitzTotalMatches(value);
        } else {
            setClassicalTotalMatches(value);  // Default to classical
        }
    }
    
    // Get win matches for specific mode
    public int getWinMatches(String mode) {
        if ("blitz".equalsIgnoreCase(mode)) {
            return getBlitzWinMatches();
        } else {
            return getClassicalWinMatches();  // Default to classical
        }
    }
    
    // Set win matches for specific mode
    public void setWinMatches(String mode, int value) {
        if ("blitz".equalsIgnoreCase(mode)) {
            setBlitzWinMatches(value);
        } else {
            setClassicalWinMatches(value);  // Default to classical
        }
    }
    
    // Get win rate for specific mode
    public double getWinRate(String mode) {
        if ("blitz".equalsIgnoreCase(mode)) {
            return getBlitzWinRate();
        } else {
            return getClassicalWinRate();  // Default to classical
        }
    }
    
    // Set win rate for specific mode
    public void setWinRate(String mode, double value) {
        if ("blitz".equalsIgnoreCase(mode)) {
            setBlitzWinRate(value);
        } else {
            setClassicalWinRate(value);  // Default to classical
        }
    }
    
    // Opponent profile getters/setters
    public StringProperty opponentUsernameProperty() {
        return opponentUsername;
    }
    
    public String getOpponentUsername() {
        return opponentUsername.get();
    }
    
    public void setOpponentUsername(String value) {
        opponentUsername.set(value);
    }
    
    public javafx.beans.property.IntegerProperty opponentEloProperty() {
        return opponentElo;
    }
    
    public int getOpponentElo() {
        return opponentElo.get();
    }
    
    public void setOpponentElo(int value) {
        opponentElo.set(value);
    }
    
    // Custom board setup getters/setters
    public javafx.beans.property.ObjectProperty<java.util.Map<String, String>> customBoardSetupProperty() {
        return customBoardSetup;
    }
    
    public java.util.Map<String, String> getCustomBoardSetup() {
        return customBoardSetup.get();
    }
    
    public void setCustomBoardSetup(java.util.Map<String, String> value) {
        customBoardSetup.set(value);
    }
    
    public BooleanProperty useCustomBoardProperty() {
        return useCustomBoard;
    }
    
    public boolean isUseCustomBoard() {
        return useCustomBoard.get();
    }
    
    public void setUseCustomBoard(boolean value) {
        useCustomBoard.set(value);
    }
    
    // Game action trigger getters/setters
    public StringProperty gameActionTriggerProperty() {
        return gameActionTrigger;
    }
    
    public String getGameActionTrigger() {
        return gameActionTrigger.get();
    }
    
    public void setGameActionTrigger(String value) {
        gameActionTrigger.set(value);
    }
    
    // Game action result getters/setters
    public StringProperty gameActionResultProperty() {
        return gameActionResult;
    }
    
    public String getGameActionResult() {
        return gameActionResult.get();
    }
    
    public void setGameActionResult(String value) {
        gameActionResult.set(value);
    }
    
    // Player side getters/setters
    public BooleanProperty playerIsRedProperty() {
        return playerIsRed;
    }
    
    public boolean isPlayerRed() {
        return playerIsRed.get();
    }
    
    public void setPlayerIsRed(boolean value) {
        playerIsRed.set(value);
    }
    
    // Friend list getters/setters
    public javafx.collections.ObservableList<String> getFriendsList() {
        return friendsList;
    }
    
    public void addFriend(String username) {
        if (username != null && !username.isEmpty() && !friendsList.contains(username)) {
            friendsList.add(username);
        }
    }
    
    public void removeFriend(String username) {
        if (username != null) {
            friendsList.remove(username);
        }
    }
    
    public void clearFriends() {
        friendsList.clear();
    }
    
    // Pending friend requests getters/setters
    public javafx.collections.ObservableList<String> getPendingFriendRequests() {
        return pendingFriendRequests;
    }
    
    public void addPendingFriendRequest(String fromUser) {
        if (fromUser != null && !fromUser.isEmpty() && !pendingFriendRequests.contains(fromUser)) {
            pendingFriendRequests.add(fromUser);
        }
    }
    
    public void removePendingFriendRequest(String fromUser) {
        if (fromUser != null) {
            pendingFriendRequests.remove(fromUser);
        }
    }
    
    public void clearPendingFriendRequests() {
        pendingFriendRequests.clear();
    }
    
    // Callback for updating online players list (used by InfoHandler)
    private java.util.function.Consumer<java.util.List<String>> onlinePlayersUpdateCallback;
    private java.util.function.Consumer<java.util.List<String>> onlinePlayersNotInGameUpdateCallback;  // Players not in game
    private java.util.function.Consumer<java.util.List<String>> searchResultsUpdateCallback;
    private java.util.function.BiConsumer<java.util.List<application.components.FriendsPanel.FriendRequestInfo>, 
                                         java.util.List<application.components.FriendsPanel.FriendRequestInfo>> friendRequestsUpdateCallback;
    // Callback for updating badge on bottom menu Friends icon
    private java.util.function.Consumer<Integer> friendsBadgeUpdateCallback;
    // Callback for updating friend elo in PlayWithFriendPanel (username, timeControl, elo)
    private TriConsumer<String, String, Integer> friendEloUpdateCallback;
    // Callback for updating game history (used by InfoHandler)
    private java.util.function.BiConsumer<java.util.List<application.components.HistoryPanel.HistoryEntry>,
                                         java.util.List<application.components.HistoryPanel.HistoryEntry>> gameHistoryUpdateCallback;
    
    // Callback for updating leaderboard (used by InfoHandler)
    private java.util.function.Consumer<com.google.gson.JsonObject> leaderboardUpdateCallback;
    
    // Callback for applying opponent move (from server)
    private OpponentMoveCallback opponentMoveCallback;
    
    // Interface for opponent move callback
    @FunctionalInterface
    public interface OpponentMoveCallback {
        void onOpponentMove(int fromCol, int fromRow, int toCol, int toRow);
    }
    
    public void setOnlinePlayersUpdateCallback(java.util.function.Consumer<java.util.List<String>> callback) {
        this.onlinePlayersUpdateCallback = callback;
    }
    
    public void setOnlinePlayersNotInGameUpdateCallback(java.util.function.Consumer<java.util.List<String>> callback) {
        this.onlinePlayersNotInGameUpdateCallback = callback;
    }
    
    public void setSearchResultsUpdateCallback(java.util.function.Consumer<java.util.List<String>> callback) {
        this.searchResultsUpdateCallback = callback;
    }
    
    public void setFriendRequestsUpdateCallback(java.util.function.BiConsumer<
        java.util.List<application.components.FriendsPanel.FriendRequestInfo>,
        java.util.List<application.components.FriendsPanel.FriendRequestInfo>> callback) {
        this.friendRequestsUpdateCallback = callback;
    }
    
    public void setFriendsBadgeUpdateCallback(java.util.function.Consumer<Integer> callback) {
        this.friendsBadgeUpdateCallback = callback;
    }
    
    public void setFriendEloUpdateCallback(TriConsumer<String, String, Integer> callback) {
        this.friendEloUpdateCallback = callback;
    }
    
    public void setGameHistoryUpdateCallback(java.util.function.BiConsumer<
        java.util.List<application.components.HistoryPanel.HistoryEntry>,
        java.util.List<application.components.HistoryPanel.HistoryEntry>> callback) {
        this.gameHistoryUpdateCallback = callback;
    }
    
    public java.util.function.BiConsumer<
        java.util.List<application.components.HistoryPanel.HistoryEntry>,
        java.util.List<application.components.HistoryPanel.HistoryEntry>> getGameHistoryUpdateCallback() {
        return gameHistoryUpdateCallback;
    }
    
    public void setLeaderboardUpdateCallback(java.util.function.Consumer<com.google.gson.JsonObject> callback) {
        this.leaderboardUpdateCallback = callback;
    }
    
    public java.util.function.Consumer<com.google.gson.JsonObject> getLeaderboardUpdateCallback() {
        return leaderboardUpdateCallback;
    }
    
    public void updateOnlinePlayers(java.util.List<String> players) {
        if (onlinePlayersUpdateCallback != null) {
            onlinePlayersUpdateCallback.accept(players);
        }
    }
    
    public void updateOnlinePlayersNotInGame(java.util.List<String> players) {
        if (onlinePlayersNotInGameUpdateCallback != null) {
            onlinePlayersNotInGameUpdateCallback.accept(players);
        }
    }
    
    public void updateSearchResults(java.util.List<String> results) {
        if (searchResultsUpdateCallback != null) {
            searchResultsUpdateCallback.accept(results);
        }
    }
    
    public void updateFriendRequests(java.util.List<application.components.FriendsPanel.FriendRequestInfo> pending,
                                     java.util.List<application.components.FriendsPanel.FriendRequestInfo> accepted) {
        if (friendRequestsUpdateCallback != null) {
            friendRequestsUpdateCallback.accept(pending, accepted);
        }
        
        // Calculate total for badge: pending + accepted (rejected không đếm vì backend xóa)
        int totalCount = (pending != null ? pending.size() : 0) + (accepted != null ? accepted.size() : 0);
        updateFriendsBadge(totalCount);
    }
    
    public void updateGameHistory(java.util.List<application.components.HistoryPanel.HistoryEntry> peopleHistory,
                                  java.util.List<application.components.HistoryPanel.HistoryEntry> aiHistory) {
        if (gameHistoryUpdateCallback != null) {
            gameHistoryUpdateCallback.accept(peopleHistory, aiHistory);
        }
    }
    
    public void updateFriendsBadge(int count) {
        if (friendsBadgeUpdateCallback != null) {
            friendsBadgeUpdateCallback.accept(count);
        }
    }
    
    public void updateFriendElo(String username, String timeControl, int elo) {
        if (friendEloUpdateCallback != null) {
            friendEloUpdateCallback.accept(username, timeControl, elo);
        }
    }
    
    // Helper interface for TriConsumer (Java 8 doesn't have it)
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
    
    // Set opponent move callback
    public void setOpponentMoveCallback(OpponentMoveCallback callback) {
        this.opponentMoveCallback = callback;
    }
    
    // Apply opponent move from server
    public void applyOpponentMove(int fromCol, int fromRow, int toCol, int toRow) {
        if (opponentMoveCallback != null) {
            opponentMoveCallback.onOpponentMove(fromCol, fromRow, toCol, toRow);
        } else {
            System.err.println("[UIState] opponentMoveCallback is null, cannot apply move");
        }
    }
    
    // Toast notification property
    private final StringProperty toastMessage = new SimpleStringProperty("");
    
    public StringProperty toastMessageProperty() {
        return toastMessage;
    }
    
    public String getToastMessage() {
        return toastMessage.get();
    }
    
    public void setToastMessage(String value) {
        toastMessage.set(value);
    }
    
    /**
     * Show toast notification with error message.
     * @param message Error message to display
     */
    public void showToast(String message) {
        if (message != null && !message.isEmpty()) {
            // Reset to empty first to ensure listener triggers even if same message
            // This ensures the property change listener will fire even for duplicate messages
            String currentMessage = getToastMessage();
            if (message.equals(currentMessage)) {
                // If same message, reset first then set again
                setToastMessage("");
                javafx.application.Platform.runLater(() -> {
                    setToastMessage(message);
                });
            } else {
                // Different message, can set directly
                setToastMessage(message);
            }
        }
    }
}
