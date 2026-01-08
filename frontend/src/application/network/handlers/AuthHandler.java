package application.network.handlers;

import application.network.NetworkManager;
import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Handler for authentication-related messages.
 * Handles: AUTHENTICATED, ERROR
 */
public class AuthHandler implements MessageHandler {
    private final UIState uiState;
    private Consumer<String> onUsernameSet;
    
    public AuthHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    /**
     * Set callback for when username should be set in SocketClient.
     */
    public void setOnUsernameSet(Consumer<String> callback) {
        this.onUsernameSet = callback;
    }
    
    @Override
    public boolean canHandle(String messageType) {
        return messageType.equals("AUTHENTICATED") || 
               messageType.equals("ERROR");
    }
    
    @Override
    public boolean handle(String messageType, String payload) {
        switch (messageType) {
            case "AUTHENTICATED":
                handleAuthenticated();
                return true;
            case "ERROR":
                handleError(payload);
                return true;
            default:
                return false;
        }
    }
    
    private void handleAuthenticated() {
        if (uiState != null) {
            // Set username in socket client context
            String username = uiState.getUsername();
            if (username != null && !username.isEmpty() && onUsernameSet != null) {
                onUsernameSet.accept(username);
            }
            
            NetworkManager networkManager = NetworkManager.getInstance();
            
            // Hide reconnecting overlay if it was showing (auto-login after reconnect)
            networkManager.hideReconnectingOverlay();
            
            // Fetch user stats (elo) and friends list from backend after successful authentication
            try {
                if (networkManager.isConnected() && username != null && !username.isEmpty()) {
                    // Fetch stats for both modes
                    networkManager.info().requestUserStats(username, "all");  // Fetch all modes at once
                    // Request friends list
                    networkManager.friend().requestFriendsList();
                    // Request active game to restore game state if any
                    networkManager.info().requestActiveGame(username);
                }
            } catch (IOException e) {
                System.err.println("[AuthHandler] Failed to fetch user data: " + e.getMessage());
            }
            
            // Navigate to main menu on successful authentication
            uiState.navigateToMainMenu();
        }
    }
    
    private void handleError(String payload) {
        try {
            JsonObject errorJson = JsonParser.parseString(payload).getAsJsonObject();
            String errorMessage = errorJson.has("message") 
                ? errorJson.get("message").getAsString() 
                : "Unknown error";
            
            // TODO: Show error message to user via UIState
            // uiState.showError(errorMessage);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
}

