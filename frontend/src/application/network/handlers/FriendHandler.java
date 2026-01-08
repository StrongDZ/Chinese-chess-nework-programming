package application.network.handlers;

import application.components.FriendRequestNotificationDialog;
import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.scene.layout.Pane;

/**
 * Handler for friend-related messages.
 * Handles: REQUEST_ADD_FRIEND, RESPONSE_ADD_FRIEND, UNFRIEND, INFO (friend list)
 */
public class FriendHandler implements MessageHandler {
    private final UIState uiState;
    private Pane rootPane; // Reference to root pane for showing dialogs
    
    public FriendHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    /**
     * Set root pane reference for showing notification dialogs.
     */
    public void setRootPane(Pane rootPane) {
        this.rootPane = rootPane;
    }
    
    @Override
    public boolean canHandle(String messageType) {
        return messageType.equals("REQUEST_ADD_FRIEND") ||
               messageType.equals("RESPONSE_ADD_FRIEND") ||
               messageType.equals("UNFRIEND") ||
               messageType.equals("INFO"); // Handle INFO messages for friend-related responses
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
            case "INFO":
                // Only handle if it's friend-related, otherwise let InfoHandler handle it
                return handleInfo(payload);
            default:
                return false;
        }
    }
    
    private void handleFriendRequest(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            
            // Add to pending requests list
            Platform.runLater(() -> {
                uiState.addPendingFriendRequest(fromUser);
                
                // Show notification dialog if root pane is available
                if (rootPane != null) {
                    FriendRequestNotificationDialog dialog = new FriendRequestNotificationDialog(
                        fromUser,
                        () -> uiState.removePendingFriendRequest(fromUser)
                    );
                    rootPane.getChildren().add(dialog);
                }
            });
        } catch (Exception e) {
            System.err.println("[FriendHandler] Error handling friend request: " + e.getMessage());
        }
    }
    
    private void handleFriendResponse(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            boolean accepted = json.has("accepted") ? json.get("accepted").getAsBoolean() : false;
            // Backend sends from_user (the responder) when forwarding RESPONSE_ADD_FRIEND
            // This means the responder accepted/declined, so we add/remove them from friend list
            String responderUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            
            Platform.runLater(() -> {
                if (accepted) {
                    // Add responder to friend list (they accepted our request)
                    uiState.addFriend(responderUser);
                }
                // Remove from pending requests if it was there
                uiState.removePendingFriendRequest(responderUser);
            });
        } catch (Exception e) {
            System.err.println("[FriendHandler] Error handling friend response: " + e.getMessage());
        }
    }
    
    private void handleUnfriend(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            // Backend sends INFO message with unfriend info, not UNFRIEND directly
            // But we handle it here if needed
            String user = json.has("to_user") ? json.get("to_user").getAsString() : 
                         json.has("user") ? json.get("user").getAsString() : "unknown";
            
            Platform.runLater(() -> {
                uiState.removeFriend(user);
            });
        } catch (Exception e) {
            System.err.println("[FriendHandler] Error handling unfriend: " + e.getMessage());
        }
    }
    
    /**
     * Handle INFO message - only if it's friend-related.
     * @return true if handled (friend-related), false otherwise (let InfoHandler handle it)
     */
    private boolean handleInfo(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            boolean handled = false;
            
            // Check if wrapped in "data" field (backend wraps INFO in data field)
            JsonObject dataObj = json;
            if (json.has("data") && json.get("data").isJsonObject()) {
                dataObj = json.getAsJsonObject("data");
            }
            
            // Check if this is a friends list response
            if (dataObj.has("friends") && dataObj.has("status")) {
                // Friends list response from server
                String status = dataObj.get("status").getAsString();
                if ("success".equals(status) && dataObj.has("friends") && dataObj.get("friends").isJsonArray()) {
                    com.google.gson.JsonArray friendsArray = dataObj.getAsJsonArray("friends");
                    Platform.runLater(() -> {
                        // Clear existing friends list
                        uiState.clearFriends();
                        // Add friends from response
                        for (int i = 0; i < friendsArray.size(); i++) {
                            JsonObject friendObj = friendsArray.get(i).getAsJsonObject();
                            if (friendObj.has("friend_username")) {
                                String friendUsername = friendObj.get("friend_username").getAsString();
                                uiState.addFriend(friendUsername);
                                System.out.println("[FriendHandler] Added friend: " + friendUsername);
                            }
                        }
                        System.out.println("[FriendHandler] Loaded " + friendsArray.size() + " friends from server");
                    });
                    return true; // Handled
                }
            }
            
            // Check if this is a friend-related INFO message
            if (json.has("friend_request_sent")) {
                // Friend request was sent successfully
                String toUser = json.has("to_user") ? json.get("to_user").getAsString() : "";
                System.out.println("[FriendHandler] Friend request sent to: " + toUser);
                handled = true;
            } else if (json.has("friend_response_sent")) {
                // Friend response was sent successfully
                String toUser = json.has("to_user") ? json.get("to_user").getAsString() : "";
                boolean accept = json.has("accept") ? json.get("accept").getAsBoolean() : false;
                System.out.println("[FriendHandler] Friend response sent to: " + toUser + ", accepted: " + accept);
                handled = true;
            } else if (json.has("unfriend")) {
                // Unfriend was successful (can be string "ok" or object)
                String toUser = "";
                if (json.has("to_user")) {
                    toUser = json.get("to_user").getAsString();
                } else if (json.has("data")) {
                    // Backend might wrap in data field
                    JsonObject data = json.getAsJsonObject("data");
                    if (data.has("to_user")) {
                        toUser = data.get("to_user").getAsString();
                    }
                }
                // Create final variable for lambda
                final String finalToUser = toUser;
                if (!finalToUser.isEmpty()) {
                    Platform.runLater(() -> {
                        uiState.removeFriend(finalToUser);
                        System.out.println("[FriendHandler] Removed friend: " + finalToUser);
                    });
                }
                handled = true;
            }
            
            // If not friend-related, return false to let InfoHandler handle it
            return handled;
        } catch (Exception e) {
            // Not a friend-related INFO message, let InfoHandler handle it
            return false;
        }
    }
}
