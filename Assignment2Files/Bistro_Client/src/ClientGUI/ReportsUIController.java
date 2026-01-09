package ClientGUI;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public class ReportsUIController {

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private PieChart reservationStatusChart;
    @FXML private BarChart<String, Number> guestStatsChart;
    @FXML private LineChart<String, Number> timeDistributionChart;
    @FXML private AreaChart<String, Number> waitingListChart;
    @FXML private Label totalReservationsLabel;
    @FXML private Label expiredReservationsLabel;
    @FXML private Label confirmedReservationsLabel;
    @FXML private Label totalGuestsLabel;
    @FXML private Button btnBack;

    private ReportData reportData;

    @FXML
    private void initialize() {
        // Set default date range (last 30 days)
        LocalDate today = LocalDate.now();
        startDatePicker.setValue(today.minusDays(30));
        endDatePicker.setValue(today);

        // Load initial reports
        loadReports();
    }

    @FXML
    private void onSetDatesClicked(ActionEvent event) {
        if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please select both start and end dates");
            return;
        }
        loadReports();
    }

    @FXML
    private void onGenerateXlsxClicked(ActionEvent event) {
        if (reportData == null) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No data to export. Please load reports first.");
            return;
        }

        try {
            generateCsvReport();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Report generated successfully as CSV!");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to generate report: " + e.getMessage());
        }
    }

    /**
     * Handles the Back button click.
     * Navigates back to the Manager Dashboard (ManagerUI.fxml).
     */
    @FXML
    private void onBackClicked(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ManagerUI.fxml"));
            Parent root = loader.load();

            // אופציונלי: אם צריך להעביר מידע חזרה למנהל, אפשר לעשות זאת כאן
            // ManagerUIController controller = loader.getController();
            // controller.setManagerName("Manager"); 

            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Navigation Error", "Failed to load Manager Dashboard.");
        }
    }

    /**
     * Handles the Exit button click.
     * Closes the application.
     */
    @FXML
    private void onExitClicked(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }

    private void loadReports() {
        // Load data from server
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        // Query server for reservation data
        String request = "#GET_REPORTS_DATA " + startDate + " " + endDate;
        ClientUI.chat.handleMessageFromClientUI(request);

        // For now, use demo data
        loadDemoData(startDate, endDate);
    }

    private void loadDemoData(LocalDate startDate, LocalDate endDate) {
        reportData = new ReportData();

        // Demo data - in production this would come from the server
        reportData.totalReservations = 15;
        reportData.confirmedCount = 8;
        reportData.arrivedCount = 0;  // Will be populated in future
        reportData.lateCount = 0;     // Will be populated in future
        reportData.expiredReservations = 5;
        reportData.totalGuests = 45;
        reportData.startDate = startDate;
        reportData.endDate = endDate;

        updateUI();
    }

    private void updateUI() {
        Platform.runLater(() -> {
            // Update summary labels
            totalReservationsLabel.setText(String.valueOf(reportData.totalReservations));
            expiredReservationsLabel.setText(String.valueOf(reportData.expiredReservations));
            confirmedReservationsLabel.setText(String.valueOf(reportData.confirmedCount));
            totalGuestsLabel.setText(String.valueOf(reportData.totalGuests));

            // Update Pie Chart - Reservation Status
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                    new PieChart.Data("Confirmed", reportData.confirmedCount),
                    new PieChart.Data("Arrived", reportData.arrivedCount),
                    new PieChart.Data("Late", reportData.lateCount),
                    new PieChart.Data("Expired", reportData.expiredReservations)
            );
            reservationStatusChart.setData(pieData);
            reservationStatusChart.setTitle("Reservation Status Distribution");

            // Update Bar Chart - Guest Statistics
            XYChart.Series<String, Number> guestSeries = new XYChart.Series<>();
            guestSeries.setName("Number of Guests");
            guestSeries.getData().add(new XYChart.Data<>("Total Guests", reportData.totalGuests));
            guestSeries.getData().add(new XYChart.Data<>("Avg per Reservation", 
                    reportData.totalReservations > 0 ? reportData.totalGuests / reportData.totalReservations : 0));
            guestSeries.getData().add(new XYChart.Data<>("Confirmed Guests", 
                    reportData.confirmedCount > 0 ? reportData.totalGuests : 0));

            guestStatsChart.getData().clear();
            guestStatsChart.getData().add(guestSeries);

            // Update Line Chart - Time Distribution of Reservations
            updateTimeDistributionChart();

            // Update Area Chart - Waiting List Statistics
            updateWaitingListChart();
        });
    }

    private void updateTimeDistributionChart() {
        XYChart.Series<String, Number> timeSeries = new XYChart.Series<>();
        timeSeries.setName("Reservations by Time");

        // Demo data for time slots throughout the day
        String[] timeSlots = {"09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", 
                              "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00"};
        int[] reservationCounts = {1, 2, 3, 5, 7, 8, 6, 4, 3, 9, 11, 8, 5, 2};

        for (int i = 0; i < timeSlots.length; i++) {
            timeSeries.getData().add(new XYChart.Data<>(timeSlots[i], reservationCounts[i]));
        }

        timeDistributionChart.getData().clear();
        timeDistributionChart.getData().add(timeSeries);
        timeDistributionChart.setTitle("Peak Reservation Times");
    }

    private void updateWaitingListChart() {
        // Create two series for waiting list data
        XYChart.Series<String, Number> waitingCountSeries = new XYChart.Series<>();
        waitingCountSeries.setName("Waiting Count");

        XYChart.Series<String, Number> servedCountSeries = new XYChart.Series<>();
        servedCountSeries.setName("Served");

        // Demo data for each day of the month
        String[] dates = {"Day 1", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7", "Day 8", 
                         "Day 9", "Day 10", "Day 11", "Day 12", "Day 13", "Day 14", "Day 15",
                         "Day 16", "Day 17", "Day 18", "Day 19", "Day 20", "Day 21", "Day 22",
                         "Day 23", "Day 24", "Day 25", "Day 26", "Day 27", "Day 28", "Day 29", "Day 30"};
        
        int[] waitingCounts = {2, 3, 5, 2, 4, 6, 3, 5, 4, 7, 2, 5, 8, 3, 4,
                               6, 5, 7, 3, 4, 5, 2, 6, 4, 3, 5, 7, 2, 4, 3};
        
        int[] servedCounts = {5, 6, 7, 5, 8, 9, 6, 7, 8, 10, 6, 8, 11, 7, 8,
                             9, 8, 10, 6, 7, 8, 5, 9, 7, 6, 8, 10, 5, 7, 6};

        for (int i = 0; i < dates.length; i++) {
            waitingCountSeries.getData().add(new XYChart.Data<>(dates[i], waitingCounts[i]));
            servedCountSeries.getData().add(new XYChart.Data<>(dates[i], servedCounts[i]));
        }

        waitingListChart.getData().clear();
        waitingListChart.getData().add(waitingCountSeries);
        waitingListChart.getData().add(servedCountSeries);
        waitingListChart.setTitle("Monthly Waiting List & Service Statistics");
    }

    private void generateCsvReport() throws Exception {
        String filename = "Bistro_Report_" + LocalDate.now() + ".csv";
        try (FileWriter writer = new FileWriter(filename)) {
            // Write header
            writer.write("BISTRO RESERVATION REPORT\n");
            writer.write("Date Range: " + reportData.startDate + " to " + reportData.endDate + "\n\n");
            
            // Write summary
            writer.write("SUMMARY\n");
            writer.write("Total Reservations," + reportData.totalReservations + "\n");
            writer.write("Confirmed Reservations," + reportData.confirmedCount + "\n");
            writer.write("Arrived Reservations," + reportData.arrivedCount + "\n");
            writer.write("Late Reservations," + reportData.lateCount + "\n");
            writer.write("Expired Reservations," + reportData.expiredReservations + "\n");
            writer.write("Total Guests," + reportData.totalGuests + "\n");
            
            if (reportData.totalReservations > 0) {
                double avgGuests = (double) reportData.totalGuests / reportData.totalReservations;
                writer.write("Average Guests per Reservation," + String.format("%.2f", avgGuests) + "\n");
            }
            
            // Write time distribution data
            writer.write("\nTIME DISTRIBUTION - Peak Reservation Times\n");
            writer.write("Time Slot,Number of Reservations\n");
            String[] timeSlots = {"09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", 
                                  "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00"};
            int[] reservationCounts = {1, 2, 3, 5, 7, 8, 6, 4, 3, 9, 11, 8, 5, 2};
            for (int i = 0; i < timeSlots.length; i++) {
                writer.write(timeSlots[i] + "," + reservationCounts[i] + "\n");
            }
            
            // Write waiting list data
            writer.write("\nMONTHLY WAITING LIST & SERVICE STATISTICS\n");
            writer.write("Day,Waiting Count,Served\n");
            String[] dates = {"Day 1", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7", "Day 8", 
                             "Day 9", "Day 10", "Day 11", "Day 12", "Day 13", "Day 14", "Day 15",
                             "Day 16", "Day 17", "Day 18", "Day 19", "Day 20", "Day 21", "Day 22",
                             "Day 23", "Day 24", "Day 25", "Day 26", "Day 27", "Day 28", "Day 29", "Day 30"};
            
            int[] waitingCounts = {2, 3, 5, 2, 4, 6, 3, 5, 4, 7, 2, 5, 8, 3, 4,
                                   6, 5, 7, 3, 4, 5, 2, 6, 4, 3, 5, 7, 2, 4, 3};
            
            int[] servedCounts = {5, 6, 7, 5, 8, 9, 6, 7, 8, 10, 6, 8, 11, 7, 8,
                                 9, 8, 10, 6, 7, 8, 5, 9, 7, 6, 8, 10, 5, 7, 6};

            for (int i = 0; i < dates.length; i++) {
                writer.write(dates[i] + "," + waitingCounts[i] + "," + servedCounts[i] + "\n");
            }
        }

        System.out.println("CSV report generated: " + filename);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class to hold report data
    private static class ReportData {
        int totalReservations;
        int confirmedCount;
        int arrivedCount;
        int lateCount;
        int expiredReservations;
        int totalGuests;
        LocalDate startDate;
        LocalDate endDate;
    }
}
