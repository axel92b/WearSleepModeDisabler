package com.wearsleepmodedisabler;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends RotaryActivity {
    private AppPreferences preferences;
    private Switch enabledSwitch;
    private Button timeButton;
    private TextView status;
    private boolean rendering;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = new AppPreferences(this);
        enabledSwitch = findViewById(R.id.enabled_switch);
        timeButton = findViewById(R.id.time_button);
        status = findViewById(R.id.status);
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!rendering) {
                preferences.setEnabled(checked);
                render(new AlarmScheduler(this).synchronize(true));
            }
        });
        timeButton.setOnClickListener(view -> startActivity(new Intent(this, TimeActivity.class)));
        findViewById(R.id.setup_button).setOnClickListener(
                view -> startActivity(new Intent(this, SetupActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Keep an already-due alarm: reopening the UI must not postpone it to tomorrow.
        render(new AlarmScheduler(this).synchronize(false));
    }

    private void render(int scheduleStatus) {
        rendering = true;
        enabledSwitch.setChecked(preferences.isEnabled());
        rendering = false;
        timeButton.setText(String.format(Locale.getDefault(), "%02d:%02d",
                preferences.hour(), preferences.minute()));
        timeButton.setContentDescription(getString(R.string.time_description, timeButton.getText()));
        if (scheduleStatus == R.string.scheduled) {
            String next = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(preferences.scheduledAt()));
            status.setText(getString(R.string.next_run, next));
        } else {
            status.setText(scheduleStatus);
        }
    }
}
