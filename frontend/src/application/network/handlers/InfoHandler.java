package application.network.handlers;

import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

/**
 * Handler for INFO messages (generic info from server).
 * Handles: INFO, PLAYER_LIST, USER_STATS, LEADER_BOARD
 */
public class InfoHandler implements MessageHandler {
    private final UIState uiState;
    
    public InfoHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    @Override
    public boolean canHandle(String messageType) {
        return messageType.equals("INFO") ||
               messageType.equals("PLAYER_LIST") ||
               messageType.equals("USER_STATS") ||
               messageType.equals("LEADER_BOARD") ||
               messageType.equals("GAME_HISTORY");
    }
    
    @Override
    public boolean handle(String messageType, String payload) {
        switch (messageType) {
            case "INFO":
                handleInfo(payload);
                return true;
            case "PLAYER_LIST":
                handlePlayerList(payload);
                return true;
            case "USER_STATS":
                handleUserStats(payload);
                return true;
            case "LEADER_BOARD":
                handleLeaderBoard(payload);
                return true;
            case "GAME_HISTORY":
                handleGameHistory(payload);
                return true;
            default:
                return false;
        }
    }
    
    private void handleInfo(String payload) {
        try {
            System.out.println("[InfoHandler] Received INFO message, payload: " + payload);
            
            // Backend ALWAYS wraps INFO payload in "data" field (see message_types.h line 323-325)
            // First, unwrap the "data" field to get the actual content
            JsonObject wrapper = JsonParser.parseString(payload).getAsJsonObject();
            
            // Unwrap "data" field if present
            String innerPayload = payload;
            if (wrapper.has("data")) {
                // Get the inner data - can be array, object, or primitive
                if (wrapper.get("data").isJsonArray()) {
                    innerPayload = wrapper.getAsJsonArray("data").toString();
                    System.out.println("[InfoHandler] Unwrapped data field (array): " + innerPayload);
                    
                    // Array data - could be player list or search results
                    handleSearchResults(innerPayload);
                    handlePlayerList(innerPayload);
                    return;
                } else if (wrapper.get("data").isJsonObject()) {
                    innerPayload = wrapper.getAsJsonObject("data").toString();
                    System.out.println("[InfoHandler] Unwrapped data field (object): " + innerPayload);
                } else if (wrapper.get("data").isJsonPrimitive()) {
                    innerPayload = wrapper.get("data").getAsString();
                    System.out.println("[InfoHandler] Unwrapped data field (primitive): " + innerPayload);
                }
            }
            
            // Now process the unwrapped inner payload
            // Check if it's a JSON array
            try {
                JsonArray array = JsonParser.parseString(innerPayload).getAsJsonArray();
                System.out.println("[InfoHandler] Inner payload is array, treating as search results and player list");
                handleSearchResults(innerPayload);
                handlePlayerList(innerPayload);
                return;
            } catch (Exception e) {
                // Not an array, continue to check other formats
            }
            
            // Parse as JSON object
            JsonObject response = JsonParser.parseString(innerPayload).getAsJsonObject();
            
            // Check if this is a user stats response (has "stat" or "stats" field)
            if (response.has("stat") || response.has("stats")) {
                System.out.println("[InfoHandler] Inner payload has stats field");
                handleUserStats(innerPayload);
                return;
            }
            
            // Check if this is a leaderboard response (has "leaderboard" field)
            if (response.has("leaderboard")) {
                System.out.println("[InfoHandler] Inner payload has leaderboard field");
                
                // Reuse handleLeaderBoard logic by wrapping back into payload format
                // so that any future changes in handleLeaderBoard are reused
                if (uiState.getLeaderboardUpdateCallback() != null) {
                    // Directly notify via callback with the response object
                    uiState.getLeaderboardUpdateCallback().accept(response);
                }
                return;
            }
            
            // Check if this is a logout response
            if (response.has("logout")) {
                String logoutStatus = response.get("logout").getAsString();
                if ("ok".equals(logoutStatus)) {
                    // Reset username in UIState
                    uiState.setUsername("");
                    System.out.println("[InfoHandler] Logout successful, username reset");
                }
                return;
            }
            
            // Check if this is an active game restore response
            if (response.has("action") && 
                "active_game_restore".equals(response.get("action").getAsString())) {
                handleActiveGameRestore(response);
                return;
            }
            
            // Check if this is a suggest move response
            if (response.has("action") && 
                "suggest_move".equals(response.get("action").getAsString())) {
                handleSuggestMove(response);
                return;
            }
            
            // Check if this is a replay data response (from REPLAY_REQUEST)
            // Response format: { "status": "success", "game_type": "archived", "game": { ... moves ... } }
            if (response.has("game") && response.has("game_type")) {
                System.out.println("[InfoHandler] Detected replay data response");
                handleReplayData(response);
                return;
            }
            
            // Check if this is a quick matching response
            if (response.has("quick_matching")) {
                boolean quickMatching = response.get("quick_matching").getAsBoolean();
                if (quickMatching && response.has("status")) {
                    String status = response.get("status").getAsString();
                    if ("waiting".equals(status)) {
                        // Show waiting panel
                        uiState.openWaiting();
                        System.out.println("[InfoHandler] Quick matching - waiting for opponent");
                    }
                }
                return;
            }
            
            // Check if it's a friend requests response (has "pending" or "accepted" field)
            if (response.has("pending") || response.has("accepted")) {
                System.out.println("[InfoHandler] Inner payload has friend requests");
                handleFriendRequests(innerPayload);
                return;
            }
            
            // Check if it's a leaderboard response with all_users_stats (new format)
            if (response.has("all_users_stats")) {
                System.out.println("[InfoHandler] Inner payload has all_users_stats");
                if (uiState.getLeaderboardUpdateCallback() != null) {
                    uiState.getLeaderboardUpdateCallback().accept(response);
                }
                return;
            }
            
            // Check if it's a leaderboard response (old format with "leaderboard" field)
            if (response.has("leaderboard")) {
                System.out.println("[InfoHandler] Inner payload has leaderboard");
                if (uiState.getLeaderboardUpdateCallback() != null) {
                    uiState.getLeaderboardUpdateCallback().accept(response);
                }
                return;
            }
            
            System.out.println("[InfoHandler] INFO message not recognized, ignoring");
            // TODO: Handle other INFO message types
        } catch (Exception e) {
            // Ignore parse errors for unknown INFO formats
            System.err.println("[InfoHandler] Error parsing INFO message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handlePlayerList(String payload) {
        try {
            System.out.println("[InfoHandler] Parsing player list, payload: " + payload);
            JsonArray players = JsonParser.parseString(payload).getAsJsonArray();
            java.util.List<String> playerList = new java.util.ArrayList<>();
            java.util.List<String> playersNotInGame = new java.util.ArrayList<>();  // Players not in game
            String currentUser = uiState.getUsername();
            System.out.println("[InfoHandler] Current user: " + currentUser + ", Total players in array: " + players.size());
            
            for (int i = 0; i < players.size(); i++) {
                String username = null;
                boolean inGame = false;
                
                // Handle both old format (string) and new format (object with username and in_game)
                if (players.get(i).isJsonPrimitive()) {
                    // Old format: just username string
                    username = players.get(i).getAsString();
                } else if (players.get(i).isJsonObject()) {
                    // New format: object with username and in_game
                    JsonObject playerObj = players.get(i).getAsJsonObject();
                    if (playerObj.has("username")) {
                        username = playerObj.get("username").getAsString();
                    }
                    if (playerObj.has("in_game")) {
                        inGame = playerObj.get("in_game").getAsBoolean();
                    }
                }
                
                // Exclude current user from list
                if (username != null && (currentUser == null || !username.equals(currentUser))) {
                    playerList.add(username);
                    // Only add to not-in-game list if in_game is false or not specified (backward compatibility)
                    if (!inGame) {
                        playersNotInGame.add(username);
                    }
                }
            }
            
            System.out.println("[InfoHandler] Player list after filtering: " + playerList.size() + " players");
            System.out.println("[InfoHandler] Players not in game: " + playersNotInGame.size() + " players");
            if (!playerList.isEmpty()) {
                System.out.println("[InfoHandler] Sample players: " + playerList.subList(0, Math.min(5, playerList.size())));
            }
            
            // Update online players list via UIState callback (all online players)
            uiState.updateOnlinePlayers(playerList);
            // Also update players not in game list for PlayWithFriendPanel
            uiState.updateOnlinePlayersNotInGame(playersNotInGame);
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing player list: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleUserStats(String payload) {
        try {
            JsonObject response = JsonParser.parseString(payload).getAsJsonObject();
            
            // Check status
            if (response.has("status") && response.get("status").getAsString().equals("error")) {
                System.err.println("[InfoHandler] Error getting user stats: " + 
                    response.get("message").getAsString());
                return;
            }
            
            // Handle single stat object
            if (response.has("stat")) {
                JsonObject stat = response.getAsJsonObject("stat");
                processStatObject(stat);
            }
            
            // Handle stats array (when time_control = "all")
            if (response.has("stats") && response.get("stats").isJsonArray()) {
                JsonArray statsArray = response.getAsJsonArray("stats");
                // Aggregate all stats for total statistics
                int totalGames = 0;
                int totalWins = 0;
                int totalLosses = 0;
                int totalDraws = 0;
                int latestRating = 0;
                String username = null;
                
                for (int i = 0; i < statsArray.size(); i++) {
                    JsonObject stat = statsArray.get(i).getAsJsonObject();
                    
                    if (stat.has("username") && username == null) {
                        username = stat.get("username").getAsString();
                    }
                    
                    if (stat.has("total_games")) {
                        totalGames += stat.get("total_games").getAsInt();
                    }
                    if (stat.has("wins")) {
                        totalWins += stat.get("wins").getAsInt();
                    }
                    if (stat.has("losses")) {
                        totalLosses += stat.get("losses").getAsInt();
                    }
                    if (stat.has("draws")) {
                        totalDraws += stat.get("draws").getAsInt();
                    }
                    
                    // Get time_control from stat (default to "classical" if not present)
                    String timeControl = "classical";
                    if (stat.has("time_control") && stat.get("time_control").isJsonPrimitive()) {
                        timeControl = stat.get("time_control").getAsString();
                    }
                    
                    // Lấy username từ stat object (có thể khác với username ở đầu loop)
                    String statUsername = stat.has("username") ? stat.get("username").getAsString() : username;
                    String currentUsername = uiState.getUsername();
                    String opponentUsername = uiState.getOpponentUsername();
                    
                    boolean isCurrentUser = statUsername != null && statUsername.equals(currentUsername);
                    boolean isOpponent = statUsername != null && statUsername.equals(opponentUsername);
                    
                    // Update elo for each time_control mode
                    if (stat.has("rating") && isCurrentUser) {
                        int rating = stat.get("rating").getAsInt();
                        System.out.println("[InfoHandler] Setting elo for mode=" + timeControl + ", rating=" + rating);
                        uiState.setElo(timeControl, rating);
                        System.out.println("[InfoHandler] Elo set - classical=" + uiState.getClassicalElo() + ", blitz=" + uiState.getBlitzElo());
                    } else if (stat.has("rating") && isOpponent) {
                        // Opponent elo is still single value (not mode-specific for now)
                        int rating = stat.get("rating").getAsInt();
                        uiState.setOpponentElo(rating);
                    } else if (stat.has("rating")) {
                        // Check if this is a friend (not current user, not opponent)
                        // Update friend elo in PlayWithFriendPanel
                        int rating = stat.get("rating").getAsInt();
                        if (statUsername != null && uiState.getFriendsList().contains(statUsername)) {
                            System.out.println("[InfoHandler] Updating elo for friend: " + statUsername + ", mode: " + timeControl + ", elo: " + rating);
                            uiState.updateFriendElo(statUsername, timeControl, rating);
                        }
                    }
                    
                    // Update statistics for each time_control mode (only for current user)
                    if (isCurrentUser) {
                        // Update total matches
                        if (stat.has("total_games")) {
                            int totalGamesForMode = stat.get("total_games").getAsInt();
                            uiState.setTotalMatches(timeControl, totalGamesForMode);
                            System.out.println("[InfoHandler] Setting totalMatches for mode=" + timeControl + ", totalGames=" + totalGamesForMode);
                        }
                        // Update win matches
                        if (stat.has("wins")) {
                            int winsForMode = stat.get("wins").getAsInt();
                            uiState.setWinMatches(timeControl, winsForMode);
                            System.out.println("[InfoHandler] Setting winMatches for mode=" + timeControl + ", wins=" + winsForMode);
                        }
                        // Update winrate
                        if (stat.has("wins") && stat.has("total_games")) {
                            int winsForMode = stat.get("wins").getAsInt();
                            int totalForMode = stat.get("total_games").getAsInt();
                            if (totalForMode > 0) {
                                double winRate = (double) winsForMode / totalForMode * 100.0;
                                uiState.setWinRate(timeControl, winRate);
                                System.out.println("[InfoHandler] Setting winrate for mode=" + timeControl + ", winRate=" + winRate);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing user stats: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process a single stat object and update UIState.
     */
    private void processStatObject(JsonObject stat) {
        if (!stat.has("username")) {
            return;
        }
        
        String username = stat.get("username").getAsString();
        String currentUsername = uiState.getUsername();
        String opponentUsername = uiState.getOpponentUsername();
        
        // Determine if this is current user or opponent
        boolean isCurrentUser = username.equals(currentUsername);
        boolean isOpponent = username.equals(opponentUsername);
        
        // Get time_control from stat (default to "classical" if not present)
        String timeControl = "classical";
        if (stat.has("time_control") && stat.get("time_control").isJsonPrimitive()) {
            timeControl = stat.get("time_control").getAsString();
        }
        
        // Update elo/rating based on time_control
        if (stat.has("rating")) {
            int rating = stat.get("rating").getAsInt();
            System.out.println("[InfoHandler] processStatObject - username=" + username + 
                ", currentUsername=" + currentUsername + 
                ", timeControl=" + timeControl + 
                ", rating=" + rating + 
                ", isCurrentUser=" + isCurrentUser);
            if (isCurrentUser) {
                uiState.setElo(timeControl, rating);
            } else if (isOpponent) {
                // Opponent elo is still single value (not mode-specific for now)
                uiState.setOpponentElo(rating);
            } else {
                // Check if this is a friend (not current user, not opponent)
                // Update friend elo in PlayWithFriendPanel
                if (uiState.getFriendsList().contains(username)) {
                    System.out.println("[InfoHandler] processStatObject - Updating elo for friend: " + username + ", mode: " + timeControl + ", elo: " + rating);
                    uiState.updateFriendElo(username, timeControl, rating);
                }
            }
        }
        
        // Update statistics (only for current user)
        if (isCurrentUser) {
            if (stat.has("total_games")) {
                int totalGames = stat.get("total_games").getAsInt();
                // Set total matches theo time control (classical/blitz)
                uiState.setTotalMatches(timeControl, totalGames);
                System.out.println("[InfoHandler] processStatObject - Set totalMatches for mode=" + timeControl + ", totalGames=" + totalGames);
            }
            if (stat.has("wins")) {
                int wins = stat.get("wins").getAsInt();
                // Set win matches theo time control (classical/blitz)
                uiState.setWinMatches(timeControl, wins);
                System.out.println("[InfoHandler] processStatObject - Set winMatches for mode=" + timeControl + ", wins=" + wins);
            }
            if (stat.has("wins") && stat.has("total_games")) {
                int wins = stat.get("wins").getAsInt();
                int total = stat.get("total_games").getAsInt();
                if (total > 0) {
                    double winRate = (double) wins / total * 100.0;
                    // Set winrate theo time control (classical/blitz)
                    uiState.setWinRate(timeControl, winRate);
                    System.out.println("[InfoHandler] processStatObject - Set winrate for mode=" + timeControl + ", winRate=" + winRate);
                }
            }
        }
    }
    
    private void handleSearchResults(String payload) {
        try {
            System.out.println("[InfoHandler] Parsing search results, payload: " + payload);
            JsonArray results = JsonParser.parseString(payload).getAsJsonArray();
            java.util.List<String> resultList = new java.util.ArrayList<>();
            String currentUser = uiState.getUsername();
            
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).isJsonPrimitive()) {
                    String username = results.get(i).getAsString();
                    // Exclude current user from results
                    if (currentUser == null || !username.equals(currentUser)) {
                        resultList.add(username);
                    }
                }
            }
            
            System.out.println("[InfoHandler] Search results after filtering: " + resultList.size() + " users");
            if (!resultList.isEmpty()) {
                System.out.println("[InfoHandler] Sample results: " + resultList.subList(0, Math.min(5, resultList.size())));
            }
            
            // Update search results via UIState callback
            uiState.updateSearchResults(resultList);
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing search results: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleLeaderBoard(String payload) {
        try {
            System.out.println("[InfoHandler] Received LEADER_BOARD message, payload: " + payload);
            
            // Backend wraps INFO payload in "data" field
            JsonObject wrapper = JsonParser.parseString(payload).getAsJsonObject();
            JsonObject response = wrapper;
            
            // Unwrap "data" field if present
            if (wrapper.has("data") && wrapper.get("data").isJsonObject()) {
                response = wrapper.getAsJsonObject("data");
            }
            
            // Check status
            if (response.has("status") && response.get("status").getAsString().equals("error")) {
                System.err.println("[InfoHandler] Error getting leaderboard: " + 
                    response.get("message").getAsString());
                return;
            }
            
            // Notify RankingPanel via UIState callback
            if (uiState.getLeaderboardUpdateCallback() != null) {
                uiState.getLeaderboardUpdateCallback().accept(response);
            }
            
            System.out.println("[InfoHandler] Leaderboard data processed");
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing leaderboard: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleFriendRequests(String payload) {
        try {
            System.out.println("[InfoHandler] Parsing friend requests, payload: " + payload);
            JsonObject response = JsonParser.parseString(payload).getAsJsonObject();
            
            // Check if wrapped in "data" field
            if (response.has("data") && response.get("data").isJsonObject()) {
                response = response.getAsJsonObject("data");
            }
            
            // Check status
            if (response.has("status") && response.get("status").getAsString().equals("error")) {
                System.err.println("[InfoHandler] Error getting friend requests: " + 
                    response.get("message").getAsString());
                return;
            }
            
            java.util.List<application.components.FriendsPanel.FriendRequestInfo> pending = new java.util.ArrayList<>();
            java.util.List<application.components.FriendsPanel.FriendRequestInfo> accepted = new java.util.ArrayList<>();
            
            // Parse pending requests
            if (response.has("pending") && response.get("pending").isJsonArray()) {
                JsonArray pendingArray = response.getAsJsonArray("pending");
                for (int i = 0; i < pendingArray.size(); i++) {
                    JsonObject req = pendingArray.get(i).getAsJsonObject();
                    if (req.has("from_username")) {
                        String fromUsername = req.get("from_username").getAsString();
                        pending.add(new application.components.FriendsPanel.FriendRequestInfo(fromUsername, "pending"));
                    }
                }
            }
            
            // Parse accepted requests
            if (response.has("accepted") && response.get("accepted").isJsonArray()) {
                JsonArray acceptedArray = response.getAsJsonArray("accepted");
                for (int i = 0; i < acceptedArray.size(); i++) {
                    JsonObject req = acceptedArray.get(i).getAsJsonObject();
                    if (req.has("from_username")) {
                        String fromUsername = req.get("from_username").getAsString();
                        accepted.add(new application.components.FriendsPanel.FriendRequestInfo(fromUsername, "accepted"));
                    }
                }
            }
            
            System.out.println("[InfoHandler] Friend requests parsed: " + pending.size() + " pending, " + accepted.size() + " accepted");
            
            // Update friend requests via UIState callback
            uiState.updateFriendRequests(pending, accepted);
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing friend requests: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleActiveGameRestore(JsonObject response) {
        try {
            System.out.println("[InfoHandler] Handling active game restore response");
            
            boolean hasActiveGame = response.has("has_active_game") && 
                                    response.get("has_active_game").getAsBoolean();
            
            if (!hasActiveGame) {
                System.out.println("[InfoHandler] No active game to restore");
                return;
            }
            
            // Extract game info
            String gameId = response.has("game_id") ? response.get("game_id").getAsString() : "";
            String opponent = response.has("opponent") ? response.get("opponent").getAsString() : "";
            String gameMode = response.has("game_mode") ? response.get("game_mode").getAsString() : "classical";
            boolean isRed = response.has("is_red") && response.get("is_red").getAsBoolean();
            String currentTurn = response.has("current_turn") ? response.get("current_turn").getAsString() : "red";
            
            System.out.println("[InfoHandler] Restoring active game: gameId=" + gameId + 
                ", opponent=" + opponent + ", gameMode=" + gameMode + ", isRed=" + isRed);
            
            // Set opponent info in UIState
            uiState.setOpponentUsername(opponent);
            uiState.setPlayerIsRed(isRed);
            uiState.setCurrentGameMode(gameMode);
            
            // Set game action to trigger game restore in GamePanel
            // Format: "restore_game|opponent|gameMode|isRed|currentTurn|xfen|movesJson"
            String xfen = response.has("xfen") ? response.get("xfen").getAsString() : "";
            String movesJson = "";
            if (response.has("moves") && response.get("moves").isJsonArray()) {
                movesJson = response.get("moves").toString();
            }
            
            String restoreData = opponent + "|" + gameMode + "|" + isRed + 
                "|" + currentTurn + "|" + xfen + "|" + movesJson;
            
            // Trigger game restore via game action
            uiState.setGameActionResult(restoreData);
            uiState.setGameActionTrigger("game_restore");
            
            // Open game panel
            uiState.openGame(gameMode);
            
            System.out.println("[InfoHandler] Active game restored, opening game panel");
            
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error handling active game restore: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle GAME_HISTORY response from backend.
     * Response format:
     * {
     *   "status": "success",
     *   "history": [
     *     {
     *       "game_id": "...",
     *       "red_player": "...",
     *       "black_player": "...",
     *       "result": "...",
     *       "winner": "...",
     *       "time_control": "...",
     *       "rated": true/false,
     *       "move_count": ...,
     *       "start_time": ...,
     *       "end_time": ...
     *     }
     *   ],
     *   "count": ...
     * }
     */
    private void handleGameHistory(String payload) {
        try {
            System.out.println("[InfoHandler] ========================================");
            System.out.println("[InfoHandler] ===== GAME_HISTORY response received =====");
            System.out.println("[InfoHandler] ========================================");
            System.out.println("[InfoHandler] Full payload length: " + payload.length());
            System.out.println("[InfoHandler] Full payload: " + payload);
            
            // Check if payload is an error message
            if (payload.contains("\"status\":\"error\"") || payload.contains("Feature not implemented")) {
                System.err.println("[InfoHandler] Backend returned error: " + payload);
                System.err.println("[InfoHandler] Note: Backend may not have implemented GAME_HISTORY handler in server.cpp");
                return;
            }
            
            JsonObject response = JsonParser.parseString(payload).getAsJsonObject();
            
            // Check status
            if (response.has("status") && response.get("status").getAsString().equals("error")) {
                String errorMsg = response.has("message") ? response.get("message").getAsString() : "Unknown error";
                System.err.println("[InfoHandler] Error getting game history: " + errorMsg);
                return;
            }
            
            System.out.println("[InfoHandler] Game history response status: " + 
                (response.has("status") ? response.get("status").getAsString() : "no status field"));
            
            // Check if wrapped in "data" field (backend wraps GAME_HISTORY in data field)
            JsonObject dataObj = response;
            if (response.has("data") && response.get("data").isJsonObject()) {
                System.out.println("[InfoHandler] Response wrapped in 'data' field, unwrapping...");
                dataObj = response.getAsJsonObject("data");
            }
            
            // Parse history array from data object (or root if no data field)
            if (!dataObj.has("history") || !dataObj.get("history").isJsonArray()) {
                System.err.println("[InfoHandler] Game history response missing or invalid 'history' array");
                System.err.println("[InfoHandler] Available keys in response: " + response.keySet());
                if (dataObj != response) {
                    System.err.println("[InfoHandler] Available keys in data: " + dataObj.keySet());
                }
                return;
            }
            
            JsonArray historyArray = dataObj.getAsJsonArray("history");
            String currentUsername = uiState.getUsername();
            java.util.List<application.components.HistoryPanel.HistoryEntry> peopleHistory = new java.util.ArrayList<>();
            java.util.List<application.components.HistoryPanel.HistoryEntry> aiHistory = new java.util.ArrayList<>();
            
            for (int i = 0; i < historyArray.size(); i++) {
                JsonObject game = historyArray.get(i).getAsJsonObject();
                
                // Determine opponent
                String redPlayer = game.has("red_player") ? game.get("red_player").getAsString() : "";
                String blackPlayer = game.has("black_player") ? game.get("black_player").getAsString() : "";
                String opponent = "";
                boolean isRed = currentUsername != null && currentUsername.equals(redPlayer);
                
                if (isRed) {
                    opponent = blackPlayer;
                } else {
                    opponent = redPlayer;
                }
                
                // Determine result from current user's perspective
                String result = "Draw";
                if (game.has("result")) {
                    String gameResult = game.get("result").getAsString();
                    if (game.has("winner")) {
                        String winner = game.get("winner").getAsString();
                        if (winner.equals(currentUsername)) {
                            result = "Win";
                        } else if (!winner.isEmpty()) {
                            result = "Lose";
                        }
                    } else if (gameResult.equals("draw")) {
                        result = "Draw";
                    }
                }
                
                // Get time control mode
                String timeControl = game.has("time_control") ? game.get("time_control").getAsString() : "Classical";
                String mode = "Classic Mode";
                if (timeControl.equals("blitz")) {
                    mode = "Blitz Mode";
                } else if (timeControl.equals("rapid")) {
                    mode = "Rapid Mode";
                }
                
                // Format date from end_time (milliseconds since epoch)
                String date = "N/A";
                if (game.has("end_time") && game.get("end_time").isJsonPrimitive()) {
                    try {
                        long endTimeMs = game.get("end_time").getAsLong();
                        java.time.Instant instant = java.time.Instant.ofEpochMilli(endTimeMs);
                        java.time.LocalDate localDate = java.time.LocalDate.ofInstant(instant, java.time.ZoneId.systemDefault());
                        // Format: yy/mm/dd
                        int year = localDate.getYear() % 100;
                        int month = localDate.getMonthValue();
                        int day = localDate.getDayOfMonth();
                        date = String.format("%02d/%02d/%02d", year, month, day);
                    } catch (Exception e) {
                        System.err.println("[InfoHandler] Error parsing date: " + e.getMessage());
                    }
                }
                
                // Determine if AI game (opponent is empty or contains "AI")
                boolean isAIGame = opponent.isEmpty() || opponent.toLowerCase().contains("ai");
                
                // Format opponent string (with elo if available)
                String opponentDisplay = opponent;
                if (opponentDisplay.isEmpty()) {
                    opponentDisplay = "AI";
                }
                
                // Get game_id for replay
                String gameId = game.has("game_id") ? game.get("game_id").getAsString() : "";
                
                // Nếu đang load replay cho game này, cập nhật màu quân cờ và moves
                String currentReplayGameId = uiState.getReplayGameId();
                if (currentReplayGameId != null && currentReplayGameId.equals(gameId)) {
                    // Xác định màu quân cờ của người chơi trong trận này
                    boolean playerIsRed = currentUsername != null && currentUsername.equals(redPlayer);
                    // Gọi method để ReplayPanel cập nhật
                    uiState.setReplayPlayerColor(playerIsRed, redPlayer, blackPlayer);
                    System.out.println("[InfoHandler] Found replay game: " + gameId + 
                        ", playerIsRed=" + playerIsRed + ", redPlayer=" + redPlayer + ", blackPlayer=" + blackPlayer);
                    
                    // Parse moves từ game object nếu có
                    if (game.has("moves") && game.get("moves").isJsonArray()) {
                        com.google.gson.JsonArray movesArray = game.get("moves").getAsJsonArray();
                        java.util.List<application.components.ReplayPanel.ReplayMove> replayMoves = new java.util.ArrayList<>();
                        
                        for (int moveIdx = 0; moveIdx < movesArray.size(); moveIdx++) {
                            com.google.gson.JsonObject moveObj = movesArray.get(moveIdx).getAsJsonObject();
                            
                            // Parse move data (format có thể khác nhau tùy backend)
                            // Giả sử format: { "fromRow": 6, "fromCol": 4, "toRow": 5, "toCol": 4, 
                            //                  "color": "red", "pieceType": "pawn", 
                            //                  "capturedColor": null, "capturedPieceType": null }
                            int fromRow = moveObj.has("fromRow") ? moveObj.get("fromRow").getAsInt() : -1;
                            int fromCol = moveObj.has("fromCol") ? moveObj.get("fromCol").getAsInt() : -1;
                            int toRow = moveObj.has("toRow") ? moveObj.get("toRow").getAsInt() : -1;
                            int toCol = moveObj.has("toCol") ? moveObj.get("toCol").getAsInt() : -1;
                            String color = moveObj.has("color") ? moveObj.get("color").getAsString() : "red";
                            String pieceType = moveObj.has("pieceType") ? moveObj.get("pieceType").getAsString() : "unknown";
                            String capturedColor = moveObj.has("capturedColor") && !moveObj.get("capturedColor").isJsonNull() 
                                ? moveObj.get("capturedColor").getAsString() : null;
                            String capturedPieceType = moveObj.has("capturedPieceType") && !moveObj.get("capturedPieceType").isJsonNull()
                                ? moveObj.get("capturedPieceType").getAsString() : null;
                            
                            if (fromRow >= 0 && fromCol >= 0 && toRow >= 0 && toCol >= 0) {
                                application.components.ReplayPanel.ReplayMove replayMove = 
                                    new application.components.ReplayPanel.ReplayMove(
                                        fromRow, fromCol, toRow, toCol, color, pieceType, 
                                        capturedColor, capturedPieceType
                                    );
                                replayMoves.add(replayMove);
                            }
                        }
                        
                        // Gửi moves đến ReplayPanel thông qua UIState callback
                        if (uiState.getReplayMovesCallback() != null) {
                            uiState.getReplayMovesCallback().accept(replayMoves);
                        }
                        
                        System.out.println("[InfoHandler] Parsed " + replayMoves.size() + " moves for replay game: " + gameId);
                    }
                }
                
                application.components.HistoryPanel.HistoryEntry entry = 
                    new application.components.HistoryPanel.HistoryEntry(opponentDisplay, result, mode, date, gameId);
                
                if (isAIGame) {
                    aiHistory.add(entry);
                } else {
                    peopleHistory.add(entry);
                }
            }
            
            System.out.println("[InfoHandler] Game history parsed: " + peopleHistory.size() + " people games, " + aiHistory.size() + " AI games");
            System.out.println("[InfoHandler] Calling uiState.updateGameHistory()...");
            
            // Update history via UIState callback
            uiState.updateGameHistory(peopleHistory, aiHistory);
            
            System.out.println("[InfoHandler] ✓ Game history updated successfully");
            System.out.println("[InfoHandler] ========================================");
            
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing game history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle replay data response from REPLAY_REQUEST
     * Response format: { "status": "success", "game_type": "archived", "game": { ... moves ... } }
     */
    private void handleReplayData(JsonObject response) {
        try {
            System.out.println("[InfoHandler] ========================================");
            System.out.println("[InfoHandler] Processing replay data response");
            
            // Check status
            if (response.has("status") && "error".equals(response.get("status").getAsString())) {
                String message = response.has("message") ? response.get("message").getAsString() : "Unknown error";
                System.err.println("[InfoHandler] Replay request failed: " + message);
                return;
            }
            
            // Get game object
            if (!response.has("game")) {
                System.err.println("[InfoHandler] Replay response missing 'game' field");
                return;
            }
            
            JsonObject game = response.getAsJsonObject("game");
            String gameId = game.has("game_id") ? game.get("game_id").getAsString() : "";
            String redPlayer = game.has("red_player") ? game.get("red_player").getAsString() : "";
            String blackPlayer = game.has("black_player") ? game.get("black_player").getAsString() : "";
            
            System.out.println("[InfoHandler] Replay game: id=" + gameId + 
                ", redPlayer=" + redPlayer + ", blackPlayer=" + blackPlayer);
            
            // Xác định màu quân cờ của người chơi
            String currentUsername = uiState.getUsername();
            boolean playerIsRed = currentUsername != null && currentUsername.equals(redPlayer);
            
            // Cập nhật player color cho ReplayPanel
            uiState.setReplayPlayerColor(playerIsRed, redPlayer, blackPlayer);
            System.out.println("[InfoHandler] Player is red: " + playerIsRed + " (currentUser=" + currentUsername + ")");
            
            // Parse moves
            if (game.has("moves") && game.get("moves").isJsonArray()) {
                JsonArray movesArray = game.get("moves").getAsJsonArray();
                java.util.List<application.components.ReplayPanel.ReplayMove> replayMoves = new java.util.ArrayList<>();
                
                System.out.println("[InfoHandler] Found " + movesArray.size() + " moves in replay");
                
                for (int moveIdx = 0; moveIdx < movesArray.size(); moveIdx++) {
                    JsonObject moveObj = movesArray.get(moveIdx).getAsJsonObject();
                    
                    // Backend format: from_x (col), from_y (row), to_x (col), to_y (row), player (username), piece, captured
                    int fromCol = moveObj.has("from_x") ? moveObj.get("from_x").getAsInt() : -1;
                    int backendFromRow = moveObj.has("from_y") ? moveObj.get("from_y").getAsInt() : -1;
                    int toCol = moveObj.has("to_x") ? moveObj.get("to_x").getAsInt() : -1;
                    int backendToRow = moveObj.has("to_y") ? moveObj.get("to_y").getAsInt() : -1;
                    
                    // Convert: Backend row (0=top đen) → Frontend row (0=top đỏ)
                    // Công thức: frontendRow = 9 - backendRow
                    int frontendFromRow = 9 - backendFromRow;
                    int frontendToRow = 9 - backendToRow;
                    
                    // player field chứa username, cần convert sang color
                    String color = "red";
                    if (moveObj.has("player")) {
                        String player = moveObj.get("player").getAsString();
                        // Xác định color dựa trên player username
                        if (player.equals(redPlayer)) {
                            color = "red";
                        } else if (player.equals(blackPlayer)) {
                            color = "black";
                        } else {
                            // Fallback: dựa vào move number (chẵn = red, lẻ = black)
                            color = (moveIdx % 2 == 0) ? "red" : "black";
                        }
                    }
                    
                    String pieceType = moveObj.has("piece") ? moveObj.get("piece").getAsString() : "unknown";
                    
                    String capturedPieceType = null;
                    String capturedColor = null;
                    if (moveObj.has("captured") && !moveObj.get("captured").isJsonNull() && 
                        moveObj.get("captured").isJsonPrimitive() &&
                        !moveObj.get("captured").getAsString().isEmpty()) {
                        capturedPieceType = moveObj.get("captured").getAsString();
                        // Quân bị ăn thuộc màu đối thủ
                        capturedColor = color.equals("red") ? "black" : "red";
                    }
                    
                    System.out.println("[InfoHandler] Move " + moveIdx + ": " + color + " " + pieceType + 
                        " from FE(" + frontendFromRow + "," + fromCol + ") BE(" + backendFromRow + "," + fromCol + 
                        ") to FE(" + frontendToRow + "," + toCol + ") BE(" + backendToRow + "," + toCol + ")" +
                        (capturedPieceType != null ? " captured " + capturedPieceType : ""));
                    
                    if (frontendFromRow >= 0 && fromCol >= 0 && frontendToRow >= 0 && toCol >= 0) {
                        application.components.ReplayPanel.ReplayMove replayMove = 
                            new application.components.ReplayPanel.ReplayMove(
                                frontendFromRow, fromCol, frontendToRow, toCol, color, pieceType, 
                                capturedColor, capturedPieceType
                            );
                        replayMoves.add(replayMove);
                    }
                }
                
                // Gửi moves đến ReplayPanel thông qua UIState callback
                if (uiState.getReplayMovesCallback() != null) {
                    System.out.println("[InfoHandler] Sending " + replayMoves.size() + " moves to ReplayPanel");
                    uiState.getReplayMovesCallback().accept(replayMoves);
                } else {
                    System.err.println("[InfoHandler] WARNING: replayMovesCallback is null!");
                }
            } else {
                System.err.println("[InfoHandler] No moves found in replay response");
            }
            
            System.out.println("[InfoHandler] ✓ Replay data processed successfully");
            System.out.println("[InfoHandler] ========================================");
            
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing replay data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle suggest move response from INFO message
     * Response format: { "action": "suggest_move", "suggested_move": { "piece": "...", "from": { "row": ..., "col": ... }, "to": { "row": ..., "col": ... } } }
     */
    private void handleSuggestMove(JsonObject response) {
        try {
            System.out.println("[InfoHandler] Received suggest move response");
            
            if (!response.has("suggested_move")) {
                System.err.println("[InfoHandler] Suggest move response missing 'suggested_move' field");
                return;
            }
            
            JsonObject suggestedMove = response.getAsJsonObject("suggested_move");
            
            // Parse suggested move coordinates from server
            // Server format: {"piece":"...", "from":{"row":..., "col":...}, "to":{"row":..., "col":...}}
            JsonObject from = suggestedMove.getAsJsonObject("from");
            JsonObject to = suggestedMove.getAsJsonObject("to");
            
            int backendFromRow = from.get("row").getAsInt();
            int fromCol = from.get("col").getAsInt();
            int backendToRow = to.get("row").getAsInt();
            int toCol = to.get("col").getAsInt();
            
            String piece = suggestedMove.has("piece") ? suggestedMove.get("piece").getAsString() : "";
            
            System.out.println("[InfoHandler] Received suggest move: " + piece + 
                " from BE(row=" + backendFromRow + ",col=" + fromCol + 
                ") to BE(row=" + backendToRow + ",col=" + toCol + ")");
            
            // Convert backend coordinates to frontend coordinates
            // Backend: row 0 = top (đen), row 9 = bottom (đỏ)
            // Frontend: row 0 = top (đỏ), row 9 = bottom (đen)
            // Công thức: frontendRow = 9 - backendRow
            int frontendFromRow = 9 - backendFromRow;
            int frontendToRow = 9 - backendToRow;
            
            // Trigger highlight suggest move thông qua UIState
            javafx.application.Platform.runLater(() -> {
                // Store suggest move info in UIState để GamePanel có thể access
                uiState.setGameActionTrigger("");
                uiState.setGameActionResult("");
                javafx.application.Platform.runLater(() -> {
                    // Format: "fromRow_fromCol_toRow_toCol"
                    String suggestMoveInfo = String.format("%d_%d_%d_%d", frontendFromRow, fromCol, frontendToRow, toCol);
                    uiState.setGameActionResult(suggestMoveInfo);
                    uiState.setGameActionTrigger("suggest_move");
                    System.out.println("[InfoHandler] Suggest move trigger set: " + suggestMoveInfo);
                });
            });
        } catch (Exception e) {
            System.err.println("[InfoHandler] Error parsing suggest move: " + e.getMessage());
            e.printStackTrace();
        }
    }
}