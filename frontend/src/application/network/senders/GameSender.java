package application.network.senders;

import application.network.SocketClient;
import application.network.MessageType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;

/**
 * Sender for game-related messages.
 * Handles: CHALLENGE_REQUEST, CHALLENGE_RESPONSE, CHALLENGE_CANCEL, MOVE, RESIGN, DRAW_REQUEST, etc.
 */
public class GameSender {
    private final SocketClient socketClient;
    private final Gson gson = new Gson();
    
    public GameSender(SocketClient socketClient) {
        this.socketClient = socketClient;
    }
    
    /**
     * Send challenge request to another player.
     */
    public void sendChallenge(String targetUsername, String timeControl, boolean rated) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("to_user", targetUsername);
        payload.addProperty("time_control", timeControl);
        payload.addProperty("rated", rated);
        socketClient.send(MessageType.CHALLENGE_REQUEST, gson.toJson(payload));
    }
    
    /**
     * Respond to a challenge.
     */
    public void respondChallenge(String fromUser, boolean accepted) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("from_user", fromUser);
        payload.addProperty("accepted", accepted);
        socketClient.send(MessageType.CHALLENGE_RESPONSE, gson.toJson(payload));
    }
    
    /**
     * Cancel a pending challenge.
     */
    public void cancelChallenge(String toUser) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("to_user", toUser);
        socketClient.send(MessageType.CHALLENGE_CANCEL, gson.toJson(payload));
    }
    
    /**
     * Send a game move.
     */
    public void sendMove(int fromX, int fromY, int toX, int toY) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("from_x", fromX);
        payload.addProperty("from_y", fromY);
        payload.addProperty("to_x", toX);
        payload.addProperty("to_y", toY);
        socketClient.send(MessageType.MOVE, gson.toJson(payload));
    }
    
    /**
     * Send a game move with additional info.
     */
    public void sendMove(int fromX, int fromY, int toX, int toY, String piece, String captured, String notation) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("from_x", fromX);
        payload.addProperty("from_y", fromY);
        payload.addProperty("to_x", toX);
        payload.addProperty("to_y", toY);
        payload.addProperty("piece", piece);
        payload.addProperty("captured", captured);
        payload.addProperty("notation", notation);
        socketClient.send(MessageType.MOVE, gson.toJson(payload));
    }
    
    /**
     * Resign from current game.
     */
    public void resign() throws IOException {
        socketClient.send(MessageType.RESIGN, "{}");
    }
    
    /**
     * Send draw request.
     */
    public void requestDraw() throws IOException {
        socketClient.send(MessageType.DRAW_REQUEST, "{}");
    }
    
    /**
     * Respond to draw request.
     */
    public void respondDraw(boolean accepted) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("accepted", accepted);
        socketClient.send(MessageType.DRAW_RESPONSE, gson.toJson(payload));
    }
    
    /**
     * Request AI match.
     */
    public void requestAIMatch(int difficulty) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("difficulty", difficulty);
        socketClient.send(MessageType.AI_MATCH, gson.toJson(payload));
    }
    
    /**
     * Request move suggestion from AI.
     */
    public void requestSuggestMove() throws IOException {
        socketClient.send(MessageType.SUGGEST_MOVE, "{}");
    }
}
