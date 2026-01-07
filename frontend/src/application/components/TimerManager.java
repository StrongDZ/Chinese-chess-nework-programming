package application.components;

import application.state.UIState;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Manager để quản lý timer trong game
 */
public class TimerManager {
    
    private final UIState state;
    private final GamePanel gamePanel;
    
    private Timeline[] countdownTimers = new Timeline[4];  // 4 timers
    private int[] remainingSeconds = new int[4];  // Thời gian còn lại (giây)
    private boolean isUpdatingFromCountdown = false;  // Flag để tránh vòng lặp
    
    // Callback để cập nhật timer khi đổi lượt
    private Runnable onTurnChangeCallback = null;
    
    public TimerManager(UIState state, GamePanel gamePanel) {
        this.state = state;
        this.gamePanel = gamePanel;
    }
    
    /**
     * Tạo container cho các timer
     */
    public VBox createTimersContainer() {
        VBox container = new VBox(15);
        container.setAlignment(Pos.TOP_LEFT);
        
        // Tạo 4 timer riêng biệt và bind với state
        // Timer 1: bind với timer1Value
        StackPane timer1Pane = createTimerPane(state.timer1ValueProperty(), "2:00", 0);
        
        // Timer 2: bind với timer2Value
        StackPane timer2Pane = createTimerPane(state.timer2ValueProperty(), "10:00", 1);
        
        // Timer 3: bind với timer3Value
        StackPane timer3Pane = createTimerPane(state.timer3ValueProperty(), "10:00", 2);
        
        // Timer 4: bind với timer4Value
        StackPane timer4Pane = createTimerPane(state.timer4ValueProperty(), "2:00", 3);
        
        container.getChildren().addAll(timer1Pane, timer2Pane, timer3Pane, timer4Pane);
        
        // Bắt đầu countdown khi giá trị thay đổi (chỉ khi không phải từ countdown)
        state.timer1ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(0, newVal);
            }
        });
        state.timer2ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(1, newVal);
            }
        });
        state.timer3ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(2, newVal);
            }
        });
        state.timer4ValueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isUpdatingFromCountdown) {
                startCountdown(3, newVal);
            }
        });
        
        return container;
    }
    
    /**
     * Bắt đầu countdown cho tất cả timers khi game được mở
     */
    public void initializeTimers() {
        // Sử dụng Platform.runLater để đảm bảo giá trị đã được set
        Platform.runLater(() -> {
            // Force start countdown cho tất cả timers
            String timer1 = state.getTimer1Value();
            String timer2 = state.getTimer2Value();
            String timer3 = state.getTimer3Value();
            String timer4 = state.getTimer4Value();
            
            if (timer1 != null) {
                isUpdatingFromCountdown = true;
                startCountdown(0, timer1);
                isUpdatingFromCountdown = false;
            }
            if (timer2 != null) {
                isUpdatingFromCountdown = true;
                startCountdown(1, timer2);
                isUpdatingFromCountdown = false;
            }
            if (timer3 != null) {
                isUpdatingFromCountdown = true;
                startCountdown(2, timer3);
                isUpdatingFromCountdown = false;
            }
            if (timer4 != null) {
                isUpdatingFromCountdown = true;
                startCountdown(3, timer4);
                isUpdatingFromCountdown = false;
            }
            
            // Sau khi khởi tạo tất cả timers, cập nhật để chỉ chạy timer của bên đang đến lượt (nếu là blitz/custom mode)
            Platform.runLater(() -> {
                updateTimersOnTurnChange();
            });
        });
    }
    
    /**
     * Dừng tất cả timers
     */
    public void stopAllTimers() {
        for (int i = 0; i < 4; i++) {
            if (countdownTimers[i] != null) {
                countdownTimers[i].stop();
            }
        }
    }
    
    /**
     * Bắt đầu countdown cho một timer
     */
    private void startCountdown(int timerIndex, String initialValue) {
        // Dừng timer cũ nếu có
        if (countdownTimers[timerIndex] != null) {
            countdownTimers[timerIndex].stop();
        }
        
        // Parse giá trị ban đầu
        int seconds = parseTimeToSeconds(initialValue);
        
        if (seconds < 0) {
            // "Unlimited time" - không đếm
            return;
        }
        
        remainingSeconds[timerIndex] = seconds;
        
        // Cập nhật label ban đầu (không trigger listener)
        isUpdatingFromCountdown = true;
        updateTimerLabel(timerIndex, seconds);
        isUpdatingFromCountdown = false;
        
        // Kiểm tra xem có phải blitz hoặc custom mode không (chế độ timer theo lượt)
        String gameMode = state.getCurrentGameMode();
        boolean isTurnBasedMode = "blitz".equalsIgnoreCase(gameMode) || "custom".equalsIgnoreCase(gameMode);
        
        // Xác định timer thuộc bên nào
        // Timer 1, 2 (index 0, 1): Red player
        // Timer 3, 4 (index 2, 3): Black player
        boolean isRedTimer = (timerIndex == 0 || timerIndex == 1);
        boolean isBlackTimer = (timerIndex == 2 || timerIndex == 3);
        
        // Lấy currentTurn từ GamePanel
        String currentTurn = gamePanel.getCurrentTurn();
        
        // Tạo Timeline đếm ngược mỗi giây
        countdownTimers[timerIndex] = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                // Kiểm tra lại game mode mỗi lần để đảm bảo logic đúng
                String currentGameMode = state.getCurrentGameMode();
                boolean isTurnBased = "blitz".equalsIgnoreCase(currentGameMode) || "custom".equalsIgnoreCase(currentGameMode);
                
                // Lấy currentTurn mới nhất từ GamePanel
                String currentTurnNow = gamePanel.getCurrentTurn();
                
                // Trong blitz/custom mode, chỉ đếm nếu đến lượt của bên đó
                if (isTurnBased) {
                    if (isRedTimer && !currentTurnNow.equals("Red")) {
                        // Không phải lượt Red, dừng timer
                        countdownTimers[timerIndex].stop();
                        return;
                    }
                    if (isBlackTimer && !currentTurnNow.equals("Black")) {
                        // Không phải lượt Black, dừng timer
                        countdownTimers[timerIndex].stop();
                        return;
                    }
                }
                
                remainingSeconds[timerIndex]--;
                if (remainingSeconds[timerIndex] >= 0) {
                    // Update label mà không trigger listener
                    isUpdatingFromCountdown = true;
                    updateTimerLabel(timerIndex, remainingSeconds[timerIndex]);
                    isUpdatingFromCountdown = false;
                } else {
                    countdownTimers[timerIndex].stop();
                }
            })
        );
        countdownTimers[timerIndex].setCycleCount(Timeline.INDEFINITE);
        
        // Trong blitz/custom mode, KHÔNG tự động play timer
        // Chỉ tạo Timeline, để updateTimersOnTurnChange() quyết định timer nào được play
        if (!isTurnBasedMode) {
            // Không phải blitz/custom mode, chạy bình thường
            countdownTimers[timerIndex].play();
        }
        // Nếu là turn-based mode, không play ở đây, để updateTimersOnTurnChange() xử lý
    }
    
    /**
     * Cập nhật timer khi đổi lượt (chỉ trong blitz/custom mode)
     */
    public void updateTimersOnTurnChange() {
        String gameMode = state.getCurrentGameMode();
        boolean isTurnBasedMode = "blitz".equalsIgnoreCase(gameMode) || "custom".equalsIgnoreCase(gameMode);
        
        if (!isTurnBasedMode) {
            // Không phải blitz/custom mode, không cần làm gì
            return;
        }
        
        // Dừng tất cả timers một cách chắc chắn - đảm bảo không có timer nào đang chạy
        for (int i = 0; i < 4; i++) {
            if (countdownTimers[i] != null) {
                countdownTimers[i].stop();
                // Đảm bảo status là stopped
                if (countdownTimers[i].getStatus() == Timeline.Status.RUNNING) {
                    countdownTimers[i].stop();
                }
            }
        }
        
        // Đợi một chút để đảm bảo tất cả timers đã dừng hoàn toàn
        Platform.runLater(() -> {
            // Kiểm tra lại game mode (có thể đã thay đổi)
            String currentGameMode = state.getCurrentGameMode();
            boolean isStillTurnBased = "blitz".equalsIgnoreCase(currentGameMode) || "custom".equalsIgnoreCase(currentGameMode);
            
            if (!isStillTurnBased) {
                return; // Không còn turn-based mode
            }
            
            // Lấy currentTurn từ GamePanel
            String currentTurn = gamePanel.getCurrentTurn();
            
            // Bắt đầu timer của bên đang đến lượt
            if (currentTurn.equals("Red")) {
                // Bắt đầu timer 1 và 2 (Red) - dừng timer 3 và 4 (Black) để chắc chắn
                if (countdownTimers[2] != null) {
                    countdownTimers[2].stop();
                }
                if (countdownTimers[3] != null) {
                    countdownTimers[3].stop();
                }
                if (countdownTimers[0] != null && remainingSeconds[0] >= 0) {
                    countdownTimers[0].play();
                }
                if (countdownTimers[1] != null && remainingSeconds[1] >= 0) {
                    countdownTimers[1].play();
                }
            } else if (currentTurn.equals("Black")) {
                // Bắt đầu timer 3 và 4 (Black) - dừng timer 1 và 2 (Red) để chắc chắn
                if (countdownTimers[0] != null) {
                    countdownTimers[0].stop();
                }
                if (countdownTimers[1] != null) {
                    countdownTimers[1].stop();
                }
                if (countdownTimers[2] != null && remainingSeconds[2] >= 0) {
                    countdownTimers[2].play();
                }
                if (countdownTimers[3] != null && remainingSeconds[3] >= 0) {
                    countdownTimers[3].play();
                }
            }
        });
    }
    
    /**
     * Parse thời gian từ string sang seconds
     */
    private int parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.contains("Unlimited")) {
            return -1;  // Unlimited
        }
        
        // Parse "X mins" hoặc "X min"
        if (timeStr.contains("min")) {
            try {
                String number = timeStr.replaceAll("[^0-9]", "");
                int minutes = Integer.parseInt(number);
                return minutes * 60;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        
        // Parse "MM:SS"
        if (timeStr.contains(":")) {
            try {
                String[] parts = timeStr.split(":");
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60 + seconds;
            } catch (Exception e) {
                return -1;
            }
        }
        
        return -1;
    }
    
    /**
     * Cập nhật label của timer
     */
    private void updateTimerLabel(int timerIndex, int totalSeconds) {
        String formattedTime = formatSecondsToTime(totalSeconds);
        
        switch (timerIndex) {
            case 0:
                state.setTimer1Value(formattedTime);
                break;
            case 1:
                state.setTimer2Value(formattedTime);
                break;
            case 2:
                state.setTimer3Value(formattedTime);
                break;
            case 3:
                state.setTimer4Value(formattedTime);
                break;
        }
    }
    
    /**
     * Format seconds thành string "MM:SS"
     */
    private String formatSecondsToTime(int totalSeconds) {
        if (totalSeconds < 0) {
            return "Unlimited time";
        }
        
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Tạo một timer pane
     */
    private StackPane createTimerPane(javafx.beans.property.StringProperty valueProperty, String defaultValue, int index) {
        Rectangle timerBg = new Rectangle(120, 50);
        timerBg.setArcWidth(10);
        timerBg.setArcHeight(10);
        timerBg.setStroke(Color.color(0.3, 0.3, 0.3));
        timerBg.setStrokeWidth(1);
        
        // Timer 2 và 3 (index 1, 2) = #A8A4A4, còn lại = xám
        if (index == 1 || index == 2) {
            timerBg.setFill(Color.web("#A8A4A4"));
        } else {
            timerBg.setFill(Color.color(0.85, 0.85, 0.85));
        }
        
        Label timerLabel = new Label(defaultValue);
        timerLabel.textProperty().bind(valueProperty);  // Bind text với state
        timerLabel.setStyle(
            "-fx-font-family: 'Kolker Brush'; " +
            "-fx-font-size: 35px; " +
            "-fx-text-fill: black; " +
            "-fx-background-color: transparent;"
        );
        timerLabel.setAlignment(Pos.CENTER);
        
        StackPane timerPane = new StackPane();
        timerPane.setPrefSize(120, 50);
        timerPane.setAlignment(Pos.CENTER);
        timerPane.getChildren().addAll(timerBg, timerLabel);
        
        return timerPane;
    }
}


