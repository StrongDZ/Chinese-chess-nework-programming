package application.network.handlers;

import application.state.UIState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

/**
 * Handler for game-related messages.
 * Handles: GAME_START, MOVE, INVALID_MOVE, GAME_END, CHALLENGE_REQUEST, CHALLENGE_RESPONSE
 */
public class GameHandler implements MessageHandler {
    private final UIState uiState;
    
    public GameHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    @Override
    public boolean canHandle(String messageType) {
        return messageType.equals("GAME_START") ||
               messageType.equals("MOVE") ||
               messageType.equals("INVALID_MOVE") ||
               messageType.equals("GAME_END") ||
               messageType.equals("CHALLENGE_REQUEST") ||
               messageType.equals("CHALLENGE_RESPONSE") ||
               messageType.equals("CHALLENGE_CANCEL") ||
               messageType.equals("DRAW_REQUEST") ||
               messageType.equals("DRAW_RESPONSE") ||
               messageType.equals("MESSAGE");
    }
    
    @Override
    public boolean handle(String messageType, String payload) {
        switch (messageType) {
            case "GAME_START":
                handleGameStart(payload);
                return true;
            case "MOVE":
                handleMove(payload);
                return true;
            case "INVALID_MOVE":
                handleInvalidMove(payload);
                return true;
            case "GAME_END":
                handleGameEnd(payload);
                return true;
            case "CHALLENGE_REQUEST":
                handleChallengeRequest(payload);
                return true;
            case "CHALLENGE_RESPONSE":
                handleChallengeResponse(payload);
                return true;
            case "CHALLENGE_CANCEL":
                handleChallengeCancel(payload);
                return true;
            case "DRAW_REQUEST":
                handleDrawRequest(payload);
                return true;
            case "DRAW_RESPONSE":
                handleDrawResponse(payload);
                return true;
            case "MESSAGE":
                handleMessage(payload);
                return true;
            default:
                return false;
        }
    }
    
    private void handleGameStart(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            
            // Extract opponent username
            String opponent = null;
            if (json.has("opponent")) {
                opponent = json.get("opponent").getAsString();
                if (opponent != null && !opponent.isEmpty()) {
                    uiState.setOpponentUsername(opponent);
                    
                    // Fetch opponent profile
                    try {
                        application.network.NetworkManager.getInstance().info().requestUserStats(opponent);
                    } catch (Exception e) {
                        System.err.println("[GameHandler] Error fetching opponent profile: " + e.getMessage());
                    }
                } else {
                    // AI game - no opponent
                    uiState.setOpponentUsername("AI");
                }
            }
            
            // Extract game mode if available
            String gameMode = "classical"; // Default to classical
            if (json.has("game_mode")) {
                gameMode = json.get("game_mode").getAsString();
                // Normalize game mode: "classic" -> "classical"
                if ("classic".equals(gameMode)) {
                    gameMode = "classical";
                }
            }
            
            // Extract game_id and player_is_red from opponent_data if available
            if (json.has("opponent_data")) {
                JsonObject opponentData = json.getAsJsonObject("opponent_data");
                if (opponentData.has("game_id")) {
                    String gameId = opponentData.get("game_id").getAsString();
                    // TODO: Store game_id in UIState if needed for future use
                    System.out.println("[GameHandler] Game started with ID: " + gameId);
                }
                // Extract player_is_red to determine player side
                if (opponentData.has("player_is_red")) {
                    boolean playerIsRed = opponentData.get("player_is_red").getAsBoolean();
                    uiState.setPlayerIsRed(playerIsRed);
                    System.out.println("[GameHandler] Player is red: " + playerIsRed);
                } else {
                    // Default to red if not specified
                    uiState.setPlayerIsRed(true);
                    System.out.println("[GameHandler] Player side not specified, defaulting to red");
                }
            } else {
                // Default to red if no opponent_data
                uiState.setPlayerIsRed(true);
                System.out.println("[GameHandler] No opponent_data, defaulting to red");
            }
            
            // Close waiting panel if open (from quick matching)
            if (uiState.isWaitingVisible()) {
                uiState.closeWaiting();
            }
            
            // Open game panel with the game mode
            uiState.openGame(gameMode);
            
            System.out.println("[GameHandler] Game started - Opponent: " + opponent + ", Mode: " + gameMode);
        } catch (Exception e) {
            System.err.println("[GameHandler] Error parsing GAME_START: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleMove(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            // TODO: Apply opponent's move to the board
            // int fromX = json.get("from_x").getAsInt();
            // int fromY = json.get("from_y").getAsInt();
            // int toX = json.get("to_x").getAsInt();
            // int toY = json.get("to_y").getAsInt();
            // uiState.applyOpponentMove(fromX, fromY, toX, toY);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleInvalidMove(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Invalid move";
            // TODO: Show invalid move message and revert
            // uiState.showInvalidMove(reason);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleGameEnd(String payload) {
        try {
            System.out.println("[GameHandler] Received GAME_END message, payload: " + payload);
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String winSide = json.has("win_side") ? json.get("win_side").getAsString() : "unknown";
            
            System.out.println("[GameHandler] Parsed winSide: " + winSide);
            
            // Xác định kết quả game dựa trên winSide
            String currentUsername = uiState.getUsername();
            final boolean isDraw = "draw".equalsIgnoreCase(winSide);
            final boolean isWinner = !isDraw && currentUsername != null && currentUsername.equals(winSide);
            final String finalWinSide = winSide;
            
            System.out.println("[GameHandler] Processing GAME_END - isDraw: " + isDraw + ", isWinner: " + isWinner + ", currentUsername: " + currentUsername);
            
            // Trigger game result dialog thông qua UIState
            Platform.runLater(() -> {
                System.out.println("[GameHandler] Platform.runLater - resetting trigger");
                uiState.setGameActionTrigger("");
                uiState.setGameActionResult("");
                Platform.runLater(() -> {
                    // QUAN TRỌNG: Set result TRƯỚC trigger vì listener đọc result ngay khi trigger thay đổi
                    if (isDraw) {
                        System.out.println("[GameHandler] Setting game result to DRAW");
                        uiState.setGameActionResult("draw");
                        uiState.setGameActionTrigger("game_result");
                    } else if (isWinner) {
                        System.out.println("[GameHandler] Setting game result to WIN");
                        uiState.setGameActionResult("win");
                        uiState.setGameActionTrigger("game_result");
                    } else {
                        System.out.println("[GameHandler] Setting game result to LOSE");
                        uiState.setGameActionResult("lose");
                        uiState.setGameActionTrigger("game_result");
                    }
                    System.out.println("[GameHandler] Game ended - winSide: " + finalWinSide + ", isWinner: " + isWinner + ", isDraw: " + isDraw);
                });
            });
        } catch (Exception e) {
            System.err.println("[GameHandler] Error parsing GAME_END: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleChallengeRequest(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Show challenge dialog
            // uiState.showChallengeDialog(fromUser);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleChallengeResponse(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            boolean accepted = json.has("accepted") && json.get("accepted").getAsBoolean();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Handle challenge response
            // if (accepted) uiState.startGameWithOpponent(fromUser);
            // else uiState.showChallengeRejected(fromUser);
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleChallengeCancel(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            // TODO: Hide challenge dialog
            // uiState.hideChallengeDialog();
        } catch (Exception e) {
            // Ignore parse errors
        }
    }
    
    private void handleDrawRequest(String payload) {
        try {
            // Khi nhận được DRAW_REQUEST từ đối phương, hiển thị dialog
            // Reset trigger trước để đảm bảo listener luôn trigger
            Platform.runLater(() -> {
                uiState.setGameActionTrigger("");
                uiState.setGameActionResult("");
                // Set giá trị mới sau một khoảng thời gian ngắn để đảm bảo listener trigger
                Platform.runLater(() -> {
                    // QUAN TRỌNG: Set result TRƯỚC trigger vì listener đọc result ngay khi trigger thay đổi
                    uiState.setGameActionResult("received");
                    uiState.setGameActionTrigger("draw_request");
                    System.out.println("[GameHandler] Draw request received from opponent - trigger set, result=received");
                });
            });
        } catch (Exception e) {
            System.err.println("[GameHandler] Error handling DRAW_REQUEST: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleDrawResponse(String payload) {
        try {
            System.out.println("[GameHandler] Received DRAW_RESPONSE message, payload: " + payload);
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            boolean accepted = json.has("accept_draw") && json.get("accept_draw").getAsBoolean();
            
            System.out.println("[GameHandler] DRAW_RESPONSE - accepted: " + accepted);
            
            Platform.runLater(() -> {
                if (accepted) {
                    // Đối phương chấp nhận draw
                    // LƯU Ý: Backend sẽ gửi GAME_END sau đó, nên ta KHÔNG nên trigger game_result ở đây
                    // Chỉ cần log và đợi GAME_END message từ backend
                    System.out.println("[GameHandler] Draw accepted by opponent - waiting for GAME_END from backend");
                    // KHÔNG trigger game_result ở đây vì backend sẽ gửi GAME_END
                } else {
                    // Đối phương từ chối draw - ẩn dialog
                    uiState.setGameActionTrigger("");
                    uiState.setGameActionResult("");
                    Platform.runLater(() -> {
                        // QUAN TRỌNG: Set result TRƯỚC trigger
                        uiState.setGameActionResult("hide");
                        uiState.setGameActionTrigger("draw_request");
                        System.out.println("[GameHandler] Draw declined by opponent");
                    });
                }
            });
        } catch (Exception e) {
            System.err.println("[GameHandler] Error handling DRAW_RESPONSE: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleMessage(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String message = json.has("message") ? json.get("message").getAsString() : "";
            
            if (message != null && !message.isEmpty()) {
                // Trigger chat popup thông qua UIState
                Platform.runLater(() -> {
                    uiState.setGameActionTrigger("");
                    uiState.setGameActionResult("");
                    Platform.runLater(() -> {
                        // QUAN TRỌNG: Set result TRƯỚC trigger vì listener đọc result ngay khi trigger thay đổi
                        uiState.setGameActionResult(message);
                        uiState.setGameActionTrigger("chat_message");
                        System.out.println("[GameHandler] Chat message received: " + message);
                    });
                });
            }
        } catch (Exception e) {
            System.err.println("[GameHandler] Error handling MESSAGE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
