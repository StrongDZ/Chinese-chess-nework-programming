package application.components;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * Interface for game panels that can be used with Managers
 */
public interface IGamePanel {
    /**
     * Get the current turn (red or black)
     */
    String getCurrentTurn();
    
    /**
     * Set the current turn
     */
    void setCurrentTurn(String turn);
    
    /**
     * Get children list for adding/removing nodes
     */
    javafx.collections.ObservableList<Node> getChildren();
    
    /**
     * Reset chess pieces to initial position
     */
    void resetChessPieces();
}
