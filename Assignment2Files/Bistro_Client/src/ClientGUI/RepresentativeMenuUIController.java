package ClientGUI;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.io.IOException;

/**
 * Controller class for the Representative Dashboard.
 */
public class RepresentativeMenuUIController {

    @FXML private Label welcomeLabel;
    
    private String currentUserName;

    /**
     * Sets the name of the representative on the dashboard and stores it for navigation.
     */
    public void setRepresentativeName(String name) {
        this.currentUserName = name; // שמירת השם
        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome Representative: " + name);
        }
    }

    // --- Basic Actions ---

    @FXML
    void onMakeReservation(ActionEvent event) {
        navigate(event, "ReservationUI.fxml");
    }

    @FXML
    void onCancelReservation(ActionEvent event) {
        navigate(event, "ClientUIView.fxml");
    }

    @FXML
    void onEnterWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    @FXML
    void onLeaveWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }
    @FXML
    void onRegister(ActionEvent event) {
        navigate(event, "RegisterUI.fxml");
    }


    @FXML
    void onGetTable(ActionEvent event) {
        navigate(event, "ReceiveTableUI.fxml");
    }

    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    // --- Special Representative Actions ---

    @FXML
    void onViewDetails(ActionEvent event) {
    	navigate(event, "RepresentativeViewDetails.fxml");
    }

    @FXML
    void onUpdateDetails(ActionEvent event) {
        // TODO: לממש מסך UpdateDetailsUI.fxml
        System.out.println("Navigate to Update Details Screen");
    }

    /**
     * Handles "Current Restaurant Status".
     * Navigates to the HostDashboard.
     */
    @FXML
    void onRestaurantStatus(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("HostDashboard.fxml"));
            Parent root = loader.load();

            HostDashboardController controller = loader.getController();
            
            // הגדרת הקשר (שם ותפקיד) לתצוגה
            controller.setUserContext(currentUserName, "Representative"); 

            // --- הוספי את השורה הזו: הגדרת נתיב חזרה לנציג ---
            controller.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
            // ----------------------------------------------------

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle("Host Dashboard - Representative (" + currentUserName + ")");
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading HostDashboard.fxml");
        }
    }

    // --- Navigation ---

    @FXML
    void onBack(ActionEvent event) {
        navigate(event, "UserLoginUIView.fxml");
    }

    @FXML
    void onExit(ActionEvent event) {
        System.exit(0);
    }

    /**
     * Navigates to a new screen and sets the "Return Path" so the user can navigate back.
     * <p>
     * This method loads the FXML file, retrieves its controller, and injects the return path
     * (RepresentativeMenuUI) into the destination controller.
     * </p>
     *
     * @param event        The action event that triggered navigation.
     * @param fxmlFileName The name of the FXML file to load.
     */
    private void navigate(ActionEvent event, String fxmlFileName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFileName));
            Parent root = loader.load();
            Object controller = loader.getController();

            // Set the return path based on the controller type
            switch (controller) {
                case ClientWaitingListController c -> 
                    c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

                case BillPaymentController c -> 
                    c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

                case ReservationUIController c -> 
                    c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

                case ReceiveTableUIController c -> 
                    c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

                case HostDashboardController c -> 
                    c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");

                case ClientUIController c -> {
                    c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
                    c.setUserContext(currentUserName, "Representative");
                }
                case RepresentativeViewDetailsUIController c -> {
                c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
                }
                case RegisterUIController c -> {
                    c.setUserContext(currentUserName, "Representative");
                    c.setReturnPath("RepresentativeMenuUI.fxml", "Representative Dashboard", currentUserName, "Representative");
                }

                default -> {}
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}