/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import net.micode.notes.R;
import net.micode.notes.auth.UserAuthManager;
import net.micode.notes.sync.SyncManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 同步设置界面
 * <p>
 * 显示云同步设置，包括登录状态、同步开关、同步按钮和进度显示。
 * </p>
 */
public class SyncActivity extends BaseActivity {

    private static final String PREFS_SYNC = "sync_settings";
    private static final String KEY_AUTO_SYNC = "auto_sync";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private TextView mTvDeviceId;
    private TextView mTvLastSyncTime;
    private TextView mTvSyncStatus;
    private SwitchMaterial mSwitchAutoSync;
    private ProgressBar mProgressSync;
    private MaterialButton mBtnSyncNow;

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);

        mPrefs = getSharedPreferences(PREFS_SYNC, MODE_PRIVATE);

        initViews();
        loadSettings();
    }

    private void initViews() {
        mTvDeviceId = findViewById(R.id.tv_device_id);
        mTvLastSyncTime = findViewById(R.id.tv_last_sync_time);
        mTvSyncStatus = findViewById(R.id.tv_sync_status);
        mSwitchAutoSync = findViewById(R.id.switch_auto_sync);
        mProgressSync = findViewById(R.id.progress_sync);
        mBtnSyncNow = findViewById(R.id.btn_sync_now);

        mSwitchAutoSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean(KEY_AUTO_SYNC, isChecked).apply();
        });

        mBtnSyncNow.setOnClickListener(v -> startSync());
    }

    private void loadSettings() {
        // Load auto sync setting
        boolean autoSync = mPrefs.getBoolean(KEY_AUTO_SYNC, false);
        mSwitchAutoSync.setChecked(autoSync);

        // Load user info
        UserAuthManager authManager = UserAuthManager.getInstance(this);
        if (authManager.isLoggedIn()) {
            String username = authManager.getUsername();
            String deviceId = authManager.getDeviceId();
            mTvDeviceId.setText("已登录: " + username + "\n设备ID: " + deviceId);
        } else {
            mTvDeviceId.setText("未登录，请先登录账号");
            mBtnSyncNow.setEnabled(false);
        }

        // Load last sync time
        long lastSync = mPrefs.getLong(KEY_LAST_SYNC, 0);
        if (lastSync > 0) {
            String timeStr = formatTime(lastSync);
            mTvLastSyncTime.setText(timeStr);
        }

        // Set initial status
        mTvSyncStatus.setText(R.string.sync_status_idle);
    }

    private void startSync() {
        mTvSyncStatus.setText(R.string.sync_status_syncing);
        mProgressSync.setVisibility(View.VISIBLE);
        mBtnSyncNow.setEnabled(false);

        Toast.makeText(this, R.string.sync_toast_started, Toast.LENGTH_SHORT).show();

        SyncManager.getInstance().syncNotes(new SyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    mTvSyncStatus.setText(R.string.sync_status_success);
                    mProgressSync.setVisibility(View.INVISIBLE);
                    mBtnSyncNow.setEnabled(true);

                    long currentTime = System.currentTimeMillis();
                    mPrefs.edit().putLong(KEY_LAST_SYNC, currentTime).apply();
                    mTvLastSyncTime.setText(formatTime(currentTime));

                    Toast.makeText(SyncActivity.this, R.string.sync_toast_success, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    mTvSyncStatus.setText(R.string.sync_status_failed);
                    mProgressSync.setVisibility(View.INVISIBLE);
                    mBtnSyncNow.setEnabled(true);

                    String message = getString(R.string.sync_toast_failed, error);
                    Toast.makeText(SyncActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String formatTime(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeMillis));
    }
}
