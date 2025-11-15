import javafx.animation.AnimationTimer;
import javafx.application.Platform;

public class MultiplayerGameTimer {
    private static AnimationTimer timer;
    private static int timeRemaining = 600; // 10 minutes in seconds
    private static boolean timerRunning = false;
    private static long lastUpdateTime = 0;
    private static Runnable onTimeUpdate;
    private static Runnable onTimeUp;

    public static void initialize(Runnable updateCallback, Runnable timeUpCallback) {
        onTimeUpdate = updateCallback;
        onTimeUp = timeUpCallback;
    }

    public static void startTimer() {
        if (timerRunning) return;
        
        timerRunning = true;
        lastUpdateTime = System.nanoTime();
        
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long elapsedNanos = now - lastUpdateTime;
                long elapsedSeconds = elapsedNanos / 1_000_000_000;
                
                if (elapsedSeconds >= 1) {
                    timeRemaining--;
                    lastUpdateTime = now;
                    
                    Platform.runLater(() -> {
                        if (onTimeUpdate != null) {
                            onTimeUpdate.run();
                        }
                        
                        if (timeRemaining <= 0) {
                            stopTimer();
                            if (onTimeUp != null) {
                                onTimeUp.run();
                            }
                        }
                    });
                }
            }
        };
        
        timer.start();
        System.out.println("Game timer started! Time remaining: " + timeRemaining + "s");
    }

    public static void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        timerRunning = false;
        System.out.println("Game timer stopped");
    }

    public static int getTimeRemaining() {
        return timeRemaining;
    }

    public static String getFormattedTime() {
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static boolean isRunning() {
        return timerRunning;
    }

    public static void reset() {
        stopTimer();
        timeRemaining = 600;
        lastUpdateTime = 0;
        System.out.println("Game timer reset to 10:00");
    }
}