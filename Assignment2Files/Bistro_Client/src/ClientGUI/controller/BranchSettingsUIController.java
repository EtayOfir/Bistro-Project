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
import ClientGUI.controller.ManagerUIController;
import ClientGUI.controller.RepresentativeMenuUIController;

import java.io.IOException;

/**
 * Controller for the Branch Settings Management screen.
 * <p>
 * This controller allows the Branch Manager to:
 * <ul>
 * <li>View and edit weekly opening/closing hours.</li>
 * <li>Manage special opening hours for specific dates (e.g., holidays).</li>
 * <li>Manage the restaurant's physical layout configuration (tables and capacity).</li>
 * </ul>
 * <p>
 * All data is fetched from and saved to the server using specific protocol commands.
 */
public class BranchSettingsUIController {
	@FXML private ComboBox<String> dayCombo;
	// ===== Special date (override) =====
	@FXML private DatePicker specialDatePicker;
	@FXML private TextField specialOpenField;
	@FXML private TextField specialCloseField;

	@FXML private TableView<SpecialRow> specialTable;
	@FXML private TableColumn<SpecialRow, String> colSpecialDate;
	@FXML private TableColumn<SpecialRow, String> colSpecialOpen;
	@FXML private TableColumn<SpecialRow, String> colSpecialClose;


	@FXML private TextField openField;
	@FXML private TextField closeField;

	@FXML private TableView<TableRow> tablesTable;
	@FXML private TableColumn<TableRow, Integer> colTableNumber;
	@FXML private TableColumn<TableRow, Integer> colCapacity;

	@FXML private TextField tableNumberField;
	@FXML private TextField capacityField;

	@FXML private Label statusLabel;

	private final ObservableList<TableRow> rows = FXCollections.observableArrayList();
	private final java.util.Map<String, String[]> weeklyHours = new java.util.HashMap<>();

	private String returnScreenFXML = "ManagerUI.fxml";
	private String returnTitle = "Manager Dashboard";
	private String currentUserName;
	private String currentUserRole;
	@FXML private TableView<DayRow> hoursTable;
	@FXML private TableColumn<DayRow, String> colDay;
	@FXML private TableColumn<DayRow, String> colOpen;
	@FXML private TableColumn<DayRow, String> colClose;

	private final ObservableList<DayRow> days = FXCollections.observableArrayList();
	private final ObservableList<SpecialRow> specialRows = FXCollections.observableArrayList();

	/**
     * Data model representing a specific date with overridden operating hours.
     * Used for the {@link #specialTable}.
     */
	public static class SpecialRow {
	    private final String date;   
	    private final String open;   
	    private final String close; 

	    public SpecialRow(String date, String open, String close) {
	        this.date = date;
	        this.open = open.substring(0,5);
	        this.close = close.substring(0,5);
	    }

	    public String getDate() { return date; }
	    public String getOpen() { return open; }
	    public String getClose() { return close; }
	}

	/**
     * Data model representing standard weekly operating hours for a specific day.
     * Used for the {@link #hoursTable}.
     */
	public static class DayRow {
		private final String day;
		private final String open;
		private final String close;

		public DayRow(String day, String open, String close) {
			this.day = day;
			this.open = open.substring(0,5);
			this.close = close.substring(0,5);
		}

		public String getDay() { return day; }
		public String getOpen() { return open; }
		public String getClose() { return close; }
	}

	/**
     * Initializes the controller class.
     * <p>
     * Sets up the TableView columns, listeners for row selection (to populate input fields),
     * and triggers the initial data loading from the server via {@code Platform.runLater}.
     */
	@FXML
	public void initialize() {
		colDay.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDay()));
		colOpen.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getOpen()));
		colClose.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getClose()));

		hoursTable.setItems(days);
		colSpecialDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDate()));
		colSpecialOpen.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getOpen()));
		colSpecialClose.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getClose()));
		specialTable.setItems(specialRows);

		specialTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
		    if (n != null) {
		        specialDatePicker.setValue(java.time.LocalDate.parse(n.getDate()));
		        specialOpenField.setText(n.getOpen());
		        specialCloseField.setText(n.getClose());
		    }
		});

		hoursTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
			if (n != null) {
				openField.setText(n.getOpen());
				closeField.setText(n.getClose());
			}
		});

		colTableNumber.setCellValueFactory(c -> c.getValue().tableNumberProperty().asObject());
		colCapacity.setCellValueFactory(c -> c.getValue().capacityProperty().asObject());
		tablesTable.setItems(rows);
		dayCombo.setItems(FXCollections.observableArrayList(
				"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"
				));
		dayCombo.getSelectionModel().selectFirst();

		dayCombo.setOnAction(e -> {
			String day = dayCombo.getValue();
			if (day != null && weeklyHours.containsKey(day)) {
				String[] oc = weeklyHours.get(day);
				openField.setText(normalizeTime(oc[0]));
				closeField.setText(normalizeTime(oc[1]));
			}
		});

		Platform.runLater(this::loadBranchSettings);
		Platform.runLater(this::loadWeeklyHours);     
		Platform.runLater(this::loadWeeklyOpeningHours);   
		Platform.runLater(this::loadSpecialOpeningHours);



	}

	/**
     * Injects the logged-in user's context (identity and role) into this controller.
     * <p>
     * Since JavaFX controllers are instantiated by the FXMLLoader, this method 
     * serves as a post-initialization step to pass session data from the 
     * previous screen to ensure the user's state is preserved.
     *
     * @param user The username of the currently logged-in user.
     * @param role The authorization role of the user (e.g., "BRANCH_MANAGER").
     */
	public void setUserContext(String user, String role) {
		this.currentUserName = user;
		this.currentUserRole = role;
	}

	/**
     * Configures the navigation target for the "Back" button.
     * <p>
     * This method is used to pass necessary navigation data from the previous controller,
     * allowing this screen to return the user to their origin (typically the Manager Dashboard)
     * while preserving their session context (username and role).
     *
     * @param fxml  The filename of the FXML view to return to (e.g., "ManagerUI.fxml").
     * @param title The window title to set when returning to the previous screen.
     * @param user  The current user's username (to maintain the active session).
     * @param role  The current user's role (to maintain authorization).
     */
	public void setReturnPath(String fxml, String title, String user, String role) {
		this.returnScreenFXML = fxml;
		this.returnTitle = title;
		this.currentUserName = user;
		this.currentUserRole = role;
	}

	/**
     * Event handler for the "Refresh" button.
     * <p>
     * Manually triggers a reload of the branch settings (opening hours and table configurations)
     * from the server. This is useful if the data has been modified externally or if
     * the initial load failed due to connectivity issues.
     *
     * @param e The {@link ActionEvent} triggered by clicking the refresh button.
     */
	@FXML
	public void onRefresh(ActionEvent e) {
	    loadBranchSettings();
	}


	/**
     * Fetches the general branch settings and table configurations.
     * <p>
     * Sends request: {@code #GET_BRANCH_SETTINGS}
     * <p>
     * Expected response format: {@code BRANCH_SETTINGS|openTime|closeTime|tablesPayload}
     * Where {@code tablesPayload} is a tilde-separated list of "id,capacity".
     */
	private void loadBranchSettings() {
		try {
			if (ClientUI.chat == null) {
				setStatus("No connection to server");
				return;
			}

			ClientUI.chat.handleMessageFromClientUI("#GET_BRANCH_SETTINGS");
			String resp = ClientUI.chat.waitForMessage();

			if (resp == null || !resp.startsWith("BRANCH_SETTINGS|")) {
				setStatus("Loading error: " + resp);
				return;
			}

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
				setStatus("Successfuly Loaded");
			});

		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Loading error");
		}
	}
	
	/**
     * Fetches the standard weekly operating hours.
     * <p>
     * Sends request: {@code #GET_OPENING_HOURS_WEEKLY}
     * <p>
     * Populates both the {@code hoursTable} and the {@code weeklyHours} map used 
     * for the dropdown selection logic.
     */
	private void loadWeeklyOpeningHours() {
		try {
			if (ClientUI.chat == null) {
				setStatus("No connection to server");
				return;
			}

			ClientUI.chat.handleMessageFromClientUI("#GET_OPENING_HOURS_WEEKLY");
			String resp = ClientUI.chat.waitForMessage();

			if (resp == null || !resp.startsWith("OPENING_HOURS_WEEKLY|")) {
				setStatus("Weekly hours load error: " + resp);
				return;
			}

			String payload = resp.split("\\|", 2)[1];
			weeklyHours.clear();

			if (payload != null && !payload.equalsIgnoreCase("EMPTY") && !payload.equalsIgnoreCase("ERROR")) {
				String[] items = payload.split("~");
				for (String it : items) {
					String[] p = it.split(",");
					if (p.length >= 3) {
						String day = p[0].trim();
						String open = p[1].trim();
						String close = p[2].trim();
						weeklyHours.put(day, new String[]{open, close});
					}
				}
			}

			Platform.runLater(() -> {
				String day = dayCombo.getValue();
				if (day != null && weeklyHours.containsKey(day)) {
					String[] oc = weeklyHours.get(day);
					openField.setText(normalizeTime(oc[0]));
					closeField.setText(normalizeTime(oc[1]));
				}
			});

			setStatus("Weekly hours loaded");

		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Weekly hours load error");
		}
	}
	
	/**
     * Fetches special opening hours (exceptions/holidays).
     * <p>
     * Sends request: {@code #GET_OPENING_HOURS_SPECIAL}
     * <p>
     * Parses the response and updates {@link #specialRows}.
     */
	private void loadSpecialOpeningHours() {
	    try {
	        if (ClientUI.chat == null) {
	            setStatus("No connection to server");
	            return;
	        }

	        ClientUI.chat.handleMessageFromClientUI("#GET_OPENING_HOURS_SPECIAL");
	        String resp = ClientUI.chat.waitForMessage();

	        Platform.runLater(() -> specialRows.clear());

	        if (resp == null || !resp.startsWith("OPENING_HOURS_SPECIAL|")) {
	            setStatus("Special hours load error: " + resp);
	            return;
	        }

	        String payload = resp.split("\\|", 2)[1];
	        if (payload.equalsIgnoreCase("EMPTY")) {
	            setStatus("No special dates");
	            return;
	        }

	        for (String row : payload.split("~")) {
	            String[] p = row.split(",", -1);
	            if (p.length >= 3) {
	                String date = p[0].trim();           // YYYY-MM-DD
	                String open = p[1].trim();           // HH:mm:ss
	                String close = p[2].trim();          // HH:mm:ss
	                Platform.runLater(() -> specialRows.add(new SpecialRow(date, open, close)));
	            }
	        }

	        setStatus("Special dates loaded");

	    } catch (Exception e) {
	        e.printStackTrace();
	        setStatus("Special hours load error");
	    }
	}


	/**
     * Saves the operating hours for a day selected in the TableView.
     * Similar to {@link #onSaveDayHours(ActionEvent)} but takes input from the table selection.
     */
	@FXML
	public void onSaveHours(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        DayRow selected = hoursTable.getSelectionModel().getSelectedItem();
	        if (selected == null) {
	            setStatus("Select a day first");
	            return;
	        }

	        String open = normalizeTime(openField.getText());
	        String close = normalizeTime(closeField.getText());

	        if (!isValidHHmm(open) || !isValidHHmm(close)) {
	            setStatus("Wrong time format (HH:mm)");
	            return;
	        }

	        ClientUI.chat.handleMessageFromClientUI(
	            "#SET_BRANCH_HOURS_BY_DAY " + selected.getDay() + " " + open + " " + close
	        );

	        String resp = ClientUI.chat.waitForMessage();
	        setStatus(resp != null ? resp : "Done");

	        loadWeeklyHours(); // מרענן את הטבלה
	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Saving error");
	    }
	}


	/**
     * Saves the operating hours for a day selected via the ComboBox.
     * <p>
     * Validates the time format (HH:mm) before sending.
     * Sends command: {@code #SET_BRANCH_HOURS_BY_DAY <day> <open> <close>}
     *
     * @param e The event triggered by the Save button.
     */
	@FXML
	public void onSaveDayHours(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        String day = dayCombo.getValue();
	        if (day == null || day.isBlank()) { setStatus("Choose a day"); return; }

	        String open = normalizeTime(openField.getText());
	        String close = normalizeTime(closeField.getText());

	        if (!isValidHHmm(open) || !isValidHHmm(close)) {
	            setStatus("Wrong time format (HH:mm)");
	            return;
	        }

	        String cmd = "#SET_BRANCH_HOURS_BY_DAY " + day + " " + open + " " + close;
	        System.out.println("DEBUG UI -> " + cmd);

	        ClientUI.chat.handleMessageFromClientUI(cmd);
	        String resp = ClientUI.chat.waitForMessage();

	        setStatus(resp != null ? resp : "Done");

	        loadWeeklyHours();         
	        loadWeeklyOpeningHours();  
	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Saving error");
	    }
	}

	/**
     * Adds a new table or updates an existing table's capacity.
     * <p>
     * Sends command: {@code #UPSERT_RESTAURANT_TABLE <tableNum> <capacity>}
     *
     * @param e The event triggered by the Add/Update Table button.
     */
	@FXML
	private void onUpsertTable(ActionEvent e) {
		try {
			if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

			int t = Integer.parseInt(tableNumberField.getText().trim());
			int c = Integer.parseInt(capacityField.getText().trim());

			ClientUI.chat.handleMessageFromClientUI("#UPSERT_RESTAURANT_TABLE " + t + " " + c);
			String resp = ClientUI.chat.waitForMessage();

			setStatus(resp != null ? resp : "Done");
			loadBranchSettings();

		} catch (NumberFormatException nfe) {
			setStatus("TableNumber/Capacity most be a number ");
		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Error in updating table");
		}
	}

	/**
     * Deletes a table from the restaurant configuration.
     * <p>
     * Sends command: {@code #DELETE_RESTAURANT_TABLE <tableNum>}
     *
     * @param e The event triggered by the Delete Table button.
     */
	@FXML
	private void onDeleteTable(ActionEvent e) {
		try {
			if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

			int t = Integer.parseInt(tableNumberField.getText().trim());

			ClientUI.chat.handleMessageFromClientUI("#DELETE_RESTAURANT_TABLE " + t);
			String resp = ClientUI.chat.waitForMessage();

			setStatus(resp != null ? resp : "Done");
			loadBranchSettings();

		} catch (NumberFormatException nfe) {
			setStatus("TableNumber most be a number");
		} catch (Exception ex) {
			ex.printStackTrace();
			setStatus("Error delete");
		}
	}

	/**
     * Handles the navigation back to the previous screen.
     * <p>
     * This method loads the FXML file specified in {@link #returnScreenFXML}, 
     * restores the window title to {@link #returnTitle}, and swaps the current 
     * scene within the existing stage.
     *
     * @param event The action event (button click) used to retrieve the current Stage.
     */
	@FXML
	private void onBack(ActionEvent event) {
		try {
			FXMLLoader loader = ViewLoader.fxml(returnScreenFXML);
			Parent root = loader.load();
			Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
			stage.setTitle(returnTitle);
			stage.setScene(new Scene(root));
			stage.show();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	/**
     * Updates the status label on the UI with a given message.
     * <p>
     * This method is designed to be thread-safe. It wraps the UI update logic 
     * in {@link Platform#runLater}, allowing it to be safely called from 
     * background network threads without throwing an {@code IllegalStateException}.
     *
     * @param s The message string to display to the user.
     */
	private void setStatus(String s) {
		Platform.runLater(() -> statusLabel.setText(s));
	}

	/**
     * Normalizes a time string to ensuring it is in HH:mm format (5 characters).
     * Useful for trimming seconds if they exist in the DB response.
     */
	private String normalizeTime(String t) {
		if (t == null) return "";
		t = t.trim();
		if (t.length() >= 5) return t.substring(0, 5);
		return t;
	}

	/**
     * Validates that a string matches the HH:mm time format.
     *
     * @param t The time string to check.
     * @return {@code true} if valid (e.g., "09:30", "23:59"), {@code false} otherwise.
     */
	private boolean isValidHHmm(String t) {
		return t != null && t.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
	}

	/**
     * A data model representing a physical restaurant table for the TableView.
     * <p>
     * Uses JavaFX {@link IntegerProperty} to allow for potential bi-directional binding 
     * and automatic UI updates, ensuring the TableView stays synchronized with the model.
     */
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
	
	/**
     * Fetches and populates the weekly operating hours table.
     * <p>
     * Sends the command {@code #GET_OPENING_HOURS_WEEKLY} to the server.
     * <p>
     * Expected response format: {@code OPENING_HOURS_WEEKLY|day,open,close~day,open,close...}
     * <p>
     * The method parses the tilde-separated list and populates the {@code days} observable list,
     * which immediately updates the UI table.
     */
	private void loadWeeklyHours() {
		try {
			ClientUI.chat.handleMessageFromClientUI("#GET_OPENING_HOURS_WEEKLY");
			String resp = ClientUI.chat.waitForMessage();

			days.clear();

			if (resp == null || !resp.startsWith("OPENING_HOURS_WEEKLY|")) return;

			String payload = resp.split("\\|",2)[1];
			if (payload.equals("EMPTY")) return;

			for (String row : payload.split("~")) {
				String[] p = row.split(",");
				days.add(new DayRow(p[0], p[1], p[2]));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
     * Creates or updates a special opening hour rule for a specific date.
     * <p>
     * <b>Validation Checks:</b>
     * <ul>
     * <li>Server connection exists.</li>
     * <li>A date is selected in the DatePicker.</li>
     * <li>Time fields match the HH:mm format.</li>
     * </ul>
     * <p>
     * Sends command: {@code #SET_BRANCH_HOURS_BY_DATE <YYYY-MM-DD> <Open> <Close>}
     * <p>
     * Upon success, refreshes the special hours table to show the new rule.
     *
     * @param e The event triggered by the Save button.
     */
	@FXML
	public void onSaveSpecialDate(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        if (specialDatePicker.getValue() == null) {
	            setStatus("Pick a date first");
	            return;
	        }

	        String dateStr = specialDatePicker.getValue().toString(); // YYYY-MM-DD
	        String open = normalizeTime(specialOpenField.getText());
	        String close = normalizeTime(specialCloseField.getText());

	        if (!isValidHHmm(open) || !isValidHHmm(close)) {
	            setStatus("Wrong time format (HH:mm)");
	            return;
	        }

	        String cmd = "#SET_BRANCH_HOURS_BY_DATE " + dateStr + " " + open + " " + close;
	        System.out.println("DEBUG UI -> " + cmd);

	        ClientUI.chat.handleMessageFromClientUI(cmd);
	        String resp = ClientUI.chat.waitForMessage();

	        setStatus(resp != null ? resp : "Done");

	        loadSpecialOpeningHours();

	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Saving special date error");
	    }
	}
	
	/**
     * Deletes an existing special opening hour rule.
     * <p>
     * Requires a date to be selected in the DatePicker to identify which rule to remove.
     * <p>
     * Sends command: {@code #DELETE_OPENING_HOURS_SPECIAL <YYYY-MM-DD>}
     *
     * @param e The event triggered by the Delete button.
     */
	@FXML
	public void onDeleteSpecialDate(ActionEvent e) {
	    try {
	        if (ClientUI.chat == null) { setStatus("No connection to server"); return; }

	        if (specialDatePicker.getValue() == null) {
	            setStatus("Pick a date first");
	            return;
	        }

	        String dateStr = specialDatePicker.getValue().toString();

	        String cmd = "#DELETE_OPENING_HOURS_SPECIAL " + dateStr;
	        System.out.println("DEBUG UI -> " + cmd);

	        ClientUI.chat.handleMessageFromClientUI(cmd);
	        String resp = ClientUI.chat.waitForMessage();

	        setStatus(resp != null ? resp : "Done");

	        loadSpecialOpeningHours();

	    } catch (Exception ex) {
	        ex.printStackTrace();
	        setStatus("Delete special date error");
	    }
	}




}
