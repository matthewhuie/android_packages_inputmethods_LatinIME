/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethodcommon.InputMethodSettingsFragment;

public final class SettingsFragment extends InputMethodSettingsFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private PreferenceScreen mKeypressVibrationDurationSettingsPref;
    private PreferenceScreen mKeypressSoundVolumeSettingsPref;
    private ListPreference mVoicePreference;
    private ListPreference mShowCorrectionSuggestionsPreference;
    private ListPreference mAutoCorrectionThresholdPreference;
    private ListPreference mKeyPreviewPopupDismissDelay;
    // Use bigrams to predict the next word when there is no input for it yet
    private CheckBoxPreference mBigramPrediction;
    private Preference mDebugSettingsPreference;

    private void setPreferenceEnabled(final String preferenceKey, final boolean enabled) {
        final Preference preference = findPreference(preferenceKey);
        if (preference != null) {
            preference.setEnabled(enabled);
        }
    }

    private void ensureConsistencyOfAutoCorrectionSettings() {
        final String autoCorrectionOff = getResources().getString(
                R.string.auto_correction_threshold_mode_index_off);
        final String currentSetting = mAutoCorrectionThresholdPreference.getValue();
        mBigramPrediction.setEnabled(!currentSetting.equals(autoCorrectionOff));
    }

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        addPreferencesFromResource(R.xml.prefs);

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, the
        // {@link SubtypeLocale} class may not have been initialized. It is safe to call
        // {@link SubtypeLocale#init(Context)} multiple times.
        SubtypeLocale.init(context);
        mVoicePreference = (ListPreference) findPreference(Settings.PREF_VOICE_MODE);
        mShowCorrectionSuggestionsPreference =
                (ListPreference) findPreference(Settings.PREF_SHOW_SUGGESTIONS_SETTING);
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mAutoCorrectionThresholdPreference =
                (ListPreference) findPreference(Settings.PREF_AUTO_CORRECTION_THRESHOLD);
        mBigramPrediction = (CheckBoxPreference) findPreference(Settings.PREF_BIGRAM_PREDICTIONS);
        ensureConsistencyOfAutoCorrectionSettings();

        final PreferenceGroup generalSettings =
                (PreferenceGroup) findPreference(Settings.PREF_GENERAL_SETTINGS);
        final PreferenceGroup textCorrectionGroup =
                (PreferenceGroup) findPreference(Settings.PREF_CORRECTION_SETTINGS);
        final PreferenceGroup gestureTypingSettings =
                (PreferenceGroup) findPreference(Settings.PREF_GESTURE_SETTINGS);
        final PreferenceGroup miscSettings =
                (PreferenceGroup) findPreference(Settings.PREF_MISC_SETTINGS);

        mDebugSettingsPreference = findPreference(Settings.PREF_DEBUG_SETTINGS);
        if (mDebugSettingsPreference != null) {
            if (ProductionFlag.IS_INTERNAL) {
                final Intent debugSettingsIntent = new Intent(Intent.ACTION_MAIN);
                debugSettingsIntent.setClassName(
                        context.getPackageName(), DebugSettingsActivity.class.getName());
                mDebugSettingsPreference.setIntent(debugSettingsIntent);
            } else {
                miscSettings.removePreference(mDebugSettingsPreference);
            }
        }

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            generalSettings.removePreference(mVoicePreference);
        }

        final PreferenceGroup advancedSettings =
                (PreferenceGroup) findPreference(Settings.PREF_ADVANCED_SETTINGS);
        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            generalSettings.removePreference(findPreference(Settings.PREF_VIBRATE_ON));
            if (null != advancedSettings) { // Theoretically advancedSettings cannot be null
                advancedSettings.removePreference(
                        findPreference(Settings.PREF_VIBRATION_DURATION_SETTINGS));
            }
        }

        final boolean showKeyPreviewPopupOption = res.getBoolean(
                R.bool.config_enable_show_popup_on_keypress_option);
        mKeyPreviewPopupDismissDelay =
                (ListPreference) findPreference(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        if (!showKeyPreviewPopupOption) {
            generalSettings.removePreference(findPreference(Settings.PREF_POPUP_ON));
            if (null != advancedSettings) { // Theoretically advancedSettings cannot be null
                advancedSettings.removePreference(mKeyPreviewPopupDismissDelay);
            }
        } else {
            final String[] entries = new String[] {
                    res.getString(R.string.key_preview_popup_dismiss_no_delay),
                    res.getString(R.string.key_preview_popup_dismiss_default_delay),
            };
            final String popupDismissDelayDefaultValue = Integer.toString(res.getInteger(
                    R.integer.config_key_preview_linger_timeout));
            mKeyPreviewPopupDismissDelay.setEntries(entries);
            mKeyPreviewPopupDismissDelay.setEntryValues(
                    new String[] { "0", popupDismissDelayDefaultValue });
            if (null == mKeyPreviewPopupDismissDelay.getValue()) {
                mKeyPreviewPopupDismissDelay.setValue(popupDismissDelayDefaultValue);
            }
            mKeyPreviewPopupDismissDelay.setEnabled(
                    SettingsValues.isKeyPreviewPopupEnabled(prefs, res));
        }

        setPreferenceEnabled(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST,
                SettingsValues.showsLanguageSwitchKey(prefs));

        final PreferenceScreen dictionaryLink =
                (PreferenceScreen) findPreference(Settings.PREF_CONFIGURE_DICTIONARIES_KEY);
        final Intent intent = dictionaryLink.getIntent();

        final int number = context.getPackageManager().queryIntentActivities(intent, 0).size();
        // TODO: The experimental version is not supported by the Dictionary Pack Service yet
        if (ProductionFlag.IS_EXPERIMENTAL || 0 >= number) {
            textCorrectionGroup.removePreference(dictionaryLink);
        }

        final boolean gestureInputEnabledByBuildConfig = res.getBoolean(
                R.bool.config_gesture_input_enabled_by_build_config);
        if (!gestureInputEnabledByBuildConfig) {
            getPreferenceScreen().removePreference(gestureTypingSettings);
        }

        mKeypressVibrationDurationSettingsPref =
                (PreferenceScreen) findPreference(Settings.PREF_VIBRATION_DURATION_SETTINGS);
        if (mKeypressVibrationDurationSettingsPref != null) {
            mKeypressVibrationDurationSettingsPref.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference arg0) {
                            showKeypressVibrationDurationSettingsDialog();
                            return true;
                        }
                    });
            mKeypressVibrationDurationSettingsPref.setSummary(
                    res.getString(R.string.settings_keypress_vibration_duration,
                            SettingsValues.getCurrentVibrationDuration(prefs, res)));
        }

        mKeypressSoundVolumeSettingsPref =
                (PreferenceScreen) findPreference(Settings.PREF_KEYPRESS_SOUND_VOLUME);
        if (mKeypressSoundVolumeSettingsPref != null) {
            mKeypressSoundVolumeSettingsPref.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference arg0) {
                            showKeypressSoundVolumeSettingDialog();
                            return true;
                        }
                    });
            mKeypressSoundVolumeSettingsPref.setSummary(String.valueOf(
                    getCurrentKeyPressSoundVolumePercent(prefs, res)));
        }
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, res);
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean isShortcutImeEnabled = SubtypeSwitcher.getInstance().isShortcutImeEnabled();
        if (isShortcutImeEnabled) {
            updateVoiceModeSummary();
        } else {
            getPreferenceScreen().removePreference(mVoicePreference);
        }
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
        updateCustomInputStylesSummary();
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        (new BackupManager(getActivity())).dataChanged();
        if (key.equals(Settings.PREF_POPUP_ON)) {
            setPreferenceEnabled(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                    prefs.getBoolean(Settings.PREF_POPUP_ON, true));
        } else if (key.equals(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY)) {
            setPreferenceEnabled(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST,
                    SettingsValues.showsLanguageSwitchKey(prefs));
        } else if (key.equals(Settings.PREF_GESTURE_INPUT)) {
            final boolean gestureInputEnabledByConfig = getResources().getBoolean(
                    R.bool.config_gesture_input_enabled_by_build_config);
            if (gestureInputEnabledByConfig) {
                final boolean gestureInputEnabledByUser = prefs.getBoolean(
                        Settings.PREF_GESTURE_INPUT, true);
                setPreferenceEnabled(Settings.PREF_GESTURE_PREVIEW_TRAIL,
                        gestureInputEnabledByUser);
                setPreferenceEnabled(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT,
                        gestureInputEnabledByUser);
            }
        }
        ensureConsistencyOfAutoCorrectionSettings();
        updateVoiceModeSummary();
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, getResources());
    }

    private void updateShowCorrectionSuggestionsSummary() {
        mShowCorrectionSuggestionsPreference.setSummary(
                getResources().getStringArray(R.array.prefs_suggestion_visibilities)
                [mShowCorrectionSuggestionsPreference.findIndexOfValue(
                        mShowCorrectionSuggestionsPreference.getValue())]);
    }

    private void updateCustomInputStylesSummary() {
        final PreferenceScreen customInputStyles =
                (PreferenceScreen)findPreference(Settings.PREF_CUSTOM_INPUT_STYLES);
        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        final Resources res = getResources();
        final String prefSubtype = SettingsValues.getPrefAdditionalSubtypes(prefs, res);
        final InputMethodSubtype[] subtypes =
                AdditionalSubtype.createAdditionalSubtypesArray(prefSubtype);
        final StringBuilder styles = new StringBuilder();
        for (final InputMethodSubtype subtype : subtypes) {
            if (styles.length() > 0) styles.append(", ");
            styles.append(SubtypeLocale.getSubtypeDisplayName(subtype, res));
        }
        customInputStyles.setSummary(styles);
    }

    private void updateKeyPreviewPopupDelaySummary() {
        final ListPreference lp = mKeyPreviewPopupDismissDelay;
        final CharSequence[] entries = lp.getEntries();
        if (entries == null || entries.length <= 0) return;
        lp.setSummary(entries[lp.findIndexOfValue(lp.getValue())]);
    }

    private void updateVoiceModeSummary() {
        mVoicePreference.setSummary(
                getResources().getStringArray(R.array.voice_input_modes_summary)
                        [mVoicePreference.findIndexOfValue(mVoicePreference.getValue())]);
    }

    private void refreshEnablingsOfKeypressSoundAndVibrationSettings(
            final SharedPreferences sp, final Resources res) {
        if (mKeypressVibrationDurationSettingsPref != null) {
            final boolean hasVibratorHardware =
                    AudioAndHapticFeedbackManager.getInstance().hasVibrator();
            final boolean vibrateOnByUser = sp.getBoolean(Settings.PREF_VIBRATE_ON,
                    res.getBoolean(R.bool.config_default_vibration_enabled));
            mKeypressVibrationDurationSettingsPref.setEnabled(
                    hasVibratorHardware && vibrateOnByUser);
        }

        if (mKeypressSoundVolumeSettingsPref != null) {
            final boolean soundOn = sp.getBoolean(Settings.PREF_SOUND_ON,
                    res.getBoolean(R.bool.config_default_sound_enabled));
            mKeypressSoundVolumeSettingsPref.setEnabled(soundOn);
        }
    }

    private void showKeypressVibrationDurationSettingsDialog() {
        final SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        final Context context = getActivity();
        final PreferenceScreen settingsPref = mKeypressVibrationDurationSettingsPref;
        final SeekBarDialog.Listener listener = new SeekBarDialog.Adapter() {
            @Override
            public void onPositiveButtonClick(final SeekBarDialog dialog) {
                final int ms = dialog.getValue();
                sp.edit().putInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, ms).apply();
                if (settingsPref != null) {
                    settingsPref.setSummary(dialog.getValueText());
                }
            }

            @Override
            public void onStopTrackingTouch(final SeekBarDialog dialog) {
                final int ms = dialog.getValue();
                AudioAndHapticFeedbackManager.getInstance().vibrate(ms);
            }
        };
        final int currentMs = SettingsValues.getCurrentVibrationDuration(sp, getResources());
        final SeekBarDialog.Builder builder = new SeekBarDialog.Builder(context);
        builder.setTitle(R.string.prefs_keypress_vibration_duration_settings)
                .setListener(listener)
                .setMaxValue(AudioAndHapticFeedbackManager.MAX_KEYPRESS_VIBRATION_DURATION)
                .setValueFromat(R.string.settings_keypress_vibration_duration)
                .setValue(currentMs)
                .create()
                .show();
    }

    private static final int PERCENT_INT = 100;
    private static final float PERCENT_FLOAT = 100.0f;

    private static int getCurrentKeyPressSoundVolumePercent(final SharedPreferences sp,
            final Resources res) {
        return (int)(SettingsValues.getCurrentKeypressSoundVolume(sp, res) * PERCENT_FLOAT);
    }

    private void showKeypressSoundVolumeSettingDialog() {
        final SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        final Context context = getActivity();
        final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final PreferenceScreen settingsPref = mKeypressSoundVolumeSettingsPref;
        final SeekBarDialog.Listener listener = new SeekBarDialog.Adapter() {
            @Override
            public void onPositiveButtonClick(final SeekBarDialog dialog) {
                final float volume = dialog.getValue() / PERCENT_FLOAT;
                sp.edit().putFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, volume).apply();
                if (settingsPref != null) {
                    settingsPref.setSummary(dialog.getValueText());
                }
            }

            @Override
            public void onStopTrackingTouch(final SeekBarDialog dialog) {
                final float volume = dialog.getValue() / PERCENT_FLOAT;
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume);
            }
        };
        final SeekBarDialog.Builder builder = new SeekBarDialog.Builder(context);
        final int currentVolumeInt = getCurrentKeyPressSoundVolumePercent(sp, getResources());
        builder.setTitle(R.string.prefs_keypress_sound_volume_settings)
                .setListener(listener)
                .setMaxValue(PERCENT_INT)
                .setValue(currentVolumeInt)
                .create()
                .show();
    }
}
