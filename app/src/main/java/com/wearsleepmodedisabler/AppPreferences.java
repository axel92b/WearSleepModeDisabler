package com.wearsleepmodedisabler;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalTime;

final class AppPreferences {
    private final SharedPreferences preferences;

    AppPreferences(Context context) {
        preferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences("automation", Context.MODE_PRIVATE);
    }

    boolean isEnabled() {
        return preferences.getBoolean("enabled", true);
    }

    void setEnabled(boolean enabled) {
        preferences.edit().putBoolean("enabled", enabled).apply();
    }

    int hour() {
        return preferences.getInt("hour", 6);
    }

    int minute() {
        return preferences.getInt("minute", 0);
    }

    void setTime(int hour, int minute) {
        LocalTime.of(hour, minute);
        preferences.edit().putInt("hour", hour).putInt("minute", minute).apply();
    }

    long scheduledAt() {
        return preferences.getLong("scheduled_at", 0);
    }

    void setScheduledAt(long time) {
        preferences.edit().putLong("scheduled_at", time).apply();
    }

    void recordAttempt(SleepModeController.Result result) {
        preferences.edit()
                .putString("last_result", result.name())
                .putLong("last_attempt", System.currentTimeMillis())
                .apply();
    }

    SleepModeController.Result lastResult() {
        String result = preferences.getString("last_result", null);
        return result == null ? null : SleepModeController.Result.valueOf(result);
    }

    long lastAttemptAt() {
        return preferences.getLong("last_attempt", 0);
    }
}
