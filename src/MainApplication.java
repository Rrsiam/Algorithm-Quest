import javafx.application.Application;
import javafx.stage.Stage;

public class MainApplication extends Application {
    
    private static Stage primaryStage;
    private static GameManager gameManager;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        MainApplication.primaryStage = primaryStage;
        MainApplication.gameManager = new GameManager(primaryStage);
        showMainMenu();
    }
    
    public static void showMainMenu() {
        gameManager.showMainMenu();
    }
    
    public static void showBfsMultiplayer() {
        gameManager.showBfsMultiplayer();
    }

    public static void showBfsGameLevels() {
        gameManager.showBfsGameLevels();
    }
    
    public static void showSearchingLevel() {
        gameManager.showSearchingLevel();
    }
    
    public static void showSortingLevel() {
        gameManager.showSortingLevel();
    }
    
    public static Stage getPrimaryStage() {
        return primaryStage;
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}