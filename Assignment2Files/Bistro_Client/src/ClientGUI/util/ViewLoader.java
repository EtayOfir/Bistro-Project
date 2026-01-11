package ClientGUI.util;

import javafx.fxml.FXMLLoader;
import java.net.URL;

/**
 * Centralized loader for all JavaFX FXML views.
 * Prevents "Location is not set" errors by enforcing
 * absolute classpath-based loading.
 */
public final class ViewLoader {

    private ViewLoader() {} // utility class

    public static FXMLLoader fxml(String fileName) {
        String path = "/ClientGUI/view/" + fileName;
        URL url = ViewLoader.class.getResource(path);
        System.out.println("Loading FXML: " + path + " => " + url);

        if (url == null) {
            throw new IllegalStateException("FXML not found on classpath: " + path);
        }

        return new FXMLLoader(url);
    }
}
