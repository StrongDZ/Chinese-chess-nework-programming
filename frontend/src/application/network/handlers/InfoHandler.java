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
               messageType.equals("LEADER_BOARD");
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
            String currentUser = uiState.getUsername();
            System.out.println("[InfoHandler] Current user: " + currentUser + ", Total players in array: " + players.size());
            
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i).isJsonPrimitive()) {
                    String username = players.get(i).getAsString();
                    // Exclude current user from list
                    if (currentUser == null || !username.equals(currentUser)) {
                        playerList.add(username);
                    }
                }
            }
            
            System.out.println("[InfoHandler] Player list after filtering: " + playerList.size() + " players");
            if (!playerList.isEmpty()) {
                System.out.println("[InfoHandler] Sample players: " + playerList.subList(0, Math.min(5, playerList.size())));
            }
            
            // Update online players list via UIState callback
            uiState.updateOnlinePlayers(playerList);
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
}
