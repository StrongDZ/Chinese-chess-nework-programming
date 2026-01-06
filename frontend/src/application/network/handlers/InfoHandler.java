package application.network.handlers;

import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

/**
 * Handler for INFO messages (generic info from server).
 * Handles: INFO, PLAYER_LIST, USER_STATS, LEADER_BOARD
 */
public class InfoHandler implements MessageHandler {
    private final UIState uiState;
    
    public InfoHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    @Override
    public boolean canHandle(String messageType) {
        return messageType.equals("INFO") ||
               messageType.equals("PLAYER_LIST") ||
               messageType.equals("USER_STATS") ||
               messageType.equals("LEADER_BOARD");
    }
    
    @Override
    public boolean handle(String messageType, String payload) {
        switch (messageType) {
            case "INFO":
                handleInfo(payload);
                return true;
            case "PLAYER_LIST":
                handlePlayerList(payload);
                return true;
            case "USER_STATS":
                handleUserStats(payload);
                return true;
            case "LEADER_BOARD":
                handleLeaderBoard(payload);
                return true;
            default:
                return false;
        }
    }
    
    private void handleInfo(String payload) {
        // INFO can have various formats
        // TODO: Parse and handle specific info types
    }
    
    private void handlePlayerList(String payload) {
        try {
            JsonArray players = JsonParser.parseString(payload).getAsJsonArray();
            // TODO: Update player list in UIState
            // uiState.updatePlayerList(players);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleUserStats(String payload) {
        try {
            JsonObject stats = JsonParser.parseString(payload).getAsJsonObject();
            // TODO: Update user stats in UIState
            // uiState.updateUserStats(stats);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleLeaderBoard(String payload) {
        try {
            JsonArray leaderboard = JsonParser.parseString(payload).getAsJsonArray();
            // TODO: Update leaderboard in UIState
            // uiState.updateLeaderboard(leaderboard);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
}
