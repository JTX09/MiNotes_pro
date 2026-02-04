package net.micode.notes.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.micode.notes.R;
import net.micode.notes.tool.SecurityManager;

import net.micode.notes.data.ThemeRepository;
import net.micode.notes.data.NotesRepository;
import android.provider.Settings;
import android.net.Uri;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.ListPreference;
import net.micode.notes.capsule.CapsuleService;
import net.micode.notes.capsule.ClipboardMonitorService;

import static android.app.Activity.RESULT_OK;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String PREFERENCE_SECURITY_KEY = "pref_key_security";
    public static final String PREFERENCE_THEME_MODE = "pref_theme_mode";
    public static final String PREFERENCE_CAPSULE_ENABLE = "pref_key_capsule_enable";
    public static final String PREFERENCE_FONT_SIZE = "pref_font_size";
    public static final String PREFERENCE_BG_RANDOM_APPEAR = "pref_key_bg_random_appear";
    public static final String PREFERENCE_LANGUAGE = "pref_language";
    public static final String PREFERENCE_CLOUD_SYNC_KEY = "pref_key_cloud_sync";
    public static final String PREFERENCE_EXPORT_NOTES_KEY = "pref_key_export_notes";
    
    public static final int REQUEST_CODE_CHECK_PASSWORD = 104;
    public static final int REQUEST_CODE_OVERLAY_PERMISSION = 105;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        loadThemePreference();
        loadSecurityPreference();
        loadCapsulePreference();
        loadCloudSyncPreference();
        loadExportPreference();
        loadFontSizePreference();
        loadLanguagePreference();
    }
    
    private void loadLanguagePreference() {
        ListPreference languagePref = findPreference(PREFERENCE_LANGUAGE);
        if (languagePref != null) {
            languagePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String language = (String) newValue;
                net.micode.notes.tool.LocaleHelper.setLocale(getContext(), language);
                // 重新启动应用或 Activity 以应用更改
                getActivity().recreate();
                return true;
            });
        }
    }
    
    private void loadFontSizePreference() {
        ListPreference fontSizePref = findPreference(PREFERENCE_FONT_SIZE);
        if (fontSizePref != null) {
            fontSizePref.setOnPreferenceChangeListener((preference, newValue) -> {
                // ListPreference 默认存储字符串，NoteEditActivity 需要整数
                // 这里我们只需确保它能保存，NoteEditActivity 稍后会修改读取逻辑
                return true;
            });
        }
    }

    private void loadCloudSyncPreference() {
        Preference syncPref = findPreference(PREFERENCE_CLOUD_SYNC_KEY);
        if (syncPref != null) {
            syncPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getActivity(), SyncActivity.class);
                startActivity(intent);
                return true;
            });
        }
    }

    private void loadExportPreference() {
        Preference exportPref = findPreference(PREFERENCE_EXPORT_NOTES_KEY);
        if (exportPref != null) {
            exportPref.setOnPreferenceClickListener(preference -> {
                exportNotes();
                return true;
            });
        }
    }

    private void exportNotes() {
        net.micode.notes.tool.BackupUtils backupUtils = net.micode.notes.tool.BackupUtils.getInstance(getContext());
        int state = backupUtils.exportToText();
        String message;
        switch (state) {
            case net.micode.notes.tool.BackupUtils.STATE_SUCCESS:
                message = getString(R.string.success_sdcard_export) + ": " + backupUtils.getExportedTextFileDir() + backupUtils.getExportedTextFileName();
                break;
            case net.micode.notes.tool.BackupUtils.STATE_SD_CARD_UNMOUONTED:
                message = getString(R.string.error_sdcard_unmounted);
                break;
            case net.micode.notes.tool.BackupUtils.STATE_SYSTEM_ERROR:
            default:
                message = getString(R.string.error_sdcard_export);
                break;
        }
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    private void loadCapsulePreference() {
        SwitchPreferenceCompat capsulePref = findPreference(PREFERENCE_CAPSULE_ENABLE);
        if (capsulePref != null) {
            capsulePref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (boolean) newValue;
                if (enabled) {
                    checkCapsulePermissions();
                } else {
                    stopCapsuleService();
                }
                return true;
            });
        }
    }

    private void checkCapsulePermissions() {
        Context context = getContext();
        if (context == null) return;

        // Check Overlay Permission
        if (!Settings.canDrawOverlays(context)) {
            new AlertDialog.Builder(context)
                .setTitle(R.string.capsule_permission_alert_window_title)
                .setMessage(R.string.capsule_permission_alert_window_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // Reset switch if cancelled
                    SwitchPreferenceCompat pref = findPreference(PREFERENCE_CAPSULE_ENABLE);
                    if (pref != null) pref.setChecked(false);
                })
                .show();
            return;
        }
        
        // Check Accessibility Permission
        if (!isAccessibilitySettingsOn(context)) {
             new AlertDialog.Builder(context)
                .setTitle(R.string.capsule_permission_accessibility_title)
                .setMessage(R.string.capsule_permission_accessibility_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                 .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // Reset switch
                    SwitchPreferenceCompat pref = findPreference(PREFERENCE_CAPSULE_ENABLE);
                    if (pref != null) pref.setChecked(false);
                })
                .show();
             return;
        }

        startCapsuleService();
    }
    
    private boolean isAccessibilitySettingsOn(Context mContext) {
        int accessibilityEnabled = 0;
        final String service = mContext.getPackageName() + "/" + ClipboardMonitorService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    mContext.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    mContext.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void startCapsuleService() {
        Context context = getContext();
        if (context != null) {
            Intent intent = new Intent(context, CapsuleService.class);
            // In Android 8.0+, we might need startForegroundService, but for now stick to startService
            // The service itself should call startForeground
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
    }

    private void stopCapsuleService() {
        Context context = getContext();
        if (context != null) {
            Intent intent = new Intent(context, CapsuleService.class);
            context.stopService(intent);
        }
    }

    private void loadThemePreference() {
        ListPreference themePref = findPreference(PREFERENCE_THEME_MODE);
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                ThemeRepository.applyTheme((String) newValue);
                return true;
            });
        }
    }

    private void loadSecurityPreference() {
        Preference securityPref = findPreference(PREFERENCE_SECURITY_KEY);
        if (securityPref != null) {
            securityPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (!SecurityManager.getInstance(getActivity()).isPasswordSet()) {
                        showSetPasswordDialog();
                    } else {
                        Intent intent = new Intent(getActivity(), PasswordActivity.class);
                        intent.setAction(PasswordActivity.ACTION_CHECK_PASSWORD);
                        startActivityForResult(intent, REQUEST_CODE_CHECK_PASSWORD);
                    }
                    return true;
                }
            });
        }
    }

    private void showSetPasswordDialog() {
        new AlertDialog.Builder(getActivity())
                .setTitle("设置密码")
                .setItems(new String[]{"数字锁", "手势锁"}, (dialog, which) -> {
                    int type = (which == 0) ? SecurityManager.TYPE_PIN : SecurityManager.TYPE_PATTERN;
                    Intent intent = new Intent(getActivity(), PasswordActivity.class);
                    intent.setAction(PasswordActivity.ACTION_SETUP_PASSWORD);
                    intent.putExtra(PasswordActivity.EXTRA_PASSWORD_TYPE, type);
                    startActivity(intent);
                })
                .show();
    }

    private void showManagePasswordDialog() {
        new AlertDialog.Builder(getActivity())
                .setTitle("管理密码")
                .setItems(new String[]{"更改密码", "取消密码"}, (dialog, which) -> {
                    if (which == 0) { // Change
                        showSetPasswordDialog();
                    } else { // Remove
                        SecurityManager.getInstance(getActivity()).removePassword();
                        Toast.makeText(getActivity(), "密码已取消", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CHECK_PASSWORD && resultCode == RESULT_OK) {
            showManagePasswordDialog();
        }
    }
}
