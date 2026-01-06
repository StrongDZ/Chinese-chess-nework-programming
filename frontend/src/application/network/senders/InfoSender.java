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
     */
    public void requestUserStats(String targetUsername) throws IOException {
        JsonObject payload = new JsonObject();
        if (targetUsername != null && !targetUsername.isEmpty()) {
            payload.addProperty("target_username", targetUsername);
        }
        socketClient.send(MessageType.USER_STATS, gson.toJson(payload));
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
     */
    public void requestGameHistory(int limit) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("limit", limit);
        socketClient.send(MessageType.GAME_HISTORY, gson.toJson(payload));
    }
}
