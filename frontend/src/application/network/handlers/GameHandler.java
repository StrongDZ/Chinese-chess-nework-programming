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
    private application.components.PlayWithFriendPanel playWithFriendPanel;  // Reference to PlayWithFriendPanel
    
    public GameHandler(UIState uiState) {
        this.uiState = uiState;
    }
    
    /**
     * Set reference to PlayWithFriendPanel for challenge handling.
     */
    public void setPlayWithFriendPanel(application.components.PlayWithFriendPanel panel) {
        this.playWithFriendPanel = panel;
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
                }
                // Nếu opponent rỗng, sẽ xử lý ở phần opponent_data (AI game)
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
            
            // Extract time_limit if available
            int timeLimit = 0;  // 0 = unlimited
            if (json.has("time_limit")) {
                timeLimit = json.get("time_limit").getAsInt();
            }
            
            // Store mode and time limit in UIState
            uiState.setCurrentGameMode(gameMode);
            uiState.setCurrentTimeLimit(timeLimit);
            
            // Extract game_id, player_is_red, và ai_difficulty from opponent_data if available
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
                
                // Extract ai_difficulty nếu là AI game
                if (opponentData.has("ai_difficulty")) {
                    String aiDifficulty = opponentData.get("ai_difficulty").getAsString();
                    // Format: "AI (Easy)", "AI (Medium)", "AI (Hard)"
                    String capitalized = aiDifficulty.substring(0, 1).toUpperCase() + 
                                        aiDifficulty.substring(1).toLowerCase();
                    uiState.setOpponentUsername("AI (" + capitalized + ")");
                    System.out.println("[GameHandler] AI game detected, set opponentUsername to: AI (" + capitalized + ")");
                } else if (opponentData.has("is_ai_game") && opponentData.get("is_ai_game").getAsBoolean()) {
                    // Nếu là AI game nhưng không có ai_difficulty, giữ nguyên giá trị hiện tại hoặc set default
                    String currentOpponent = uiState.getOpponentUsername();
                    if (currentOpponent == null || currentOpponent.isEmpty() || "AI".equals(currentOpponent)) {
                        uiState.setOpponentUsername("AI (Medium)");
                        System.out.println("[GameHandler] AI game detected but no difficulty, defaulting to: AI (Medium)");
                    }
                }
            } else {
                // Default to red if no opponent_data
                uiState.setPlayerIsRed(true);
                System.out.println("[GameHandler] No opponent_data, defaulting to red");
                
                // Nếu opponent rỗng và không có opponent_data, có thể là AI game
                if ((opponent == null || opponent.isEmpty()) && 
                    (uiState.getOpponentUsername() == null || uiState.getOpponentUsername().isEmpty() || "AI".equals(uiState.getOpponentUsername()))) {
                    // Giữ nguyên giá trị đã set từ AIDifficultyPanel hoặc set default
                    String currentOpponent = uiState.getOpponentUsername();
                    if (currentOpponent == null || currentOpponent.isEmpty() || "AI".equals(currentOpponent)) {
                        uiState.setOpponentUsername("AI (Medium)");
                        System.out.println("[GameHandler] No opponent and no opponent_data, defaulting to: AI (Medium)");
                    }
                }
            }
            
            // Close waiting panel if open (from quick matching)
            if (uiState.isWaitingVisible()) {
                uiState.closeWaiting();
            }
            
            // Stop countdown timer and hide waiting panel if challenge was accepted (from PlayWithFriendPanel)
            if (playWithFriendPanel != null) {
                Platform.runLater(() -> {
                    playWithFriendPanel.stopCountdownTimer();
                    // Ẩn waiting panel khi vào game
                    playWithFriendPanel.hideWaitingForResponsePanel();
                    System.out.println("[GameHandler] Stopped countdown timer and hid waiting panel after GAME_START");
                });
            }
            
            // Open game panel with the game mode
            uiState.openGame(gameMode);
            
            System.out.println("[GameHandler] Game started - Opponent: " + opponent + ", Mode: " + gameMode + ", TimeLimit: " + timeLimit + "s");
        } catch (Exception e) {
            System.err.println("[GameHandler] Error parsing GAME_START: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleMove(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            
            // Parse move coordinates from server
            // Server format: {"from":{"row":..., "col":...}, "to":{"row":..., "col":...}, "piece":"..."}
            JsonObject from = json.getAsJsonObject("from");
            JsonObject to = json.getAsJsonObject("to");
            
            int backendFromRow = from.get("row").getAsInt();
            int fromCol = from.get("col").getAsInt();
            int backendToRow = to.get("row").getAsInt();
            int toCol = to.get("col").getAsInt();
            
            String piece = json.has("piece") ? json.get("piece").getAsString() : "Unknown";
            
            System.out.println("[GameHandler] Received MOVE: " + piece + 
                " from BE(row=" + backendFromRow + ",col=" + fromCol + 
                ") to BE(row=" + backendToRow + ",col=" + toCol + ")");
            
            // Apply opponent's move to the board
            // IMPORTANT: UIState.applyOpponentMove signature is (fromCol, fromRow, toCol, toRow)
            // Pass backend coordinates, applyOpponentMove will convert internally
            // (Backend row 0=top đen → Frontend row 0=top đỏ via formula: frontendRow = 9 - backendRow)
            Platform.runLater(() -> {
                uiState.applyOpponentMove(fromCol, backendFromRow, toCol, backendToRow);
            });
        } catch (Exception e) {
            System.err.println("[GameHandler] Error parsing MOVE: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("[GameHandler] Received CHALLENGE_REQUEST, payload: " + payload);
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            String mode = json.has("mode") ? json.get("mode").getAsString() : "classical";
            int timeLimit = json.has("time_limit") ? json.get("time_limit").getAsInt() : 0;
            
            System.out.println("[GameHandler] Parsed from_user: " + fromUser + ", mode: " + mode + ", timeLimit: " + timeLimit);
            System.out.println("[GameHandler] PlayWithFriendPanel is " + (playWithFriendPanel != null ? "set" : "null"));
            
            // Show challenge request dialog in PlayWithFriendPanel with mode and time info
            if (playWithFriendPanel != null) {
                Platform.runLater(() -> {
                    System.out.println("[GameHandler] Calling playWithFriendPanel.showChallengeRequest(" + fromUser + ", " + mode + ", " + timeLimit + ")");
                    playWithFriendPanel.showChallengeRequest(fromUser, mode, timeLimit);
                });
            } else {
                System.err.println("[GameHandler] PlayWithFriendPanel not set, cannot show challenge request");
            }
        } catch (Exception e) {
            System.err.println("[GameHandler] Error handling challenge request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleChallengeResponse(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            // Backend sends "accept" (boolean), not "accepted"
            boolean accepted = json.has("accept") && json.get("accept").getAsBoolean();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            System.out.println("[GameHandler] Received challenge response from: " + fromUser + ", accepted: " + accepted);
            
            // Handle challenge response in PlayWithFriendPanel
            if (playWithFriendPanel != null) {
                Platform.runLater(() -> {
                    if (accepted) {
                        // Challenge accepted - game will start automatically via GAME_START message
                        playWithFriendPanel.onChallengeAccepted();
                    } else {
                        // Challenge rejected
                        playWithFriendPanel.onChallengeRejected(fromUser);
                    }
                });
            } else {
                System.err.println("[GameHandler] PlayWithFriendPanel not set, cannot handle challenge response");
            }
        } catch (Exception e) {
            System.err.println("[GameHandler] Error handling challenge response: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleChallengeCancel(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String fromUser = json.has("from_user") ? json.get("from_user").getAsString() : "unknown";
            System.out.println("[GameHandler] Received CHALLENGE_CANCEL from: " + fromUser);
            
            // Hide challenge request dialog in PlayWithFriendPanel
            if (playWithFriendPanel != null) {
                Platform.runLater(() -> {
                    System.out.println("[GameHandler] Hiding challenge request dialog for: " + fromUser);
                    playWithFriendPanel.hideChallengeRequest();
                });
            } else {
                System.err.println("[GameHandler] PlayWithFriendPanel not set, cannot hide challenge request dialog");
            }
        } catch (Exception e) {
            System.err.println("[GameHandler] Error handling challenge cancel: " + e.getMessage());
            e.printStackTrace();
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
