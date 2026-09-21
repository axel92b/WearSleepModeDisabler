package com.wearsleepmodedisabler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;

public class DailyScheduleTest {
    @Test
    public void beforeTargetUsesToday() {
        assertNext("2026-09-21T02:00:00Z", "Europe/Helsinki", 6, 0,
                "2026-09-21T03:00:00Z");
    }

    @Test
    public void afterTargetUsesTomorrow() {
        assertNext("2026-09-21T04:00:00Z", "Europe/Helsinki", 6, 0,
                "2026-09-22T03:00:00Z");
    }

    @Test
    public void exactlyAtTargetUsesTomorrow() {
        assertNext("2026-09-21T06:00:00Z", "UTC", 6, 0,
                "2026-09-22T06:00:00Z");
    }

    @Test
    public void midnightRollsOverTheYear() {
        assertNext("2026-12-31T23:59:59Z", "UTC", 0, 0,
                "2027-01-01T00:00:00Z");
    }

    @Test
    public void minuteSelectionIsPreserved() {
        assertNext("2026-09-21T05:30:00Z", "UTC", 6, 47,
                "2026-09-21T06:47:00Z");
    }

    @Test
    public void springGapShiftsTimeForward() {
        assertNext("2026-03-08T06:00:00Z", "America/New_York", 2, 30,
                "2026-03-08T07:30:00Z");
    }

    @Test
    public void fallOverlapUsesFirstOccurrence() {
        assertNext("2026-11-01T04:00:00Z", "America/New_York", 1, 30,
                "2026-11-01T05:30:00Z");
    }

    @Test
    public void fallOverlapDoesNotRunTwice() {
        assertNext("2026-11-01T05:45:00Z", "America/New_York", 1, 30,
                "2026-11-02T06:30:00Z");
    }

    @Test
    public void dailyRunStaysLocalAcrossSpringDst() {
        assertNext("2026-03-07T11:01:00Z", "America/New_York", 6, 0,
                "2026-03-08T10:00:00Z");
    }

    @Test(expected = java.time.DateTimeException.class)
    public void invalidTimeIsRejected() {
        DailySchedule.next(Instant.now(), ZoneId.of("UTC"), 24, 0);
    }

    private void assertNext(String now, String zone, int hour, int minute, String expected) {
        assertEquals(Instant.parse(expected),
                DailySchedule.next(Instant.parse(now), ZoneId.of(zone), hour, minute));
    }
}
