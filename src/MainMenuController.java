import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class MainMenuController {
    
    @FXML
    private AnchorPane rootPane;
    @FXML
    private Button bfsGameLevelsBtn;
    
    @FXML
    private Button bfsMultiplayerBtn;
    
    @FXML
    private Button searchingLevelBtn;
    
    @FXML
    private Button sortingLevelBtn;
    
     @FXML
    private void initialize() {
        setupButtonActions();
        
        // Set fixed window size
        Platform.runLater(() -> {
            Stage stage = (Stage) rootPane.getScene().getWindow();
            stage.setMinWidth(600);
            stage.setMinHeight(400);
            stage.setMaxWidth(600);
            stage.setMaxHeight(400);
            stage.setResizable(false);
        });
    }
    
    private void setupButtonActions() {
        bfsGameLevelsBtn.setOnAction(e -> MainApplication.showBfsGameLevels());
        bfsMultiplayerBtn.setOnAction(e -> MainApplication.showBfsMultiplayer());
        searchingLevelBtn.setOnAction(e -> MainApplication.showSearchingLevel());
        sortingLevelBtn.setOnAction(e -> MainApplication.showSortingLevel());
    }
}