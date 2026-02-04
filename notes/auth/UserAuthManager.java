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
import android.util.Log;

import androidx.annotation.Nullable;

import net.micode.notes.api.AliyunConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 用户认证管理器（阿里云EMAS Serverless HTTP API版本）
 * <p>
 * 使用阿里云EMAS Serverless的HTTP API进行用户认证。
 * 需要先登录阿里云控制台创建EMAS应用并开通Serverless服务。
 * </p>
 */
public class UserAuthManager {

    private static final String TAG = "UserAuthManager";
    private static final String PREFS_NAME = "UserAuth";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_TOKEN_EXPIRE_TIME = "token_expire_time";

    // Token过期时间：7天
    private static final long TOKEN_EXPIRE_DURATION = 7 * 24 * 60 * 60 * 1000;

    // EMAS Serverless API地址
    private static final String BASE_URL = AliyunConfig.BASE_URL;
    private static final String API_AUTH = BASE_URL + "/auth";
    private static final String API_REFRESH_TOKEN = BASE_URL + "/auth/refresh";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static UserAuthManager sInstance;
    private final ExecutorService mExecutor;
    private final OkHttpClient mHttpClient;
    private SharedPreferences mPrefs;
    private Context mContext;

    private String mUserId;
    private String mUsername;
    private String mAuthToken;
    private String mRefreshToken;
    private String mDeviceId;
    private boolean mIsLoggedIn;
    private long mTokenExpireTime;

    private UserAuthManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mExecutor = Executors.newSingleThreadExecutor();
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor(new net.micode.notes.api.RetryInterceptor())
                .build();
        loadUserInfo();
    }

    public static synchronized UserAuthManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new UserAuthManager(context);
        }
        return sInstance;
    }

    /**
     * 初始化（配置EMAS Serverless）
     */
    public void initialize(Context context) {
        Log.d(TAG, "Initializing UserAuthManager");
        Log.d(TAG, "AppKey: " + AliyunConfig.APP_KEY);
        // 这里可以添加EMAS SDK初始化（如果需要）
    }

    /**
     * 认证回调接口
     */
    public interface AuthCallback {
        void onSuccess(String userId, String username);
        void onError(String error);
    }

    /**
     * 加载本地存储的用户信息
     */
    private void loadUserInfo() {
        mIsLoggedIn = mPrefs.getBoolean(KEY_IS_LOGGED_IN, false);
        mUserId = mPrefs.getString(KEY_USER_ID, null);
        mUsername = mPrefs.getString(KEY_USERNAME, null);
        mAuthToken = mPrefs.getString(KEY_AUTH_TOKEN, null);
        mRefreshToken = mPrefs.getString(KEY_REFRESH_TOKEN, null);
        mTokenExpireTime = mPrefs.getLong(KEY_TOKEN_EXPIRE_TIME, 0);
        mDeviceId = mPrefs.getString(KEY_DEVICE_ID, null);

        if (mDeviceId == null) {
            mDeviceId = generateDeviceId();
            mPrefs.edit().putString(KEY_DEVICE_ID, mDeviceId).apply();
        }

        Log.d(TAG, "User info loaded, logged in: " + mIsLoggedIn);
    }

    /**
     * 用户注册
     */
    public void register(String username, String password, AuthCallback callback) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            callback.onError("用户名和密码不能为空");
            return;
        }

        mExecutor.execute(() -> {
            try {
                String hashedPassword = hashPassword(password);

                // 构建JSON请求体
                JSONObject json = new JSONObject();
                json.put("action", "register");
                json.put("username", username);
                json.put("password", hashedPassword);
                json.put("deviceId", mDeviceId);
                json.put("appKey", AliyunConfig.APP_KEY);

                RequestBody body = RequestBody.create(json.toString(), JSON);
                Request request = new Request.Builder()
                        .url(API_AUTH)
                        .post(body)
                        .build();

                mHttpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Register failed", e);
                        callback.onError("网络错误: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        handleAuthResponse(response, username, callback);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Register error", e);
                callback.onError("注册失败: " + e.getMessage());
            }
        });
    }

    /**
     * 用户登录
     */
    public void login(String username, String password, AuthCallback callback) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            callback.onError("用户名和密码不能为空");
            return;
        }

        mExecutor.execute(() -> {
            try {
                String hashedPassword = hashPassword(password);

                // 构建JSON请求体
                JSONObject json = new JSONObject();
                json.put("action", "login");
                json.put("username", username);
                json.put("password", hashedPassword);
                json.put("deviceId", mDeviceId);
                json.put("appKey", AliyunConfig.APP_KEY);

                RequestBody body = RequestBody.create(json.toString(), JSON);
                Request request = new Request.Builder()
                        .url(API_AUTH)
                        .post(body)
                        .build();

                mHttpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Login failed", e);
                        callback.onError("网络错误: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        handleAuthResponse(response, username, callback);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Login error", e);
                callback.onError("登录失败: " + e.getMessage());
            }
        });
    }

    /**
     * 处理认证响应
     */
    private void handleAuthResponse(Response response, String username, AuthCallback callback) {
        String responseBody = null;
        try {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                callback.onError("服务器错误: " + response.code() + " - " + errorBody);
                return;
            }

            responseBody = response.body().string();
            JSONObject json = new JSONObject(responseBody);

            if (json.getBoolean("success")) {
                mUserId = json.getString("userId");
                // 支持两种字段名：token 或 authToken
                if (json.has("token")) {
                    mAuthToken = json.getString("token");
                } else if (json.has("authToken")) {
                    mAuthToken = json.getString("authToken");
                }
                if (json.has("refreshToken")) {
                    mRefreshToken = json.getString("refreshToken");
                }
                mTokenExpireTime = System.currentTimeMillis() + TOKEN_EXPIRE_DURATION;
                mUsername = username;
                mIsLoggedIn = true;

                saveUserInfo();

                callback.onSuccess(mUserId, mUsername);
            } else {
                callback.onError(json.getString("message"));
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Parse response error", e);
            callback.onError("解析响应失败");
        } finally {
            response.close();
        }
    }

    /**
     * 保存用户信息到本地
     */
    private void saveUserInfo() {
        mPrefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, mIsLoggedIn)
                .putString(KEY_USER_ID, mUserId)
                .putString(KEY_USERNAME, mUsername)
                .putString(KEY_AUTH_TOKEN, mAuthToken)
                .putString(KEY_REFRESH_TOKEN, mRefreshToken)
                .putLong(KEY_TOKEN_EXPIRE_TIME, mTokenExpireTime)
                .putString(KEY_DEVICE_ID, mDeviceId)
                .apply();
    }

    /**
     * 登出
     */
    public void logout() {
        mIsLoggedIn = false;
        mUserId = null;
        mUsername = null;
        mAuthToken = null;
        mRefreshToken = null;
        mTokenExpireTime = 0;

        mPrefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRE_TIME)
                .apply();

        Log.i(TAG, "User logged out");
    }

    /**
     * 密码哈希（使用SHA-256）
     *
     * @throws RuntimeException 如果SHA-256算法不可用
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Hash algorithm not found", e);
            throw new RuntimeException("密码哈希失败：系统不支持SHA-256算法", e);
        }
    }

    /**
     * 生成设备ID
     */
    private String generateDeviceId() {
        return "device_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Getter Methods ====================

    public boolean isLoggedIn() {
        return mIsLoggedIn;
    }

    @Nullable
    public String getUserId() {
        return mUserId;
    }

    @Nullable
    public String getUsername() {
        return mUsername;
    }

    @Nullable
    public String getAuthToken() {
        return mAuthToken;
    }

    public String getDeviceId() {
        return mDeviceId;
    }

    @Nullable
    public String getRefreshToken() {
        return mRefreshToken;
    }

    /**
     * 检查Token是否即将过期（24小时内）
     */
    public boolean isTokenExpiringSoon() {
        if (!mIsLoggedIn || mTokenExpireTime == 0) {
            return false;
        }
        long timeUntilExpire = mTokenExpireTime - System.currentTimeMillis();
        return timeUntilExpire < 24 * 60 * 60 * 1000; // 24小时内过期
    }

    /**
     * 检查Token是否已过期
     */
    public boolean isTokenExpired() {
        if (!mIsLoggedIn || mTokenExpireTime == 0) {
            return false;
        }
        return System.currentTimeMillis() >= mTokenExpireTime;
    }

    /**
     * 刷新Token
     *
     * @param callback 刷新回调
     */
    public void refreshToken(TokenRefreshCallback callback) {
        if (mRefreshToken == null) {
            callback.onError("没有可用的刷新令牌");
            return;
        }

        mExecutor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("action", "refresh");
                json.put("refreshToken", mRefreshToken);
                json.put("deviceId", mDeviceId);

                RequestBody body = RequestBody.create(json.toString(), JSON);
                Request request = new Request.Builder()
                        .url(API_REFRESH_TOKEN)
                        .post(body)
                        .build();

                mHttpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Token refresh failed", e);
                        callback.onError("网络错误: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = null;
                        try {
                            if (!response.isSuccessful()) {
                                callback.onError("服务器错误: " + response.code());
                                return;
                            }

                            responseBody = response.body().string();
                            JSONObject json = new JSONObject(responseBody);

                            if (json.getBoolean("success")) {
                                mAuthToken = json.getString("token");
                                if (json.has("refreshToken")) {
                                    mRefreshToken = json.getString("refreshToken");
                                }
                                mTokenExpireTime = System.currentTimeMillis() + TOKEN_EXPIRE_DURATION;
                                saveUserInfo();
                                callback.onSuccess(mAuthToken);
                            } else {
                                callback.onError(json.getString("message"));
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Parse refresh response error", e);
                            callback.onError("解析响应失败");
                        } finally {
                            response.close();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Refresh token error", e);
                callback.onError("刷新失败: " + e.getMessage());
            }
        });
    }

    /**
     * Token刷新回调接口
     */
    public interface TokenRefreshCallback {
        void onSuccess(String newToken);
        void onError(String error);
    }
}
