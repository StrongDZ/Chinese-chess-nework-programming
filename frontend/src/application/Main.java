package application;

import application.components.AuthPanel;
import application.components.BackgroundLayer;
import application.components.BoardComponent;
import application.components.LoginPanel;
import application.components.RegisterPanel;
import application.components.TitleImage;
import application.state.UIState;
import application.state.UIState.BoardState;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.nio.file.Path;

/**
 * JavaFX port of the React landing page for the Chinese Chess project.
 */
public class Main extends Application {
<<<<<<< Updated upstream
	
	private static final double WINDOW_WIDTH = 1200;
	private static final double WINDOW_HEIGHT = 800;
	
	@Override
	public void start(Stage primaryStage) {
		try {
			// Create main container using StackPane for layering
			StackPane root = new StackPane();
			
			// Initialize components
			BackgroundComponent background = new BackgroundComponent();
			TitleComponent titles = new TitleComponent();
			ChessBoardComponent chessBoard = new ChessBoardComponent();
			
			// Set component sizes and positions
			background.setPrefSize(WINDOW_WIDTH, WINDOW_HEIGHT);
			
			// Set chessboard size (adjust as needed based on image dimensions)
			chessBoard.setBoardSize(450, 450);
			
			// Position titles at top center
			StackPane.setAlignment(titles, Pos.TOP_CENTER);
			StackPane.setMargin(titles, new Insets(50, 0, 0, 0));
			
			// Position chessboard in center
			StackPane.setAlignment(chessBoard, Pos.CENTER);
			
			// Add components to root in proper z-order (background first, then board, then titles)
			root.getChildren().addAll(background, chessBoard, titles);
			
			// Create scene and apply stylesheet
			Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			
			// Configure stage
			primaryStage.setTitle("Chinese Chess");
			primaryStage.setScene(scene);
			primaryStage.setResizable(true);
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
=======

    private static final double CANVAS_WIDTH = 1920;
    private static final double CANVAS_HEIGHT = 1080;

    @Override
    public void start(Stage stage) {
        UIState state = new UIState();

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

        stageLayer.getChildren().addAll(
                background,
                cnTitle,
                enTitle,
                board,
                authPanel,
                loginPanel,
                registerPanel
        );

        StackPane.setAlignment(stageLayer, Pos.CENTER);
        root.getChildren().add(stageLayer);

        Scene scene = new Scene(root, 1200, 800);
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
        launch(args);
    }
>>>>>>> Stashed changes
}

