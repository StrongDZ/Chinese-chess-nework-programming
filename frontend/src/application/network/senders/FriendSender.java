package application.network.senders;

import application.network.SocketClient;
import application.network.MessageType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;

/**
 * Sender for friend-related messages.
 * Handles: REQUEST_ADD_FRIEND, RESPONSE_ADD_FRIEND, UNFRIEND
 */
public class FriendSender {
    private final SocketClient socketClient;
    private final Gson gson = new Gson();
    
    public FriendSender(SocketClient socketClient) {
        this.socketClient = socketClient;
    }
    
    /**
     * Send friend request.
     */
    public void sendFriendRequest(String targetUsername) throws IOException {
        sendFriendRequest(targetUsername, "", 0);
    }
    
    /**
     * Send friend request with game mode and time limit (for challenge requests).
     * @param targetUsername Target username
     * @param mode Game mode: "classical" or "blitz" (optional)
     * @param timeLimit Time limit in seconds (0 for unlimited, optional)
     */
    public void sendFriendRequest(String targetUsername, String mode, int timeLimit) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("to_user", targetUsername);
        if (mode != null && !mode.isEmpty()) {
            payload.addProperty("mode", mode);
        }
        if (timeLimit > 0) {
            payload.addProperty("time_limit", timeLimit);
        }
        socketClient.send(MessageType.REQUEST_ADD_FRIEND, gson.toJson(payload));
    }
    
    /**
     * Respond to friend request.
     * @param requesterUsername The username who sent the friend request (to_user in backend)
     * @param accepted Whether to accept or decline the request
     */
    public void respondFriendRequest(String requesterUsername, boolean accepted) throws IOException {
        JsonObject payload = new JsonObject();
        // Backend expects to_user as the original requester and "accept" (not "accepted")
        payload.addProperty("to_user", requesterUsername);
        payload.addProperty("accept", accepted);
        socketClient.send(MessageType.RESPONSE_ADD_FRIEND, gson.toJson(payload));
    }
    
    /**
     * Unfriend a user.
     */
    public void unfriend(String username) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("to_user", username);
        socketClient.send(MessageType.UNFRIEND, gson.toJson(payload));
    }
    
    /**
     * Request friends list from server.
     */
    public void requestFriendsList() throws IOException {
        // Use INFO message with special payload to request friends list
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "list_friends");
        socketClient.send(MessageType.INFO, gson.toJson(payload));
    }
    
    /**
     * Request all received friend requests (pending + accepted) from server.
     */
    public void requestAllReceivedRequests() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "list_all_received_requests");
        socketClient.send(MessageType.INFO, gson.toJson(payload));
    }
}
