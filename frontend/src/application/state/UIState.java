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
    private final BooleanProperty gameModeVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    private final BooleanProperty classicModeVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty blitzModeVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty customModeVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty gameVisible = new SimpleBooleanProperty(false);  // Thêm dòng này
    
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
    
    // Custom board setup - lưu vị trí tùy chỉnh của các quân cờ
    // Format: Map<"row_col", "color_pieceType">, ví dụ: "0_0" -> "Red_Rook"
    private final javafx.beans.property.ObjectProperty<java.util.Map<String, String>> customBoardSetup = 
        new javafx.beans.property.SimpleObjectProperty<>(new java.util.HashMap<>());
    
    // Flag để biết có sử dụng custom board setup không
    private final BooleanProperty useCustomBoard = new SimpleBooleanProperty(false);

    // Profile statistics
    private final javafx.beans.property.IntegerProperty totalMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.IntegerProperty winMatches = new javafx.beans.property.SimpleIntegerProperty(0);
    private final javafx.beans.property.DoubleProperty winRate = new javafx.beans.property.SimpleDoubleProperty(0.0);
    
    // Opponent profile (for game panel)
    private final StringProperty opponentUsername = new SimpleStringProperty("");
    private final javafx.beans.property.IntegerProperty opponentElo = new javafx.beans.property.SimpleIntegerProperty(100);

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
    }

    public void closeFriends() {
        setFriendsVisible(false);
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
    }

    public void closeGame() {
        setGameVisible(false);
        setAppState(AppState.MAIN_MENU);
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
}

