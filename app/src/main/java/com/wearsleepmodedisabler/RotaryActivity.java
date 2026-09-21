package com.wearsleepmodedisabler;

import android.app.Activity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.ScrollView;

abstract class RotaryActivity extends Activity {
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_SCROLL
                || !event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            return super.dispatchGenericMotionEvent(event);
        }

        // Route to the page even when a button owns focus; ScrollView handles scaling and bounds.
        ScrollView scroll = findViewById(R.id.content_scroll);
        scroll.onGenericMotionEvent(event);
        return true;
    }
}
