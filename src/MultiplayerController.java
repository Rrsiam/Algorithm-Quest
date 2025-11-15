import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.*;

public class MultiplayerController {

    @FXML
    private AnchorPane rootPane;

    @FXML
    private Rectangle player;

    @FXML
    private Label timerLabel;

    private Circle opponentPlayer;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static int playerId;
    private static boolean gameStarted = false;
    private static boolean connectedToServer = false;

    private double step = 5;
    private List<Rectangle> walls = new ArrayList<>();
    private List<Line> barriers = new ArrayList<>();
    private List<Rectangle> collectibles = new ArrayList<>();
    private List<Rectangle> classroomDoors = new ArrayList<>();
    private List<Rectangle> washroomDoors = new ArrayList<>();
    private List<Rectangle> lifts = new ArrayList<>();
    private List<Rectangle> blackRectangles = new ArrayList<>(); // For washroom collision

    private Map<Rectangle, Integer> doorIDs = new HashMap<>();
    private int nextDoorID = 1;
    private boolean allClassroomsVisited = false;

    private static String currentFloor = "Ground Floor";
    private static MultiplayerController currentInstance;

    private static final Map<String, String> floorMap = new HashMap<>();
    static {
        floorMap.put("Ground Floor", "/fxml/game2mul.fxml");
        floorMap.put("First Floor", "/fxml/game3mul.fxml");
        floorMap.put("Top Floor", "/fxml/game3mul.fxml"); // Assuming game3.fxml is the top floor
    }

    @FXML
public void initialize() {
    System.out.println("Initializing MultiplayerController for " + currentFloor + "...");
    currentInstance = this;

    // Initialize the global timer with callbacks
    MultiplayerGameTimer.initialize(this::updateTimerDisplay, this::handleTimeUp);

    // FIRST: Set up focus and key listeners BEFORE anything else
    setupFocusAndKeyHandling();

    // THEN: Initialize game elements
    initializeGameElements();

    // Initialize network connection only if not already connected
    if (!connectedToServer) {
        setupNetwork();
        startNetworkListener();
    } else {
        // If already connected, just update the game state
        gameStarted = true;
        updatePlayerColor();
    }

    // Initialize timer display
    initializeTimer();

    System.out.println(
            "Controller initialized for " + currentFloor + ". Timer: " + MultiplayerGameTimer.getFormattedTime()
                    + ", running: " + MultiplayerGameTimer.isRunning());

    // DEBUG: Print all classroom door IDs
    System.out.println("Classroom door IDs: " + doorIDs.values());

    // Set fixed window size
    Platform.runLater(() -> {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.setMinWidth(620);
        stage.setMinHeight(440);
        stage.setMaxWidth(620);
        stage.setMaxHeight(440);
        stage.setResizable(false);
    });
}

    private void initializeTimer() {
        // Update the display with current timer state
        updateTimerDisplay();

        // If game is started but timer isn't running, start it
        if (gameStarted && !MultiplayerGameTimer.isRunning()) {
            MultiplayerGameTimer.startTimer();
        }
    }

    private void updateTimerDisplay() {
        if (timerLabel != null) {
            String timeText = MultiplayerGameTimer.getFormattedTime();
            timerLabel.setText("Time: " + timeText);

            // Change color to red when less than 1 minute remaining
            if (MultiplayerGameTimer.getTimeRemaining() <= 60) {
                timerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: red;");
            } else {
                timerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: black;");
            }
        }
    }

    private void handleTimeUp() {
        System.out.println("Time's up! Game over.");

        // Check if any player has won before time ran out
        if (gameStarted) {
            // Game was running but time ran out - check server for winner
            if (out != null && connectedToServer) {
                out.println("TIME_UP");
            } else {
                // Single player mode - player loses
                showTimeUpDefeatScreen();
            }
        }
    }

    private void showTimeUpDefeatScreen() {
        Platform.runLater(() -> {
            showProgrammaticLoseScreen("Time's up! Neither player found the IT room in time.");
        });
    }

    // This method handles the TIME_UP_DEFEAT message from server
    private void handleTimeUpDefeat() {
        System.out.println("Handling time up defeat from server");
        Platform.runLater(() -> {
            showProgrammaticLoseScreen("Time's up! Neither player found the IT room in time.");
        });
    }

    private void setupFocusAndKeyHandling() {
        // Make the rootPane focus traversable and request focus
        rootPane.setFocusTraversable(true);

        // Add event filter to capture key events
        rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            handleKeyPress(event.getCode());
            event.consume(); // Consume the event to prevent further propagation
        });

        // Set mouse click to force focus
        rootPane.setOnMouseClicked(event -> {
            rootPane.requestFocus();
            System.out.println("RootPane focused by mouse click");
        });

        // Request focus after a short delay to ensure scene is shown
        Platform.runLater(() -> {
            rootPane.requestFocus();
            System.out.println("Focus requested on rootPane");
        });
    }

    private void handleKeyPress(KeyCode code) {
        if (!gameStarted) {
            System.out.println("Game not started yet. Waiting for server...");
            return;
        }

        if (MultiplayerGameTimer.getTimeRemaining() <= 0) {
            System.out.println("Game over - time's up!");
            return;
        }

        System.out.println("Key pressed: " + code);

        double oldX = player.getLayoutX();
        double oldY = player.getLayoutY();
        boolean moved = false;

        switch (code) {
            case UP:
                player.setLayoutY(player.getLayoutY() - step);
                moved = true;
                break;
            case DOWN:
                player.setLayoutY(player.getLayoutY() + step);
                moved = true;
                break;
            case LEFT:
                player.setLayoutX(player.getLayoutX() - step);
                moved = true;
                break;
            case RIGHT:
                player.setLayoutX(player.getLayoutX() + step);
                moved = true;
                break;
            default:
                // Ignore other keys
                return;
        }

        if (!moved)
            return;

        System.out.println("Attempting move from (" + oldX + ", " + oldY + ") to (" +
                player.getLayoutX() + ", " + player.getLayoutY() + ")");

        // Check boundaries
        if (player.getLayoutX() < 0 || player.getLayoutX() + player.getWidth() > rootPane.getWidth()
                || player.getLayoutY() < 0 || player.getLayoutY() + player.getHeight() > rootPane.getHeight()) {
            player.setLayoutX(oldX);
            player.setLayoutY(oldY);
            System.out.println("Movement blocked: out of bounds");
            return;
        }

        // Wall collisions
        for (Rectangle wall : walls) {
            if (player.getBoundsInParent().intersects(wall.getBoundsInParent())) {
                player.setLayoutX(oldX);
                player.setLayoutY(oldY);
                System.out.println("Movement blocked: wall collision");
                return;
            }
        }

        // Line barriers collisions
        for (Line line : barriers) {
            if (player.getBoundsInParent().intersects(line.getBoundsInParent())) {
                player.setLayoutX(oldX);
                player.setLayoutY(oldY);
                System.out.println("Movement blocked: barrier collision");
                return;
            }
        }

        // Black rectangles (washrooms) collisions - FIXED COLLISION DETECTION
        for (Rectangle blackRect : blackRectangles) {
            if (checkCollision(player, blackRect)) {
                player.setLayoutX(oldX);
                player.setLayoutY(oldY);
                System.out.println("Movement blocked: washroom collision");
                return;
            }
        }

        // If we reached here, movement is valid
        System.out.println("Movement successful to: " + player.getLayoutX() + ", " + player.getLayoutY());

        // Send position update to server
        sendPositionUpdate();

        // Check interactions
        handleCollectibles();
        handleClassroomDoors();
        handleWashroomDoors();
        handleLiftInteractions();
    }

    // Improved collision detection method
    private boolean checkCollision(Rectangle player, Rectangle obstacle) {
        double playerLeft = player.getLayoutX();
        double playerRight = player.getLayoutX() + player.getWidth();
        double playerTop = player.getLayoutY();
        double playerBottom = player.getLayoutY() + player.getHeight();

        double obstacleLeft = obstacle.getLayoutX();
        double obstacleRight = obstacle.getLayoutX() + obstacle.getWidth();
        double obstacleTop = obstacle.getLayoutY();
        double obstacleBottom = obstacle.getLayoutY() + obstacle.getHeight();

        return playerRight > obstacleLeft &&
                playerLeft < obstacleRight &&
                playerBottom > obstacleTop &&
                playerTop < obstacleBottom;
    }

    private void initializeGameElements() {
        System.out.println("Initializing game elements for " + currentFloor + "...");

        // Clear previous collections
        walls.clear();
        barriers.clear();
        collectibles.clear();
        classroomDoors.clear();
        washroomDoors.clear();
        lifts.clear();
        blackRectangles.clear();
        doorIDs.clear();

        for (var node : rootPane.getChildren()) {
            if (node instanceof Rectangle r) {
                // Handle color comparison safely
                Color fillColor = null;
                if (r.getFill() instanceof Color) {
                    fillColor = (Color) r.getFill();
                }

                if (fillColor != null) {
                    if (fillColor.equals(Color.WHITE)) {
                        classroomDoors.add(r);
                        try {
                            if (r.getId() != null) {
                                int id = Integer.parseInt(r.getId());
                                doorIDs.put(r, id);
                                System.out.println("Found classroom door with ID: " + id + " at position: "
                                        + r.getLayoutX() + ", " + r.getLayoutY());
                            }
                        } catch (Exception e) {
                            System.out.println("Invalid ID for classroom door: " + r.getId());
                        }

                    } else if (fillColor.equals(Color.ORANGE)) {
                        washroomDoors.add(r);
                        System.out.println("Found washroom door at: " + r.getLayoutX() + ", " + r.getLayoutY());

                    } else if (fillColor.equals(Color.DODGERBLUE)
                            || fillColor.equals(Color.GRAY)) {
                        walls.add(r);

                    } else if (isWashroomColor(fillColor)) {
                        // FIXED: Properly identify washroom rectangles (#1e2226 color)
                        blackRectangles.add(r);
                        System.out.println("Found washroom collision rectangle at: " + r.getLayoutX() + ", "
                                + r.getLayoutY() + " with color: " + fillColor);

                    } else if (r.getId() != null && (r.getId().equals("100") || r.getId().equals("200") ||
                            r.getId().equals("300") || r.getId().equals("400") ||
                            r.getId().equals("500") || r.getId().equals("600"))) {
                        collectibles.add(r);

                    } else if (fillColor.equals(Color.LIGHTGRAY) || fillColor.equals(Color.RED)) {
                        lifts.add(r);
                    }
                }
            } else if (node instanceof Line line) {
                barriers.add(line);
            }
        }

        // Remove doors without valid IDs
        classroomDoors.removeIf(d -> doorIDs.get(d) == null);

        // Sort doors by ID
        classroomDoors.sort(Comparator.comparingInt(doorIDs::get));
        System.out.println("Sorted classroom doors: " + doorIDs.values());

        // Create opponent player visualization
        if (opponentPlayer == null) {
            opponentPlayer = new Circle(5, Color.RED);
            opponentPlayer.setVisible(false);
            rootPane.getChildren().add(opponentPlayer);
        }

        System.out.println("Game elements initialized. Walls: " + walls.size() +
                ", Barriers: " + barriers.size() +
                ", Washroom collision rectangles: " + blackRectangles.size() +
                ", Classroom doors: " + classroomDoors.size() +
                ", Washroom doors: " + washroomDoors.size());

        // DEBUG: Check if room 22 exists for victory condition
        boolean hasRoom22 = doorIDs.containsValue(22);
        System.out.println("Room 22 exists for victory condition: " + hasRoom22);
        if (!hasRoom22) {
            System.out.println("WARNING: Room 22 not found! Available rooms: " + doorIDs.values());
        }
    }

    // Helper method to identify washroom collision rectangles (#1e2226 color)
    private boolean isWashroomColor(Color color) {
        // Check for black color
        if (color.equals(Color.BLACK)) {
            return true;
        }

        // Check for #1e2226 color (dark gray/black used for washrooms)
        // #1e2226 in RGB: R=0.1176, G=0.1333, B=0.1490
        double red = color.getRed();
        double green = color.getGreen();
        double blue = color.getBlue();

        // Allow some tolerance for color comparison
        boolean isDarkGray = Math.abs(red - 0.1176) < 0.01 &&
                Math.abs(green - 0.1333) < 0.01 &&
                Math.abs(blue - 0.1490) < 0.01;

        return isDarkGray;
    }

    private void setupNetwork() {
        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connectedToServer = true;
            System.out.println("Connected to game server successfully");
        } catch (IOException e) {
            System.err.println("Cannot connect to game server: " + e.getMessage());
            showAlert("Connection Error", "Cannot connect to game server. Starting in single-player mode.");
            // Allow single player mode
            gameStarted = true;
            connectedToServer = false;
        }
    }

    private void startNetworkListener() {
        if (!connectedToServer)
            return;

        Thread networkThread = new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received from server: " + message);
                    handleNetworkMessage(message);
                }
            } catch (IOException e) {
                System.out.println("Disconnected from server: " + e.getMessage());
                Platform.runLater(() -> {
                    if (gameStarted) { // Only show alert if game was actually running
                        showAlert("Disconnected", "Lost connection to the game server.");
                        connectedToServer = false;
                    }
                });
            }
        });
        networkThread.setDaemon(true);
        networkThread.start();
    }

    private void handleNetworkMessage(String message) {
        Platform.runLater(() -> {
            try {
                String[] parts = message.split(":");
                switch (parts[0]) {
                    case "PLAYER_ID":
                        playerId = Integer.parseInt(parts[1]);
                        System.out.println("Assigned player ID: " + playerId);
                        updatePlayerColor();
                        break;

                    case "START_GAME":
                        gameStarted = true;
                        System.out.println("Game started! Both players connected. Timer starting now!");
                        showInfo("Game Started", "Competition begins! Find the IT room first! You have 10 minutes.");
                        // Start the timer when both players are connected and game starts
                        MultiplayerGameTimer.startTimer();
                        // Ensure focus after game start
                        rootPane.requestFocus();
                        break;

                    case "PLAYER_POSITION":
                        int otherPlayerId = Integer.parseInt(parts[1]);
                        if (otherPlayerId != playerId) {
                            double x = Double.parseDouble(parts[2]);
                            double y = Double.parseDouble(parts[3]);
                            System.out.println("Updating opponent position: " + x + ", " + y);
                            updateOpponentPosition(x, y);
                        }
                        break;

                    case "ITEM_COLLECTED":
                        int collectorId = Integer.parseInt(parts[1]);
                        String itemId = parts[2];
                        handleRemoteItemCollection(collectorId, itemId);
                        break;

                    case "ROOM_VISITED":
                        int visitorId = Integer.parseInt(parts[1]);
                        int roomId = Integer.parseInt(parts[2]);
                        handleRemoteRoomVisit(visitorId, roomId);
                        break;

                    case "FLOOR_CHANGED":
                        int floorChangerId = Integer.parseInt(parts[1]);
                        String floor = parts[2];
                        if (floorChangerId != playerId) {
                            handleRemoteFloorChange(floor);
                        }
                        break;

                    // FIXED: Handle victory message properly
                    case "VICTORY":
                        int winnerId = Integer.parseInt(parts[1]);
                        System.out.println(
                                "Received VICTORY message for player: " + winnerId + ", I am player: " + playerId);
                        if (winnerId == playerId) {
                            System.out.println("I am the winner! Showing victory screen.");
                            showProgrammaticWinScreen(
                                    "Congratulations Player " + playerId + "! You found the IT Room!");
                        } else {
                            System.out.println("I am the loser! Showing defeat screen.");
                            showProgrammaticLoseScreen("The other player reached the IT room first!");
                        }
                        break;

                    case "DEFEAT":
                        String defeatMessage = parts.length > 1 ? parts[1] : "The other player won!";
                        System.out.println("Received DEFEAT message: " + defeatMessage);
                        showProgrammaticLoseScreen(defeatMessage);
                        break;

                    case "TIME_UP_DEFEAT":
                        handleTimeUpDefeat();
                        break;

                    default:
                        System.out.println("Unknown message: " + message);
                }
            } catch (Exception e) {
                System.err.println("Error handling network message: " + e.getMessage());
            }
        });
    }

    private void updatePlayerColor() {
        if (playerId == 1) {
            player.setFill(Color.BLUE);
        } else {
            player.setFill(Color.GREEN);
        }
        System.out.println("Player color updated for player " + playerId);
    }

    private void updateOpponentPosition(double x, double y) {
        if (opponentPlayer != null) {
            opponentPlayer.setCenterX(x + 3.5);
            opponentPlayer.setCenterY(y + 3.5);
            opponentPlayer.setVisible(true);
        }
    }

    private void handleRemoteItemCollection(int collectorId, String itemId) {
        // Remove item from local game
        Iterator<Rectangle> iterator = collectibles.iterator();
        while (iterator.hasNext()) {
            Rectangle item = iterator.next();
            if (item.getId() != null && item.getId().equals(itemId)) {
                Platform.runLater(() -> {
                    if (rootPane.getChildren().contains(item)) {
                        rootPane.getChildren().remove(item);
                    }
                });
                iterator.remove();
                break;
            }
        }

        // Don't show popup for opponent's actions to avoid interruption
        System.out.println("Player " + collectorId + " collected item " + itemId);
    }

    private void handleRemoteRoomVisit(int visitorId, int roomId) {
        // Don't show popup for opponent's room visits
        System.out.println("Player " + visitorId + " visited room " + roomId);

        // Only update visual if it's the current player's action
        if (visitorId == playerId && currentInstance != null) {
            // Find and mark the door as visited
            for (Rectangle door : currentInstance.classroomDoors) {
                if (currentInstance.doorIDs.get(door) == roomId) {
                    door.setFill(Color.BLACK);
                    break;
                }
            }
        }
    }

    private void handleRemoteFloorChange(String floor) {
        currentFloor = floor;
        // Don't show popup for opponent's floor changes
        System.out.println("Opponent moved to " + floor);

        // Hide opponent when they change floors
        if (opponentPlayer != null) {
            opponentPlayer.setVisible(false);
        }
    }

    private void sendPositionUpdate() {
        if (out != null && connectedToServer) {
            String message = "POSITION_UPDATE:" + player.getLayoutX() + ":" + player.getLayoutY();
            out.println(message);
        }
    }

    private void handleCollectibles() {
        Iterator<Rectangle> iterator = collectibles.iterator();
        while (iterator.hasNext()) {
            Rectangle item = iterator.next();
            if (player.getBoundsInParent().intersects(item.getBoundsInParent())) {
                if (out != null && connectedToServer && item.getId() != null) {
                    out.println("ITEM_COLLECTED:" + item.getId());
                }
                Platform.runLater(() -> {
                    if (rootPane.getChildren().contains(item)) {
                        rootPane.getChildren().remove(item);
                    }
                });
                iterator.remove();
                break;
            }
        }
    }

    private void handleClassroomDoors() {
        for (Rectangle door : classroomDoors) {
            if (player.getBoundsInParent().intersects(door.getBoundsInParent())) {
                int id = doorIDs.get(door);
                if (door.getFill().equals(Color.BLACK))
                    continue;

                if (id == nextDoorID) {
                    door.setFill(Color.BLACK);
                    nextDoorID++;
                    checkAllClassroomsVisited();

                    if (out != null && connectedToServer) {
                        out.println("ROOM_VISITED:" + id);
                    }

                    // FIXED: VICTORY CONDITION - Top Floor AND door ID 22
                    boolean isITRoom = currentFloor.equals("Top Floor") && id == 22;

                    if (isITRoom) {
                        System.out.println("=== VICTORY ACHIEVED ===");
                        System.out.println("IT Room reached! Player " + playerId + " wins!");
                        System.out.println("Floor: " + currentFloor + ", Door ID: " + id);

                        if (out != null && connectedToServer) {
                            // Send victory message to server
                            out.println("VICTORY:" + playerId);
                            System.out.println("Sent VICTORY message to server for player " + playerId);
                            showProgrammaticWinScreen(
                                    "Congratulations Player " + playerId + "! You found the IT Room!");

                            // FIXED: Don't show victory screen here - wait for server response
                            // The server will send VICTORY message back to the winning player
                        } else {
                            // Single player victory
                            showProgrammaticWinScreen(
                                    "Congratulations Player " + playerId + "! You found the IT Room!");
                        }
                        return;
                    }
                } else {
                    showInfo("Wrong Order", "You must explore Room " + nextDoorID + " first!");
                }
            }
        }
    }

    // Victory condition: Top Floor AND door ID 22
    private boolean checkIfITRoom(int doorId) {
        boolean isITRoom = currentFloor.equals("Top Floor") && doorId == 22;

        if (isITRoom) {
            System.out.println("=== VICTORY CONDITION MET ===");
            System.out.println("Player " + playerId + " reached IT Room!");
            System.out.println("Floor: " + currentFloor + ", Door ID: " + doorId);
            System.out.println("=== ===================== ===");
        } else if (currentFloor.equals("Top Floor") && doorId != 22) {
            System.out.println("On Top Floor but wrong door. Door ID: " + doorId + " (need 22)");
        } else if (doorId == 22 && !currentFloor.equals("Top Floor")) {
            System.out.println("Found door 22 but wrong floor. Current floor: " + currentFloor + " (need Top Floor)");
        }

        return isITRoom;
    }

    private void handleWashroomDoors() {
        for (Rectangle washroom : washroomDoors) {
            if (player.getBoundsInParent().intersects(washroom.getBoundsInParent())) {
                int id = 0;
                try {
                    if (washroom.getId() != null) {
                        id = Integer.parseInt(washroom.getId());
                    }
                } catch (Exception e) {
                    System.out.println("Washroom ID parsing error: " + e.getMessage());
                }

                String message;
                if (id == 100 || id == 500)
                    message = "This is Female Washroom";
                else if (id == 200 || id == 600)
                    message = "This is Male Washroom";
                else if (id == 300 || id == 400)
                    message = "This is Faculty/Staff Washroom";
                else
                    message = "Unknown Washroom";

                showInfo("Washroom", message);
                return; // Only show one washroom message at a time
            }
        }
    }

    private void handleLiftInteractions() {
        for (Rectangle lift : lifts) {
            if (player.getBoundsInParent().intersects(lift.getBoundsInParent())) {
                if (allClassroomsVisited) {
                    handleFloorChange();
                } else {
                    showInfo("Locked", "Explore all classrooms before using it!");
                }
                break;
            }
        }
    }

    private void checkAllClassroomsVisited() {
        boolean allBlack = classroomDoors.stream()
                .allMatch(door -> door.getFill().equals(Color.BLACK));

        if (allBlack && !allClassroomsVisited) {
            allClassroomsVisited = true;
            for (Rectangle lift : lifts) {
                lift.setFill(Color.GREEN);
            }
        }
    }

    private void handleFloorChange() {
        if (currentFloor.equals("Top Floor")) {
            showInfo("Top Floor", "You are in the top floor. Cannot go up!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Lift");
        alert.setHeaderText("Choose direction");
        ButtonType upButton = new ButtonType("Go Up");
        ButtonType cancelButton = new ButtonType("Cancel");
        alert.getButtonTypes().setAll(upButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == upButton) {
                String nextFxml = floorMap.get(currentFloor);
                if (nextFxml != null) {
                    try {
                        // Update current floor
                        if (currentFloor.equals("Ground Floor")) {
                            currentFloor = "First Floor";
                        } else if (currentFloor.equals("First Floor")) {
                            currentFloor = "Top Floor";
                        }

                        // Send floor change to server
                        if (out != null && connectedToServer) {
                            out.println("FLOOR_CHANGED:" + currentFloor);
                        }

                        // Load next floor
                        FXMLLoader loader = new FXMLLoader(getClass().getResource(nextFxml));
                        Parent root = loader.load();
                        Stage stage = (Stage) rootPane.getScene().getWindow();
                        Scene scene = new Scene(root);
                        stage.setScene(scene);
                        stage.show();

                    } catch (IOException e) {
                        e.printStackTrace();
                        showAlert("Error", "Cannot load next floor: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void showProgrammaticWinScreen(String message) {
        System.out.println("=== VICTORY ACHIEVED ===");
        System.out.println("Showing victory screen for player " + playerId);

        // Stop the timer
        MultiplayerGameTimer.stopTimer();

        Platform.runLater(() -> {
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

                javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(message);
                messageLabel.setStyle(
                        "-fx-font-size: 18px; -fx-text-fill: white; -fx-text-alignment: center; -fx-wrap-text: true;");

                javafx.scene.control.Button mainMenuBtn = new javafx.scene.control.Button("Main Menu");
                mainMenuBtn.setStyle(
                        "-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
                mainMenuBtn.setOnAction(e -> {
                    System.out.println("Main Menu clicked");
                    try {
                        cleanup();
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                        javafx.scene.Parent root = loader.load();
                        Stage stage = (Stage) vbox.getScene().getWindow();
                        stage.setScene(new Scene(root, 800, 600));
                        stage.show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

                vbox.getChildren().addAll(emojiLabel, winLabel, messageLabel, mainMenuBtn);

                Scene scene = new Scene(vbox, 600, 400);
                Stage stage = (Stage) rootPane.getScene().getWindow();
                stage.setScene(scene);
                stage.show();

                System.out.println("Win screen created successfully!");

            } catch (Exception e) {
                System.err.println("Error creating win screen: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void showProgrammaticLoseScreen(String message) {
        System.out.println("Handling defeat: " + message);

        // Stop the timer when game ends
        MultiplayerGameTimer.stopTimer();

        Platform.runLater(() -> {
            try {
                System.out.println("Creating lose screen programmatically...");

                // Create lose screen programmatically
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(20);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #FF5252, #FF8A80); -fx-padding: 20;");

                javafx.scene.control.Label emojiLabel = new javafx.scene.control.Label("😞");
                emojiLabel.setStyle("-fx-font-size: 48px;");

                javafx.scene.control.Label loseLabel = new javafx.scene.control.Label("You Lose!");
                loseLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");

                javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(message);
                messageLabel.setStyle(
                        "-fx-font-size: 18px; -fx-text-fill: white; -fx-text-alignment: center; -fx-wrap-text: true;");

                javafx.scene.control.Button mainMenuBtn = new javafx.scene.control.Button("Main Menu");
                mainMenuBtn.setStyle(
                        "-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
                mainMenuBtn.setOnAction(e -> {
                    System.out.println("Main Menu clicked");
                    try {
                        cleanup();
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
                        javafx.scene.Parent root = loader.load();
                        Stage stage = (Stage) vbox.getScene().getWindow();
                        stage.setScene(new Scene(root, 800, 600));
                        stage.show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

                vbox.getChildren().addAll(emojiLabel, loseLabel, messageLabel, mainMenuBtn);

                Scene scene = new Scene(vbox, 600, 400);
                Stage stage = (Stage) rootPane.getScene().getWindow();
                stage.setScene(scene);
                stage.show();

                System.out.println("Lose screen created successfully!");

            } catch (Exception e) {
                System.err.println("Error creating lose screen: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Static method to get current floor
    public static String getCurrentFloor() {
        return currentFloor;
    }

    // Static cleanup method
    public static void cleanup() {
        try {
            MultiplayerGameTimer.stopTimer();

            if (out != null) {
                out.println("PLAYER_DISCONNECTING");
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (socket != null) {
                socket.close();
            }
            connectedToServer = false;
            gameStarted = false;
            System.out.println("Network connection closed properly");
        } catch (IOException e) {
            System.err.println("Error closing network connection: " + e.getMessage());
        }
    }

    // Static reset timer method
    public static void resetTimer() {
        MultiplayerGameTimer.reset();
    }
}