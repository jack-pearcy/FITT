package com.fitt.workouts.dto;

import java.time.Duration;
import java.time.Instant;

// The bound on a time a device reports for something that already happened: not
// before the year 2000, and at most 24 hours after the server's clock. The day
// of slack keeps a phone with a slightly wrong clock working, and the lower
// bound keeps a nonsense year out of the DATETIME column.
// A time outside the bound is dropped, not refused. The device would send the
// same time on every retry, so a 400 would never clear: the set would never be
// stored and the session could never be finished.
public final class DeviceTimes {

    static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
    static final Duration AHEAD = Duration.ofHours(24);

    private DeviceTimes() {}

    // The time itself when it is inside the bound, and null when it is missing
    // or outside.
    public static Instant orNullWhenOutOfBounds(Instant fromDevice, Instant now) {
        if (fromDevice == null || fromDevice.isBefore(EARLIEST) || fromDevice.isAfter(now.plus(AHEAD))) {
            return null;
        }
        return fromDevice;
    }
}
