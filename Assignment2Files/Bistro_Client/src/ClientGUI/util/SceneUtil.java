package ClientGUI.util;

import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * Utility class for creating JavaFX scenes with consistent styling.
 * Automatically applies the bistro.css stylesheet to all scenes.
 */
public final class SceneUtil {

    private SceneUtil() {} // utility class

    /**
     * Creates a new Scene with the bistro.css stylesheet applied.
     *
     * @param root the root node of the scene
     * @return a new Scene with styling applied
     */
    public static Scene createStyledScene(Parent root) {
        Scene scene = new Scene(root);
        scene.getStylesheets().add(SceneUtil.class.getResource("/ClientGUI/view/bistro.css").toExternalForm());
        return scene;
    }
}