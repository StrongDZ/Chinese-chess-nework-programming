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
        System.out.println("[DEBUG AuthHandler] Handling: " + messageType + " payload: " + payload);
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
        System.out.println("[DEBUG AuthHandler] AUTHENTICATED received!");
        if (uiState != null) {
            // Set username in socket client context
            String username = uiState.getUsername();
            System.out.println("[DEBUG AuthHandler] Username from UIState: " + username);
            if (username != null && !username.isEmpty() && onUsernameSet != null) {
                onUsernameSet.accept(username);
            }
            
            // Fetch user stats (elo) from backend after successful authentication
            try {
                NetworkManager networkManager = NetworkManager.getInstance();
                if (networkManager.isConnected() && username != null && !username.isEmpty()) {
                    // Fetch stats for both modes
                    networkManager.info().requestUserStats(username, "all");  // Fetch all modes at once
                }
            } catch (IOException e) {
                System.err.println("[AuthHandler] Failed to fetch user stats: " + e.getMessage());
            }
            
            // Navigate to main menu on successful authentication
            System.out.println("[DEBUG AuthHandler] Navigating to main menu...");
            uiState.navigateToMainMenu();
            System.out.println("[DEBUG AuthHandler] Navigation called");
        } else {
            System.err.println("[DEBUG AuthHandler] ERROR: uiState is null!");
        }
    }
    
    private void handleError(String payload) {
        System.err.println("[DEBUG AuthHandler] ERROR received: " + payload);
        try {
            JsonObject errorJson = JsonParser.parseString(payload).getAsJsonObject();
            String errorMessage = errorJson.has("message") 
                ? errorJson.get("message").getAsString() 
                : "Unknown error";
            System.err.println("[DEBUG AuthHandler] Error message: " + errorMessage);
            
            // TODO: Show error message to user via UIState
            // uiState.showError(errorMessage);
        } catch (Exception e) {
            System.err.println("[DEBUG AuthHandler] Failed to parse error: " + e.getMessage());
        }
    }
}

