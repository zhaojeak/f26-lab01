package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.domain.WaitlistEntry;
import edu.cmu.cs214.booking.repo.BookingStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates bookings and the waitlist. Enforces the core invariant: a room
 * never holds two confirmed bookings whose intervals overlap. Persistence is
 * delegated to a {@link BookingStore}.
 */
public class BookingService {

    private final BookingStore store;
    private int nextBookingSeq = 1;
    private int nextWaitlistSeq = 1;

    public BookingService(BookingStore store) {
        this.store = store;
    }

    /**
     * Attempts to book {@code room} for {@code user} over {@code interval}. If the
     * room is free over that interval the booking is confirmed; otherwise the user
     * is placed on the room's waitlist.
     */
    public BookingResult book(Room room, User user, TimeInterval interval) {
        for (Booking existing : store.bookingsForRoom(room)) {
            if (existing.interval().overlaps(interval)) {
                int position = store.waitlistForRoom(room).size() + 1;
                int seq = nextWaitlistSeq++;
                store.addWaitlistEntry(new WaitlistEntry("w" + seq, room, user, interval, seq));
                return new BookingResult.Waitlisted(position);
            }
        }
        Booking booking = new Booking("b" + nextBookingSeq++, room, user, interval);
        store.addBooking(booking);
        return new BookingResult.Confirmed(booking);
    }


    /**
     * Reports whether {@code room} is free over {@code interval}, so callers can
     * check availability before attempting to book.
     */
    public boolean isAvailable(Room room, TimeInterval interval) {
        for (Booking b : store.bookingsForRoom(room)) {
            if (b.interval().start() >= interval.start() && b.interval().start() < interval.end()) {
                return false;
            }
        }
        return true;
    }

    /** Returns the confirmed bookings for {@code room}. */
    public List<Booking> listBookings(Room room) {
        return store.bookingsForRoom(room);
    }

    /**
     * Cancels the confirmed booking with the given id, freeing its time slot, then
     * promotes at most one waiting user from that room's waitlist into the freed
     * slot. Promotion is first-come first-served by arrival sequence and never
     * creates an overlap with the remaining confirmed bookings. Unknown ids are
     * ignored: nothing happens and no waitlist user is promoted.
     */
    public void cancelBooking(String bookingId) {
        Optional<Booking> cancelled = store.findBooking(bookingId);
        if (cancelled.isEmpty()) {
            return;
        }
        Room room = cancelled.get().room();
        store.removeBooking(bookingId);

        List<WaitlistEntry> waiters = store.waitlistForRoom(room).stream()
                .sorted(Comparator.comparingInt(WaitlistEntry::seq))
                .toList();
        for (WaitlistEntry waiter : waiters) {
            boolean fits = store.bookingsForRoom(room).stream()
                    .noneMatch(b -> b.interval().overlaps(waiter.interval()));
            if (fits) {
                store.removeWaitlistEntry(waiter.id());
                Booking promoted = new Booking("b" + nextBookingSeq++, room, waiter.user(), waiter.interval());
                store.addBooking(promoted);
                return;
            }
        }
    }
}
