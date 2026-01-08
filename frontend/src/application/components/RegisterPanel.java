package application.components;

import application.network.NetworkManager;
import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;

/**
 * Register form mirroring the custom element from the React UI.
 */
public class RegisterPanel extends StackPane {

    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);
    private final NetworkManager networkManager = NetworkManager.getInstance();

    public RegisterPanel(UIState state) {
        setPrefSize(700, 500);
        setLayoutX(960 - 300);
        setLayoutY(150);

        // Dùng Pane để có thể set vị trí theo pixel
        Pane container = new Pane();
        container.setPrefSize(700, 500);
        container.setStyle("-fx-background-color: transparent;");

        // Title với vị trí pixel cụ thể
        ImageView title = new ImageView(application.util.AssetHelper.image("register-title.png"));
        title.setFitWidth(500);
        title.setPreserveRatio(true);
        title.setLayoutX(50);  // Vị trí X theo pixel (từ trái)
        title.setLayoutY(-50);  // Giá trị âm để di chuyển lên trên container
        title.setManaged(false);  // Cho phép vượt ra ngoài bounds của container

        InputField username = new InputField("Username", false);
        InputField password = new InputField("Password", true);
        InputField confirm = new InputField("Confirm Password", true);

        NextButton nextButton = new NextButton();
        // Prevent multiple clicks
        final boolean[] isProcessing = {false};
        nextButton.setOnMouseClicked(e -> {
            // Prevent duplicate clicks
            if (isProcessing[0]) {
                return;
            }
            
            // Validate input
            String usernameValue = username.getValue();
            String passwordValue = password.getValue();
            String confirmValue = confirm.getValue();
            
            if (usernameValue != null && !usernameValue.trim().isEmpty() &&
                passwordValue != null && !passwordValue.trim().isEmpty() &&
                confirmValue != null && !confirmValue.trim().isEmpty() &&
                passwordValue.equals(confirmValue)) {
                isProcessing[0] = true;
                // Send register request (connection already established)
                try {
                    String trimmedUsername = usernameValue.trim();
                    String trimmedPassword = passwordValue.trim();
                    networkManager.auth().register(trimmedUsername, trimmedPassword);
                    // Save credentials for auto-login after reconnect
                    networkManager.saveCredentials(trimmedUsername, trimmedPassword);
                    // Username will be set after successful authentication
                    state.setUsername(trimmedUsername);
                } catch (IOException ex) {
                    System.err.println("Failed to send register request: " + ex.getMessage());
                    ex.printStackTrace();
                    // TODO: Show error message to user
                } finally {
                    // Reset after a delay to allow response
                    javafx.application.Platform.runLater(() -> {
                        PauseTransition delay = new PauseTransition(Duration.seconds(1));
                        delay.setOnFinished(event -> isProcessing[0] = false);
                        delay.play();
                    });
                }
            }
        });

        // Input row với vị trí pixel
        VBox inputColumn = new VBox(20, username, password, confirm);
        HBox inputRow = new HBox(20, inputColumn, nextButton);
        inputRow.setLayoutX(50);  // Vị trí X theo pixel
        inputRow.setLayoutY(220);  // Vị trí Y theo pixel

        // Link với vị trí pixel
        LinkLabel goLogin = new LinkLabel("Log in", state::openLoginPanel);
        goLogin.setLayoutX(60);  // Vị trí X theo pixel
        goLogin.setLayoutY(520);  // Vị trí Y theo pixel

        container.getChildren().addAll(title, inputRow, goLogin);
        getChildren().add(container);

        visibleProperty().bind(state.registerVisibleProperty());
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        state.registerVisibleProperty().addListener((obs, oldVal, newVal) -> fadeTo(newVal ? 1 : 0));
    }

    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}
