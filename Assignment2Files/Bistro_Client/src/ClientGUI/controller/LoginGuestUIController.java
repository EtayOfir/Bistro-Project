package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;

/**
 * Controller class for the Guest's Main Menu (LoginGuestUI).
 * <p>
 * This controller handles the navigation logic for a guest user.
 * It provides functionality to navigate to reservation creation, cancellation,
 * waiting list management, and bill payment.
 * Unlike the subscriber screen, this screen does not include a personal area.
 * </p>
 *
 * @author LINOY
 * @version 1.0
 */
public class LoginGuestUIController {

    /**
     * Handles the "Make a Reservation" button click.
     * Navigates the guest to the Reservation screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onMakeReservation(ActionEvent event) {
        navigate(event, "ReservationUI.fxml");
    }

    /**
     * Handles the "Cancel Reservation" button click.
     * Navigates the guest to the Client View screen where cancellations are managed.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onCancelReservation(ActionEvent event) {
        navigate(event, "ClientUIView.fxml");
    }

    /**
     * Handles the "Leave Waiting List" button click.
     * Navigates the guest to the Waiting List management screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onLeaveWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    /**
     * Handles the "Pay Bill" button click.
     * Navigates the guest to the Bill Payment screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    /**
     * Handles the "Sign Off" button click.
     * Returns the guest to the main Login screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    private void onSignOff(ActionEvent event) {
        try {
            // Disconnect from server cleanly
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("LOGOUT");
                ClientUI.chat.closeConnection();
                ClientUI.chat = null;
            }

            // Return to login screen
            FXMLLoader loader = ViewLoader.fxml("UserLoginUIView.fxml");
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Login");
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the "Exit" button click.
     * Terminates the application completely.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onExit(ActionEvent event) {
        try {
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("LOGOUT");
                ClientUI.chat.closeConnection();
                ClientUI.chat = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    /**
     * Navigates to a new screen and sets the "Return Path" so the user can navigate back.
     * <p>
     * This method loads the FXML file, retrieves its controller, and injects the return path
     * (LoginGuestUI) into the destination controller.
     * </p>
     *
     * @param event        The action event that triggered navigation.
     * @param fxmlFileName The name of the FXML file to load.
     */
    private void navigate(ActionEvent event, String fxmlFileName) {
        try {
        	FXMLLoader loader = ViewLoader.fxml(fxmlFileName);
            Parent root = loader.load();
            Object controller = loader.getController();

            // Set the return path based on the controller type
            switch (controller) {
                case ReservationUIController c -> 
                    c.setReturnPath("LoginGuestUI.fxml", "Guest Menu", "Guest", "Guest");

                case ClientWaitingListController c -> 
                    c.setReturnPath("LoginGuestUI.fxml", "Guest Menu", "Guest", "Guest");

                case BillPaymentController c -> 
                    c.setReturnPath("LoginGuestUI.fxml", "Guest Menu", "Guest", "Guest");
                
                case ClientUIController c -> {
                     c.setReturnPath("LoginGuestUI.fxml", "Guest Menu", "Guest", "Guest");
                     c.setUserContext("Guest", "Guest");
                }

                default -> {}
            }

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(SceneUtil.createStyledScene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}