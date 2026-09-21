package com.wearsleepmodedisabler;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public final class SetupActivity extends RotaryActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        findViewById(R.id.allow_alarms).setOnClickListener(view -> requestAlarms());
        findViewById(R.id.allow_dnd).setOnClickListener(view -> requestDndAccess());
        findViewById(R.id.test_button).setOnClickListener(view -> {
            SleepModeController.Result result = new SleepModeController(this).disable();
            new AppPreferences(this).recordAttempt(result);
            Log.i("SetupActivity", "Manual Sleep action: " + result.name());
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        new AlarmScheduler(this).synchronize(false);
        render();
    }

    private void render() {
        SleepModeController.Result readiness = new SleepModeController(this).readiness();
        TextView permissionStatus = findViewById(R.id.permission_status);
        permissionStatus.setText(readiness.message);
        boolean canControlDnd = getSystemService(NotificationManager.class)
                .isNotificationPolicyAccessGranted();
        findViewById(R.id.allow_dnd).setVisibility(canControlDnd ? View.GONE : View.VISIBLE);
        boolean canSchedule = new AlarmScheduler(this).canScheduleExactAlarms();
        TextView alarmStatus = findViewById(R.id.alarm_status);
        alarmStatus.setText(canSchedule ? R.string.alarms_allowed : R.string.alarm_permission_required);
        findViewById(R.id.allow_alarms).setVisibility(canSchedule ? View.GONE : View.VISIBLE);
        findViewById(R.id.test_button).setEnabled(readiness == SleepModeController.Result.READY);
        AppPreferences preferences = new AppPreferences(this);
        TextView result = findViewById(R.id.test_result);
        if (preferences.lastResult() == null) {
            result.setText(R.string.no_attempt);
        } else {
            String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(preferences.lastAttemptAt()));
            result.setText(getString(R.string.last_attempt, when,
                    getString(preferences.lastResult().message)));
        }
    }

    private void requestDndAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e("SetupActivity", "Watch has no accessible DND permission screen", e);
            TextView status = findViewById(R.id.permission_status);
            status.setText(R.string.dnd_adb_instructions);
        }
    }

    private void requestAlarms() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.e("SetupActivity", "Watch has no accessible alarm permission screen", e);
                TextView status = findViewById(R.id.alarm_status);
                status.setText(R.string.alarm_adb_instructions);
            }
        }
    }
}
