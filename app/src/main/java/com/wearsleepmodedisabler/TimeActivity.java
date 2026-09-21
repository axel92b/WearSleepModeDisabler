package com.wearsleepmodedisabler;

import android.app.Activity;
import android.os.Bundle;
import android.widget.NumberPicker;

import java.util.Locale;

public final class TimeActivity extends Activity {
    private NumberPicker hour;
    private NumberPicker minute;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time);
        AppPreferences preferences = new AppPreferences(this);
        hour = findViewById(R.id.hour_picker);
        minute = findViewById(R.id.minute_picker);
        configure(hour, 23, savedInstanceState == null
                ? preferences.hour() : savedInstanceState.getInt("hour"));
        configure(minute, 59, savedInstanceState == null
                ? preferences.minute() : savedInstanceState.getInt("minute"));
        findViewById(R.id.save_button).setOnClickListener(view -> {
            preferences.setTime(hour.getValue(), minute.getValue());
            new AlarmScheduler(this).synchronize(true);
            finish();
        });
    }

    private void configure(NumberPicker picker, int max, int value) {
        picker.setMinValue(0);
        picker.setMaxValue(max);
        picker.setFormatter(number -> String.format(Locale.getDefault(), "%02d", number));
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("hour", hour.getValue());
        outState.putInt("minute", minute.getValue());
        super.onSaveInstanceState(outState);
    }
}
