package com.wearsleepmodedisabler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class DailySchedule {
    private DailySchedule() {}

    static Instant next(Instant now, ZoneId zone, int hour, int minute) {
        LocalTime time = LocalTime.of(hour, minute);
        LocalDate date = now.atZone(zone).toLocalDate();
        ZonedDateTime candidate = date.atTime(time).atZone(zone);
        if (!candidate.toInstant().isAfter(now)) {
            candidate = date.plusDays(1).atTime(time).atZone(zone);
        }
        // atZone shifts nonexistent times forward and uses the first offset in an overlap.
        return candidate.toInstant();
    }
}
