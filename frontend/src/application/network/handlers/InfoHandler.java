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
            
            // INFO message can contain various types of data
            // First check if it's a JSON array (could be player list or search results)
            try {
                JsonArray array = JsonParser.parseString(payload).getAsJsonArray();
                // Check if this is a search result (when search field has text) or player list
                // For now, we'll treat all arrays as potential search results if they come after a search request
                // But also update online players for backward compatibility
                System.out.println("[InfoHandler] INFO payload is array, treating as search results and player list");
                handleSearchResults(payload);
                handlePlayerList(payload); // Also update online players for compatibility
                return;
            } catch (Exception e) {
                // Not an array, continue to check other formats
            }
            
            // Check if it's a user stats response
            JsonObject response = JsonParser.parseString(payload).getAsJsonObject();
            
            // Check if it's wrapped in "data" field (backend wraps INFO in data field)
            if (response.has("data")) {
                Object dataObj = response.get("data");
                if (dataObj instanceof JsonArray) {
                    // Array wrapped in data field - could be player list or search results
                    System.out.println("[InfoHandler] INFO payload has 'data' field with array, treating as both search results and player list");
                    JsonArray playersArray = response.getAsJsonArray("data");
                    String arrayString = playersArray.toString();
                    handleSearchResults(arrayString); // Update search results
                    handlePlayerList(arrayString); // Also update online players for compatibility
                    return;
                } else if (dataObj instanceof JsonObject) {
                    // Try to handle as nested object
                    JsonObject dataObjJson = response.getAsJsonObject("data");
                    // Check if it's a logout response
                    if (dataObjJson.has("logout")) {
                        String logoutStatus = dataObjJson.get("logout").getAsString();
                        if ("ok".equals(logoutStatus)) {
                            // Reset username in UIState
                            uiState.setUsername("");
                            System.out.println("[InfoHandler] Logout successful, username reset");
                        }
                        return;
                    }
                    if (dataObjJson.has("stat") || dataObjJson.has("stats")) {
                        System.out.println("[InfoHandler] INFO payload has 'data' field with stats");
                        handleUserStats(dataObjJson.toString());
                        return;
                    }
                    // Check if it's a friend requests response (has "pending" or "accepted" field)
                    if (dataObjJson.has("pending") || dataObjJson.has("accepted")) {
                        System.out.println("[InfoHandler] INFO payload has 'data' field with friend requests");
                        handleFriendRequests(payload); // Pass full payload, handleFriendRequests will unwrap
                        return;
                    }
                }
            }
            
            // Check if this is a user stats response (has "stat" or "stats" field)
            if (response.has("stat") || response.has("stats")) {
                System.out.println("[InfoHandler] INFO payload has stats field");
                handleUserStats(payload);
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
                System.out.println("[InfoHandler] INFO payload has friend requests");
                handleFriendRequests(payload);
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
                    
                    // Update elo for each time_control mode
                    if (stat.has("rating") && stat.has("time_control")) {
                        int rating = stat.get("rating").getAsInt();
                        String timeControl = stat.get("time_control").getAsString();
                        String currentUsername = uiState.getUsername();
                        String opponentUsername = uiState.getOpponentUsername();
                        
                        // Lấy username từ stat object (có thể khác với username ở đầu loop)
                        String statUsername = stat.has("username") ? stat.get("username").getAsString() : username;
                        
                        boolean isCurrentUser = statUsername != null && statUsername.equals(currentUsername);
                        boolean isOpponent = statUsername != null && statUsername.equals(opponentUsername);
                        
                        System.out.println("[InfoHandler] Processing stat - statUsername=" + statUsername + 
                            ", currentUsername=" + currentUsername + 
                            ", timeControl=" + timeControl + 
                            ", rating=" + rating + 
                            ", isCurrentUser=" + isCurrentUser);
                        
                        if (isCurrentUser) {
                            System.out.println("[InfoHandler] Setting elo for mode=" + timeControl + ", rating=" + rating);
                            uiState.setElo(timeControl, rating);
                            System.out.println("[InfoHandler] Elo set - classical=" + uiState.getClassicalElo() + ", blitz=" + uiState.getBlitzElo());
                        } else if (isOpponent) {
                            // Opponent elo is still single value (not mode-specific for now)
                            uiState.setOpponentElo(rating);
                        } else {
                            // Check if this is a friend (not current user, not opponent)
                            // Update friend elo in PlayWithFriendPanel
                            if (statUsername != null && uiState.getFriendsList().contains(statUsername)) {
                                System.out.println("[InfoHandler] Updating elo for friend: " + statUsername + ", mode: " + timeControl + ", elo: " + rating);
                                uiState.updateFriendElo(statUsername, timeControl, rating);
                            }
                        }
                    }
                }
                
                // Update UIState with aggregated stats
                if (username != null) {
                    String currentUsername = uiState.getUsername();
                    String opponentUsername = uiState.getOpponentUsername();
                    boolean isCurrentUser = username.equals(currentUsername);
                    boolean isOpponent = username.equals(opponentUsername);
                    
                    if (isCurrentUser) {
                        uiState.setTotalMatches(totalGames);
                        uiState.setWinMatches(totalWins);
                        if (totalGames > 0) {
                            double winRate = (double) totalWins / totalGames * 100.0;
                            uiState.setWinRate(winRate);
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
                uiState.setTotalMatches(stat.get("total_games").getAsInt());
            }
            if (stat.has("wins")) {
                uiState.setWinMatches(stat.get("wins").getAsInt());
            }
            if (stat.has("wins") && stat.has("total_games")) {
                int wins = stat.get("wins").getAsInt();
                int total = stat.get("total_games").getAsInt();
                if (total > 0) {
                    double winRate = (double) wins / total * 100.0;
                    uiState.setWinRate(winRate);
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
            JsonArray leaderboard = JsonParser.parseString(payload).getAsJsonArray();
            // TODO: Update leaderboard in UIState
            // uiState.updateLeaderboard(leaderboard);
        } catch (Exception e) {
            // Ignore parse errors
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
}
