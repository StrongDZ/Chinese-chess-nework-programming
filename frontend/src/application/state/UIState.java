package application.state;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Central state container that mimics the custom-event flow in the React code.
 */
public class UIState {

    public enum BoardState { NORMAL, SMALL }
    public enum AppState { LANDING, MAIN_MENU, SETTINGS, IN_GAME }

    private final ObjectProperty<BoardState> boardState = new SimpleObjectProperty<>(BoardState.NORMAL);
    private final ObjectProperty<AppState> appState = new SimpleObjectProperty<>(AppState.LANDING);
    private final BooleanProperty authPanelVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty loginVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty registerVisible = new SimpleBooleanProperty(false);
    private final StringProperty username = new SimpleStringProperty("username");  // Thêm username property
    private final BooleanProperty settingsVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty friendsVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty inventoryVisible = new SimpleBooleanProperty(false);
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
    
    // Elo score
    private final javafx.beans.property.IntegerProperty elo = new javafx.beans.property.SimpleIntegerProperty(100);

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
    
    // Thêm getter/setter cho elo
    public javafx.beans.property.IntegerProperty eloProperty() {
        return elo;
    }
    
    public int getElo() {
        return elo.get();
    }
    
    public void setElo(int value) {
        elo.set(value);
    }
    
    public void addElo(int delta) {
        elo.set(elo.get() + delta);
    }
}

