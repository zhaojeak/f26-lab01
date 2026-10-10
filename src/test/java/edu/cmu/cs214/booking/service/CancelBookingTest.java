package edu.cmu.cs214.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

class CancelBookingTest {

    private final Room roomA = new Room("A", "Alpha", 10);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");

    private Booking confirmed(BookingResult r) {
        return assertInstanceOf(BookingResult.Confirmed.class, r).booking();
    }

    @Test
    void cancellingRemovesTheBooking() {
        BookingService svc = new BookingService(new InMemoryBookingStore());
        Booking a = confirmed(svc.book(roomA, alice, new TimeInterval(600, 660)));
        svc.cancelBooking(a.id());
        assertEquals(0, svc.listBookings(roomA).size());
    }

    @Test
    void cancellingUnknownIdDoesNothing() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        svc.book(roomA, alice, new TimeInterval(600, 660));
        svc.book(roomA, bob, new TimeInterval(630, 700)); // waitlisted
        svc.cancelBooking("no-such-id");
        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(1, store.waitlistForRoom(roomA).size());
    }

    @Test
    void cancellingPromotesTheWaitlistedUser() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        Booking a = confirmed(svc.book(roomA, alice, new TimeInterval(600, 660)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(630, 700)));
        svc.cancelBooking(a.id());
        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(bob, svc.listBookings(roomA).get(0).user());
        assertEquals(new TimeInterval(630, 700), svc.listBookings(roomA).get(0).interval());
        assertEquals(0, store.waitlistForRoom(roomA).size());
    }
}
