package application.components;

import application.state.UIState;
import application.game.TimeoutHandler;
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
    private int[] initialSeconds = new int[4];  // Thời gian ban đầu để reset (giây)
    private boolean isUpdatingFromCountdown = false;  // Flag để tránh vòng lặp
    
    // Timer xám (index 1, 2): đếm trước
    // Timer còn lại (index 0, 3): chỉ chạy khi timer xám hết, reset sau mỗi lượt
    
    // Callback để cập nhật timer khi đổi lượt
    private Runnable onTurnChangeCallback = null;
    
    // Timeout handler
    private TimeoutHandler timeoutHandler;
    
    public TimerManager(UIState state, GamePanel gamePanel) {
        this.state = state;
        this.gamePanel = gamePanel;
        this.timeoutHandler = new TimeoutHandler();
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
        // Dừng tất cả timers cũ trước (nếu có)
        stopAllTimers();
        
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
            
            // Sau khi khởi tạo tất cả timers, cập nhật để chỉ chạy timer của bên đang đến lượt
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
                // Đảm bảo timer đã dừng hoàn toàn
                int retryCount = 0;
                while (countdownTimers[i].getStatus() == Timeline.Status.RUNNING && retryCount < 10) {
                    countdownTimers[i].stop();
                    retryCount++;
                }
            }
        }
    }
    
    /**
     * Set callback để xử lý khi timeout (timer còn lại hết)
     */
    public void setTimeoutCallback(TimeoutHandler.TimeoutCallback callback) {
        timeoutHandler.setTimeoutCallback(callback);
    }
    
    /**
     * Bắt đầu countdown cho một timer
     */
    private void startCountdown(int timerIndex, String initialValue) {
        // Dừng và xóa timer cũ nếu có
        if (countdownTimers[timerIndex] != null) {
            countdownTimers[timerIndex].stop();
            // Đảm bảo timer đã dừng hoàn toàn
            int retryCount = 0;
            while (countdownTimers[timerIndex].getStatus() == Timeline.Status.RUNNING && retryCount < 10) {
                countdownTimers[timerIndex].stop();
                retryCount++;
            }
            countdownTimers[timerIndex] = null;  // Xóa reference
        }
        
        // Parse giá trị ban đầu
        int seconds = parseTimeToSeconds(initialValue);
        
        if (seconds < 0) {
            // "Unlimited time" - không đếm
            return;
        }
        
        remainingSeconds[timerIndex] = seconds;
        initialSeconds[timerIndex] = seconds;  // Lưu giá trị ban đầu để reset
        
        // Cập nhật label ban đầu (không trigger listener)
        isUpdatingFromCountdown = true;
        updateTimerLabel(timerIndex, seconds);
        isUpdatingFromCountdown = false;
        
        // Xác định timer thuộc bên nào và loại timer
        // Timer 1, 2 (index 0, 1): red player
        // Timer 3, 4 (index 2, 3): black player
        // Timer xám (index 1, 2): đếm trước
        // Timer còn lại (index 0, 3): chỉ chạy khi timer xám hết
        boolean isRedTimer = (timerIndex == 0 || timerIndex == 1);
        boolean isBlackTimer = (timerIndex == 2 || timerIndex == 3);
        boolean isGrayTimer = (timerIndex == 1 || timerIndex == 2);  // Timer xám
        boolean isRemainingTimer = (timerIndex == 0 || timerIndex == 3);  // Timer còn lại
        
        // Lấy currentTurn từ GamePanel
        String currentTurn = gamePanel.getCurrentTurn();
        
        // Tạo Timeline đếm ngược mỗi giây
        countdownTimers[timerIndex] = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                // Lấy currentTurn mới nhất từ GamePanel
                String currentTurnNow = gamePanel.getCurrentTurn();
                
                // Chỉ đếm nếu đến lượt của bên đó
                if (isRedTimer && !currentTurnNow.equals("red")) {
                    countdownTimers[timerIndex].stop();
                    return;
                }
                if (isBlackTimer && !currentTurnNow.equals("black")) {
                    countdownTimers[timerIndex].stop();
                    return;
                }
                
                // LOGIC MỚI: Timer còn lại chỉ đếm khi timer xám đã hết
                if (isRemainingTimer) {
                    // Timer còn lại: kiểm tra timer xám tương ứng
                    int grayTimerIndex = (timerIndex == 0) ? 1 : 2;  // Timer 0 -> xám 1, Timer 3 -> xám 2
                    if (remainingSeconds[grayTimerIndex] > 0) {
                        // Timer xám chưa hết, timer còn lại không đếm
                        countdownTimers[timerIndex].stop();
                        return;
                    }
                    // Timer xám đã hết, timer còn lại mới đếm
                }
                
                // Đếm ngược
                remainingSeconds[timerIndex]--;
                if (remainingSeconds[timerIndex] >= 0) {
                    isUpdatingFromCountdown = true;
                    updateTimerLabel(timerIndex, remainingSeconds[timerIndex]);
                    isUpdatingFromCountdown = false;
                } else {
                    // Timer hết, dừng lại
                    countdownTimers[timerIndex].stop();
                    
                    // Xử lý timeout - nếu timer còn lại hết thì người chơi thua
                    if (isRemainingTimer) {
                        // Timer còn lại hết = người chơi thua
                        timeoutHandler.handleTimeout(timerIndex, currentTurnNow);
                        return;  // Không xử lý thêm
                    }
                    
                    // Nếu timer xám hết, bắt đầu timer còn lại
                    if (isGrayTimer) {
                        int remainingTimerIndex = (timerIndex == 1) ? 0 : 3;  // Timer xám 1 -> còn lại 0, xám 2 -> còn lại 3
                        if (countdownTimers[remainingTimerIndex] != null && remainingSeconds[remainingTimerIndex] > 0) {
                            // Kiểm tra turn trước khi start timer còn lại (sử dụng currentTurnNow đã có)
                            boolean isRemainingTimerForCurrentTurn = 
                                (remainingTimerIndex == 0 && currentTurnNow.equals("red")) ||
                                (remainingTimerIndex == 3 && currentTurnNow.equals("black"));
                            
                            if (isRemainingTimerForCurrentTurn) {
                                countdownTimers[remainingTimerIndex].play();
                                System.out.println("[TimerManager] Gray timer " + timerIndex + " exhausted, started remaining timer " + remainingTimerIndex);
                            }
                        }
                    }
                }
            })
        );
        countdownTimers[timerIndex].setCycleCount(Timeline.INDEFINITE);
        
        // Chỉ play timer nếu đến lượt của bên đó
        // Timer còn lại chỉ play nếu timer xám đã hết
        boolean shouldPlay = false;
        if ((isRedTimer && currentTurn.equals("red")) || 
            (isBlackTimer && currentTurn.equals("black"))) {
            if (isGrayTimer) {
                // Timer xám: play nếu đến lượt
                shouldPlay = true;
            } else if (isRemainingTimer) {
                // Timer còn lại: chỉ play nếu timer xám đã hết
                int grayTimerIndex = (timerIndex == 0) ? 1 : 2;
                shouldPlay = (remainingSeconds[grayTimerIndex] <= 0);
            }
        }
        
        if (shouldPlay) {
            countdownTimers[timerIndex].play();
            System.out.println("[TimerManager] Started timer " + timerIndex + " for " + currentTurn + " player");
        } else {
            System.out.println("[TimerManager] Timer " + timerIndex + " NOT started");
        }
    }
    
    /**
     * Cập nhật timer khi đổi lượt (áp dụng cho TẤT CẢ các mode)
     */
    public void updateTimersOnTurnChange() {
        // Dừng tất cả timers một cách chắc chắn - đảm bảo không có timer nào đang chạy
        for (int i = 0; i < 4; i++) {
            if (countdownTimers[i] != null) {
                countdownTimers[i].stop();
                // Đảm bảo status là stopped - thử nhiều lần nếu cần
                int retryCount = 0;
                while (countdownTimers[i].getStatus() == Timeline.Status.RUNNING && retryCount < 5) {
                    countdownTimers[i].stop();
                    retryCount++;
                    try {
                        Thread.sleep(10);  // Đợi một chút
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        // Đợi một chút để đảm bảo tất cả timers đã dừng hoàn toàn
        Platform.runLater(() -> {
            // Lấy currentTurn từ GamePanel
            String currentTurn = gamePanel.getCurrentTurn();
            
            System.out.println("[TimerManager] ========================================");
            System.out.println("[TimerManager] updateTimersOnTurnChange: currentTurn=" + currentTurn);
            
            // Đảm bảo tất cả timers đã dừng trước khi bắt đầu timer mới
            for (int i = 0; i < 4; i++) {
                if (countdownTimers[i] != null && countdownTimers[i].getStatus() == Timeline.Status.RUNNING) {
                    System.out.println("[TimerManager] WARNING: Timer " + i + " still running, forcing stop");
                    countdownTimers[i].stop();
                }
            }
            
            // Reset timer còn lại về giá trị ban đầu sau mỗi lượt
            // Timer 0 (red còn lại) và Timer 3 (black còn lại)
            if (currentTurn.equals("red")) {
                // Reset timer còn lại của red (timer 0) về giá trị ban đầu
                if (initialSeconds[0] > 0) {
                    remainingSeconds[0] = initialSeconds[0];
                    isUpdatingFromCountdown = true;
                    updateTimerLabel(0, remainingSeconds[0]);
                    isUpdatingFromCountdown = false;
                    System.out.println("[TimerManager] Reset red remaining timer (0) to " + remainingSeconds[0] + "s");
                }
            } else if (currentTurn.equals("black")) {
                // Reset timer còn lại của black (timer 3) về giá trị ban đầu
                if (initialSeconds[3] > 0) {
                    remainingSeconds[3] = initialSeconds[3];
                    isUpdatingFromCountdown = true;
                    updateTimerLabel(3, remainingSeconds[3]);
                    isUpdatingFromCountdown = false;
                    System.out.println("[TimerManager] Reset black remaining timer (3) to " + remainingSeconds[3] + "s");
                }
            }
            
            // Bắt đầu timer của bên đang đến lượt
            if (currentTurn.equals("red")) {
                // Dừng timer 3 và 4 (black) để chắc chắn
                if (countdownTimers[2] != null) {
                    countdownTimers[2].stop();
                }
                if (countdownTimers[3] != null) {
                    countdownTimers[3].stop();
                }
                
                // Timer xám của red (timer 1): play nếu còn thời gian
                if (countdownTimers[1] != null && remainingSeconds[1] > 0) {
                    countdownTimers[1].play();
                    System.out.println("[TimerManager] Started red gray timer 1 (remaining: " + remainingSeconds[1] + "s)");
                }
                
                // Timer còn lại của red (timer 0): chỉ play nếu timer xám đã hết
                if (countdownTimers[0] != null && remainingSeconds[1] <= 0 && remainingSeconds[0] > 0) {
                    countdownTimers[0].play();
                    System.out.println("[TimerManager] Started red remaining timer 0 (gray timer 1 exhausted, remaining: " + remainingSeconds[0] + "s)");
                }
            } else if (currentTurn.equals("black")) {
                // Dừng timer 1 và 2 (red) để chắc chắn
                if (countdownTimers[0] != null) {
                    countdownTimers[0].stop();
                }
                if (countdownTimers[1] != null) {
                    countdownTimers[1].stop();
                }
                
                // Timer xám của black (timer 2): play nếu còn thời gian
                if (countdownTimers[2] != null && remainingSeconds[2] > 0) {
                    countdownTimers[2].play();
                    System.out.println("[TimerManager] Started black gray timer 2 (remaining: " + remainingSeconds[2] + "s)");
                }
                
                // Timer còn lại của black (timer 3): chỉ play nếu timer xám đã hết
                if (countdownTimers[3] != null && remainingSeconds[2] <= 0 && remainingSeconds[3] > 0) {
                    countdownTimers[3].play();
                    System.out.println("[TimerManager] Started black remaining timer 3 (gray timer 2 exhausted, remaining: " + remainingSeconds[3] + "s)");
                }
            }
            System.out.println("[TimerManager] ========================================");
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


