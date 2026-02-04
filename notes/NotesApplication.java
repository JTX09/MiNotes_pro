package net.micode.notes;

import android.app.Application;
import android.util.Log;

import net.micode.notes.auth.UserAuthManager;
import net.micode.notes.data.ThemeRepository;
import net.micode.notes.sync.SyncWorker;
import net.micode.notes.capsule.CapsuleService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.provider.Settings;

import com.google.android.material.color.DynamicColors;

import net.micode.notes.tool.LocaleHelper;
import android.content.Context;

public class NotesApplication extends Application {
    
    private static final String TAG = "NotesApplication";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        DynamicColors.applyToActivitiesIfAvailable(this);

        ThemeRepository repository = new ThemeRepository(this);
        ThemeRepository.applyTheme(repository.getThemeMode());

        UserAuthManager authManager = UserAuthManager.getInstance(this);
        authManager.initialize(this);
        
        Log.d(TAG, "EMAS Serverless initialized");

        SyncWorker.initialize(this);

        // Start CapsuleService if enabled
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean capsuleEnabled = prefs.getBoolean("pref_key_capsule_enable", false);
        if (capsuleEnabled && Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(this, CapsuleService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }
}
