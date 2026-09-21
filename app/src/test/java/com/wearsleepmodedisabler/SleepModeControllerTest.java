package com.wearsleepmodedisabler;

import static org.junit.Assert.assertEquals;

import android.app.NotificationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SleepModeControllerTest {
    private FakeSettings settings;
    private FakeDnd dnd;
    private SleepModeController controller;

    @Before
    public void setUp() {
        settings = new FakeSettings();
        dnd = new FakeDnd();
        controller = new SleepModeController(settings, dnd);
    }

    @Test
    public void unsupportedDeviceIsNotWritten() {
        settings.samsungWatch = false;
        assertEquals(SleepModeController.Result.UNSUPPORTED_DEVICE, controller.disable());
        assertEquals(0, settings.writes);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void missingPermissionIsNotWritten() {
        settings.permission = false;
        assertEquals(SleepModeController.Result.PERMISSION_REQUIRED, controller.disable());
        assertEquals(0, settings.writes);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void missingSettingIsNotCreated() {
        settings.state = null;
        assertEquals(SleepModeController.Result.UNKNOWN_SETTING, controller.disable());
        assertEquals(0, settings.writes);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void unexpectedFirmwareValueIsNotWritten() {
        settings.state = "2";
        assertEquals(SleepModeController.Result.UNKNOWN_SETTING, controller.disable());
        assertEquals(0, settings.writes);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void alreadyOffDoesNotWrite() {
        settings.state = "0";
        assertEquals(SleepModeController.Result.BOTH_ALREADY_OFF, controller.disable());
        assertEquals(0, settings.writes);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void enabledSettingIsTurnedOffAndReadBack() {
        assertEquals(SleepModeController.Result.BOTH_OFF, controller.disable());
        assertEquals("0", settings.state);
        assertEquals(1, settings.writes);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void writeRejectionIsReported() {
        settings.acceptWrite = false;
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_NONE;
        assertEquals(SleepModeController.Result.WRITE_FAILED, controller.disable());
        assertEquals(0, dnd.writes);
    }

    @Test
    public void ignoredWriteIsNotReportedAsSuccess() {
        settings.persistWrite = false;
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_NONE;
        assertEquals(SleepModeController.Result.VERIFY_FAILED, controller.disable());
        assertEquals(0, dnd.writes);
    }

    @Test
    public void readPermissionFailureIsReported() {
        settings.failRead = true;
        assertEquals(SleepModeController.Result.READ_FAILED, controller.disable());
        assertEquals(0, settings.writes);
    }

    @Test
    public void writePermissionRevokedDuringAttemptIsReported() {
        settings.failWrite = true;
        assertEquals(SleepModeController.Result.WRITE_FAILED, controller.disable());
    }

    @Test
    public void missingDndPermissionPreventsPartialChanges() {
        dnd.permission = false;
        assertEquals(SleepModeController.Result.DND_PERMISSION_REQUIRED, controller.disable());
        assertEquals(0, settings.writes);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void allDndModesAreTurnedOffAlongsideSleep() {
        for (int filter : new int[] {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                NotificationManager.INTERRUPTION_FILTER_NONE,
                NotificationManager.INTERRUPTION_FILTER_ALARMS
        }) {
            settings.state = "1";
            dnd.filter = filter;
            assertEquals(SleepModeController.Result.BOTH_OFF, controller.disable());
            assertEquals("0", settings.state);
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dnd.filter);
        }
        assertEquals(3, dnd.writes);
    }

    @Test
    public void leftoverDndIsClearedWhenSleepIsAlreadyOff() {
        settings.state = "0";
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        assertEquals(SleepModeController.Result.BOTH_OFF, controller.disable());
        assertEquals(0, settings.writes);
        assertEquals(1, dnd.writes);
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dnd.filter);
    }

    @Test
    public void dndReadFailureIsReportedAsPartialResult() {
        dnd.failRead = true;
        assertEquals(SleepModeController.Result.DND_READ_FAILED, controller.disable());
        assertEquals("0", settings.state);
        assertEquals(0, dnd.writes);
    }

    @Test
    public void unknownDndStateIsNotReportedAsOff() {
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        assertEquals(SleepModeController.Result.DND_READ_FAILED, controller.disable());
        assertEquals(0, dnd.writes);
    }

    @Test
    public void dndPermissionRevokedDuringWriteIsReportedAsPartialResult() {
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        dnd.failWrite = true;
        assertEquals(SleepModeController.Result.DND_WRITE_FAILED, controller.disable());
        assertEquals("0", settings.state);
    }

    @Test
    public void ignoredDndRequestIsNotReportedAsSuccess() {
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        dnd.persistWrite = false;
        assertEquals(SleepModeController.Result.DND_VERIFY_FAILED, controller.disable());
        assertEquals("0", settings.state);
    }

    @Test
    public void dndVerificationReadFailureIsReported() {
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        dnd.failVerification = true;
        assertEquals(SleepModeController.Result.DND_READ_FAILED, controller.disable());
    }

    @Test
    public void retryAfterPartialFailureClearsDndWithoutRewritingSleep() {
        dnd.filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        dnd.failWrite = true;
        assertEquals(SleepModeController.Result.DND_WRITE_FAILED, controller.disable());
        dnd.failWrite = false;
        assertEquals(SleepModeController.Result.BOTH_OFF, controller.disable());
        assertEquals(1, settings.writes);
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dnd.filter);
    }

    private static final class FakeDnd implements SleepModeController.DndAccess {
        boolean permission = true;
        boolean failRead;
        boolean failWrite;
        boolean failVerification;
        boolean persistWrite = true;
        int filter = NotificationManager.INTERRUPTION_FILTER_ALL;
        int writes;

        @Override
        public boolean canControl() {
            return permission;
        }

        @Override
        public int currentFilter() {
            if (failRead || (failVerification && writes > 0)) {
                throw new SecurityException("DND read denied");
            }
            return filter;
        }

        @Override
        public void turnOff() {
            if (failWrite) {
                throw new SecurityException("DND write denied");
            }
            writes++;
            if (persistWrite) {
                filter = NotificationManager.INTERRUPTION_FILTER_ALL;
            }
        }
    }

    private static final class FakeSettings implements SleepModeController.SettingsAccess {
        boolean samsungWatch = true;
        boolean permission = true;
        boolean acceptWrite = true;
        boolean persistWrite = true;
        boolean failRead;
        boolean failWrite;
        String state = "1";
        int writes;

        @Override
        public boolean isSamsungWatch() {
            return samsungWatch;
        }

        @Override
        public boolean canWrite() {
            return permission;
        }

        @Override
        public String read() {
            if (failRead) {
                throw new SecurityException("Read denied");
            }
            return state;
        }

        @Override
        public boolean writeOff() {
            if (failWrite) {
                throw new SecurityException("Write denied");
            }
            writes++;
            if (acceptWrite && persistWrite) {
                state = "0";
            }
            return acceptWrite;
        }
    }
}
