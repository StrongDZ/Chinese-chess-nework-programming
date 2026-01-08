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
     * Request AI match with full parameters.
     * @param gameMode Game mode: "classical", "blitz", or "custom"
     * @param aiMode AI difficulty: "easy", "medium", or "hard"
     * @param timeLimit Time limit in seconds (0 for unlimited)
     * @param gameTimer Game timer in seconds (0 for classical, 600 for blitz, custom value for custom)
     * @param playerSide Player side: "red" or "black" (default: "red")
     */
    public void requestAIMatch(String gameMode, String aiMode, int timeLimit, int gameTimer, String playerSide) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("game_mode", gameMode != null ? gameMode : "classical");
        payload.addProperty("ai_mode", aiMode != null ? aiMode : "medium");
        payload.addProperty("time_limit", timeLimit);
        payload.addProperty("game_timer", gameTimer);
        payload.addProperty("playerSide", playerSide != null ? playerSide : "red");
        System.out.println("[GameSender] Sending AI_MATCH request: game_mode=" + gameMode + ", ai_mode=" + aiMode + ", time_limit=" + timeLimit + ", game_timer=" + gameTimer + ", playerSide=" + playerSide);
        socketClient.send(MessageType.AI_MATCH, gson.toJson(payload));
    }
    
    /**
     * Request AI match with default parameters (backward compatibility).
     * @param difficulty AI difficulty: 0=easy, 1=medium, 2=hard
     */
    public void requestAIMatch(int difficulty) throws IOException {
        String aiMode = "medium";
        if (difficulty == 0) aiMode = "easy";
        else if (difficulty == 1) aiMode = "medium";
        else if (difficulty == 2) aiMode = "hard";
        requestAIMatch("classical", aiMode, 0, 0, "red");
    }
    
    /**
     * Request custom game with custom board setup.
     * @param customBoardSetup Map of "row_col" -> "color_pieceType" (e.g., "0_0" -> "red_Rook")
     * @param opponent Opponent username (empty if playing with AI)
     * @param aiMode AI difficulty: "easy", "medium", or "hard" (if playing with AI)
     * @param timeLimit Time limit in seconds (0 for unlimited)
     * @param gameTimer Game timer in seconds
     * @param playerSide Player side: "red" or "black" (default: "red")
     */
    public void requestCustomGame(java.util.Map<String, String> customBoardSetup, 
                                  String opponent, String aiMode, 
                                  int timeLimit, int gameTimer, String playerSide) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("game_mode", "custom");
        payload.addProperty("time_limit", timeLimit);
        payload.addProperty("game_timer", gameTimer);
        payload.addProperty("playerSide", playerSide != null ? playerSide : "red");
        
        // Convert custom board setup map to JSON object
        JsonObject boardSetupJson = new JsonObject();
        if (customBoardSetup != null) {
            for (java.util.Map.Entry<String, String> entry : customBoardSetup.entrySet()) {
                boardSetupJson.addProperty(entry.getKey(), entry.getValue());
            }
        }
        payload.add("custom_board_setup", boardSetupJson);
        
        if (opponent != null && !opponent.isEmpty()) {
            payload.addProperty("opponent", opponent);
        }
        if (aiMode != null && !aiMode.isEmpty()) {
            payload.addProperty("ai_mode", aiMode);
        }
        
        System.out.println("[GameSender] Sending CUSTOM_GAME request: opponent=" + opponent + 
                          ", ai_mode=" + aiMode + ", time_limit=" + timeLimit + 
                          ", game_timer=" + gameTimer + ", playerSide=" + playerSide +
                          ", board_setup_size=" + (customBoardSetup != null ? customBoardSetup.size() : 0));
        socketClient.send(MessageType.CUSTOM_GAME, gson.toJson(payload));
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
    
    /**
     * Send game end notification.
     * @param winSide Username of winner, "draw" for draw, or "red"/"black" for side
     */
    public void sendGameEnd(String winSide) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("win_side", winSide);
        socketClient.send(MessageType.GAME_END, gson.toJson(payload));
    }
}
