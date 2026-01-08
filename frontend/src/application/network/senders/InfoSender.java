package application.network.senders;

import application.network.SocketClient;
import application.network.MessageType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;

/**
 * Sender for info-related requests.
 * Handles: PLAYER_LIST, USER_STATS, LEADER_BOARD, GAME_HISTORY
 */
public class InfoSender {
    private final SocketClient socketClient;
    private final Gson gson = new Gson();
    
    public InfoSender(SocketClient socketClient) {
        this.socketClient = socketClient;
    }
    
    /**
     * Request list of online players.
     */
    public void requestPlayerList() throws IOException {
        socketClient.send(MessageType.PLAYER_LIST, "{}");
    }
    
    /**
     * Request user stats.
     * @param targetUsername Username to get stats for
     * @param timeControl Time control (classical, blitz, or "all" for all time controls)
     */
    public void requestUserStats(String targetUsername, String timeControl) throws IOException {
        JsonObject payload = new JsonObject();
        if (targetUsername != null && !targetUsername.isEmpty()) {
            payload.addProperty("target_username", targetUsername);
        }
        if (timeControl != null && !timeControl.isEmpty()) {
            payload.addProperty("time_control", timeControl);
        }
        socketClient.send(MessageType.USER_STATS, gson.toJson(payload));
    }
    
    /**
     * Request user stats (defaults to "all" time controls).
     */
    public void requestUserStats(String targetUsername) throws IOException {
        requestUserStats(targetUsername, "all");
    }
    
    /**
     * Request leaderboard.
     */
    public void requestLeaderBoard(String timeControl, int limit) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("time_control", timeControl);
        payload.addProperty("limit", limit);
        socketClient.send(MessageType.LEADER_BOARD, gson.toJson(payload));
    }
    
    /**
     * Request game history.
     * @param limit Maximum number of games to retrieve
     */
    public void requestGameHistory(int limit) throws IOException {
        JsonObject payload = new JsonObject();
        // Get username from socket client context
        String username = socketClient.getUsername();
        if (username != null && !username.isEmpty()) {
            payload.addProperty("username", username);
        }
        payload.addProperty("limit", limit);
        payload.addProperty("offset", 0);  // Start from beginning
        socketClient.send(MessageType.GAME_HISTORY, gson.toJson(payload));
    }
    
    /**
     * Search users by username prefix.
     * @param searchQuery Username prefix to search for
     */
    public void searchUsers(String searchQuery) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "search_users");
        payload.addProperty("search_query", searchQuery);
        socketClient.send(MessageType.INFO, gson.toJson(payload));
    }
}
