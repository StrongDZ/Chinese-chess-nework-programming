package application.components;

import application.state.UIState;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Shows the login/register buttons when the board is in the small state.
 */
public class AuthPanel extends VBox {

    private final FadeTransition fade = new FadeTransition(Duration.millis(300), this);

    public AuthPanel(UIState state) {
        setSpacing(40);
        setAlignment(Pos.CENTER);
        setLayoutX(960 - 180);
        setLayoutY(450);

        ImageButton loginBtn = new ImageButton("login.png", 287);
        ImageButton registerBtn = new ImageButton("register.png", 380);

        loginBtn.setOnMouseClicked(e -> state.openLoginPanel());
        registerBtn.setOnMouseClicked(e -> state.openRegisterPanel());

        getChildren().addAll(loginBtn, registerBtn);

        // Ẩn khi authPanelVisible là false hoặc khi ở MAIN_MENU
        visibleProperty().bind(
            state.authPanelVisibleProperty()
                .and(state.appStateProperty().isEqualTo(UIState.AppState.LANDING))
        );
        managedProperty().bind(visibleProperty());
        setOpacity(0);

        state.authPanelVisibleProperty().addListener((obs, oldVal, newVal) -> fadeTo(newVal ? 1 : 0));
    }

    private void fadeTo(double value) {
        fade.stop();
        fade.setFromValue(getOpacity());
        fade.setToValue(value);
        fade.play();
    }
}

