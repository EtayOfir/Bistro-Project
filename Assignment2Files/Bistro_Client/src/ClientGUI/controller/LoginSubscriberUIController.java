package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.io.IOException;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;
import entities.Subscriber;

/**
 * Controller class for the Subscriber's Main Menu (LoginSubscriberUI).
 * <p>
 * This controller handles the navigation logic for a logged-in subscriber.
 * It also handles the display of the specific subscriber's name upon entry.
 * </p>
 *
 * @author Bistro Team
 * @version 1.1
 */
public class LoginSubscriberUIController {

    /**
     * Label to display the welcome message with the user's name.
     * Ensure this fx:id matches the Label in your FXML file.
     */
    @FXML
    private Label welcomeLabel;

    /**
     * The currently logged-in subscriber instance.
     * Used to maintain session context and pass data to subsequent screens.
     */
    private Subscriber currentSubscriber;

    /**
     * Sets the current subscriber for this session and updates the welcome label.
     * <p>
     * This method is typically called by the previous controller (e.g., UserLoginUI)
     * immediately after loading this view to inject the logged-in user's details.
     * </p>
     *
     * @param subscriber The {@link Subscriber} object representing the logged-in user.
     */
    public void setSubscriber(Subscriber subscriber) {
        this.currentSubscriber = subscriber;
        if (welcomeLabel != null && subscriber != null) {
            welcomeLabel.setText("Welcome Subscriber: " + subscriber.getUserName());
        }
    }
    
    /**
     * Sets the subscriber's name on the main menu screen.
     * <p>
     * This method is called by the previous screen (UserLoginUI) before
     * showing this stage, allowing the transfer of session data.
     * </p>
     *
     * @param username The username of the logged-in subscriber.
     */
    public void setSubscriberName(String username) {
        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome Subscriber: " + username);
        }
    }

    /**
     * Handles the "Make a Reservation" button click.
     * Navigates the user to the Reservation screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onMakeReservation(ActionEvent event) {
        // Trigger cleanup of expired reservations on server before making reservation
        triggerExpiredReservationsCleanup();
        navigate(event, "ReservationUI.fxml");
    }

    /**
     * Handles the "Cancel Reservation" button click.
     * Navigates the user to the Client View screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onCancelReservation(ActionEvent event) {
        // Trigger cleanup of expired reservations on server before canceling reservation
        triggerExpiredReservationsCleanup();
        navigate(event, "ClientUIView.fxml");
    }

    /**
     * Handles the "Leave Waiting List" button click.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onLeaveWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    /**
     * Handles the "Pay Bill" button click.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    /**
     * Handles the "Personal Area" button click.
     * Navigates to the subscriber's personal dashboard.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onPersonalArea(ActionEvent event) {
        navigate(event, "SubscriberUI.fxml");
    }

    /**
     * Handles the "Back" button click.
     * Returns to the main login screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onBack(ActionEvent event) {
        navigate(event, "UserLoginUIView.fxml");
    }

    /**
     * Handles the "Exit" button click.
     * Terminates the application.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onExit(ActionEvent event) {
        System.exit(0);
    }

    /**
     * Navigates to a new screen and sets the "Return Path" so the user can navigate back.
     * <p>
     * This method loads the FXML file, retrieves its controller, and injects the return path
     * (LoginSubscriberUI) into the destination controller.
     * </p>
     *
     * @param event        The action event that triggered navigation.
     * @param fxmlFileName The name of the FXML file to load.
     */
    private void triggerExpiredReservationsCleanup() {
        try {
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("#DELETE_EXPIRED_RESERVATIONS");
                System.out.println("DEBUG: Triggered expired reservations cleanup");
            } else {
                System.out.println("DEBUG: ClientUI.chat is null, skipping expired reservations cleanup");
            }
        } catch (Exception e) {
            System.err.println("ERROR triggering expired reservations cleanup: " + e.getMessage());
            // Don't throw - continue with navigation even if cleanup fails
        }
    }

    /**
     * Navigates to a new screen and sets the "Return Path" so the user can navigate back.
     * <p>
     * This method loads the FXML file, retrieves its controller, and injects the return path
     * (LoginSubscriberUI) into the destination controller.
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
            
            // Assuming 'subscriberName' or 'currentSubscriber' holds the username
            String subName = (currentSubscriber != null) ? currentSubscriber.getUserName() : "Subscriber";

            // Set the return path based on the controller type
            switch (controller) {
            case SubscriberUIController c -> {
                if (currentSubscriber != null) {
                    c.loadSubscriber(currentSubscriber); 
                } else {
                    System.err.println("Error: currentSubscriber is null in LoginSubscriberUIController");
                }
            }
                case ReservationUIController c -> {
                    c.setReturnPath("LoginSubscriberUI.fxml", "Subscriber Menu", subName, "Subscriber");
                    c.setSubscriber(currentSubscriber);
                }

                case ClientWaitingListController c -> 
                    c.setReturnPath("LoginSubscriberUI.fxml", "Subscriber Menu", subName, "Subscriber");

                case BillPaymentController c -> 
                    c.setReturnPath("LoginSubscriberUI.fxml", "Subscriber Menu", subName, "Subscriber");
                
                // Note: SubscriberUI (Personal Area) has its own logic, handled separately if needed.
                case ClientUIController c -> {
                    c.setReturnPath("LoginSubscriberUI.fxml", "Subscriber Menu", subName, "Subscriber");
                    c.setUserContext(subName, "Subscriber");
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