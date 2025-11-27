package application.components;

import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Register form mirroring the custom element from the React UI.
 */
public class RegisterPanel extends StackPane {

    private final FadeTransition fade = new FadeTransition(Duration.millis(250), this);

    public RegisterPanel(UIState state) {
        setPrefSize(600, 520);
        setLayoutX(960 - 300);
        setLayoutY(350);

        VBox container = new VBox(30);
        container.setPadding(new Insets(20));
        container.setAlignment(Pos.TOP_CENTER);
        container.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-border-radius: 30; -fx-background-radius: 30;");

        ImageView title = new ImageView(application.util.AssetHelper.image("register-title.png"));
        title.setFitWidth(460);
        title.setPreserveRatio(true);

        InputField username = new InputField("Username", false);
        InputField password = new InputField("Password", true);
        InputField confirm = new InputField("Confirm Password", true);

        NextButton nextButton = new NextButton();
        nextButton.setOnMouseClicked(e -> state.closeRegisterPanel());

        VBox inputColumn = new VBox(20, username, password, confirm);
        HBox inputRow = new HBox(20, inputColumn, nextButton);
        inputRow.setAlignment(Pos.CENTER);

        LinkLabel goLogin = new LinkLabel("Log in", state::openLoginPanel);

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

