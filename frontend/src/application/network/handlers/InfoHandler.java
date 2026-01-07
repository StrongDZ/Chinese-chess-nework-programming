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
            // INFO message can contain various types of data
            JsonObject response = JsonParser.parseString(payload).getAsJsonObject();
            
            // Check if this is a user stats response (has "stat" or "stats" field)
            if (response.has("stat") || response.has("stats")) {
                handleUserStats(payload);
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
            
            // TODO: Handle other INFO message types
        } catch (Exception e) {
            // Ignore parse errors for unknown INFO formats
        }
    }
    
    private void handlePlayerList(String payload) {
        try {
            JsonArray players = JsonParser.parseString(payload).getAsJsonArray();
            // TODO: Update player list in UIState
            // uiState.updatePlayerList(players);
        } catch (Exception e) {
            // Ignore parse errors
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
    
    private void handleLeaderBoard(String payload) {
        try {
            JsonArray leaderboard = JsonParser.parseString(payload).getAsJsonArray();
            // TODO: Update leaderboard in UIState
            // uiState.updateLeaderboard(leaderboard);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
}
