package methods;

public class CommonMethods {

	
		public static void sendSMSMock(StringBuilder sb) {
			
			System.out.println("[MOCK SMS] "+sb.toString());
		}
import entities.Reservation;

public class CommonMethods {
	
	/**
	 * Observer interface for waiting list entries that want to be notified
	 * when reservations become available due to cancellations.
	 */
	public interface CancellationObserver {
		void onReservationCancelled(Reservation cancelledReservation);
	}
	
	/**
	 * Mock send sms method to simulate the sending of 
	 * some methods like register, reservadafsdas waiting lists
	 **/
	public static void sendSMSMock(StringBuilder sb) {
		System.out.println("[SMS MOCK]"+sb.toString());
	} 

}
