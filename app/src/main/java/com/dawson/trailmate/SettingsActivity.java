package com.dawson.trailmate;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFERENCES_NAME = "trailmate_preferences";
    public static final String KEY_USE_MILES = "use_miles";

    private RadioGroup distanceUnitRadioGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        distanceUnitRadioGroup =
                findViewById(R.id.distanceUnitRadioGroup);

        Button saveSettingsButton =
                findViewById(R.id.saveSettingsButton);

        Button backButton =
                findViewById(R.id.backButton);

        SharedPreferences preferences =
                getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);

        boolean useMiles =
                preferences.getBoolean(KEY_USE_MILES, true);

        if (useMiles) {
            distanceUnitRadioGroup.check(R.id.unitMilesRadioButton);
        } else {
            distanceUnitRadioGroup.check(R.id.unitKilometersRadioButton);
        }

        saveSettingsButton.setOnClickListener(view -> saveSettings());
        backButton.setOnClickListener(view -> finish());
    }

    private void saveSettings() {
        int selectedId = distanceUnitRadioGroup.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(
                    this,
                    "Choose a distance unit.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        boolean useMiles = selectedId == R.id.unitMilesRadioButton;

        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USE_MILES, useMiles)
                .apply();

        Toast.makeText(
                this,
                "Settings saved.",
                Toast.LENGTH_SHORT
        ).show();

        finish();
    }
}