import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.geometry.Bounds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class CafeController {

    @FXML
    private AnchorPane anchorPane;

    @FXML
    private Rectangle player; // Gold player square

    @FXML
    private Label victoryLabel;

    @FXML
    private Label timerLabel;

    @FXML
    private Rectangle player1, player11, player111, player1111, player11111; // Plates

    private static final double MOVE_SPEED = 5.0;

    private List<Rectangle> plates;
    private List<Integer> plateValues; // numeric mapping
    private boolean[] locked;

    private int passIndex = 0; // current bubble sort index
    private boolean swappedInPass = false;

    private int timeLeft = 180; // 3 minutes in seconds
    private boolean gameOver = false;

    @FXML
public void initialize() {
    anchorPane.setFocusTraversable(true);
    anchorPane.requestFocus();

    plates = List.of(player1, player11, player111, player1111, player11111);
    plateValues = new ArrayList<>();
    locked = new boolean[plates.size()];

    // Map colors to numbers
    for (Rectangle r : plates) {
        switch (r.getFill().toString()) {
            case "0x000000ff" -> plateValues.add(1); // black
            case "0xff0000ff" -> plateValues.add(2); // red
            case "0x00ff00ff" -> plateValues.add(3); // green
            case "0x0000ffff" -> plateValues.add(4); // blue
            case "0xffffffff" -> plateValues.add(5); // white
            default -> plateValues.add(0);
        }
    }

    anchorPane.setOnMouseClicked(e -> anchorPane.requestFocus());

    // Set fixed window size
    Platform.runLater(() -> {
        Stage stage = (Stage) anchorPane.getScene().getWindow();
        stage.setMinWidth(650);
        stage.setMinHeight(500);
        stage.setMaxWidth(650);
        stage.setMaxHeight(500);
        stage.setResizable(false);
    });

    showIntroPopups(); // show sequence of popups
}

    /** Show intro messages in sequence */
    private void showIntroPopups() {
        Platform.runLater(() -> {
            Alert alert1 = new Alert(Alert.AlertType.INFORMATION);
            alert1.setTitle("Cafe Game");
            alert1.setHeaderText(null);
            alert1.setContentText("You are a waiter of this cafe.");
            Optional<ButtonType> result1 = alert1.showAndWait();
            if (result1.isPresent() && result1.get() == ButtonType.OK) {
                Alert alert2 = new Alert(Alert.AlertType.INFORMATION);
                alert2.setTitle("Cafe Game");
                alert2.setHeaderText(null);
                alert2.setContentText("Here magenta coloured tables are reserved for VIP customers.\n You put plates in wrong order in the reserved tables.");
                Optional<ButtonType> result2 = alert2.showAndWait();
                if (result2.isPresent() && result2.get() == ButtonType.OK) {
                    Alert alert3 = new Alert(Alert.AlertType.INFORMATION);
                    alert3.setTitle("Cafe Game");
                    alert3.setHeaderText(null);
                    alert3.setContentText(
                            "You should start from T1 to T5.\n You can simply compare two adjacent plates and swap them if they are in the wrong order.");
                    Optional<ButtonType> result3 = alert3.showAndWait();
                    if (result3.isPresent() && result3.get() == ButtonType.OK) {
                        Alert alert4 = new Alert(Alert.AlertType.INFORMATION);
                        alert4.setTitle("Cafe Game");
                        alert4.setHeaderText(null);
                        alert4.setContentText(
                                "You have to do it until the the plates order is Black, Red, Green, Blue, White");
                        Optional<ButtonType> result4 = alert4.showAndWait();
                        if (result4.isPresent() && result4.get() == ButtonType.OK) {
                            Alert alert5 = new Alert(Alert.AlertType.INFORMATION);
                            alert5.setTitle("Cafe Game");
                            alert5.setHeaderText(null);
                            alert5.setContentText("Now you have 3 minutes to order it! Let's go");
                            Optional<ButtonType> result5 = alert5.showAndWait();
                            if (result5.isPresent() && result5.get() == ButtonType.OK) {
                                startCountdown(); // start timer after final popup
                            }
                        }
                    }
                }
            }
        });
    }

    @FXML
    private void handleKeyPressed(KeyEvent event) {
        if (gameOver)
            return; // stop movement after game over

        double newX = player.getLayoutX();
        double newY = player.getLayoutY();

        switch (event.getCode()) {
            case UP -> newY -= MOVE_SPEED;
            case DOWN -> newY += MOVE_SPEED;
            case LEFT -> newX -= MOVE_SPEED;
            case RIGHT -> newX += MOVE_SPEED;
            default -> {
                return;
            }
        }

        double oldX = player.getLayoutX();
        double oldY = player.getLayoutY();

        player.setLayoutX(newX);
        player.setLayoutY(newY);

        if (checkCollision()) {
            player.setLayoutX(oldX);
            player.setLayoutY(oldY);
        }

        checkBubbleSortTouch();

        anchorPane.requestFocus();
    }

    /** Collision with walls/other shapes, ignoring plates */
    private boolean checkCollision() {
        Bounds playerBounds = player.getBoundsInParent();
        for (var node : anchorPane.getChildren()) {
            if (node == player || plates.contains(node))
                continue;
            if (node instanceof Rectangle shape) {
                if (playerBounds.intersects(shape.getBoundsInParent()))
                    return true;
            }
        }
        double left = player.getLayoutX();
        double right = left + player.getWidth();
        double top = player.getLayoutY();
        double bottom = top + player.getHeight();
        return left < 0 || top < 0 || right > anchorPane.getWidth() || bottom > anchorPane.getHeight();
    }

    /** Bubble sort logic: swap only when player touches the SECOND plate in pair */
    private void checkBubbleSortTouch() {
        if (passIndex >= plates.size() - 1) {
            if (!swappedInPass) {
                triggerWin(); // all passes done, sorted
            }
            passIndex = 0;
            swappedInPass = false;
        }

        Rectangle first = plates.get(passIndex);
        Rectangle second = plates.get(passIndex + 1);

        // Skip already locked plates
        if (locked[passIndex] && locked[passIndex + 1]) {
            passIndex++;
            return;
        }

        // Only trigger swap when touching the SECOND plate
        if (player.getBoundsInParent().intersects(second.getBoundsInParent())) {
            int val1 = plateValues.get(passIndex);
            int val2 = plateValues.get(passIndex + 1);

            if (val1 > val2) {
                swapPlates(first, second);
                plateValues.set(passIndex, val2);
                plateValues.set(passIndex + 1, val1);
                swappedInPass = true;
            }

            lockCorrectPlates();
            passIndex++;
        }
    }

    private void swapPlates(Rectangle a, Rectangle b) {
        var temp = a.getFill();
        a.setFill(b.getFill());
        b.setFill(temp);
    }

    private void lockCorrectPlates() {
        for (int i = 0; i < plateValues.size(); i++) {
            if (plateValues.get(i) == i + 1)
                locked[i] = true;
        }
    }

    /** Countdown timer logic */
    private void startCountdown() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (gameOver) {
                        timer.cancel();
                        return;
                    }
                    int minutes = timeLeft / 60;
                    int seconds = timeLeft % 60;
                    timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
                    timeLeft--;
                    if (timeLeft < 0) {
                        triggerLose();
                        timer.cancel();
                    }
                });
            }
        }, 0, 1000);
    }

    /** Trigger win screen */
private void triggerWin() {
    gameOver = true;
    try {
        System.out.println("Creating win screen programmatically...");
        
        // Create win screen programmatically
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(20);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #81C784); -fx-padding: 20;");
        
        javafx.scene.control.Label emojiLabel = new javafx.scene.control.Label("🎉");
        emojiLabel.setStyle("-fx-font-size: 48px;");
        
        javafx.scene.control.Label winLabel = new javafx.scene.control.Label("You Win!");
        winLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");
        
        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label("Congratulations! You sorted all the plates correctly!");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white; -fx-text-alignment: center; -fx-wrap-text: true;");
        
        javafx.scene.control.Button tryAgainBtn = new javafx.scene.control.Button("Play Again");
        tryAgainBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        tryAgainBtn.setOnAction(e -> {
            System.out.println("Play Again clicked - restarting cafe game");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/cafe.fxml"));
                javafx.scene.Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        javafx.scene.control.Button mainMenuBtn = new javafx.scene.control.Button("Main Menu");
        mainMenuBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        mainMenuBtn.setOnAction(e -> {
            System.out.println("Main Menu clicked");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                javafx.scene.Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        vbox.getChildren().addAll(emojiLabel, winLabel, messageLabel, tryAgainBtn, mainMenuBtn);
        
        Scene scene = new Scene(vbox, 600, 400);
        Stage stage = (Stage) anchorPane.getScene().getWindow();
        stage.setScene(scene);
        stage.show();
        
        System.out.println("Win screen created successfully!");
        
    } catch (Exception e) {
        System.err.println("Error creating win screen: " + e.getMessage());
        e.printStackTrace();
    }
}

/** Trigger lose screen */
private void triggerLose() {
    gameOver = true;
    try {
        System.out.println("Creating lose screen programmatically...");
        
        // Create lose screen programmatically
        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(20);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #FF5252, #FF8A80); -fx-padding: 20;");
        
        javafx.scene.control.Label emojiLabel = new javafx.scene.control.Label("😞");
        emojiLabel.setStyle("-fx-font-size: 48px;");
        
        javafx.scene.control.Label loseLabel = new javafx.scene.control.Label("Time's Up!");
        loseLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");
        
        javafx.scene.control.Label messageLabel = new javafx.scene.control.Label("You didn't sort the plates in time.\nBetter luck next time!");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white; -fx-text-alignment: center; -fx-wrap-text: true;");
        
        javafx.scene.control.Button tryAgainBtn = new javafx.scene.control.Button("Try Again");
        tryAgainBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        tryAgainBtn.setOnAction(e -> {
            System.out.println("Try Again clicked - restarting cafe game");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/cafe.fxml"));
                javafx.scene.Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        javafx.scene.control.Button mainMenuBtn = new javafx.scene.control.Button("Main Menu");
        mainMenuBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        mainMenuBtn.setOnAction(e -> {
            System.out.println("Main Menu clicked");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                javafx.scene.Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        vbox.getChildren().addAll(emojiLabel, loseLabel, messageLabel, tryAgainBtn, mainMenuBtn);
        
        Scene scene = new Scene(vbox, 600, 400);
        Stage stage = (Stage) anchorPane.getScene().getWindow();
        stage.setScene(scene);
        stage.show();
        
        System.out.println("Lose screen created successfully!");
        
    } catch (Exception e) {
        System.err.println("Error creating lose screen: " + e.getMessage());
        e.printStackTrace();
    }
}
}
