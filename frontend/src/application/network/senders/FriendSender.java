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
        JsonObject payload = new JsonObject();
        payload.addProperty("to_user", targetUsername);
        socketClient.send(MessageType.REQUEST_ADD_FRIEND, gson.toJson(payload));
    }
    
    /**
     * Respond to friend request.
     */
    public void respondFriendRequest(String fromUser, boolean accepted) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("from_user", fromUser);
        payload.addProperty("accepted", accepted);
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
}
