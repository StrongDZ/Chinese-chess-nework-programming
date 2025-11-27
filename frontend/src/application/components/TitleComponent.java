package application.components;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Reusable component for displaying title images.
 * Displays Chinese and English titles in a vertical layout.
 */
public class TitleComponent extends VBox {
    private ImageView chineseTitleView;
    private ImageView englishTitleView;
    
    public TitleComponent() {
        initializeComponent();
    }
    
    private void initializeComponent() {
        try {
            // Set spacing between titles
            this.setSpacing(10);
            this.setAlignment(Pos.CENTER);
            
            // Load Chinese title image
            String cnPath = "file:" + System.getProperty("user.dir") + "/assets/title_cn.png";
            Image chineseTitleImage = new Image(cnPath);
            chineseTitleView = new ImageView(chineseTitleImage);
            chineseTitleView.setPreserveRatio(true);
            chineseTitleView.setSmooth(true);
            
            // Load English title image
            String enPath = "file:" + System.getProperty("user.dir") + "/assets/title_en.png";
            Image englishTitleImage = new Image(enPath);
            englishTitleView = new ImageView(englishTitleImage);
            englishTitleView.setPreserveRatio(true);
            englishTitleView.setSmooth(true);
            
            // Add titles to container
            this.getChildren().addAll(chineseTitleView, englishTitleView);
        } catch (Exception e) {
            System.err.println("Error loading title images: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Set the scale of both title images
     * @param scale The scale factor (1.0 = original size)
     */
    public void setTitleScale(double scale) {
        if (chineseTitleView != null) {
            chineseTitleView.setScaleX(scale);
            chineseTitleView.setScaleY(scale);
        }
        if (englishTitleView != null) {
            englishTitleView.setScaleX(scale);
            englishTitleView.setScaleY(scale);
        }
    }
    
    /**
     * Get the Chinese title ImageView
     * @return The Chinese title ImageView
     */
    public ImageView getChineseTitleView() {
        return chineseTitleView;
    }
    
    /**
     * Get the English title ImageView
     * @return The English title ImageView
     */
    public ImageView getEnglishTitleView() {
        return englishTitleView;
    }
}
