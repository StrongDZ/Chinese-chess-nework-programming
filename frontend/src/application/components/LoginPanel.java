package application.components;

import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Login inputs mirrored from the web implementation.
 */
public class LoginPanel extends StackPane {

    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);

    public LoginPanel(UIState state) {
        setPrefSize(600, 420);
        setLayoutX(960 - 300);
        setLayoutY(350);

        VBox container = new VBox(30);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.TOP_CENTER);
        container.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-border-radius: 30; -fx-background-radius: 30;");

        ImageView title = new ImageView(application.util.AssetHelper.image("login-title.png"));
        title.setFitWidth(460);
        title.setPreserveRatio(true);

        InputField username = new InputField("Username", false);
        InputField password = new InputField("Password", true);

        NextButton nextButton = new NextButton();
        nextButton.setOnMouseClicked(e -> state.closeLoginPanel());

        HBox inputRow = new HBox(20, new VBox(20, username, password), nextButton);
        inputRow.setAlignment(Pos.CENTER);

        HBox links = new HBox(40);
        links.setAlignment(Pos.CENTER);
        LinkLabel forgot = new LinkLabel("Forgot password?", () -> {});
        LinkLabel gotoRegister = new LinkLabel("Register", state::openRegisterPanel);
        links.getChildren().addAll(forgot, gotoRegister);

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

