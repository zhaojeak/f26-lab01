package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

class IsAvailableTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");

    private BookingService newService() {
        return new BookingService(new InMemoryBookingStore());
    }

    /**
     * An existing booking that starts before the request and runs into it is a
     * conflict, but the agent's isAvailable only checks bookings whose start
     * point falls inside the request interval and misses this case.
     */
    @Test
    void notAvailableWhenAnEarlierBookingRunsIntoTheRequest() {
        BookingService svc = newService();
        svc.book(roomA, alice, new TimeInterval(570, 630)); // starts before [600,660)
        assertFalse(svc.isAvailable(roomA, new TimeInterval(600, 660)));
    }

    /**
     * For each position relationship of an existing booking to the request
     * interval, isAvailable must agree with what a subsequent book would do:
     * true (Confirmed) iff the intervals do not overlap.
     */
    @Test
    void isAvailableAgreesWithBook() {
        assertAgrees(new TimeInterval(480, 540), new TimeInterval(600, 660), true);
        assertAgrees(new TimeInterval(540, 600), new TimeInterval(600, 660), true);
        assertAgrees(new TimeInterval(570, 630), new TimeInterval(600, 660), false);
        assertAgrees(new TimeInterval(540, 720), new TimeInterval(600, 660), false);
        assertAgrees(new TimeInterval(610, 640), new TimeInterval(600, 660), false);
        assertAgrees(new TimeInterval(630, 720), new TimeInterval(600, 660), false);
        assertAgrees(new TimeInterval(600, 660), new TimeInterval(600, 660), false);
        assertAgrees(new TimeInterval(660, 720), new TimeInterval(600, 660), true);
        assertAgrees(new TimeInterval(700, 760), new TimeInterval(600, 660), true);
    }

    private void assertAgrees(TimeInterval existing, TimeInterval query, boolean available) {
        BookingService svc = newService();
        svc.book(roomA, alice, existing);
        if (available) {
            assertTrue(svc.isAvailable(roomA, query),
                    "expected available for E=" + existing + " Q=" + query);
        } else {
            assertFalse(svc.isAvailable(roomA, query),
                    "expected NOT available for E=" + existing + " Q=" + query);
        }
    }
}
