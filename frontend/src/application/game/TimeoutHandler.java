package application.game;

import javafx.application.Platform;

/**
 * Handler for game timeout logic
 * Manages what happens when a player runs out of time
 */
public class TimeoutHandler {
    
    /**
     * Callback interface for timeout events
     */
    public interface TimeoutCallback {
        /**
         * Called when a player runs out of time
         * @param losingPlayerColor "red" or "black" - the player who ran out of time
         */
        void onTimeout(String losingPlayerColor);
    }
    
    private TimeoutCallback callback;
    
    public TimeoutHandler() {
        // Constructor
    }
    
    /**
     * Set the callback to be called when timeout occurs
     */
    public void setTimeoutCallback(TimeoutCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Handle timeout for a specific timer
     * @param timerIndex Timer index (0=red remaining, 1=red gray, 2=black gray, 3=black remaining)
     * @param currentTurn Current player's turn ("red" or "black")
     */
    public void handleTimeout(int timerIndex, String currentTurn) {
        // Timer còn lại (0 hoặc 3) hết = người chơi đó thua
        // Timer xám (1 hoặc 2) hết = chuyển sang timer còn lại (không thua)
        
        boolean isRemainingTimer = (timerIndex == 0 || timerIndex == 3);
        
        if (isRemainingTimer) {
            // Timer còn lại hết = người chơi thua
            String losingPlayer = (timerIndex == 0) ? "red" : "black";
            
            System.out.println("[TimeoutHandler] Player " + losingPlayer + " ran out of time (timer " + timerIndex + " expired)");
            
            // Gọi callback trên JavaFX thread
            if (callback != null) {
                Platform.runLater(() -> {
                    callback.onTimeout(losingPlayer);
                });
            }
        } else {
            // Timer xám hết = chuyển sang timer còn lại (không thua ngay)
            System.out.println("[TimeoutHandler] Gray timer " + timerIndex + " expired, switching to remaining timer");
            // Logic này đã được xử lý trong TimerManager
        }
    }
    
    /**
     * Check if timeout should result in game loss
     * @param timerIndex Timer index
     * @return true if this timer expiration means the player loses
     */
    public boolean isLosingTimeout(int timerIndex) {
        // Chỉ timer còn lại (0, 3) hết mới dẫn đến thua
        return (timerIndex == 0 || timerIndex == 3);
    }
}
