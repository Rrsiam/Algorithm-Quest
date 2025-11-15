import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GameManager {
    private Stage primaryStage;
    
    public GameManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }
    
    public void showMainMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainMenu.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 800, 600);
            primaryStage.setTitle("Algorithm Quest");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void showBfsGameLevels() {
        loadFXML("/fxml/game1.fxml", "BFS Game Level");
    }

    public void showBfsMultiplayer() {
        loadFXML("/fxml/game1mul.fxml", "BFS Multiplayer Game Level");
    }
    
    public void showSearchingLevel() {
        loadFXML("/fxml/library.fxml", "Searching Game Level");
    }
    
    public void showSortingLevel() {
        loadFXML("/fxml/cafe.fxml", "Sorting Game Level");
    }
    
    private void loadFXML(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root, 800, 600);
            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}