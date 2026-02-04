package net.micode.notes.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public class ThemeRepository {
    private static final String PREF_THEME_MODE = "pref_theme_mode";
    public static final String THEME_MODE_SYSTEM = "system";
    public static final String THEME_MODE_LIGHT = "light";
    public static final String THEME_MODE_DARK = "dark";

    private final SharedPreferences mPrefs;

    public ThemeRepository(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public String getThemeMode() {
        return mPrefs.getString(PREF_THEME_MODE, THEME_MODE_SYSTEM);
    }

    public void setThemeMode(String mode) {
        mPrefs.edit().putString(PREF_THEME_MODE, mode).apply();
        applyTheme(mode);
    }

    public static void applyTheme(String mode) {
        switch (mode) {
            case THEME_MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_MODE_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
