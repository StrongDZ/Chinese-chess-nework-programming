package application.util;

import java.nio.file.Path;

import javafx.scene.image.Image;

/**
 * Small helper to resolve asset images relative to the JavaFX frontend folder.
 */
public final class AssetHelper {

    // Resolve asset root based on where the app is launched.
    // If you run from frontend/, assets are in "<user.dir>/assets".
    // If you run from repo root, assets are in "<user.dir>/frontend/assets".
    private static final Path ASSET_ROOT;
    static {
        Path wd = Path.of(System.getProperty("user.dir"));
        Path candidate1 = wd.resolve("assets");
        Path candidate2 = wd.resolve("frontend").resolve("assets");
        if (candidate1.toFile().isDirectory()) {
            ASSET_ROOT = candidate1;
        } else {
            ASSET_ROOT = candidate2;
        }
    }

    private AssetHelper() {
    }

    public static Image image(String fileName) {
        Path path = ASSET_ROOT.resolve(fileName);
        return new Image(path.toUri().toString(), true);
    }
}

