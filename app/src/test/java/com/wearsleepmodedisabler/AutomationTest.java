package com.wearsleepmodedisabler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowBuild;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w227dp-h227dp-round")
public class AutomationTest {
    private Application context;
    private AppPreferences preferences;
    private AlarmScheduler scheduler;
    private ShadowAlarmManager alarms;
    private NotificationManager notifications;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.createDeviceProtectedStorageContext()
                .getSharedPreferences("automation", Context.MODE_PRIVATE).edit().clear().commit();
        preferences = new AppPreferences(context);
        scheduler = new AlarmScheduler(context);
        alarms = shadowOf(context.getSystemService(AlarmManager.class));
        alarms.setCanScheduleExactAlarms(true);
        ShadowBuild.setManufacturer("samsung");
        shadowOf(context.getPackageManager()).setSystemFeature(PackageManager.FEATURE_WATCH, true);
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        notifications = context.getSystemService(NotificationManager.class);
        shadowOf(notifications).setNotificationPolicyAccessGranted(true);
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        Settings.Global.putString(context.getContentResolver(), SleepModeController.SETTING, "1");
    }

    @Test
    public void defaultsAreEnabledAtSix() {
        assertTrue(preferences.isEnabled());
        assertEquals(6, preferences.hour());
        assertEquals(0, preferences.minute());
    }

    @Test
    public void targetApiRetainsGlobalDndControl() {
        assertEquals("Target 35+ cannot disable Samsung's global DND via this API",
                34, context.getApplicationInfo().targetSdkVersion);
    }

    @Test
    public void preferencesPersistInDeviceProtectedStorage() {
        preferences.setTime(7, 15);
        preferences.setEnabled(false);
        AppPreferences reloaded = new AppPreferences(context);
        assertEquals(7, reloaded.hour());
        assertEquals(15, reloaded.minute());
        assertFalse(reloaded.isEnabled());
        assertFalse(context.getSharedPreferences("automation", Context.MODE_PRIVATE)
                .contains("enabled"));
    }

    @Test
    public void schedulesOneWakeupAlarmAndReplacesIt() {
        assertEquals(R.string.scheduled, scheduler.synchronize(true));
        ShadowAlarmManager.ScheduledAlarm alarm = alarms.getNextScheduledAlarm();
        assertNotNull(alarm);
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type);
        assertTrue(alarm.allowWhileIdle);
        assertEquals(preferences.scheduledAt(), alarm.triggerAtTime);
        assertTrue(alarm.triggerAtTime > System.currentTimeMillis());
        assertEquals(6, Instant.ofEpochMilli(alarm.triggerAtTime)
                .atZone(ZoneId.systemDefault()).getHour());

        preferences.setTime(8, 37);
        scheduler.synchronize(true);
        assertEquals(1, alarms.getScheduledAlarms().size());
        assertNotEquals(alarm.triggerAtTime, preferences.scheduledAt());
        assertEquals(37, Instant.ofEpochMilli(preferences.scheduledAt())
                .atZone(ZoneId.systemDefault()).getMinute());
    }

    @Test
    public void disablingCancelsPendingAlarm() {
        scheduler.synchronize(true);
        preferences.setEnabled(false);
        assertEquals(R.string.automation_off, scheduler.synchronize(true));
        assertNull(alarms.getNextScheduledAlarm());
        assertEquals(0, preferences.scheduledAt());
    }

    @Test
    public void deniedExactAlarmPermissionCancelsSchedule() {
        scheduler.synchronize(true);
        alarms.setCanScheduleExactAlarms(false);
        assertEquals(R.string.alarm_permission_required, scheduler.synchronize(false));
        assertNull(alarms.getNextScheduledAlarm());
        assertEquals(0, preferences.scheduledAt());
    }

    @Test
    public void deniedSleepPermissionDoesNotSchedule() {
        shadowOf(context).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        assertEquals(R.string.sleep_permission_required, scheduler.synchronize(true));
        assertNull(alarms.getNextScheduledAlarm());
    }

    @Test
    public void deniedDndPermissionCancelsSchedule() {
        scheduler.synchronize(true);
        shadowOf(notifications).setNotificationPolicyAccessGranted(false);
        assertEquals(R.string.dnd_permission_required, scheduler.synchronize(false));
        assertNull(alarms.getNextScheduledAlarm());
        assertEquals(0, preferences.scheduledAt());
    }

    @Test
    public void unknownSamsungSettingDoesNotSchedule() {
        Settings.Global.putString(context.getContentResolver(), SleepModeController.SETTING, null);
        assertEquals(R.string.unknown_setting, scheduler.synchronize(true));
        assertNull(alarms.getNextScheduledAlarm());
    }

    @Test
    public void reopeningDoesNotPostponeAnAlarmThatIsDue() {
        long due = System.currentTimeMillis() - 1_000;
        preferences.setScheduledAt(due);
        scheduler.synchronize(false);
        assertEquals(due, alarms.getNextScheduledAlarm().triggerAtTime);
    }

    @Test
    public void alarmDisablesSleepAndDndAndSchedulesTheNextDay() {
        Intent intent = dueIntent();
        new AlarmReceiver().onReceive(context, intent);
        assertEquals("0", Settings.Global.getString(context.getContentResolver(),
                SleepModeController.SETTING));
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,
                notifications.getCurrentInterruptionFilter());
        assertEquals(SleepModeController.Result.BOTH_OFF, preferences.lastResult());
        assertTrue(preferences.scheduledAt() > System.currentTimeMillis());
        assertNotNull(alarms.getNextScheduledAlarm());
    }

    @Test
    public void duplicateDeliveryDoesNotDisableSleepAgain() {
        Intent intent = dueIntent();
        new AlarmReceiver().onReceive(context, intent);
        Settings.Global.putString(context.getContentResolver(), SleepModeController.SETTING, "1");
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        new AlarmReceiver().onReceive(context, intent);
        assertSleepStillOn();
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                notifications.getCurrentInterruptionFilter());
    }

    @Test
    public void staleOrDisabledDeliveryDoesNothing() {
        Intent oldIntent = dueIntent();
        preferences.setScheduledAt(System.currentTimeMillis() + 60_000);
        new AlarmReceiver().onReceive(context, oldIntent);
        assertSleepStillOn();
        Intent intent = dueIntent();
        preferences.setEnabled(false);
        new AlarmReceiver().onReceive(context, intent);
        assertSleepStillOn();
        assertNull(preferences.lastResult());
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                notifications.getCurrentInterruptionFilter());
    }

    @Test
    public void earlyDeliveryDoesNotDisableSleep() {
        long future = System.currentTimeMillis() + 60_000;
        preferences.setScheduledAt(future);
        new AlarmReceiver().onReceive(context, alarmIntent(future));
        assertSleepStillOn();
        assertNull(preferences.lastResult());
        assertEquals(future, preferences.scheduledAt());
    }

    @Test
    public void missingPermissionAtDeliveryIsRecorded() {
        Intent intent = dueIntent();
        shadowOf(context).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        new AlarmReceiver().onReceive(context, intent);
        assertSleepStillOn();
        assertEquals(SleepModeController.Result.PERMISSION_REQUIRED, preferences.lastResult());
        assertEquals(0, preferences.scheduledAt());
    }

    @Test
    public void missingDndPermissionAtDeliveryIsRecordedWithoutChangingSleep() {
        Intent intent = dueIntent();
        shadowOf(notifications).setNotificationPolicyAccessGranted(false);
        new AlarmReceiver().onReceive(context, intent);
        assertSleepStillOn();
        assertEquals(SleepModeController.Result.DND_PERMISSION_REQUIRED, preferences.lastResult());
        assertEquals(0, preferences.scheduledAt());
    }

    @Test
    public void bootAndPackageUpdateRestoreFutureAlarms() {
        for (String action : new String[] {
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED
        }) {
            preferences.setScheduledAt(System.currentTimeMillis() - 60_000);
            new RescheduleReceiver().onReceive(context, new Intent(action));
            assertTrue(preferences.scheduledAt() > System.currentTimeMillis());
            assertSleepStillOn();
        }
    }

    @Test
    public void timeZoneChangeRecomputesTheLocalTime() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            new RescheduleReceiver().onReceive(context, new Intent(Intent.ACTION_TIMEZONE_CHANGED));
            assertEquals(6, Instant.ofEpochMilli(preferences.scheduledAt())
                    .atZone(ZoneId.of("Pacific/Honolulu")).getHour());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void disabledAutomationStaysDisabledAfterReboot() {
        preferences.setEnabled(false);
        new RescheduleReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull(alarms.getNextScheduledAlarm());
        assertEquals(0, preferences.scheduledAt());
    }

    @Test
    public void mainScreenShowsDefaultsAndSwitchControlsScheduling() {
        try (ActivityController<MainActivity> activity =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            Switch toggle = activity.get().findViewById(R.id.enabled_switch);
            assertTrue(toggle.isChecked());
            TextView time = activity.get().findViewById(R.id.time_button);
            assertEquals("06:00", time.getText().toString());
            toggle.setChecked(false);
            assertFalse(preferences.isEnabled());
            assertNull(alarms.getNextScheduledAlarm());
            toggle.setChecked(true);
            assertTrue(preferences.isEnabled());
            assertNotNull(alarms.getNextScheduledAlarm());
        }
    }

    @Test
    public void pickerSavesSelectedTime() {
        try (ActivityController<TimeActivity> activity =
                     Robolectric.buildActivity(TimeActivity.class).setup()) {
            NumberPicker hour = activity.get().findViewById(R.id.hour_picker);
            NumberPicker minute = activity.get().findViewById(R.id.minute_picker);
            hour.setValue(9);
            minute.setValue(45);
            activity.get().findViewById(R.id.save_button).performClick();
            assertEquals(9, preferences.hour());
            assertEquals(45, preferences.minute());
            assertTrue(activity.get().isFinishing());
        }
    }

    @Test
    public void abandoningPickerDoesNotChangeTime() {
        try (ActivityController<TimeActivity> activity =
                     Robolectric.buildActivity(TimeActivity.class).setup()) {
            NumberPicker hour = activity.get().findViewById(R.id.hour_picker);
            hour.setValue(9);
            activity.get().finish();
            assertEquals(6, preferences.hour());
            assertEquals(0, preferences.minute());
        }
    }

    @Test
    public void manualTestWorksWithoutEnablingAutomation() {
        preferences.setEnabled(false);
        try (ActivityController<SetupActivity> activity =
                     Robolectric.buildActivity(SetupActivity.class).setup()) {
            activity.get().findViewById(R.id.test_button).performClick();
            assertEquals(SleepModeController.Result.BOTH_OFF, preferences.lastResult());
            assertEquals("0", Settings.Global.getString(context.getContentResolver(),
                    SleepModeController.SETTING));
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,
                    notifications.getCurrentInterruptionFilter());
            assertFalse(preferences.isEnabled());
            assertNull(alarms.getNextScheduledAlarm());
        }
    }

    @Test
    public void manualTestClearsDndLeftByThePreviousVersion() {
        Settings.Global.putString(context.getContentResolver(), SleepModeController.SETTING, "0");
        try (ActivityController<SetupActivity> activity =
                     Robolectric.buildActivity(SetupActivity.class).setup()) {
            activity.get().findViewById(R.id.test_button).performClick();
            assertEquals(SleepModeController.Result.BOTH_OFF, preferences.lastResult());
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,
                    notifications.getCurrentInterruptionFilter());
        }
    }

    @Test
    public void setupRequiresDndAccessBeforeTesting() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(false);
        try (ActivityController<SetupActivity> activity =
                     Robolectric.buildActivity(SetupActivity.class).setup()) {
            assertFalse(activity.get().findViewById(R.id.test_button).isEnabled());
            TextView status = activity.get().findViewById(R.id.permission_status);
            assertEquals(context.getString(R.string.dnd_permission_required),
                    status.getText().toString());
        }
    }

    @Test
    public void previousVersionResultDoesNotImplyDndWasDisabled() {
        preferences.recordAttempt(SleepModeController.Result.SETTING_OFF);
        try (ActivityController<SetupActivity> activity =
                     Robolectric.buildActivity(SetupActivity.class).setup()) {
            TextView result = activity.get().findViewById(R.id.test_result);
            assertTrue(result.getText().toString().contains(context.getString(R.string.setting_off)));
            assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    notifications.getCurrentInterruptionFilter());
        }
    }

    private Intent dueIntent() {
        long due = System.currentTimeMillis() - 1_000;
        preferences.setScheduledAt(due);
        return alarmIntent(due);
    }

    private Intent alarmIntent(long time) {
        return new Intent(context, AlarmReceiver.class)
                .setAction(AlarmScheduler.ACTION_DISABLE)
                .putExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, time);
    }

    private void assertSleepStillOn() {
        assertEquals("1", Settings.Global.getString(context.getContentResolver(),
                SleepModeController.SETTING));
    }
}
