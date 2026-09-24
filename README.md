# Sleep Off for Galaxy Watch

A small standalone Wear OS app with an **Auto sleep off** switch and a daily,
24-hour time picker. Turns off manually enabled sleep mode. 
Defaults: **enabled, 06:00**, in the watch's local time.
Each run turns off **both Sleep mode and Do Not Disturb (DND)**.
The scheduled action is silent; no phone app, internet, continuous service, sound,
or vibration is used.

> **Samsung watches only.** This app is designed exclusively for Samsung Galaxy
> Watch devices running Wear OS (One UI Watch). It relies on Samsung-specific
> Sleep mode settings and will not work on Wear OS watches from other
> manufacturers (e.g., Google Pixel Watch, Fossil, TicWatch). On unsupported
> devices, the app refuses to act rather than reporting a false success.

## Bezel navigation

Rotate the physical bezel (Watch 6 Classic), or use the enabled touch bezel
(Watch 6), to scroll the main and setup screens. Clockwise scrolls down;
counterclockwise scrolls up. Touch scrolling still works.

The time picker remains touch-only. No additional permissions are required
for bezel scrolling.

## Important: Samsung / One UI 8 compatibility

Samsung does not provide a public API to disable its Sleep mode. This app uses
the undocumented global setting `setting_bedtime_mode_running_state`, with
`WRITE_SECURE_SETTINGS` granted once through ADB (no root). This setting is used
by the community [DND/Bedtime Sync project](https://github.com/Silleellie/dnd-bedtime-sync)
([integration reference](https://github.com/Silleellie/dnd-bedtime-sync/blob/master/wear/src/main/java/it/silleellie/dndsync/DNDSyncListenerService.java)).
This app is an independent implementation, not a copy of that app.

**Sleep mode exit has been reported working on Galaxy Watch 6 / One UI 8.**
The original Sleep-only action left DND enabled; version 1.1 explicitly turns
DND off as well. This combined action still needs an on-watch check. Reading back
the Sleep setting alone does not prove Samsung's Modes service acted on it.
Firmware updates can break this integration.

The app only changes an existing, recognized Samsung Sleep setting from `1` to
`0`. It refuses unknown devices/settings rather than creating a fake setting
and reporting success. Once Sleep is off, it uses Android's
`NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL)` to disable
DND and checks the current filter. It does not write `zen_mode` directly:
Android's notification service can revert a direct setting write.

**DND is turned off even if Sleep was already off**, including separately enabled
DND. This also lets Test now retry after a partial failure. If the Sleep setting
cannot be read/changed, the app leaves DND alone. Both permissions must be granted
before scheduling. No power-saving or phone settings are changed directly, but
Samsung's phone/watch mode synchronization may propagate mode changes.

This sideloaded app deliberately targets **API 34**, while compiling with API 35.
For apps targeting API 35+, Android's DND API changes only the app's own automatic
rule, not Samsung's global DND state
([Android API documentation](https://developer.android.com/reference/android/app/NotificationManager#setInterruptionFilter(int))).
Do not raise `targetSdk` without replacing and hardware-testing this integration.
This compatibility choice is not suitable for a new Google Play submission
requiring a newer target API.

## Build

Open the project in Android Studio, or use JDK 17+ (JDK 21 is also supported)
and an Android SDK with platform 35:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk" # macOS; adjust if needed
./gradlew :app:assembleDebug
```

Alternatively set `sdk.dir` in your untracked `local.properties`. The APK is
`app/build/outputs/apk/debug/app-debug.apk`. The app supports API 30+ and declares
the watch hardware requirement.

## Install and grant permissions (once)

1. On the watch, enable Developer options by tapping **Software version** under
   **Settings > About watch > Software information** repeatedly.
2. Enable **ADB debugging** and **Wireless debugging**. Keep the watch and
   computer on the same Wi-Fi network.
3. Choose **Pair new device** and pair using its address, pairing port and code.
   Then connect using the address and **connection port** on the main wireless
   debugging screen; this is usually different from the pairing port:

   ```sh
   adb pair WATCH_IP:PAIRING_PORT
   adb connect WATCH_IP:CONNECTION_PORT
   adb devices
   ```

4. Install and grant Sleep and DND control:

   ```sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell pm grant com.wearsleepmodedisabler android.permission.WRITE_SECURE_SETTINGS
   adb shell cmd notification allow_dnd com.wearsleepmodedisabler
   ```

5. Open **Sleep Off** on the watch. Under **Setup & test**, tap **Allow alarms**,
   and allow precise alarms if prompted. If Samsung does not expose that screen,
   or access stays **Not allowed**, grant the app's UID access:

   ```sh
   adb shell cmd appops set --uid com.wearsleepmodedisabler SCHEDULE_EXACT_ALARM allow
   ```

   The `--uid` flag matters: on One UI 8, a UID-level `deny` can override a
   package-level `allow`. Inspect both with:

   ```sh
   adb shell cmd appops get com.wearsleepmodedisabler SCHEDULE_EXACT_ALARM
   ```

   `Uid mode: SCHEDULE_EXACT_ALARM: allow` confirms the UID denial is cleared.
   Reopen Sleep Off after applying the command so it refreshes and schedules
   the next run. No APK reinstall is needed for this permission fix.

   DND access can also be granted with **Allow DND access** if the watch exposes
   that system screen. Reopen the app after granting permissions. The main screen should show the
   next scheduled time, not a setup warning. The enabled switch alone does not
   mean the required permissions have been granted.

6. After completing the test below, turn off ADB and wireless debugging to avoid
   unnecessary battery use. Neither connection is needed for daily operation.

If several devices are connected, add `-s WATCH_IP:CONNECTION_PORT` immediately
after `adb` for device-specific commands.

### Updating from version 1.0

Install with `adb install -r` as above to preserve your schedule and existing
permissions, then run the **new** `cmd notification allow_dnd` command and reopen
the app. The previous Sleep-only result remains visible in the last-attempt
history until a new attempt runs; it is not reinterpreted as a DND success.

## Required on-watch check

1. Manually enable Samsung **Sleep** mode from the watch's Modes/quick panel.
2. Open **Sleep Off > Setup & test > Test now**. This immediately attempts to
   turn Sleep mode and DND off, even when the automation switch is off.
3. Verify that the actual Sleep icon and sleep screen disappear, **DND is off**,
   and normal watch behavior returns. Do not treat a setting write alone as
   confirmation. If Sleep is already off but DND remains, Test now can still
   disable DND.
4. Set the daily time a few minutes ahead, leave automation enabled, enable
   Sleep mode again, and exit the app. Confirm both modes turn off at the chosen
   time with the screen off. Then restore your preferred time.

If the setting is missing, toggle Sleep mode manually once and reopen the app.
If it remains missing, or the setting changes but the actual mode does not,
**this firmware is not supported by this integration**. Turn automation off;
do not rely on it. Useful diagnostics while manually toggling Sleep on/off:

```sh
adb shell settings get global setting_bedtime_mode_running_state
adb shell settings get global zen_mode
adb logcat -s SleepModeController AlarmScheduler AlarmReceiver SetupActivity
```

Watch/phone **Sync modes**, **Sync Do not disturb**, or another schedule may re-enable
Sleep or DND.
Disable conflicting sync/schedules if you want this app to control the watch
independently. This app makes one attempt per day, not a continuous override.

## Scheduling behavior

- The switch and selected time persist locally, including across reboots.
- Changing the time or enabling the switch schedules the next occurrence; a
  time already passed today runs tomorrow. Disabling cancels the pending alarm.
- Uses `AlarmManager.setExactAndAllowWhileIdle` with a wakeup alarm so the app
  does not need to stay open. Android/OEM restrictions can still affect delivery.
- Restores the next future alarm after reboot (including before first unlock),
  app updates, clock changes, time-zone changes, and exact-alarm permission grants.
- Uses local calendar days, not a repeating 24-hour interval. At a spring DST
  gap the time shifts forward by the gap; at a fall overlap it uses the first
  occurrence only.
- The watch cannot run this while powered off. A reboot after the chosen time
  schedules tomorrow; there is no boot-time catch-up. Force-stopping the app
  cancels Android alarms until it is opened again. Reopening re-arms its saved
  deadline, which can immediately run an overdue action.
- Open the app once after installing or granting the ADB permission. Revoking
  exact-alarm access cancels Android's alarms; grant access again and reopen.
- Setup shows the last attempt and any Sleep or DND write/verification failure.
  The app requests no notification permission and does not create error sounds
  or vibrations.

## Development checks

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Tests cover local-time scheduling, DST, permissions, setting validation, alarm
cancellation/rescheduling, stale deliveries, persistence, and the settings UI.
Rotary-input tests cover page scroll direction/bounds and focused controls.
Unit tests cannot prove that Samsung's private Modes service honors the setting;
that requires the physical-watch check above.
