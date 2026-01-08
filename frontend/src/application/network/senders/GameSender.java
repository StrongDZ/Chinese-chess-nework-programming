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
        String payloadJson = gson.toJson(payload);
        System.out.println("[GameSender] Sending CHALLENGE_REQUEST to: " + targetUsername + ", payload: " + payloadJson);
        socketClient.send(MessageType.CHALLENGE_REQUEST, payloadJson);
        System.out.println("[GameSender] CHALLENGE_REQUEST sent successfully");
    }
    
    /**
     * Respond to a challenge.
     * @param challengerUsername The username of the person who sent the challenge (to_user in backend)
     * @param accepted Whether to accept the challenge
     */
    public void respondChallenge(String challengerUsername, boolean accepted) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("to_user", challengerUsername);  // Backend expects to_user (challenger)
        payload.addProperty("accept", accepted);  // Backend expects accept (not accepted)
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
        payload.addProperty("accept_draw", accepted);  // Backend expects "accept_draw", not "accepted"
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
    
    /**
     * Request quick matching to find an opponent.
     */
    public void requestQuickMatching() throws IOException {
        socketClient.send(MessageType.QUICK_MATCHING, "{}");
    }
    
    /**
     * Cancel quick matching request.
     */
    public void cancelQuickMatching() throws IOException {
        socketClient.send(MessageType.CANCEL_QM, "{}");
    }
    
    /**
     * Send chat message to opponent.
     */
    public void sendMessage(String message) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        socketClient.send(MessageType.MESSAGE, gson.toJson(payload));
    }
}
