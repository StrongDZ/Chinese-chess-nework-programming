package application.components;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * Reusable component for displaying background images.
 * Handles image loading, scaling, and positioning.
 */
public class BackgroundComponent extends StackPane {
    private ImageView backgroundImageView;
    
    public BackgroundComponent() {
        initializeComponent();
    }
    
    private void initializeComponent() {
        try {
            // Load background image from assets folder
            String imagePath = "file:" + System.getProperty("user.dir") + "/assets/bg.jpg";
            Image backgroundImage = new Image(imagePath);
            backgroundImageView = new ImageView(backgroundImage);
            
            // Set image to cover entire area while preserving aspect ratio
            backgroundImageView.setPreserveRatio(true);
            backgroundImageView.setSmooth(true);
            
            // Bind image size to container size
            backgroundImageView.fitWidthProperty().bind(this.widthProperty());
            backgroundImageView.fitHeightProperty().bind(this.heightProperty());
            
            this.getChildren().add(backgroundImageView);
        } catch (Exception e) {
            System.err.println("Error loading background image: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Set the preferred size of the background component
     * @param width The preferred width
     * @param height The preferred height
     */
    public void setPrefSize(double width, double height) {
        this.setPrefWidth(width);
        this.setPrefHeight(height);
    }
    
    /**
     * Get the ImageView for potential external styling
     * @return The background ImageView
     */
    public ImageView getBackgroundImageView() {
        return backgroundImageView;
    }
}
