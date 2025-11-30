package application.components;

import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Login inputs mirrored from the web implementation.
 */
public class LoginPanel extends StackPane {

    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);

    public LoginPanel(UIState state) {
        setPrefSize(700, 500);
        setLayoutX(960 - 300);
        setLayoutY(150);

        // Dùng Pane để có thể set vị trí theo pixel
        Pane container = new Pane();
        container.setPrefSize(700, 500);
        container.setStyle("-fx-background-color: transparent;");

        // Title với vị trí pixel cụ thể
        ImageView title = new ImageView(application.util.AssetHelper.image("login-title.png"));
        title.setFitWidth(500);
        title.setPreserveRatio(true);
        title.setLayoutX(50);  // Vị trí X theo pixel (từ trái)
        title.setLayoutY(-50);  // Giá trị âm để di chuyển lên trên container
        title.setManaged(false);  // Cho phép vượt ra ngoài bounds của container

        InputField username = new InputField("Username", false);
        InputField password = new InputField("Password", true);

        NextButton nextButton = new NextButton();
        nextButton.setOnMouseClicked(e -> {
            // Validate input
            String usernameValue = username.getValue();
            String passwordValue = password.getValue();
            
            if (usernameValue != null && !usernameValue.trim().isEmpty() &&
                passwordValue != null && !passwordValue.trim().isEmpty()) {
                // Lưu username vào state
                state.setUsername(usernameValue.trim());
                // TODO: Gọi API để validate
                // Nếu hợp lệ, chuyển sang main menu
                state.navigateToMainMenu();
            }
        });

        // Input row với vị trí pixel
        HBox inputRow = new HBox(20, new VBox(20, username, password), nextButton);
        inputRow.setLayoutX(50);  // Vị trí X theo pixel
        inputRow.setLayoutY(250);  // Vị trí Y theo pixel

        // Links với vị trí pixel
        HBox links = new HBox(40);
        LinkLabel forgot = new LinkLabel("Forgot password?", () -> {});
        LinkLabel gotoRegister = new LinkLabel("Register", state::openRegisterPanel);
        links.getChildren().addAll(forgot, gotoRegister);
        links.setLayoutX(50);  // Vị trí X theo pixel
        links.setLayoutY(450);  // Vị trí Y theo pixel

        container.getChildren().addAll(title, inputRow, links);
        getChildren().add(container);

        visibleProperty().bind(state.loginVisibleProperty());
        managedProperty().bind(visibleProperty());
        setOpacity(0);
        state.loginVisibleProperty().addListener((obs, oldVal, newVal) -> fadeTo(newVal ? 1 : 0));
    }

    private void fadeTo(double target) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(target);
        fade.play();
    }
}

