package application.network.handlers;

import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Handler for game-related messages.
 * Handles: GAME_START, MOVE, INVALID_MOVE, GAME_END, CHALLENGE_REQUEST, CHALLENGE_RESPONSE
 */
public class GameHandler implements MessageHandler {
    private final UIState uiState;
    
    public GameHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    @Override
    public boolean canHandle(String messageType) {
        return messageType.equals("GAME_START") ||
               messageType.equals("MOVE") ||
               messageType.equals("INVALID_MOVE") ||
               messageType.equals("GAME_END") ||
               messageType.equals("CHALLENGE_REQUEST") ||
               messageType.equals("CHALLENGE_RESPONSE") ||
               messageType.equals("CHALLENGE_CANCEL");
    }
    
    @Override
    public boolean handle(String messageType, String payload) {
        switch (messageType) {
            case "GAME_START":
                handleGameStart(payload);
                return true;
            case "MOVE":
                handleMove(payload);
                return true;
            case "INVALID_MOVE":
                handleInvalidMove(payload);
                return true;
            case "GAME_END":
                handleGameEnd(payload);
                return true;
            case "CHALLENGE_REQUEST":
                handleChallengeRequest(payload);
                return true;
            case "CHALLENGE_RESPONSE":
                handleChallengeResponse(payload);
                return true;
            case "CHALLENGE_CANCEL":
                handleChallengeCancel(payload);
                return true;
            default:
                return false;
        }
    }
    
    private void handleGameStart(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            // TODO: Extract game info and start game in UIState
            // String opponent = json.get("opponent").getAsString();
            // String myColor = json.get("my_color").getAsString();
            // uiState.startGame(opponent, myColor);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleMove(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            // TODO: Apply opponent's move to the board
            // int fromX = json.get("from_x").getAsInt();
            // int fromY = json.get("from_y").getAsInt();
            // int toX = json.get("to_x").getAsInt();
            // int toY = json.get("to_y").getAsInt();
            // uiState.applyOpponentMove(fromX, fromY, toX, toY);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleInvalidMove(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Invalid move";
            // TODO: Show invalid move message and revert
            // uiState.showInvalidMove(reason);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleGameEnd(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String winSide = json.has("win_side") ? json.get("win_side").getAsString() : "unknown";
            // TODO: Show game result
            // uiState.endGame(winSide);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleChallengeRequest(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Show challenge dialog
            // uiState.showChallengeDialog(fromUser);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleChallengeResponse(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            boolean accepted = json.has("accepted") && json.get("accepted").getAsBoolean();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Handle challenge response
            // if (accepted) uiState.startGameWithOpponent(fromUser);
            // else uiState.showChallengeRejected(fromUser);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleChallengeCancel(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Hide challenge dialog
            // uiState.hideChallengeDialog();
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
}
