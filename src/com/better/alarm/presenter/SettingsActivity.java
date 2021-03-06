/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.better.alarm.presenter;

import javax.inject.Inject;

import roboguice.RoboGuice;
import roboguice.activity.RoboPreferenceActivity;
import roboguice.inject.InjectPreference;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;

import com.better.alarm.R;
import com.better.alarm.view.AlarmPreference;

/**
 * Settings for the Alarm Clock.
 */
public class SettingsActivity extends RoboPreferenceActivity {

    private static final int ALARM_STREAM_TYPE_BIT = 1 << AudioManager.STREAM_ALARM;

    private static final String KEY_ALARM_IN_SILENT_MODE = "alarm_in_silent_mode";
    public static final String KEY_ALARM_SNOOZE = "snooze_duration";
    public static final String KEY_VOLUME_BEHAVIOR = "volume_button_setting";
    static final String KEY_DEFAULT_RINGTONE = "default_ringtone";
    static final String KEY_AUTO_SILENCE = "auto_silence";
    static final String KEY_PREALARM_DURATION = "prealarm_duration";
    public static final String KEY_FADE_IN_TIME_SEC = "fade_in_time_sec";

    @Inject private Vibrator vibrator;

    @InjectPreference(KEY_ALARM_SNOOZE) private ListPreference snoozeListPref;
    @InjectPreference(KEY_ALARM_IN_SILENT_MODE) private CheckBoxPreference alarmInSilentModePref;
    @InjectPreference(KEY_AUTO_SILENCE) private ListPreference autoSilencePreference;
    @InjectPreference(KEY_PREALARM_DURATION) private ListPreference preAlarmDuration;
    @InjectPreference(KEY_FADE_IN_TIME_SEC) private ListPreference fadeInTime;
    @InjectPreference("theme") private ListPreference theme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        RoboGuice.getInjector(this).getInstance(DynamicThemeHandler.class).setThemeFor(this, SettingsActivity.class);
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        final AlarmPreference ringtone = (AlarmPreference) findPreference(KEY_DEFAULT_RINGTONE);
        Uri alert = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (alert != null) {
            ringtone.setAlert(alert);
        }
        ringtone.setChangeDefault();

        if (!vibrator.hasVibrator() && findPreference("vibrate") != null) {
            getPreferenceScreen().removePreference(findPreference("vibrate"));
        }

        findPreference("theme").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(
                                getBaseContext().getPackageName());
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                    }
                });
                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getActionBar().setDisplayHomeAsUpEnabled(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            goBack();
            return true;
        } else return false;
    }

    private void goBack() {
        // This is called when the Home (Up) button is pressed
        // in the Action Bar.
        Intent parentActivityIntent = new Intent(this, AlarmsListActivity.class);
        parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(parentActivityIntent);
        finish();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (KEY_ALARM_IN_SILENT_MODE.equals(preference.getKey())) {
            CheckBoxPreference pref = (CheckBoxPreference) preference;
            int ringerModeStreamTypes = Settings.System.getInt(getContentResolver(),
                    Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

            if (pref.isChecked()) {
                ringerModeStreamTypes &= ~ALARM_STREAM_TYPE_BIT;
            } else {
                ringerModeStreamTypes |= ALARM_STREAM_TYPE_BIT;
            }

            Settings.System.putInt(getContentResolver(), Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                    ringerModeStreamTypes);

            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void updateFadeInTimeSummary(String duration) {
        fadeInTime.setSummary(getString(R.string.fade_in_summary, Integer.parseInt(duration)));
    }

    private void updateAutoSnoozeSummary(String delay) {
        int i = Integer.parseInt(delay);
        if (i == -1) {
            autoSilencePreference.setSummary(R.string.auto_silence_never);
        } else {
            autoSilencePreference.setSummary(getString(R.string.auto_silence_summary, i));
        }
    }

    private void updatePreAlarmDurationSummary(String duration) {
        int i = Integer.parseInt(duration);
        if (i == -1) {
            preAlarmDuration.setSummary(getString(R.string.prealarm_off_summary));
        } else {
            preAlarmDuration.setSummary(getString(R.string.prealarm_summary, i));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        final int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);
        alarmInSilentModePref.setChecked((silentModeStreams & ALARM_STREAM_TYPE_BIT) == 0);

        snoozeListPref.setSummary(snoozeListPref.getEntry());
        snoozeListPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final int idx = snoozeListPref.findIndexOfValue((String) newValue);
                snoozeListPref.setSummary(snoozeListPref.getEntries()[idx]);
                return true;
            }
        });

        updateAutoSnoozeSummary(autoSilencePreference.getValue());
        autoSilencePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateAutoSnoozeSummary((String) newValue);
                return true;
            }
        });

        updatePreAlarmDurationSummary(preAlarmDuration.getValue());
        preAlarmDuration.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updatePreAlarmDurationSummary((String) newValue);
                return true;
            }
        });

        updateFadeInTimeSummary(fadeInTime.getValue());
        fadeInTime.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateFadeInTimeSummary((String) newValue);
                return true;
            }
        });

        theme.setSummary(theme.getEntry());
    }
}
