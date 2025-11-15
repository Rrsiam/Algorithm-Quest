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
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.*;

public class BFSController {

    @FXML
    private AnchorPane rootPane;

    @FXML
    private Rectangle player;

    @FXML
    private Label timerLabel;

    private double step = 5;
    private List<Rectangle> walls = new ArrayList<>();
    private List<Line> barriers = new ArrayList<>();
    private List<Rectangle> collectibles = new ArrayList<>();
    private List<Rectangle> classroomDoors = new ArrayList<>();
    private List<Rectangle> washroomDoors = new ArrayList<>();
    private List<Rectangle> lifts = new ArrayList<>();
    private Map<Rectangle, Integer> doorIDs = new HashMap<>();

    private int nextDoorID = 1;
    private boolean allClassroomsVisited = false;
    private boolean gameWon = false;

    private static String currentFloor = null;
    private static final Map<String, String> floorMap = new HashMap<>();
    static {
        floorMap.put("Ground Floor", "/fxml/game2.fxml"); // fixed path
        floorMap.put("First Floor", "/fxml/game3.fxml"); // fixed path
    }

    @FXML
    public void initialize() {
        categorizeNodes();
        setupKeyControls();

        // Attach current floor's timer label
        GameTimer.attachLabel(timerLabel);

        if (currentFloor == null) {
            currentFloor = "Ground Floor";
            Platform.runLater(this::showIntroAndFloorPopups);
        } else {
            GameTimer.resume(); // continue timer on new floor
        }

        // Focus for key events
        rootPane.setFocusTraversable(true);
        rootPane.requestFocus();
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
             if (newScene != null) {
            newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                if (newWin != null) {
                    // Set fixed window size here
                    Stage stage = (Stage) newWin;
                    stage.setMinWidth(620);
                    stage.setMinHeight(440);
                    stage.setMaxWidth(620);
                    stage.setMaxHeight(440);
                    stage.setResizable(false);
                    
                    rootPane.requestFocus();
                }
            });
        }
        });
    }

    /** Categorize nodes based on color/type */
    private void categorizeNodes() {
        for (var node : rootPane.getChildren()) {
            if (node instanceof Rectangle r) {
                Color fillColor = (Color) r.getFill();

                if (fillColor.equals(Color.WHITE)) {
                    classroomDoors.add(r);
                    try {
                        int id = Integer.parseInt(r.getId());
                        doorIDs.put(r, id);
                    } catch (Exception e) {
                        System.out.println("Set numeric FX ID for classroom doors!");
                    }
                } else if (fillColor.equals(Color.ORANGE)) {
                    washroomDoors.add(r);
                } else if (fillColor.equals(Color.DODGERBLUE)
                        || fillColor.equals(Color.web("#1e2226"))
                        || fillColor.equals(Color.GRAY)) {
                    walls.add(r);
                } else if (fillColor.equals(Color.web("#f7eed3"))) {
                    collectibles.add(r);
                } else if (fillColor.equals(Color.LIGHTGRAY) || fillColor.equals(Color.RED)) {
                    lifts.add(r);
                }

            } else if (node instanceof Line line) {
                barriers.add(line);
            }
        }

        classroomDoors.removeIf(d -> doorIDs.get(d) == null);
        classroomDoors.sort(Comparator.comparingInt(doorIDs::get));
    }

    /** Show intro popups and start timer */
    private void showIntroAndFloorPopups() {
        List<String> intros = Arrays.asList(
                "You are a new student of UIU.",
                "You are facing problems to login on Ucam.",
                "You don't know the IT Room location, so you have to find the IT Room to solve the issue.",
                "You have to explore all classrooms in order to find the IT Room.\n You have to find it under 10 minutes.",
                "In the game interface,\n blue boxes are classrooms,\n black boxes are washrooms,\n lines are walls,\n gray & lightgray boxes are lift areas & stairs.",
                "After visiting all classrooms, the lift & stairs will unlock and turn green.",
                "Use the arrow keys to move and begin your mission!");

        for (String msg : intros) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Introduction");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        }

        Alert floorAlert = new Alert(Alert.AlertType.INFORMATION);
        floorAlert.setTitle("Floor Info");
        floorAlert.setHeaderText(null);
        floorAlert.setContentText("You are on the " + currentFloor + ".");
        floorAlert.showAndWait();

        // Start timer once, use Platform.runLater for safety
        GameTimer.start(this::onTimeUp);
    }

    /** Timer reached 0 */
    private void onTimeUp() {
    if (!gameWon) {
        Platform.runLater(() -> {
            try {
                System.out.println("Creating lose screen programmatically...");

                // Create lose screen programmatically
                javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(20);
                vbox.setAlignment(javafx.geometry.Pos.CENTER);
                vbox.setStyle(
                        "-fx-background-color: linear-gradient(to bottom, #FF5252, #FF8A80); -fx-padding: 20;");

                javafx.scene.control.Label emojiLabel = new javafx.scene.control.Label("😞");
                emojiLabel.setStyle("-fx-font-size: 48px;");

                javafx.scene.control.Label loseLabel = new javafx.scene.control.Label("Time's Up!");
                loseLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");

                javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(
                        "You didn't explore all classrooms in time.\nBetter luck next time!");
                messageLabel.setStyle(
                        "-fx-font-size: 18px; -fx-text-fill: white; -fx-text-alignment: center; -fx-wrap-text: true;");

                javafx.scene.control.Button tryAgainBtn = new javafx.scene.control.Button("Try Again");
                tryAgainBtn.setStyle(
                        "-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
                tryAgainBtn.setOnAction(e -> {
                    System.out.println("Try Again clicked - restarting BFS game");
                    try {
                        // Reset game state and timer
                        currentFloor = null;
                        gameWon = false;
                        GameTimer.reset(); // ← ADD THIS LINE to reset the timer
                        
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game1.fxml"));
                        javafx.scene.Parent root = loader.load();
                        Stage stage = (Stage) vbox.getScene().getWindow();
                        stage.setScene(new Scene(root, 800, 600));
                        stage.show();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

                javafx.scene.control.Button mainMenuBtn = new javafx.scene.control.Button("Main Menu");
                mainMenuBtn.setStyle(
                        "-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
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
                Stage stage = (Stage) rootPane.getScene().getWindow();
                stage.setScene(scene);
                stage.show();

                System.out.println("Lose screen created successfully!");

            } catch (Exception ex) {
                System.err.println("Error creating lose screen: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }
}

    /** Key movement and collision detection */
    private void setupKeyControls() {
        rootPane.setOnKeyPressed(event -> {
            double oldX = player.getLayoutX();
            double oldY = player.getLayoutY();

            if (event.getCode() == KeyCode.UP)
                player.setLayoutY(player.getLayoutY() - step);
            else if (event.getCode() == KeyCode.DOWN)
                player.setLayoutY(player.getLayoutY() + step);
            else if (event.getCode() == KeyCode.LEFT)
                player.setLayoutX(player.getLayoutX() - step);
            else if (event.getCode() == KeyCode.RIGHT)
                player.setLayoutX(player.getLayoutX() + step);

            // Boundaries
            if (player.getLayoutX() < 0 || player.getLayoutX() + player.getWidth() > rootPane.getWidth()
                    || player.getLayoutY() < 0 || player.getLayoutY() + player.getHeight() > rootPane.getHeight()) {
                player.setLayoutX(oldX);
                player.setLayoutY(oldY);
            }

            // Wall collisions
            for (Rectangle wall : walls) {
                if (player.getBoundsInParent().intersects(wall.getBoundsInParent())) {
                    player.setLayoutX(oldX);
                    player.setLayoutY(oldY);
                    break;
                }
            }

            // Line barriers
            for (Line line : barriers) {
                if (player.getBoundsInParent().intersects(line.getBoundsInParent())) {
                    player.setLayoutX(oldX);
                    player.setLayoutY(oldY);
                    break;
                }
            }

            // Collectibles
            List<Rectangle> collected = new ArrayList<>();
            for (Rectangle item : collectibles) {
                if (player.getBoundsInParent().intersects(item.getBoundsInParent())) {
                    collected.add(item);
                    rootPane.getChildren().remove(item);
                }
            }
            collectibles.removeAll(collected);

            // Classroom doors
            for (Rectangle door : classroomDoors) {
                if (player.getBoundsInParent().intersects(door.getBoundsInParent())) {
                    int id = doorIDs.get(door);
                    if (door.getFill().equals(Color.BLACK))
                        continue;

                    if (id == nextDoorID) {
                        door.setFill(Color.BLACK);
                        nextDoorID++;
                        checkAllClassroomsVisited();

                        if (currentFloor.equals("Top Floor") && id == 22) {
                            showVictory();
                            return;
                        }
                    } else {
                        showInfo("Wrong Order", "You must explore Room " + nextDoorID + " first!");
                    }
                }
            }

            // Washrooms
            for (Rectangle washroom : washroomDoors) {
                if (player.getBoundsInParent().intersects(washroom.getBoundsInParent())) {
                    int id = 0;
                    try {
                        id = Integer.parseInt(washroom.getId());
                    } catch (Exception e) {
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
                }
            }

            // Lifts
            for (Rectangle lift : lifts) {
                if (player.getBoundsInParent().intersects(lift.getBoundsInParent())) {
                    if (allClassroomsVisited)
                        handleFloorChange();
                    else
                        showInfo("Locked", "Explore all classrooms before using it!");
                    break;
                }
            }
        });
    }

    private void checkAllClassroomsVisited() {
        boolean allBlack = classroomDoors.stream().allMatch(d -> d.getFill().equals(Color.BLACK));
        if (allBlack && !allClassroomsVisited) {
            allClassroomsVisited = true;
            for (Rectangle lift : lifts)
                lift.setFill(Color.GREEN);
                showInfo("Lift & Stairs Unlocked", "All classrooms explored! You can now use the lift and stairs.");
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
                String nextFloor = nextFxml.equals("/fxml/game3.fxml") ? "Top Floor" : "First Floor";
                loadNextFloor(nextFxml, nextFloor);
            }
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

    private void showVictory() {
        gameWon = true;
        GameTimer.stop();
        rootPane.setOnKeyPressed(null);

        try {
            System.out.println("Creating victory screen programmatically...");

            // Create victory screen programmatically
            javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(20);
            vbox.setAlignment(javafx.geometry.Pos.CENTER);
            vbox.setStyle("-fx-background-color: linear-gradient(to bottom, #4CAF50, #81C784); -fx-padding: 20;");

            javafx.scene.control.Label emojiLabel = new javafx.scene.control.Label("🎉");
            emojiLabel.setStyle("-fx-font-size: 48px;");

            javafx.scene.control.Label winLabel = new javafx.scene.control.Label("You Win!");
            winLabel.setStyle("-fx-font-size: 36px; -fx-text-fill: white; -fx-font-weight: bold;");

            javafx.scene.control.Label messageLabel = new javafx.scene.control.Label(
                    "Congratulations! You explored all classrooms and found the IT Room!");
            messageLabel.setStyle(
                    "-fx-font-size: 18px; -fx-text-fill: white; -fx-text-alignment: center; -fx-wrap-text: true;");

            // ADD TRY AGAIN BUTTON
            javafx.scene.control.Button tryAgainBtn = new javafx.scene.control.Button("Play Again");
            tryAgainBtn.setStyle(
                    "-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
            tryAgainBtn.setOnAction(e -> {
                System.out.println("Play Again clicked - restarting BFS game from beginning");
                try {
                    // Reset game state
                    currentFloor = null;
                    gameWon = false;
                    GameTimer.reset(); // ← ADD THIS LINE to reset the timer

                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game1.fxml"));
                    javafx.scene.Parent root = loader.load();
                    Stage stage = (Stage) vbox.getScene().getWindow();
                    stage.setScene(new Scene(root, 800, 600));
                    stage.show();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });

            javafx.scene.control.Button mainMenuBtn = new javafx.scene.control.Button("Main Menu");
            mainMenuBtn.setStyle(
                    "-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150; -fx-pref-height: 40;");
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
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(scene);
            stage.show();

            System.out.println("Victory screen created successfully!");

        } catch (Exception e) {
            System.err.println("Error creating victory screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadNextFloor(String fxmlFile, String floorName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile)); // absolute path
            Parent root = loader.load();

            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

            currentFloor = floorName;
            // Update references
            rootPane = (AnchorPane) root.lookup("#rootPane");
            timerLabel = (Label) root.lookup("#timerLabel");
            player = (Rectangle) root.lookup("#player");
            GameTimer.attachLabel(timerLabel);
            GameTimer.resume();

            Platform.runLater(() -> showInfo("Floor Info", "You are on the " + currentFloor + "."));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
