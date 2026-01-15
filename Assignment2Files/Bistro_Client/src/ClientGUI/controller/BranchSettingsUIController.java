package ClientGUI.controller;

import ClientGUI.util.ViewLoader;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class BranchSettingsUIController {

    @FXML private TextField openField;
    @FXML private TextField closeField;

    @FXML private TableView<TableRow> tablesTable;
    @FXML private TableColumn<TableRow, Integer> colTableNumber;
    @FXML private TableColumn<TableRow, Integer> colCapacity;

    @FXML private TextField tableNumberField;
    @FXML private TextField capacityField;

    @FXML private Label statusLabel;

    private final ObservableList<TableRow> rows = FXCollections.observableArrayList();

    // Back navigation context (same pattern as other screens)
    private String returnScreenFXML = "ManagerUI.fxml";
    private String returnTitle = "Manager Dashboard";
    private String currentUserName;
    private String currentUserRole;

    @FXML
    public void initialize() {
        colTableNumber.setCellValueFactory(c -> c.getValue().tableNumberProperty().asObject());
        colCapacity.setCellValueFactory(c -> c.getValue().capacityProperty().asObject());
        tablesTable.setItems(rows);

        // טוען נתונים אחרי שהמסך עלה
        Platform.runLater(this::loadBranchSettings);
    }

    public void setUserContext(String user, String role) {
        this.currentUserName = user;
        this.currentUserRole = role;
    }

    public void setReturnPath(String fxml, String title, String user, String role) {
        this.returnScreenFXML = fxml;
        this.returnTitle = title;
        this.currentUserName = user;
        this.currentUserRole = role;
    }

    @FXML
    private void onRefresh(ActionEvent e) {
        loadBranchSettings();
    }

    private void loadBranchSettings() {
        try {
            if (ClientUI.chat == null) {
                setStatus("אין חיבור לשרת");
                return;
            }

            ClientUI.chat.handleMessageFromClientUI("#GET_BRANCH_SETTINGS");
            String resp = ClientUI.chat.waitForMessage();

            if (resp == null || !resp.startsWith("BRANCH_SETTINGS|")) {
                setStatus("שגיאה בטעינה: " + resp);
                return;
            }

            // BRANCH_SETTINGS|open|close|t1,cap~t2,cap...
            String[] parts = resp.split("\\|", -1);
            String open = parts.length > 1 ? parts[1] : "";
            String close = parts.length > 2 ? parts[2] : "";
            String tablesPayload = parts.length > 3 ? parts[3] : "EMPTY";

            Platform.runLater(() -> {
                openField.setText(normalizeTime(open));
                closeField.setText(normalizeTime(close));
                rows.clear();

                if (tablesPayload != null && !tablesPayload.equalsIgnoreCase("EMPTY") && !tablesPayload.isBlank()) {
                    String[] items = tablesPayload.split("~");
                    for (String it : items) {
                        String[] kv = it.split(",");
                        if (kv.length >= 2) {
                            try {
                                int t = Integer.parseInt(kv[0].trim());
                                int c = Integer.parseInt(kv[1].trim());
                                rows.add(new TableRow(t, c));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                setStatus("נטען בהצלחה");
            });

        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("חריגה בטעינה");
        }
    }

    @FXML
    private void onSaveHours(ActionEvent e) {
        try {
            if (ClientUI.chat == null) { setStatus("אין חיבור לשרת"); return; }

            String open = normalizeTime(openField.getText());
            String close = normalizeTime(closeField.getText());

            if (!isValidHHmm(open) || !isValidHHmm(close)) {
                setStatus("פורמט שעות לא תקין (HH:mm)");
                return;
            }

            ClientUI.chat.handleMessageFromClientUI("#SET_BRANCH_HOURS " + open + " " + close);
            String resp = ClientUI.chat.waitForMessage();

            setStatus(resp != null ? resp : "בוצע");
            loadBranchSettings();

        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("שגיאה בשמירה");
        }
    }

    @FXML
    private void onUpsertTable(ActionEvent e) {
        try {
            if (ClientUI.chat == null) { setStatus("אין חיבור לשרת"); return; }

            int t = Integer.parseInt(tableNumberField.getText().trim());
            int c = Integer.parseInt(capacityField.getText().trim());

            ClientUI.chat.handleMessageFromClientUI("#UPSERT_RESTAURANT_TABLE " + t + " " + c);
            String resp = ClientUI.chat.waitForMessage();

            setStatus(resp != null ? resp : "בוצע");
            loadBranchSettings();

        } catch (NumberFormatException nfe) {
            setStatus("TableNumber/Capacity חייבים להיות מספרים");
        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("שגיאה בעדכון שולחן");
        }
    }

    @FXML
    private void onDeleteTable(ActionEvent e) {
        try {
            if (ClientUI.chat == null) { setStatus("אין חיבור לשרת"); return; }

            int t = Integer.parseInt(tableNumberField.getText().trim());

            ClientUI.chat.handleMessageFromClientUI("#DELETE_RESTAURANT_TABLE " + t);
            String resp = ClientUI.chat.waitForMessage();

            setStatus(resp != null ? resp : "בוצע");
            loadBranchSettings();

        } catch (NumberFormatException nfe) {
            setStatus("TableNumber חייב להיות מספר");
        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("שגיאה במחיקה");
        }
    }

    @FXML
    private void onBack(ActionEvent event) {
        try {
            FXMLLoader loader = ViewLoader.fxml(returnScreenFXML);
            Parent root = loader.load();
            Object controller = loader.getController();

           
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle(returnTitle);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setStatus(String s) {
        Platform.runLater(() -> statusLabel.setText(s));
    }

    private String normalizeTime(String t) {
        if (t == null) return "";
        t = t.trim();
        // אם הגיע HH:mm:ss מהשרת, נחתוך ל-HH:mm
        if (t.length() >= 5) return t.substring(0, 5);
        return t;
    }

    private boolean isValidHHmm(String t) {
        return t != null && t.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
    }

    public static class TableRow {
        private final IntegerProperty tableNumber = new SimpleIntegerProperty();
        private final IntegerProperty capacity = new SimpleIntegerProperty();

        public TableRow(int tableNumber, int capacity) {
            this.tableNumber.set(tableNumber);
            this.capacity.set(capacity);
        }
        public IntegerProperty tableNumberProperty() { return tableNumber; }
        public IntegerProperty capacityProperty() { return capacity; }
    }
}
