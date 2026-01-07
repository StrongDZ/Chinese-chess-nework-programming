package application;

import application.components.AuthPanel;
import application.components.BackgroundLayer;
import application.components.BoardComponent;
import application.components.LoginPanel;
import application.components.RegisterPanel;
import application.components.TitleImage;
import application.state.UIState;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.io.IOException;
import java.nio.file.Path;
import application.components.MainMenuPanel;
import application.components.SettingsPanel;
import application.components.FriendsPanel;
import application.components.InventoryPanel;
import application.components.GameModePanel;
import application.components.ClassicModePanel;
import application.components.BlitzModePanel;
import application.components.CustomModePanel;
import application.components.GamePanel;
import application.components.HistoryPanel;
import application.components.ProfilePanel;
import application.network.NetworkManager;

/**
 * JavaFX port of the React landing page for the Chinese Chess project.
 * 
 * Usage: java Main [server_ip:port]
 * Example: java Main 192.168.1.100:8080
 * Default: localhost:8080
 */
public class Main extends Application {

    private static final double CANVAS_WIDTH = 1920;
    private static final double CANVAS_HEIGHT = 1080;

    @Override
    public void start(Stage stage) {
        UIState state = new UIState();
        
        // Initialize NetworkManager with UIState
        NetworkManager networkManager = NetworkManager.getInstance();
        networkManager.initialize(state);
        
        // Connect to server immediately when UI starts
        try {
            networkManager.connectToServer();
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            e.printStackTrace();
            // TODO: Show error message to user
        }

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: black;");

        Pane stageLayer = new Pane();
        stageLayer.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        stageLayer.setMinSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);
        stageLayer.setMaxSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);

        // Compute responsive scale identical to CSS (--scale)
        DoubleProperty scale = new SimpleDoubleProperty(1);
        scale.bind(Bindings.createDoubleBinding(
                () -> Math.min(root.getWidth() / CANVAS_WIDTH, root.getHeight() / CANVAS_HEIGHT),
                root.widthProperty(),
                root.heightProperty()));
        stageLayer.scaleXProperty().bind(scale);
        stageLayer.scaleYProperty().bind(scale);

        BackgroundLayer background = new BackgroundLayer("bg.jpg");

        TitleImage cnTitle = TitleImage.cnTitle(state);
        TitleImage enTitle = TitleImage.enTitle(state);

        BoardComponent board = new BoardComponent(state);
        board.setOnMouseClicked(evt -> state.toggleBoardState());

        AuthPanel authPanel = new AuthPanel(state);
        authPanel.setAlignment(Pos.CENTER);

        LoginPanel loginPanel = new LoginPanel(state);
        RegisterPanel registerPanel = new RegisterPanel(state);
        MainMenuPanel mainMenuPanel = new MainMenuPanel(state);
        SettingsPanel settingsPanel = new SettingsPanel(state);
        FriendsPanel friendsPanel = new FriendsPanel(state);
        InventoryPanel inventoryPanel = new InventoryPanel(state);
        GameModePanel gameModePanel = new GameModePanel(state);
        ClassicModePanel classicModePanel = new ClassicModePanel(state);
        BlitzModePanel blitzModePanel = new BlitzModePanel(state);
        CustomModePanel customModePanel = new CustomModePanel(state);
        GamePanel gamePanel = new GamePanel(state);
        HistoryPanel historyPanel = new HistoryPanel(state);
        ProfilePanel profilePanel = new ProfilePanel(state);

        stageLayer.getChildren().addAll(
                background,
                cnTitle,
                enTitle,
                board,
                authPanel,
                loginPanel,
                registerPanel,
               
                //classicModePanel,
                mainMenuPanel,
                gameModePanel,
                classicModePanel,
                blitzModePanel,
                customModePanel,
                gamePanel,
                settingsPanel,
                friendsPanel,
                inventoryPanel,
                historyPanel,
                profilePanel
        );

        StackPane.setAlignment(stageLayer, Pos.CENTER);
        root.getChildren().add(stageLayer);

        Scene scene = new Scene(root, 1920, 1080);
        Path cssPath = Path.of(System.getProperty("user.dir"),
                "src",
                "application",
                "application.css");
            scene.getStylesheets().add(cssPath.toUri().toString());

        stage.setTitle("Chinese Chess");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    public static void main(String[] args) {
        // Default values
        String host = "localhost";
        int port = 8080;
        
        // Parse server config from system property: -Dserver=ip:port
        String serverArg = System.getProperty("server");
        
        if (serverArg != null && !serverArg.isEmpty()) {
            String[] parts = serverArg.split(":");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                host = parts[0];
            }
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port '" + parts[1] + "', using default 8080");
                    port = 8080;
                }
            }
        } else {
            // Using default values
            System.out.println("No server config provided, using default: localhost:8080");
            System.out.println("Usage: ./mvnw javafx:run -Dserver=ip:port");
        }
        
        // Always set server config (with defaults if not provided)
        NetworkManager.setServerConfig(host, port);
        System.out.println("Server config set to: " + host + ":" + port);
        
        launch(args);
    }
}

