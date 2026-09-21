package com.wearsleepmodedisabler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.time.Instant;
import java.time.ZoneId;

final class AlarmScheduler {
    static final String ACTION_DISABLE = "com.wearsleepmodedisabler.DISABLE_SLEEP";
    static final String EXTRA_SCHEDULED_AT = "scheduled_at";
    private final Context context;
    private final AppPreferences preferences;
    private final AlarmManager alarmManager;

    AlarmScheduler(Context context) {
        this.context = context;
        preferences = new AppPreferences(context);
        alarmManager = context.getSystemService(AlarmManager.class);
    }

    boolean canScheduleExactAlarms() {
        return Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms();
    }

    int synchronize(boolean replaceTime) {
        if (!preferences.isEnabled()) {
            cancel();
            return R.string.automation_off;
        }
        SleepModeController.Result readiness = new SleepModeController(context).readiness();
        if (readiness != SleepModeController.Result.READY) {
            cancel();
            return readiness.message;
        }
        if (!canScheduleExactAlarms()) {
            cancel();
            return R.string.alarm_permission_required;
        }

        long trigger = preferences.scheduledAt();
        if (replaceTime || trigger == 0) {
            trigger = DailySchedule.next(Instant.now(), ZoneId.systemDefault(),
                    preferences.hour(), preferences.minute()).toEpochMilli();
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, trigger, pendingIntent(trigger));
            preferences.setScheduledAt(trigger);
            return R.string.scheduled;
        } catch (SecurityException e) {
            Log.e("AlarmScheduler", "Exact alarm permission was denied", e);
            cancel();
            return R.string.alarm_permission_required;
        }
    }

    private PendingIntent pendingIntent(long trigger) {
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setAction(ACTION_DISABLE)
                .putExtra(EXTRA_SCHEDULED_AT, trigger);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void cancel() {
        alarmManager.cancel(pendingIntent(0));
        preferences.setScheduledAt(0);
    }
}
