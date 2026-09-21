package com.wearsleepmodedisabler;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

final class SleepModeController {
    static final String SETTING = "setting_bedtime_mode_running_state";
    private static final String TAG = "SleepModeController";

    enum Result {
        READY(R.string.ready),
        PERMISSION_REQUIRED(R.string.sleep_permission_required),
        DND_PERMISSION_REQUIRED(R.string.dnd_permission_required),
        UNSUPPORTED_DEVICE(R.string.unsupported_device),
        UNKNOWN_SETTING(R.string.unknown_setting),
        READ_FAILED(R.string.read_failed),
        WRITE_FAILED(R.string.write_failed),
        VERIFY_FAILED(R.string.verify_failed),
        DND_READ_FAILED(R.string.dnd_read_failed),
        DND_WRITE_FAILED(R.string.dnd_write_failed),
        DND_VERIFY_FAILED(R.string.dnd_verify_failed),
        BOTH_OFF(R.string.both_off),
        BOTH_ALREADY_OFF(R.string.both_already_off),
        // Preserve the meaning of persisted results from the Sleep-only version.
        SETTING_OFF(R.string.setting_off),
        ALREADY_OFF(R.string.already_off);

        final int message;

        Result(int message) {
            this.message = message;
        }
    }

    interface SettingsAccess {
        boolean isSamsungWatch();
        boolean canWrite();
        String read();
        boolean writeOff();
    }

    interface DndAccess {
        boolean canControl();
        int currentFilter();
        void turnOff();
    }

    private final SettingsAccess settings;
    private final DndAccess dnd;

    SleepModeController(Context context) {
        this(new SettingsAccess() {
            @Override
            public boolean isSamsungWatch() {
                return "samsung".equalsIgnoreCase(Build.MANUFACTURER)
                        && context.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_WATCH);
            }

            @Override
            public boolean canWrite() {
                return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                        == PackageManager.PERMISSION_GRANTED;
            }

            @Override
            public String read() {
                return Settings.Global.getString(context.getContentResolver(), SETTING);
            }

            @Override
            public boolean writeOff() {
                return Settings.Global.putString(context.getContentResolver(), SETTING, "0");
            }
        }, new DndAccess() {
            private final NotificationManager notifications =
                    context.getSystemService(NotificationManager.class);

            @Override
            public boolean canControl() {
                return notifications.isNotificationPolicyAccessGranted();
            }

            @Override
            public int currentFilter() {
                return notifications.getCurrentInterruptionFilter();
            }

            @Override
            public void turnOff() {
                notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        });
    }

    SleepModeController(SettingsAccess settings, DndAccess dnd) {
        this.settings = settings;
        this.dnd = dnd;
    }

    Result readiness() {
        if (!settings.isSamsungWatch()) {
            return Result.UNSUPPORTED_DEVICE;
        }
        if (!settings.canWrite()) {
            return Result.PERMISSION_REQUIRED;
        }
        if (!dnd.canControl()) {
            return Result.DND_PERMISSION_REQUIRED;
        }
        try {
            return isKnownState(settings.read()) ? Result.READY : Result.UNKNOWN_SETTING;
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot read Samsung Sleep setting", e);
            return Result.READ_FAILED;
        }
    }

    Result disable() {
        Result readiness = readiness();
        if (readiness != Result.READY) {
            return readiness;
        }
        String state;
        try {
            state = settings.read();
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot read Samsung Sleep setting", e);
            return Result.READ_FAILED;
        }
        if (!isKnownState(state)) {
            return Result.UNKNOWN_SETTING;
        }
        boolean sleepWasOn = "1".equals(state);
        if (sleepWasOn) {
            try {
                if (!settings.writeOff()) {
                    Log.e(TAG, "Samsung Sleep setting write was rejected");
                    return Result.WRITE_FAILED;
                }
                if (!"0".equals(settings.read())) {
                    Log.e(TAG, "Samsung Sleep setting did not stay off");
                    return Result.VERIFY_FAILED;
                }
            } catch (SecurityException | IllegalArgumentException e) {
                Log.e(TAG, "Cannot update Samsung Sleep setting", e);
                return Result.WRITE_FAILED;
            }
        }

        int filter;
        try {
            filter = dnd.currentFilter();
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Sleep setting is off, but DND cannot be read", e);
            return Result.DND_READ_FAILED;
        }
        if (filter < NotificationManager.INTERRUPTION_FILTER_ALL
                || filter > NotificationManager.INTERRUPTION_FILTER_ALARMS) {
            Log.e(TAG, "Sleep setting is off, but DND filter is unknown: " + filter);
            return Result.DND_READ_FAILED;
        }
        boolean dndWasOn = filter != NotificationManager.INTERRUPTION_FILTER_ALL;
        if (dndWasOn) {
            try {
                dnd.turnOff();
            } catch (SecurityException | IllegalArgumentException e) {
                Log.e(TAG, "Sleep setting is off, but DND could not be disabled", e);
                return Result.DND_WRITE_FAILED;
            }
            try {
                if (dnd.currentFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    Log.e(TAG, "Sleep setting is off, but DND remains active");
                    return Result.DND_VERIFY_FAILED;
                }
            } catch (SecurityException | IllegalArgumentException e) {
                Log.e(TAG, "Cannot verify DND after requesting off", e);
                return Result.DND_READ_FAILED;
            }
        }
        // Samsung's private Modes service still needs on-device verification.
        return sleepWasOn || dndWasOn ? Result.BOTH_OFF : Result.BOTH_ALREADY_OFF;
    }

    private static boolean isKnownState(String state) {
        return "0".equals(state) || "1".equals(state);
    }
}
