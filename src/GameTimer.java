import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class GameTimer {

    private static Timeline timer;
    private static int remainingSeconds = 10 * 60; // 10 minutes shared across floors
    private static boolean started = false;
    private static Label currentLabel;

    /** Reset the timer completely */
    public static void reset() {
        stop();
        remainingSeconds = 10 * 60; // Reset to 10 minutes
        started = false;
        if (currentLabel != null) {
            updateLabel();
        }
    }
    /** Attach the current floor's timer label */
    public static void attachLabel(Label label) {
        currentLabel = label;
        updateLabel();
    }

    /** Start the timer once with an onTimeUp callback */
    public static void start(Runnable onTimeUp) {
        if (started) return;
        started = true;

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            updateLabel();

            if (remainingSeconds <= 0) {
                timer.stop();
                if (onTimeUp != null) {
                    // Ensure UI updates happen on JavaFX thread
                    Platform.runLater(onTimeUp);
                }
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    /** Resume timer after floor change */
    public static void resume() {
        if (timer != null) timer.play();
        updateLabel();
    }

    /** Pause timer */
    public static void pause() {
        if (timer != null) timer.pause();
    }

    /** Stop timer completely */
    public static void stop() {
        if (timer != null) timer.stop();
    }

    private static void updateLabel() {
        if (currentLabel != null) {
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            currentLabel.setText(String.format("Time: %02d:%02d", minutes, seconds));
        }
    }

    public static int getRemainingSeconds() {
        return remainingSeconds;
    }
}
