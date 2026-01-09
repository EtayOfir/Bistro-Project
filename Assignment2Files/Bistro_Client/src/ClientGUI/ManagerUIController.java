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
 * Controller class for the Manager Dashboard.
 * Includes operational buttons, management buttons, and access to Reports.
 */
public class ManagerUIController {

    @FXML private Label welcomeLabel;
    
    private String currentUserName;

    /**
     * Sets the name of the manager on the dashboard.
     */
    public void setManagerName(String name) {
        this.currentUserName = name;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome Manager: " + name);
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
    void onGetTable(ActionEvent event) {
        navigate(event, "ReceiveTableUI.fxml");
    }

    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    // --- Management Actions ---

    @FXML
    void onViewDetails(ActionEvent event) {
        // TODO: Implement ViewDetailsUI logic
        System.out.println("Navigate to View Details Screen");
    }

    @FXML
    void onUpdateDetails(ActionEvent event) {
        // TODO: Implement UpdateDetailsUI logic
        System.out.println("Navigate to Update Details Screen");
    }

    /**
     * Handles the "View Reports" button click.
     * Navigates to the ReportsUI screen.
     */
    @FXML
    void onViewReports(ActionEvent event) {
        navigate(event, "ReportsUI.fxml");
    }

    /**
     * Handles "Current Restaurant Status".
     * Navigates to the HostDashboard with Manager privileges.
     */
    @FXML
    void onRestaurantStatus(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("HostDashboard.fxml"));
            Parent root = loader.load();

            HostDashboardController controller = loader.getController();
            
            // הגדרת הקשר (שם ותפקיד) לתצוגה
            controller.setUserContext(currentUserName, "Manager"); 

            // --- הוספי את השורה הזו: הגדרת נתיב חזרה למנהל ---
            controller.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
            // ----------------------------------------------------

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle("Host Dashboard - Manager (" + currentUserName + ")");
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
     * (ManagerUI) into the destination controller.
     * </p>
     *
     * @param event        The action event that triggered navigation.
     * @param fxmlFileName The name of the FXML file to load (e.g., "ClientWaitingList.fxml").
     */
    private void navigate(ActionEvent event, String fxmlFileName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFileName));
            Parent root = loader.load();
            Object controller = loader.getController();

            // Set the return path based on the controller type
            switch (controller) {
                case ClientWaitingListController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case BillPaymentController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case ReservationUIController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case ReceiveTableUIController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case HostDashboardController c -> 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");

                case ClientUIController c -> { 
                    c.setReturnPath("ManagerUI.fxml", "Manager Dashboard", currentUserName, "Manager");
                    c.setUserContext(currentUserName, "Manager");
                }
                
                default -> { 
                    // Log or ignore for other controllers
                }
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}