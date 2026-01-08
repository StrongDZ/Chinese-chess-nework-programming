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
            
            // Navigate to main menu FIRST to ensure UI changes immediately
            uiState.navigateToMainMenu();
            
            // Fetch user stats (elo) and friends list from backend after successful authentication
            // Do this AFTER navigation so UI doesn't get stuck
            try {
                if (networkManager != null && networkManager.isConnected() && username != null && !username.isEmpty()) {
                    // Fetch stats for both modes
                    try {
                        networkManager.info().requestUserStats(username, "all");  // Fetch all modes at once
                    } catch (Exception e) {
                        System.err.println("[AuthHandler] Failed to fetch user stats: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    // Request friends list
                    try {
                        networkManager.friend().requestFriendsList();
                    } catch (Exception e) {
                        System.err.println("[AuthHandler] Failed to fetch friends list: " + e.getMessage());
                        e.printStackTrace();
                    }
                    
                    // Request active game to restore game state if any
                    try {
                        networkManager.info().requestActiveGame(username);
                    } catch (Exception e) {
                        System.err.println("[AuthHandler] Failed to fetch active game: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                System.err.println("[AuthHandler] Error in handleAuthenticated: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void handleError(String payload) {
        try {
            JsonObject errorJson = JsonParser.parseString(payload).getAsJsonObject();
            String errorMessage = errorJson.has("message") 
                ? errorJson.get("message").getAsString() 
                : "Unknown error";
            
            // Show error message via toast notification
            if (uiState != null) {
                uiState.showToast(errorMessage);
            }
        } catch (Exception e) {
            // If parsing fails, show generic error message
            if (uiState != null) {
                uiState.showToast("An error occurred");
            }
        }
    }
}

