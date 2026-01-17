package ManagerWaitingListUI;

// change to your package

import ClientGUI.controller.ClientUI;
import client.ChatClient;
import common.ChatIF;
import entities.WaitingEntry;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Base64;

public class ManagerWaitingListController implements ChatIF {

    @FXML private TableView<WaitingEntry> tbl;
    @FXML private TableColumn<WaitingEntry, Number> colId;
    @FXML private TableColumn<WaitingEntry, Number> colDiners;
    @FXML private TableColumn<WaitingEntry, String> colCode;
    @FXML private TableColumn<WaitingEntry, String> colSubscriber;
    @FXML private TableColumn<WaitingEntry, String> colStatus;
    @FXML private TableColumn<WaitingEntry, String> colEntryTime;
    @FXML private TableColumn<WaitingEntry, String> colContact;
    @FXML private Label lblInfo;

    private final ObservableList<WaitingEntry> data = FXCollections.observableArrayList();
    private ChatClient client;

    @FXML
    public void initialize() {
        tbl.setItems(data);

        colId.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getWaitingId()));
        colDiners.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getNumOfDiners()));
        colCode.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getConfirmationCode()));
        colSubscriber.setCellValueFactory(c -> {
            Integer sid = c.getValue().getSubscriberId();
            return new ReadOnlyStringWrapper(sid == null ? "" : sid.toString());
        });
        colStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStatus()));
        colContact.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getContactInfo()));
        colEntryTime.setCellValueFactory(c -> {
            Timestamp t = c.getValue().getEntryTime();
            return new ReadOnlyStringWrapper(t == null ? "" : t.toString());
        });

        try {
            client = new ChatClient(ClientUI.serverHost, ClientUI.serverPort, this);
            client.openConnection();
            client.handleMessageFromClientUI("#SUBSCRIBE_WAITING_LIST"); // real-time updates
        } catch (Exception e) {
            lblInfo.setText("Failed to connect: " + e.getMessage());
        }
    }

    @Override
    public void display(String message) {
        Platform.runLater(() -> handleServerMessage(message));
    }

    private void handleServerMessage(String msg) {
        if (msg == null) return;

        if (msg.startsWith("WAITING_LIST|")) {
            loadSnapshot(msg);
            return;
        }

        lblInfo.setText(msg);
    }

    private void loadSnapshot(String msg) {
        data.clear();

        String body = msg.substring("WAITING_LIST|".length());
        if ("EMPTY".equals(body)) return;

        String[] rows = body.split("~");
        for (String row : rows) {
            // new row format: WaitingID,ContactInfoB64,NumOfDiners,ConfirmationCode,SubscriberID,Status,EntryTime
            String[] cols = row.split(",", 7);
            if (cols.length < 7) continue;

            try {
                int waitingId = Integer.parseInt(cols[0]);
                String contactInfo = decodeB64Url(cols[1]);
                int diners = Integer.parseInt(cols[2]);
                String code = cols[3];
                String subStr = cols[4];
                Integer subscriberId = (subStr == null || subStr.isBlank()) ? null : Integer.parseInt(subStr);
                String status = cols[5];

                Timestamp entryTime = null;
                if (cols[6] != null && !cols[6].isBlank()) {
                    entryTime = Timestamp.valueOf(cols[6]);
                }

                data.add(new WaitingEntry(waitingId, contactInfo, subscriberId, diners, code, status, entryTime));
            } catch (Exception ignored) {
                // ignore bad rows
            }
        }
    }

    @FXML
    private void onMarkTableFound() {
        WaitingEntry selected = tbl.getSelectionModel().getSelectedItem();
        if (selected == null || client == null) return;

        client.handleMessageFromClientUI("#WAITING_UPDATE_STATUS " +
                selected.getConfirmationCode() + " TableFound");
    }

    @FXML
    private void onRemove() {
        WaitingEntry selected = tbl.getSelectionModel().getSelectedItem();
        if (selected == null || client == null) return;

        client.handleMessageFromClientUI("#WAITING_DELETE " + selected.getConfirmationCode());
    }

    private String decodeB64Url(String b64) {
        if (b64 == null || b64.isEmpty()) return "";
        byte[] bytes = Base64.getUrlDecoder().decode(b64);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
