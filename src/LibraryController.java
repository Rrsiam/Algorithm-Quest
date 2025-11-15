import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LibraryController {

    @FXML
    private AnchorPane rootPane;
    @FXML
    private Label timerLabel;

    private Rectangle player;
    private final List<Node> collidableObjects = new ArrayList<>();
    private final List<Rectangle> books = new ArrayList<>();
    private final Set<Rectangle> touchedBooks = new HashSet<>();
    private Rectangle targetBook; // The book to search

    private Timeline timer;
    private int timeRemaining = 180; // 3 minutes in seconds
    private int popupCount = 0;
    private boolean gameInitialized = false;

    @FXML
public void initialize() {
    System.out.println("Initializing Library Controller...");

    // Initialize game objects first
    initializeGameObjects();

    // Set up key listener immediately
    setupKeyListener();

    // Set fixed window size
    Platform.runLater(() -> {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setMinWidth(650);
        stage.setMinHeight(450);
        stage.setMaxWidth(650);
        stage.setMaxHeight(450);
        stage.setResizable(false);
    });

    // Show game instructions after a short delay to ensure scene is loaded
    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.seconds(0.5));
    pause.setOnFinished(e -> showGameInstructions());
    pause.play();
}

    private void initializeGameObjects() {
        // Clear any previous collections
        collidableObjects.clear();
        books.clear();
        touchedBooks.clear();

        // Identify all objects in the scene
        for (Node node : rootPane.getChildren()) {
            if (node instanceof Rectangle rect) {
                Color color = (Color) rect.getFill();
                String hex = color.toString().toLowerCase();

                // Debug output for each rectangle
                System.out.println("Rectangle found - ID: " + rect.getId() +
                        ", Color: " + hex +
                        ", Position: " + rect.getLayoutX() + ", " + rect.getLayoutY());

                if (hex.equals("0xf20acfff") || hex.equals("#f20acf")) {
                    player = rect; // Player (pink square)
                    System.out.println("Player identified at: " + player.getLayoutX() + ", " + player.getLayoutY());
                } else if (hex.equals("0xffff00ff") || hex.equals("0xffd700ff") || hex.equals("yellow")) {
                    books.add(rect); // Books (yellow rectangles)

                    // Check if this is the book with ID "1"
                    if (rect.getId() != null && rect.getId().equals("1")) {
                        targetBook = rect;
                        System.out.println("Target book with ID '1' found at: " +
                                targetBook.getLayoutX() + ", " + targetBook.getLayoutY());
                    }
                } else if (hex.equals("0x7b3f00ff") || hex.equals("#7b3f00") || // Brown shelves
                        hex.equals("0x966f33ff") || hex.equals("#966f33") || // Brown tables
                        hex.equals("0x808080ff") || hex.equals("grey") || // Grey objects
                        hex.equals("0xa52a2aff") || hex.equals("brown")) { // Brown objects
                    collidableObjects.add(rect); // Obstacles
                }
            } else if (node instanceof Line) {
                collidableObjects.add(node); // Lines as obstacles
                System.out.println("Line obstacle added");
            }
        }

        // If target book wasn't found by ID, try to find it by position
        if (targetBook == null) {
            System.out.println("Book with ID '1' not found, searching by position...");
            for (Rectangle book : books) {
                // Look for the book at position (69, 46) which is the one with id="1" in your
                // FXML
                if (Math.abs(book.getLayoutX() - 69.0) < 1 && Math.abs(book.getLayoutY() - 46.0) < 1) {
                    targetBook = book;
                    System.out.println("Target book found by position at: " +
                            targetBook.getLayoutX() + ", " + targetBook.getLayoutY());
                    break;
                }
            }
        }

        // Final fallback - select random book if still not found
        if (targetBook == null) {
            System.out.println("Target book not found by ID or position, selecting random book");
            if (!books.isEmpty()) {
                targetBook = books.get(new Random().nextInt(books.size()));
                System.out.println("Random target book selected at: " +
                        targetBook.getLayoutX() + ", " + targetBook.getLayoutY());
            }
        }

        // Final check
        if (targetBook == null) {
            System.out.println("ERROR: No target book found at all!");
        } else {
            System.out.println("SUCCESS: Target book finalized at: " +
                    targetBook.getLayoutX() + ", " + targetBook.getLayoutY());
        }

        if (player == null) {
            System.out.println("ERROR: Player not found!");
        }

        System.out.println("Total books: " + books.size());
        System.out.println("Total obstacles: " + collidableObjects.size());
    }

    private void showGameInstructions() {
        String[] instructions = {
                "You are the librarian of the UIU Library.",
                "You misplaced an important book of a Professor.\n You have to find the book by searching all shelfs",
                "Here Yellow shapes are books",
                "The book is needed to professor now and he give 3 minutes time to find it.",
                "You have to find the book 3 minutes."
        };

        showNextPopup(instructions);
    }

    private void showNextPopup(String[] instructions) {
        if (popupCount < instructions.length) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Game Instructions");
            alert.setHeaderText(null); // Remove the header text
            alert.setContentText(instructions[popupCount]);

            // Set up event handler for when the current alert closes
            alert.setOnHidden(event -> {
                popupCount++;
                if (popupCount < instructions.length) {
                    // Show next popup after a short delay
                    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                            Duration.seconds(0.5));
                    pause.setOnFinished(e -> showNextPopup(instructions));
                    pause.play();
                } else {
                    // All popups shown, start the game
                    startGame();
                }
            });

            alert.show();
        }
    }

    private void startGame() {
        System.out.println("Starting game with timer...");
        gameInitialized = true;
        startTimer();
    }

    private void startTimer() {
        updateTimerDisplay();

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeRemaining--;
            updateTimerDisplay();

            if (timeRemaining <= 0) {
                timer.stop();
                loadLoseScene();
            }
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    private void updateTimerDisplay() {
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

        // Change color to red when time is running out
        if (timeRemaining <= 30) {
            timerLabel.setStyle(
                    "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: red; -fx-background-color: white; -fx-padding: 5px;");
        }
    }

    private void setupKeyListener() {
        // Direct approach - get the current scene and add listener
        Scene scene = rootPane.getScene();
        if (scene != null) {
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleMovement);
            System.out.println("Key listener attached to scene directly");
        } else {
            // Fallback: wait for scene to be set
            rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleMovement);
                    System.out.println("Key listener attached to scene via property listener");
                }
            });
        }
    }

    private void handleMovement(KeyEvent event) {
        if (player == null) {
            System.out.println("Player is null, cannot move");
            return;
        }

        // Only allow movement after game is initialized (popups finished)
        if (!gameInitialized) {
            System.out.println("Game not initialized yet - waiting for popups to finish");
            return;
        }

        double step = 3; // Increased step for better movement
        double dx = 0, dy = 0;

        switch (event.getCode()) {
            case UP -> dy = -step;
            case DOWN -> dy = step;
            case LEFT -> dx = -step;
            case RIGHT -> dx = step;
            default -> {
                return;
            } // Ignore other keys
        }

        // Store original position
        double originalX = player.getLayoutX();
        double originalY = player.getLayoutY();

        // Move temporarily
        player.setLayoutX(originalX + dx);
        player.setLayoutY(originalY + dy);

        // Check collision with obstacles
        boolean collisionDetected = false;
        for (Node obstacle : collidableObjects) {
            if (checkCollision(player, obstacle)) {
                collisionDetected = true;
                System.out.println("Collision detected with obstacle");
                break;
            }
        }

        // If collision with obstacle, revert movement
        if (collisionDetected) {
            player.setLayoutX(originalX);
            player.setLayoutY(originalY);
        } else {
            // Only check book collisions if we actually moved
            checkBookCollisions();
        }

        event.consume(); // Prevent further processing of the key event
    }

    private void checkBookCollisions() {
        if (targetBook == null)
            return;

        for (Rectangle book : books) {
            if (checkCollision(player, book)) {
                if (book == targetBook) {
                    System.out.println("Target book found! Loading win scene...");
                    timer.stop(); // Stop the timer when game is won
                    loadWinScene();
                } else if (!touchedBooks.contains(book)) {
                    touchedBooks.add(book);
                    book.setFill(Color.RED); // Mark book as checked
                    System.out.println("Wrong book touched at: " + book.getLayoutX() + ", " + book.getLayoutY());
                    showNotBookAlert();
                }
                break; // Only process one book collision per movement
            }
        }
    }

    private boolean checkCollision(Node a, Node b) {
        try {
            Bounds boundsA = a.localToParent(a.getBoundsInLocal());
            Bounds boundsB = b.localToParent(b.getBoundsInLocal());
            return boundsA.intersects(boundsB);
        } catch (Exception e) {
            System.err.println("Error checking collision: " + e.getMessage());
            return false;
        }
    }

    private void showNotBookAlert() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Not the book!");
        alert.setHeaderText(null);
        alert.setContentText("Ah! That's not the book");
        alert.showAndWait();
    }

    private void loadWinScene() {
    try {
        System.out.println("Creating win screen programmatically...");
        
        // Create win screen programmatically
        VBox vbox = new VBox(20);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #81C784); -fx-padding: 20;");
        
        Label emojiLabel = new Label("🎉");
        emojiLabel.setStyle("-fx-font-size: 48px;");
        
        Label winLabel = new Label("You Win!");
        winLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");
        
        Label messageLabel = new Label("Congratulations! You found the book!");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");
        
        Button tryAgainBtn = new Button("Play Again");
        tryAgainBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        tryAgainBtn.setOnAction(e -> {
            System.out.println("Play Again clicked");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/library.fxml"));
                Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        Button mainMenuBtn = new Button("Main Menu");
        mainMenuBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        mainMenuBtn.setOnAction(e -> {
            System.out.println("Main Menu clicked");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        vbox.getChildren().addAll(emojiLabel, winLabel, messageLabel, tryAgainBtn, mainMenuBtn);
        
        Scene scene = new Scene(vbox, 600, 400);
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setScene(scene);
        stage.show();
        
        System.out.println("Win screen created successfully!");
        
    } catch (Exception e) {
        System.err.println("Error creating win screen: " + e.getMessage());
        e.printStackTrace();
    }
}

private void loadLoseScene() {
    try {
        System.out.println("Creating lose screen programmatically...");
        
        // Create lose screen programmatically
        VBox vbox = new VBox(20);
        vbox.setAlignment(javafx.geometry.Pos.CENTER);
        vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #FF5252, #FF8A80); -fx-padding: 20;");
        
        Label emojiLabel = new Label("😞");
        emojiLabel.setStyle("-fx-font-size: 48px;");
        
        Label loseLabel = new Label("Time's Up!");
        loseLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");
        
        Label messageLabel = new Label("You didn't find the book in time.\nBetter luck next time!");
        messageLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white; -fx-text-alignment: center;");
        
        Button tryAgainBtn = new Button("Try Again");
        tryAgainBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        tryAgainBtn.setOnAction(e -> {
            System.out.println("Try Again clicked");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/library.fxml"));
                Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        Button mainMenuBtn = new Button("Main Menu");
        mainMenuBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
        mainMenuBtn.setOnAction(e -> {
            System.out.println("Main Menu clicked");
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                Parent root = loader.load();
                Stage stage = (Stage) vbox.getScene().getWindow();
                stage.setScene(new Scene(root, 800, 600));
                stage.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        vbox.getChildren().addAll(emojiLabel, loseLabel, messageLabel, tryAgainBtn, mainMenuBtn);
        
        Scene scene = new Scene(vbox, 600, 400);
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setScene(scene);
        stage.show();
        
        System.out.println("Lose screen created successfully!");
        
    } catch (Exception e) {
        System.err.println("Error creating lose screen: " + e.getMessage());
        e.printStackTrace();
    }
}

    // Utility method for debugging - can be removed in production
    public void printGameState() {
        System.out.println("=== Game State ===");
        System.out.println(
                "Player: " + (player != null ? "at " + player.getLayoutX() + ", " + player.getLayoutY() : "null"));
        System.out.println("Target Book: "
                + (targetBook != null ? "at " + targetBook.getLayoutX() + ", " + targetBook.getLayoutY() : "null"));
        System.out.println("Books found: " + books.size());
        System.out.println("Books touched: " + touchedBooks.size());
        System.out.println("Time remaining: " + timeRemaining + " seconds");
        System.out.println("Game initialized: " + gameInitialized);
        System.out.println("=================");
    }

    
}