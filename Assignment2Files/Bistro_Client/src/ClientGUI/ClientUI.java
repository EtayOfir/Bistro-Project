package ClientGUI;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class ClientUI extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Look for ClientUIView.fxml in the same package (ClientGUI)
        //URL fxmlLocation = ClientUI.class.getResource("ClientUIView.fxml");
        URL fxmlLocation = ClientUI.class.getResource("UserLoginUIView.fxml");

        System.out.println("FXML URL = " + fxmlLocation);  // debug

        if (fxmlLocation == null) {
            throw new IllegalStateException(
                "Cannot find ClientUIView.fxml. " +
                "Make sure it is in the ClientGUI package under src."
            );
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        //ClientUIController controller = loader.getController();
        //controller.initClient("localhost", 5555); // your server host/port

        primaryStage.setTitle("User Login");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}