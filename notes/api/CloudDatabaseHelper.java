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

package net.micode.notes.api;

import android.util.Log;

import net.micode.notes.model.CloudNote;
import net.micode.notes.model.WorkingNote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 云数据库帮助类（EMAS Serverless HTTP API版本）
 * <p>
 * 通过HTTP API与阿里云EMAS Serverless交互。
 * </p>
 */
public class CloudDatabaseHelper {

    private static final String TAG = "CloudDatabaseHelper";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_NOTES = AliyunConfig.BASE_URL + "/notes";

    private String mUserId;
    private String mDeviceId;
    private String mAuthToken;
    private OkHttpClient mHttpClient;

    public CloudDatabaseHelper(String userId, String deviceId, String authToken) {
        mUserId = userId;
        mDeviceId = deviceId;
        mAuthToken = authToken;
        mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new RetryInterceptor())
                .build();
    }

    /**
     * 上传笔记到云端
     */
    public void uploadNote(WorkingNote note, CloudCallback<String> callback) {
        Log.d(TAG, "Uploading note: " + note.getNoteId());

        CloudNote cloudNote = new CloudNote(note, mDeviceId);
        JSONObject json;
        try {
            json = cloudNote.toJson();
            json.put("action", "upload");
            json.put("userId", mUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON", e);
            callback.onError("数据格式错误");
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_NOTES)
                .post(body)
                .addHeader("Authorization", "Bearer " + mAuthToken)
                .addHeader("Content-Type", "application/json")
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Upload failed", e);
                callback.onError("网络错误: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);

                    if (jsonResponse.getBoolean("success")) {
                        String cloudId = jsonResponse.getString("cloudId");
                        callback.onSuccess(cloudId);
                    } else {
                        String message = jsonResponse.optString("message", "上传失败");
                        callback.onError(message);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse response", e);
                    callback.onError("解析响应失败");
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 从云端下载用户的所有笔记
     */
    public void downloadNotes(long lastSyncTime, CloudCallback<JSONArray> callback) {
        Log.d(TAG, "Downloading notes for user: " + mUserId);

        JSONObject json = new JSONObject();
        try {
            json.put("action", "download");
            json.put("lastSyncTime", lastSyncTime);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON", e);
            callback.onError("数据格式错误");
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_NOTES)
                .post(body)
                .addHeader("Authorization", "Bearer " + mAuthToken)
                .addHeader("Content-Type", "application/json")
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Download failed", e);
                callback.onError("网络错误: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);

                    if (jsonResponse.getBoolean("success")) {
                        JSONArray notesArray = jsonResponse.getJSONArray("notes");
                        callback.onSuccess(notesArray);
                    } else {
                        String message = jsonResponse.optString("message", "下载失败");
                        callback.onError(message);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse response", e);
                    callback.onError("解析响应失败");
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 删除云端笔记
     */
    public void deleteNote(String cloudNoteId, CloudCallback<Void> callback) {
        Log.d(TAG, "Deleting note from cloud: " + cloudNoteId);

        JSONObject json = new JSONObject();

        try {
            json.put("action", "delete");
            json.put("cloudNoteId", cloudNoteId);
            json.put("userId", mUserId);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON", e);
            callback.onError("数据格式错误");
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(API_NOTES)
                .post(body)
                .addHeader("Authorization", "Bearer " + mAuthToken)
                .addHeader("Content-Type", "application/json")
                .build();

        mHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Delete failed", e);
                callback.onError("网络错误: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);

                    if (jsonResponse.getBoolean("success")) {
                        callback.onSuccess(null);
                    } else {
                        String message = jsonResponse.optString("message", "删除失败");
                        callback.onError(message);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse response", e);
                    callback.onError("解析响应失败");
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 将笔记转换为云端数据格式（已弃用，建议使用 CloudNote 模型）
     */
    public Map<String, Object> convertToCloudData(WorkingNote note) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", mUserId);
        data.put("deviceId", mDeviceId);
        data.put("noteId", String.valueOf(note.getNoteId()));
        data.put("title", note.getTitle());
        data.put("content", note.getContent());
        data.put("parentId", note.getFolderId());
        data.put("modifiedTime", note.getModifiedDate());
        data.put("syncStatus", 2);
        data.put("lastSyncTime", System.currentTimeMillis());
        return data;
    }
}
