package application.network.senders;

import application.network.SocketClient;
import application.network.MessageType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;

/**
 * Sender for authentication-related messages.
 * Handles: LOGIN, REGISTER, LOGOUT
 */
public class AuthSender {
    private final SocketClient socketClient;
    private final Gson gson = new Gson();
    
    public AuthSender(SocketClient socketClient) {
        this.socketClient = socketClient;
    }
    
    /**
     * Send login request.
     * 
     * @param username Username
     * @param password Password
     * @throws IOException if send fails
     */
    public void login(String username, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        socketClient.send(MessageType.LOGIN, gson.toJson(payload));
    }
    
    /**
     * Send register request.
     * 
     * @param username Username
     * @param password Password
     * @throws IOException if send fails
     */
    public void register(String username, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        socketClient.send(MessageType.REGISTER, gson.toJson(payload));
    }
    
    /**
     * Send logout request.
     * This resets the user session but keeps the socket connection open
     * so the user can login again with a different account.
     * 
     * @throws IOException if send fails
     */
    public void logout() throws IOException {
        socketClient.send(MessageType.LOGOUT, "{}");
        // Reset username context but keep socket connection open
        socketClient.resetUsername();
    }
}

