package com.wearsleepmodedisabler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.app.Activity;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.ScrollView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w227dp-h227dp-round")
public class BezelNavigationTest {
    private AppPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        context.createDeviceProtectedStorageContext()
                .getSharedPreferences("automation", Context.MODE_PRIVATE).edit().clear().commit();
        preferences = new AppPreferences(context);
    }

    @Test
    public void mainPageScrollsInBothDirectionsWithoutChangingSettings() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ScrollView scroll = layoutPage(activity);
            assertTrue(scroll.canScrollVertically(1));
            rotate(activity, -1);
            int down = scroll.getScrollY();
            assertTrue(down > 0);
            rotate(activity, 1);
            assertTrue(scroll.getScrollY() < down);
            assertTrue(preferences.isEnabled());
            assertEquals(6, preferences.hour());
        }
    }

    @Test
    public void setupScrollsEvenWhenAButtonHasFocus() {
        try (ActivityController<SetupActivity> controller =
                     Robolectric.buildActivity(SetupActivity.class).setup()) {
            SetupActivity activity = controller.get();
            ScrollView scroll = layoutPage(activity);
            View button = activity.findViewById(R.id.allow_dnd);
            button.setFocusableInTouchMode(true);
            assertTrue(button.requestFocus());
            scroll.scrollTo(0, 0);
            rotate(activity, -1);
            assertTrue(scroll.getScrollY() > 0);
        }
    }

    @Test
    public void scrollIsClampedAtBothEnds() {
        try (ActivityController<SetupActivity> controller =
                     Robolectric.buildActivity(SetupActivity.class).setup()) {
            SetupActivity activity = controller.get();
            ScrollView scroll = layoutPage(activity);
            rotate(activity, 100);
            assertEquals(0, scroll.getScrollY());
            rotate(activity, -1_000);
            int bottom = scroll.getChildAt(0).getHeight()
                    - (scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom());
            assertEquals(bottom, scroll.getScrollY());
            rotate(activity, -100);
            assertEquals(bottom, scroll.getScrollY());
            rotate(activity, 1_000);
            assertEquals(0, scroll.getScrollY());
        }
    }

    @Test
    public void zeroRotationDoesNotMoveThePage() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            ScrollView scroll = layoutPage(controller.get());
            rotate(controller.get(), 0);
            assertEquals(0, scroll.getScrollY());
        }
    }

    @Test
    public void unrelatedMotionDoesNotTriggerBezelScrolling() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            ScrollView scroll = layoutPage(activity);
            dispatch(activity, MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE, -1);
            dispatch(activity, MotionEvent.ACTION_MOVE, InputDevice.SOURCE_ROTARY_ENCODER, -1);
            assertEquals(0, scroll.getScrollY());
        }
    }

    @Test
    public void bezelDoesNotAdjustTimePickerValues() {
        try (ActivityController<TimeActivity> controller =
                     Robolectric.buildActivity(TimeActivity.class).setup()) {
            TimeActivity activity = controller.get();
            dispatch(activity, MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_ROTARY_ENCODER, -2);
            NumberPicker hour = activity.findViewById(R.id.hour_picker);
            NumberPicker minute = activity.findViewById(R.id.minute_picker);
            assertEquals(6, hour.getValue());
            assertEquals(0, minute.getValue());
            assertEquals(6, preferences.hour());
            assertEquals(0, preferences.minute());
        }
    }

    private ScrollView layoutPage(RotaryActivity activity) {
        ScrollView scroll = activity.findViewById(R.id.content_scroll);
        int size = Math.round(227 * activity.getResources().getDisplayMetrics().density);
        int spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
        scroll.measure(spec, spec);
        scroll.layout(0, 0, size, size);
        return scroll;
    }

    private void rotate(RotaryActivity activity, float amount) {
        assertTrue(dispatch(activity, MotionEvent.ACTION_SCROLL,
                InputDevice.SOURCE_ROTARY_ENCODER, amount));
    }

    private boolean dispatch(Activity activity, int action, int source, float amount) {
        MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.setAxisValue(MotionEvent.AXIS_SCROLL, amount);
        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(time, time, action, 1,
                new MotionEvent.PointerProperties[] {pointer},
                new MotionEvent.PointerCoords[] {coordinates},
                0, 0, 1, 1, 0, 0, source, 0);
        try {
            return activity.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }
}
