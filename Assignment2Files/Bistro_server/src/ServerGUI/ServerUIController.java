package ServerGUI;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import server.EchoServer;
import server.GetClientInfo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller class for the Server UI
 * Handles all user interactions and communication with the server
 */
public class ServerUIController {

    // UI Components
    @FXML private Label serverStatusLabel;
    @FXML private TextArea serverLogTextArea;
    @FXML private Label connectedClientsLabel;
    
    @FXML private Button startServerButton;
    @FXML private Button stopServerButton;
    @FXML private Button clearLogsButton;
    @FXML private Button doneButton;

    @FXML private ComboBox<Integer> portComboBox;
    @FXML private Spinner<Integer> portSpinner;
    @FXML private Label portStatusLabel;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML private TableView<GetClientInfo> clientsTableView;
    @FXML private TableColumn<GetClientInfo, String> clientIPColumn;
    @FXML private TableColumn<GetClientInfo, String> clientNameColumn;
    @FXML private TableColumn<GetClientInfo, String> connectionTimeColumn;

    @FXML private TextField dbUserField;
    @FXML private PasswordField dbPasswordField;

    private EchoServer echoServer;
    private DateTimeFormatter dateTimeFormatter;
    private static final String SERVER_RUNNING = "Server Running";
    private static final String SERVER_STOPPED = "Server Stopped";

    public ServerUIController() {
        dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    @FXML
    public void initialize() {
        // Initialize UI components
        setupPortSelector();
        setupDatabaseFields();
        setupButtons();
        setupTableColumns();

        // Initialize the table with an empty observable list
        if (clientsTableView.getItems() == null) {
            clientsTableView.setItems(FXCollections.observableArrayList());
        }

        updateServerStatus(false);
    }

    /**
     * Set the EchoServer reference
     */
    public void setEchoServer(EchoServer server) {
        this.echoServer = server;
    }

    /**
     * Refreshes the client table with the latest list from the server.
     * This is called by EchoServer when a client identifies themselves.
     */
    public void refreshClientTable() {
        Platform.runLater(() -> {
            if (clientsTableView != null && echoServer != null) {
                clientsTableView.getItems().clear();
                clientsTableView.getItems().addAll(echoServer.getConnectedClients().values());
            }
        });
    }

    /**
     * Setup port selector options
     */
    private void setupPortSelector() {
        if (portComboBox != null) {
            portComboBox.getItems().addAll(5555, 5556, 5557, 8888, 9999, 3306);
            portComboBox.setValue(5555);

            // Keep spinner in sync with dropdown
            portComboBox.setOnAction(event -> {
                Integer selectedPort = portComboBox.getValue();
                if (selectedPort != null && portSpinner != null) {
                    if (portSpinner.getValueFactory() == null) {
                        portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 65535, 5555));
                    }
                    portSpinner.getValueFactory().setValue(selectedPort);
                    if (portStatusLabel != null) portStatusLabel.setText("Ready");
                }
            });
        }

        if (portSpinner != null) {
            portSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 65535, 5555));
        }
    }

    /**
     * Setup database credential fields with default values
     */
    private void setupDatabaseFields() {
        if (dbUserField != null) dbUserField.setText("root");
        if (dbPasswordField != null) dbPasswordField.setText(""); // Default empty for localhost
    }

    /**
     * Setup button event handlers
     */
    private void setupButtons() {
        if (startServerButton != null) startServerButton.setOnAction(event -> startServer());
        if (stopServerButton != null) stopServerButton.setOnAction(event -> stopServer());
        if (clearLogsButton != null) clearLogsButton.setOnAction(event -> clearLogs());
        if (doneButton != null) doneButton.setOnAction(event -> done());
    }

    /**
     * Setup table columns for client display
     */
    private void setupTableColumns() {
        if (clientIPColumn != null)
            clientIPColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("clientIP"));
        if (clientNameColumn != null)
            clientNameColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("clientName"));
        if (connectionTimeColumn != null)
            connectionTimeColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("connectionTime"));
    }

    /**
     * Add a client to the table view (Called by EchoServer)
     */
    public void addClientToTable(GetClientInfo clientInfo) {
        Platform.runLater(() -> {
            if (clientsTableView != null && clientsTableView.getItems() != null) {
                clientsTableView.getItems().add(clientInfo);
                System.out.println("✓ Client added to table: " + clientInfo.getClientIP());
            } else {
                System.err.println("ERROR: Cannot add client - TableView not initialized");
            }
        });
    }

    /**
     * Remove a client from the table view (Called by EchoServer)
     */
    public void removeClientFromTable(GetClientInfo clientInfo) {
        Platform.runLater(() -> {
            if (clientsTableView != null && clientsTableView.getItems() != null) {
                clientsTableView.getItems().remove(clientInfo);
            }
        });
    }

    /**
     * Start the server
     */
    @FXML
    private void startServer() {
        if (echoServer != null) {
            try {
                int port = 5555;
                if (portSpinner != null) port = portSpinner.getValue();
                
                echoServer.setPort(port);

                new Thread(() -> {
                    try {
                        echoServer.listen();
                        Platform.runLater(() -> {
                            updateServerStatus(true);
                        });
                    } catch (IOException e) {
                        Platform.runLater(() -> {
                            addLog("ERROR - Could not listen for clients: " + e.getMessage());
                            updateServerStatus(false);
                        });
                    }
                }).start();

                if (startServerButton != null) startServerButton.setDisable(true);
                if (stopServerButton != null) stopServerButton.setDisable(false);
                if (portSpinner != null) portSpinner.setDisable(true);
                if (portComboBox != null) portComboBox.setDisable(true);
            } catch (Exception e) {
                addLog("ERROR: " + e.getMessage());
            }
        }
    }

    /**
     * Stop the server
     */
    @FXML
    private void stopServer() {
        if (echoServer != null) {
            try {
                echoServer.close();
                updateServerStatus(false);
                addLog("Server stopped");
                
                if (startServerButton != null) startServerButton.setDisable(false);
                if (stopServerButton != null) stopServerButton.setDisable(true);
                if (portSpinner != null) portSpinner.setDisable(false);
                if (portComboBox != null) portComboBox.setDisable(false);
            } catch (IOException e) {
                addLog("ERROR stopping server: " + e.getMessage());
            }
        }
    }

    /**
     * Clear the log display
     */
    @FXML
    private void clearLogs() {
        if (serverLogTextArea != null) serverLogTextArea.clear();
    }

    /**
     * Close the server and exit the program
     */
    @FXML
    private void done() {
        try {
            addLog("Shutting down server...");
            if (echoServer != null) {
                echoServer.close(); // AbstractServer.close() stops listening and closes all connections
            }
            addLog("Server closed. Exiting application...");
        } catch (IOException e) {
            addLog("ERROR while closing server: " + e.getMessage());
        }

        // Wait a moment for the log to be displayed, then exit
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // Ignore
            }
            System.exit(0);
        }).start();
    }

    /**
     * Add a message to the server log (Called by EchoServer via reflection)
     */
    public void addLog(String message) {
        if (message == null) return;

        Platform.runLater(() -> {
            if (serverLogTextArea == null) {
                System.err.println("ERROR: serverLogTextArea is null!");
                return;
            }

            try {
                String timestamp = LocalDateTime.now().format(dateTimeFormatter);
                String logEntry = "[" + timestamp + "] " + message;

                serverLogTextArea.appendText(logEntry + "\n");
                serverLogTextArea.positionCaret(serverLogTextArea.getLength());

                System.out.println("✓ GUI LOG: " + logEntry);

            } catch (Exception e) {
                System.err.println("ERROR in addLog: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Update server status indicator
     */
    private void updateServerStatus(boolean isRunning) {
        Platform.runLater(() -> {
            if (serverStatusLabel == null) return;
            
            if (isRunning) {
                serverStatusLabel.setText(SERVER_RUNNING);
                serverStatusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
            } else {
                serverStatusLabel.setText(SERVER_STOPPED);
                serverStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                if (loadingIndicator != null) loadingIndicator.setVisible(false);
            }
        });
    }

    /**
     * Update connected clients count (Called by EchoServer via reflection)
     */
    public void updateClientCount(int count) {
        Platform.runLater(() -> {
            if (connectedClientsLabel != null) {
                connectedClientsLabel.setText("Connected Clients: " + count);
            }
        });
    }

    public void shutdown() {
        if (echoServer != null) {
            try {
                echoServer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getDbUser() {
        if (dbUserField != null && !dbUserField.getText().trim().isEmpty()) {
            return dbUserField.getText().trim();
        }
        return "root";
    }

    public String getDbPassword() {
        if (dbPasswordField != null) {
            return dbPasswordField.getText();
        }
        return "";
    }
}