package application.network.handlers;

import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Handler for friend-related messages.
 * Handles: REQUEST_ADD_FRIEND, RESPONSE_ADD_FRIEND, UNFRIEND, INFO (friend list)
 */
public class FriendHandler implements MessageHandler {
    private final UIState uiState;
    
    public FriendHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    @Override
    public boolean canHandle(String messageType) {
        return messageType.equals("REQUEST_ADD_FRIEND") ||
               messageType.equals("RESPONSE_ADD_FRIEND") ||
               messageType.equals("UNFRIEND");
    }
    
    @Override
    public boolean handle(String messageType, String payload) {
        switch (messageType) {
            case "REQUEST_ADD_FRIEND":
                handleFriendRequest(payload);
                return true;
            case "RESPONSE_ADD_FRIEND":
                handleFriendResponse(payload);
                return true;
            case "UNFRIEND":
                handleUnfriend(payload);
                return true;
            default:
                return false;
        }
    }
    
    private void handleFriendRequest(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Show friend request notification
            // uiState.showFriendRequest(fromUser);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleFriendResponse(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            boolean accepted = json.has("accepted") && json.get("accepted").getAsBoolean();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Update friend list if accepted
            // if (accepted) uiState.addFriend(fromUser);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleUnfriend(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String user = json.has("user") ? json.get("user").getAsString() : "unknown";
            // TODO: Remove from friend list
            // uiState.removeFriend(user);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
}
