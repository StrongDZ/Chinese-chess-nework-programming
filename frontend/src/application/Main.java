package application;
	
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import application.components.BackgroundComponent;
import application.components.TitleComponent;
import application.components.ChessBoardComponent;

public class Main extends Application {
	
	private static final double WINDOW_WIDTH = 1200;
	private static final double WINDOW_HEIGHT = 800;
	
	@Override
	public void start(Stage primaryStage) {
		try {
			// Create main container using StackPane for layering
			StackPane root = new StackPane();
			root.setStyle("-fx-background-color: #f0f0f0;"); // Temporary background để thấy window
			
			// Initialize components
			System.out.println("Initializing components...");
			BackgroundComponent background = new BackgroundComponent();
			System.out.println("Background component created");
			
			TitleComponent titles = new TitleComponent();
			System.out.println("Title component created");
			
			ChessBoardComponent chessBoard = new ChessBoardComponent();
			System.out.println("Chessboard component created");
			
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
			try {
				scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
				System.out.println("CSS loaded");
			} catch (Exception e) {
				System.err.println("CSS not found: " + e.getMessage());
			}
			
			// Configure stage
			primaryStage.setTitle("Chinese Chess");
			primaryStage.setScene(scene);
			primaryStage.setResizable(true);
			primaryStage.show();
			System.out.println("Stage shown successfully");
		} catch(Exception e) {
			System.err.println("Fatal error in start method:");
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}
