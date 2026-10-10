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
    private final Room roomB = new Room("B", "Beta", 4);
    private final User alice = new User("u1", "Alice");
    private final User bob = new User("u2", "Bob");
    private final User carol = new User("u3", "Carol");
    private final User dave = new User("u4", "Dave");

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

    @Test
    void promotionIsFcfsAndPromotesAtMostOne() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        Booking a = confirmed(svc.book(roomA, alice, new TimeInterval(600, 720)));
        svc.book(roomA, bob, new TimeInterval(600, 660));    // waiter #1 (earliest)
        svc.book(roomA, carol, new TimeInterval(660, 700));  // waiter #2
        svc.cancelBooking(a.id());
        assertEquals(1, svc.listBookings(roomA).size());
        assertEquals(bob, svc.listBookings(roomA).get(0).user());
        assertEquals(1, store.waitlistForRoom(roomA).size()); // carol still waiting
    }

    @Test
    void promotionSkipsWaitersWhoStillConflict() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        Booking a = confirmed(svc.book(roomA, alice, new TimeInterval(600, 660)));
        confirmed(svc.book(roomA, dave, new TimeInterval(700, 760)));
        svc.book(roomA, bob, new TimeInterval(630, 730));    // waiter #1, still overlaps Dave
        svc.book(roomA, carol, new TimeInterval(610, 650));  // waiter #2, fits once Alice leaves
        svc.cancelBooking(a.id());
        assertEquals(2, svc.listBookings(roomA).size());
        Booking promoted = svc.listBookings(roomA).stream()
                .filter(b -> b.user().equals(carol))
                .findFirst()
                .orElseThrow();
        assertEquals(new TimeInterval(610, 650), promoted.interval());
        assertEquals(1, store.waitlistForRoom(roomA).size()); // bob still waiting
    }

    @Test
    void cancellationDoesNotPromoteAcrossRooms() {
        InMemoryBookingStore store = new InMemoryBookingStore();
        BookingService svc = new BookingService(store);
        Booking a = confirmed(svc.book(roomA, alice, new TimeInterval(600, 660)));
        confirmed(svc.book(roomB, bob, new TimeInterval(600, 660)));
        svc.book(roomB, carol, new TimeInterval(600, 660));  // carol waitlisted in room B
        svc.cancelBooking(a.id());
        assertEquals(0, svc.listBookings(roomA).size());
        assertEquals(1, svc.listBookings(roomB).size());     // carol NOT promoted
        assertEquals(1, store.waitlistForRoom(roomB).size());
    }
}
