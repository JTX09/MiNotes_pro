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

package net.micode.notes.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.util.UUID;

/**
 * 匿名认证管理器
 * <p>
 * 管理匿名用户的认证信息，包括生成和存储用户ID、设备ID等。
 * 使用单例模式确保全局只有一个认证管理器实例。
 * </p>
 */
public class AnonymousAuthManager {

    private static final String TAG = "AnonymousAuthManager";
    private static final String PREFS_NAME = "AnonymousAuth";
    private static final String KEY_USER_ID = "anonymous_user_id";
    private static final String KEY_DEVICE_ID = "device_id";

    private static AnonymousAuthManager sInstance;
    private SharedPreferences mPrefs;
    private String mUserId;
    private String mDeviceId;

    private AnonymousAuthManager(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取AnonymousAuthManager单例实例
     *
     * @param context 应用上下文
     * @return AnonymousAuthManager实例
     */
    public static synchronized AnonymousAuthManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AnonymousAuthManager(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * 初始化认证管理器
     *
     * @param context 应用上下文
     */
    public void initialize(Context context) {
        if (mUserId == null) {
            mUserId = mPrefs.getString(KEY_USER_ID, null);
            if (mUserId == null) {
                mUserId = generateAnonymousUserId();
                mPrefs.edit().putString(KEY_USER_ID, mUserId).apply();
                Log.i(TAG, "Generated new anonymous user ID");
            }
        }

        if (mDeviceId == null) {
            mDeviceId = mPrefs.getString(KEY_DEVICE_ID, null);
            if (mDeviceId == null) {
                mDeviceId = generateDeviceId(context);
                mPrefs.edit().putString(KEY_DEVICE_ID, mDeviceId).apply();
                Log.i(TAG, "Generated new device ID");
            }
        }

        Log.i(TAG, "AnonymousAuthManager initialized successfully");
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID
     */
    public String getUserId() {
        return mUserId;
    }

    /**
     * 获取设备ID
     *
     * @return 设备ID
     */
    public String getDeviceId() {
        return mDeviceId;
    }

    /**
     * 生成匿名用户ID
     *
     * @return 格式为 anon_xxx 的用户ID
     */
    private String generateAnonymousUserId() {
        return "anon_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成设备ID
     *
     * @param context 应用上下文
     * @return 设备ID
     */
    private String generateDeviceId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = UUID.randomUUID().toString();
        }
        return "device_" + androidId;
    }
}
