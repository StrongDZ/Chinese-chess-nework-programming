package application.network.handlers;

import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

