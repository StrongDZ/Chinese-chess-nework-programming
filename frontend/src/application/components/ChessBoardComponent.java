package application.components;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * Reusable component for displaying the chessboard with hover effect.
 * Implements mouse hover listeners to apply CSS styling.
 */
public class ChessBoardComponent extends StackPane {
    private ImageView boardImageView;
    
    public ChessBoardComponent() {
        initializeComponent();
        setupHoverEffect();
    }
    
    private void initializeComponent() {
        try {
            // Load chessboard image from assets folder
            String boardPath = "file:" + System.getProperty("user.dir") + "/assets/board.png";
            Image boardImage = new Image(boardPath);
            boardImageView = new ImageView(boardImage);
            
            // Preserve aspect ratio and enable smooth rendering
            boardImageView.setPreserveRatio(true);
            boardImageView.setSmooth(true);
            
            // Add CSS class for styling
            this.getStyleClass().add("chessboard-container");
            
            this.getChildren().add(boardImageView);
        } catch (Exception e) {
            System.err.println("Error loading chessboard image: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void setupHoverEffect() {
        // Add hover effect using mouse enter/exit events
        this.setOnMouseEntered(event -> {
            this.getStyleClass().add("chessboard-hover");
        });
        
        this.setOnMouseExited(event -> {
            this.getStyleClass().remove("chessboard-hover");
        });
    }
    
    /**
     * Set the preferred size of the chessboard
     * @param width The preferred width
     * @param height The preferred height
     */
    public void setBoardSize(double width, double height) {
        if (boardImageView != null) {
            boardImageView.setFitWidth(width);
            boardImageView.setFitHeight(height);
        }
    }
    
    /**
     * Get the board ImageView for potential external styling
     * @return The board ImageView
     */
    public ImageView getBoardImageView() {
        return boardImageView;
    }
}
