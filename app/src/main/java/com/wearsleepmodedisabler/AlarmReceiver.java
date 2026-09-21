package com.wearsleepmodedisabler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AlarmScheduler.ACTION_DISABLE.equals(intent.getAction())) {
            return;
        }
        AppPreferences preferences = new AppPreferences(context);
        long scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0);
        if (!preferences.isEnabled() || scheduledAt == 0
                || scheduledAt != preferences.scheduledAt()) {
            return;
        }
        if (System.currentTimeMillis() < scheduledAt) {
            new AlarmScheduler(context).synchronize(false);
            return;
        }

        preferences.setScheduledAt(0);
        SleepModeController.Result result = new SleepModeController(context).disable();
        preferences.recordAttempt(result);
        Log.i("AlarmReceiver", "Scheduled Sleep action: " + result.name());
        new AlarmScheduler(context).synchronize(true);
    }
}
