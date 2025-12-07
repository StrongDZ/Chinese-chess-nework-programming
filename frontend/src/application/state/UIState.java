package application.state;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Central state container that mimics the custom-event flow in the React code.
 */
public class UIState {

    public enum BoardState { NORMAL, SMALL }

    private final ObjectProperty<BoardState> boardState = new SimpleObjectProperty<>(BoardState.NORMAL);
    private final BooleanProperty authPanelVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty loginVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty registerVisible = new SimpleBooleanProperty(false);

    public ObjectProperty<BoardState> boardStateProperty() {
        return boardState;
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

    public void setBoardState(BoardState newState) {
        boardState.set(newState);
        if (newState == BoardState.NORMAL) {
            authPanelVisible.set(false);
        } else {
            authPanelVisible.set(true);
        }
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
}

