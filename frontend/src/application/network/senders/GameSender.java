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
     * @param targetUsername Username of the player to challenge
     * @param mode Game mode: "classical" or "blitz"
     * @param timeLimit Time limit in seconds (0 for unlimited)
     */
    public void sendChallenge(String targetUsername, String mode, int timeLimit) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("to_user", targetUsername);
        payload.addProperty("mode", mode != null ? mode : "classical");
        payload.addProperty("time_limit", timeLimit);
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
     * Backend expects format: {"piece":"...", "from":{"row":..., "col":...}, "to":{"row":..., "col":...}}
     */
    public void sendMove(int fromCol, int fromRow, int toCol, int toRow) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("piece", "");  // Empty piece name
        
        JsonObject from = new JsonObject();
        from.addProperty("row", fromRow);
        from.addProperty("col", fromCol);
        payload.add("from", from);
        
        JsonObject to = new JsonObject();
        to.addProperty("row", toRow);
        to.addProperty("col", toCol);
        payload.add("to", to);
        
        socketClient.send(MessageType.MOVE, gson.toJson(payload));
    }
    
    /**
     * Send a game move with additional info.
     * Backend expects format: {"piece":"...", "from":{"row":..., "col":...}, "to":{"row":..., "col":...}}
     */
    public void sendMove(int fromCol, int fromRow, int toCol, int toRow, String piece, String captured, String notation) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("piece", piece != null ? piece : "");
        
        JsonObject from = new JsonObject();
        from.addProperty("row", fromRow);
        from.addProperty("col", fromCol);
        payload.add("from", from);
        
        JsonObject to = new JsonObject();
        to.addProperty("row", toRow);
        to.addProperty("col", toCol);
        payload.add("to", to);
        
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
     * @param mode Game mode: "classical" or "blitz"
     * @param timeLimit Time limit in seconds (0 for unlimited)
     */
    public void requestQuickMatching(String mode, int timeLimit) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("mode", mode != null ? mode : "classical");
        payload.addProperty("time_limit", timeLimit);
        socketClient.send(MessageType.QUICK_MATCHING, gson.toJson(payload));
    }
    
    /**
     * Request quick matching to find an opponent (default classical, unlimited time).
     */
    public void requestQuickMatching() throws IOException {
        requestQuickMatching("classical", 0);
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
