package com.sportslive.hq.app.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {

    /**
     * Parses an ISO-8601 UTC timestamp like "2026-07-05T19:45:00Z"
     * into epoch millis. Returns -1 if parsing fails.
     */
    public static long parseIsoToMillis(String iso) {
        if (iso == null || iso.isEmpty()) return -1;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = sdf.parse(iso);
            return date != null ? date.getTime() : -1;
        } catch (ParseException e) {
            return -1;
        }
    }

    /**
     * Formats a millisecond countdown duration into "DDd HH:MM:SS" or "HH:MM:SS".
     */
    public static String formatCountdown(long millisRemaining) {
        if (millisRemaining < 0) millisRemaining = 0;

        long totalSeconds = millisRemaining / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            return String.format(Locale.US, "%dd %02d:%02d:%02d", days, hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
