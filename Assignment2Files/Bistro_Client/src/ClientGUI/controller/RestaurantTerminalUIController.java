package ClientGUI.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;

import ClientGUI.util.SceneUtil;
import ClientGUI.util.ViewLoader;

/**
 * Controller class for the Restaurant Terminal Interface.
 * <p>
 * This controller manages the on-site kiosk operations. It provides functionality for:
 * <ul>
 * <li>Getting a table (Walk-in).</li>
 * <li>Managing waiting list entries (Enter/Leave).</li>
 * <li>Canceling reservations.</li>
 * <li>Paying bills.</li>
 * <li>Subscriber login via a popup window without closing the terminal.</li>
 * </ul>
 * </p>
 *
 * @author Bistro Team
 * @version 1.0
 */
public class RestaurantTerminalUIController {

    /**
     * Optional label to display the currently logged-in subscriber's name in the terminal.
     */
    @FXML private Label statusLabel; 

    private entities.Subscriber loggedInSubscriber;
    
    /**
     * Handles the "Get Table" button click.
     * Navigates to the table reception screen (ReceiveTableUI).
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onGetTable(ActionEvent event) {
        navigate(event, "ReceiveTableUI.fxml");
    }

    /**
     * Handles the "Enter Waiting List" button click.
     * Navigates to the waiting list management screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onEnterWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    /**
     * Handles the "Leave Waiting List" button click.
     * Navigates to the waiting list management screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onLeaveWaitingList(ActionEvent event) {
        navigate(event, "ClientWaitingList.fxml");
    }

    /**
     * Handles the "Cancel Reservation" button click.
     * Navigates to the client view where reservation cancellations are processed.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onCancelReservation(ActionEvent event) {
        navigate(event, "ClientUIView.fxml");
    }

    /**
     * Handles the "Pay Bill" button click.
     * Navigates to the bill payment screen.
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onPayBill(ActionEvent event) {
        navigate(event, "BillPayment.fxml");
    }

    /**
     * Handles the "Subscriber Login" button click.
     * <p>
     * Opens a modal popup window (TerminalLoginUI) for the subscriber to log in.
     * This method does <b>not</b> close the current terminal window.
     * It passes a reference of this controller to the popup so the popup can return data (e.g., username).
     * </p>
     *
     * @param event The event triggered by clicking the button.
     */
    @FXML
    void onSubscriberLogin(ActionEvent event) {
        try {
        	FXMLLoader loader = ViewLoader.fxml("TerminalSubscriberLoginUI.fxml");
            Parent root = loader.load();

            // Link the popup controller to this parent controller
            TerminalSubscriberLoginUIController loginController = loader.getController();
            loginController.setParentController(this);

            // Create a new stage for the popup
            Stage popupStage = new Stage();
            popupStage.setTitle("Subscriber Login");
            popupStage.setScene(SceneUtil.createStyledScene(root));
            
            // Set Modality: WINDOW_MODAL blocks input to the owner window until the popup is closed
            popupStage.initModality(Modality.WINDOW_MODAL); 
            
            // Set the owner of the popup to be the current terminal window
            Stage parentStage = (Stage)((Node)event.getSource()).getScene().getWindow();
            popupStage.initOwner(parentStage);

            // Show the popup and wait for it to close
            popupStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the "Back" button click.
     * Returns to the main User Login screen.
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
        System.exit(0);
    }

    /**
     * Callback method invoked by the popup login window upon successful login.
     * <p>
     * This allows the terminal to be aware of the connected user without changing the screen.
     * </p>
     *
     * @param username The username of the subscriber who just logged in.
     */
    public void setLoggedInSubscriber(String username) {
        System.out.println("Subscriber connected via terminal: " + username);
        
        // Optional: Update the status label if it exists in the FXML
        if (statusLabel != null) {
            statusLabel.setText("Hello, " + username);
        }
    }
    
    public void setLoggedInSubscriber(entities.Subscriber sub) {
        this.loggedInSubscriber = sub;
        
        if (sub != null) {
            System.out.println("Subscriber connected via terminal (Object): " + sub.getUserName());
            
            // עדכון התווית
            if (statusLabel != null) {
                statusLabel.setText("Hello, " + sub.getUserName());
            }
            
            // עדכון השרת בזהות המלאה
            if (ClientUI.chat != null) {
                ClientUI.chat.handleMessageFromClientUI("IDENTIFY|" + sub.getUserName() + "|" + sub.getRole());
            }
        }
    }

    /**
     * Navigates to a new screen and sets the "Return Path" so the user can navigate back.
     * <p>
     * This method loads the FXML file, retrieves its controller, and injects the return path
     * (RestaurantTerminalUI) into the destination controller.
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

            String termUser = "Terminal"; 
            String termRole = "Terminal";

            // Set the return path based on the controller type
            switch (controller) {
                case ReceiveTableUIController c -> {
                    c.setReturnPath("RestaurantTerminalUI.fxml", "Restaurant Terminal", termUser, termRole);
                    if (loggedInSubscriber != null) {
                        c.setSubscriber(loggedInSubscriber);
                    }
                }
                case ClientWaitingListController c -> 
                    c.setReturnPath("RestaurantTerminalUI.fxml", "Restaurant Terminal", termUser, termRole);

                case BillPaymentController c -> 
                    c.setReturnPath("RestaurantTerminalUI.fxml", "Restaurant Terminal", termUser, termRole);
                
                case ClientUIController c -> {
                     c.setReturnPath("RestaurantTerminalUI.fxml", "Restaurant Terminal", termUser, termRole);
                     c.setUserContext(termUser, termRole);
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