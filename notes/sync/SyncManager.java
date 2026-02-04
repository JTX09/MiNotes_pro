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

package net.micode.notes.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import net.micode.notes.api.CloudCallback;
import net.micode.notes.api.CloudDatabaseHelper;
import net.micode.notes.auth.UserAuthManager;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.model.CloudNote;
import net.micode.notes.model.WorkingNote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同步管理器
 * <p>
 * 负责管理笔记的云同步操作，包括上传本地修改、下载云端更新、处理冲突等。
 * 使用单例模式确保全局只有一个同步管理器实例。
 * </p>
 * <p>
 * 修复记录：
 * 1. 修复同步时间更新逻辑 - 确保所有笔记处理完成后再更新时间戳
 * 2. 优化线程同步机制 - 使用 CountDownLatch 替代 synchronized/wait/notify
 * 3. 添加全量同步支持 - 支持强制下载所有云端笔记
 * 4. 添加同步进度回调 - 支持实时显示同步进度
 * </p>
 */
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final String PREFS_SYNC = "sync_settings";
    private static final String KEY_LAST_SYNC = "last_sync_time";
    private static final String KEY_IS_FIRST_SYNC = "is_first_sync";
    private static final long SYNC_TIMEOUT_SECONDS = 60;

    private final ExecutorService mExecutor;
    private Context mContext;
    private SharedPreferences mPrefs;
    private List<Conflict> mConflicts;
    private ConflictListener mConflictListener;

    /**
     * 静态内部类实现单例模式（Initialization-on-demand holder idiom）
     */
    private static class Holder {
        private static final SyncManager INSTANCE = new SyncManager();
    }

    private SyncManager() {
        mExecutor = Executors.newSingleThreadExecutor();
        mConflicts = new ArrayList<>();
    }

    /**
     * 同步回调接口
     */
    public interface SyncCallback {
        void onSuccess();
        void onError(String error);
    }

    /**
     * 同步进度回调接口
     */
    public interface SyncProgressCallback {
        void onProgress(int current, int total, String message);
    }

    /**
     * 冲突监听器接口
     */
    public interface ConflictListener {
        void onConflictDetected(Conflict conflict);
    }

    /**
     * 获取SyncManager单例实例
     *
     * @return SyncManager实例
     */
    public static SyncManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 初始化SyncManager
     *
     * @param context 应用上下文
     */
    public void initialize(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE);
        Log.d(TAG, "SyncManager initialized");
    }

    /**
     * 设置冲突监听器
     *
     * @param listener 冲突监听器
     */
    public void setConflictListener(ConflictListener listener) {
        mConflictListener = listener;
    }

    /**
     * 移除冲突
     *
     * @param conflict 要移除的冲突
     */
    public void removeConflict(Conflict conflict) {
        mConflicts.remove(conflict);
    }

    /**
     * 执行笔记同步（增量同步）
     *
     * @param callback 同步回调
     */
    public void syncNotes(SyncCallback callback) {
        syncNotesInternal(false, callback, null);
    }

    /**
     * 执行全量同步（强制下载所有云端笔记）
     *
     * @param callback 同步回调
     */
    public void syncAllNotes(SyncCallback callback) {
        syncNotesInternal(true, callback, null);
    }

    /**
     * 上传所有本地笔记到云端
     * <p>
     * 用于新用户登录后，将设备上所有笔记上传到云端。
     * 不管笔记的 LOCAL_MODIFIED 状态如何，都会上传。
     * </p>
     *
     * @param callback 同步回调
     */
    public void uploadAllNotes(SyncCallback callback) {
        Log.d(TAG, "========== Starting upload all notes ==========");

        mExecutor.execute(() -> {
            try {
                performUploadAll();
                Log.d(TAG, "Upload all notes completed successfully");
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Upload all notes failed", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * 执行笔记同步（带进度回调）
     *
     * @param forceFullSync 是否强制全量同步
     * @param callback 同步回调
     * @param progressCallback 进度回调
     */
    public void syncNotesWithProgress(boolean forceFullSync, SyncCallback callback, 
            SyncProgressCallback progressCallback) {
        syncNotesInternal(forceFullSync, callback, progressCallback);
    }

    /**
     * 内部同步方法
     */
    private void syncNotesInternal(boolean forceFullSync, SyncCallback callback, 
            SyncProgressCallback progressCallback) {
        Log.d(TAG, "========== Starting sync operation ==========");
        Log.d(TAG, "Force full sync: " + forceFullSync);

        mExecutor.execute(() -> {
            try {
                performSync(forceFullSync, progressCallback);
                Log.d(TAG, "Sync completed successfully");
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * 执行实际的同步操作
     */
    private void performSync(boolean forceFullSync, SyncProgressCallback progressCallback) throws Exception {
        UserAuthManager authManager = UserAuthManager.getInstance(mContext);
        if (!authManager.isLoggedIn()) {
            throw new RuntimeException("用户未登录");
        }

        // 检查并刷新Token
        ensureValidToken(authManager);

        NotesRepository repo = new NotesRepository(mContext);
        String authToken = authManager.getAuthToken();
        Log.d(TAG, "Auth token: " + (authToken != null ? authToken.substring(0, Math.min(20, authToken.length())) + "..." : "null"));
        
        CloudDatabaseHelper cloudHelper = new CloudDatabaseHelper(
            authManager.getUserId(),
            authManager.getDeviceId(),
            authToken
        );

        // 1. 上传本地修改的笔记（只上传当前用户的笔记）
        if (progressCallback != null) {
            progressCallback.onProgress(0, 100, "正在上传本地修改...");
        }
        uploadNotesSync(repo, cloudHelper, progressCallback, authManager.getUserId());

        // 2. 下载云端更新的笔记
        if (progressCallback != null) {
            progressCallback.onProgress(50, 100, "正在下载云端更新...");
        }
        boolean downloadSuccess = downloadNotesSync(repo, cloudHelper, forceFullSync, progressCallback, authManager.getUserId());

        // 3. 只有在下载成功后才更新同步时间
        if (downloadSuccess) {
            updateSyncFlags();
            // 标记已完成首次同步
            markFirstSyncCompleted();
        } else {
            throw new RuntimeException("下载云端笔记失败，同步时间未更新");
        }
    }

    /**
     * 确保Token有效
     */
    private void ensureValidToken(UserAuthManager authManager) throws Exception {
        if (authManager.isTokenExpired()) {
            Log.w(TAG, "Token已过期，尝试刷新...");
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean refreshSuccess = new AtomicBoolean(false);
            final AtomicReference<String> errorMsg = new AtomicReference<>();

            authManager.refreshToken(new UserAuthManager.TokenRefreshCallback() {
                @Override
                public void onSuccess(String newToken) {
                    Log.d(TAG, "Token刷新成功");
                    refreshSuccess.set(true);
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Token刷新失败: " + error);
                    errorMsg.set(error);
                    latch.countDown();
                }
            });

            boolean completed = latch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed || !refreshSuccess.get()) {
                throw new RuntimeException("Token刷新失败: " + errorMsg.get());
            }
        }
    }

    /**
     * 同步方式上传笔记
     */
    private void uploadNotesSync(NotesRepository repo, CloudDatabaseHelper cloudHelper,
            SyncProgressCallback progressCallback, String userId) throws Exception {
        Log.d(TAG, "Uploading local modified notes for user: " + userId);

        final List<WorkingNote> notesToUpload = new ArrayList<>();
        final CountDownLatch queryLatch = new CountDownLatch(1);
        final AtomicReference<Exception> errorRef = new AtomicReference<>();

        // 使用带用户过滤的方法，只查询当前用户的笔记
        repo.getLocalModifiedNotes(userId, new NotesRepository.Callback<List<WorkingNote>>() {
            @Override
            public void onSuccess(List<WorkingNote> notes) {
                notesToUpload.addAll(notes);
                queryLatch.countDown();
            }

            @Override
            public void onError(Exception e) {
                errorRef.set(e);
                queryLatch.countDown();
            }
        });

        boolean completed = queryLatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            throw new RuntimeException("查询本地修改笔记超时");
        }
        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        Log.d(TAG, "Found " + notesToUpload.size() + " notes to upload");

        int total = notesToUpload.size();
        for (int i = 0; i < total; i++) {
            WorkingNote note = notesToUpload.get(i);
            
            if (progressCallback != null) {
                int progress = (i * 50) / total; // 上传占50%进度
                progressCallback.onProgress(progress, 100, "正在上传笔记 " + (i + 1) + "/" + total);
            }

            uploadSingleNote(repo, cloudHelper, note);
        }
    }

    /**
     * 上传单条笔记
     */
    private void uploadSingleNote(NotesRepository repo, CloudDatabaseHelper cloudHelper, 
            WorkingNote note) throws Exception {
        final CountDownLatch uploadLatch = new CountDownLatch(1);
        final AtomicReference<String> cloudIdRef = new AtomicReference<>();
        final AtomicReference<Exception> errorRef = new AtomicReference<>();

        cloudHelper.uploadNote(note, new CloudCallback<String>() {
            @Override
            public void onSuccess(String result) {
                cloudIdRef.set(result);
                uploadLatch.countDown();
            }

            @Override
            public void onError(String err) {
                errorRef.set(new Exception(err));
                uploadLatch.countDown();
            }
        });

        boolean completed = uploadLatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            throw new RuntimeException("上传笔记超时: " + note.getNoteId());
        }
        if (errorRef.get() != null) {
            Log.e(TAG, "Failed to upload note: " + note.getNoteId(), errorRef.get());
            return; // 继续处理其他笔记
        }

        Log.d(TAG, "Uploaded note: " + note.getNoteId() + " with cloudId: " + cloudIdRef.get());

        String cloudNoteId = cloudIdRef.get();
        if (cloudNoteId != null && !cloudNoteId.isEmpty()) {
            note.setCloudNoteId(cloudNoteId);
            if (!note.saveNote()) {
                Log.w(TAG, "Failed to save cloudNoteId for note: " + note.getNoteId());
            }
        }

        markNoteAsSynced(repo, note.getNoteId());
    }

    /**
     * 标记笔记为已同步
     */
    private void markNoteAsSynced(NotesRepository repo, long noteId) throws Exception {
        final CountDownLatch markLatch = new CountDownLatch(1);
        final AtomicReference<Exception> errorRef = new AtomicReference<>();

        repo.markNoteSynced(noteId, new NotesRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                markLatch.countDown();
            }

            @Override
            public void onError(Exception e) {
                errorRef.set(e);
                markLatch.countDown();
            }
        });

        boolean completed = markLatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            throw new RuntimeException("标记笔记同步状态超时: " + noteId);
        }
        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        Log.d(TAG, "Marked note " + noteId + " as synced");
    }

    /**
     * 上传所有本地笔记到云端（不管modified状态）
     */
    private void performUploadAll() throws Exception {
        UserAuthManager authManager = UserAuthManager.getInstance(mContext);
        if (!authManager.isLoggedIn()) {
            throw new RuntimeException("用户未登录");
        }

        String userId = authManager.getUserId();
        String authToken = authManager.getAuthToken();
        Log.d(TAG, "Uploading all notes for user: " + userId);

        NotesRepository repo = new NotesRepository(mContext);
        CloudDatabaseHelper cloudHelper = new CloudDatabaseHelper(
            userId,
            authManager.getDeviceId(),
            authToken
        );

        // 获取当前用户的所有笔记（不管modified状态）
        final List<WorkingNote> allNotes = new ArrayList<>();
        final CountDownLatch queryLatch = new CountDownLatch(1);
        final AtomicReference<Exception> errorRef = new AtomicReference<>();

        repo.getNotesByCloudUserId(userId, new NotesRepository.Callback<List<WorkingNote>>() {
            @Override
            public void onSuccess(List<WorkingNote> notes) {
                allNotes.addAll(notes);
                queryLatch.countDown();
            }

            @Override
            public void onError(Exception e) {
                errorRef.set(e);
                queryLatch.countDown();
            }
        });

        boolean completed = queryLatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            throw new RuntimeException("查询笔记超时");
        }
        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        Log.d(TAG, "Found " + allNotes.size() + " notes to upload");

        // 上传所有笔记
        int total = allNotes.size();
        int successCount = 0;
        for (int i = 0; i < total; i++) {
            WorkingNote note = allNotes.get(i);
            try {
                uploadSingleNote(repo, cloudHelper, note);
                successCount++;
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload note: " + note.getNoteId(), e);
                // 继续上传其他笔记
            }
        }

        Log.d(TAG, "Upload completed: " + successCount + "/" + total + " notes uploaded");

        // 更新同步时间
        updateSyncFlags();
        markFirstSyncCompleted();
    }

    /**
     * 同步方式下载笔记
     * 
     * @return 是否下载成功
     */
    private boolean downloadNotesSync(NotesRepository repo, CloudDatabaseHelper cloudHelper,
            boolean forceFullSync, SyncProgressCallback progressCallback, String userId) throws Exception {
        Log.d(TAG, "Downloading cloud updates");

        long lastSyncTime = forceFullSync ? 0 : mPrefs.getLong(KEY_LAST_SYNC, 0);
        Log.d(TAG, "Last sync time: " + lastSyncTime + (forceFullSync ? " (强制全量同步)" : ""));
        
        // 首次同步时传递 0，获取所有笔记
        // 后续同步只获取修改过的笔记
        long downloadSince = (lastSyncTime == 0) ? 0 : lastSyncTime;
        
        final AtomicReference<JSONArray> notesArrayRef = new AtomicReference<>();
        final CountDownLatch downloadLatch = new CountDownLatch(1);
        final AtomicReference<Exception> errorRef = new AtomicReference<>();
        final AtomicLong maxModifiedTime = new AtomicLong(0);

        cloudHelper.downloadNotes(downloadSince, new CloudCallback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray result) {
                notesArrayRef.set(result);
                
                // 计算云端最新修改时间
                long latestCloudTime = 0;
                try {
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject noteJson = result.getJSONObject(i);
                        long modifiedTime = noteJson.optLong("modifiedTime", 0);
                        if (modifiedTime > latestCloudTime) {
                            latestCloudTime = modifiedTime;
                        }
                    }
                    maxModifiedTime.set(latestCloudTime);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to calculate latest cloud time", e);
                }
                
                downloadLatch.countDown();
            }

            @Override
            public void onError(String err) {
                errorRef.set(new Exception(err));
                downloadLatch.countDown();
            }
        });

        boolean completed = downloadLatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            throw new RuntimeException("下载云端笔记超时");
        }
        if (errorRef.get() != null) {
            throw errorRef.get();
        }

        JSONArray notesArray = notesArrayRef.get();
        if (notesArray == null) {
            Log.w(TAG, "Downloaded notes array is null");
            return true; // 视为成功，只是没有数据
        }

        Log.d(TAG, "Downloaded " + notesArray.length() + " notes from cloud");

        // 处理下载的笔记
        int total = notesArray.length();
        int successCount = 0;
        
        for (int i = 0; i < total; i++) {
            if (progressCallback != null) {
                int progress = 50 + (i * 50) / total; // 下载占50-100%进度
                progressCallback.onProgress(progress, 100, "正在处理笔记 " + (i + 1) + "/" + total);
            }

            CloudNote cloudNote = new CloudNote(notesArray.getJSONObject(i));
            boolean processed = processDownloadedNote(repo, cloudNote, userId);
            if (processed) {
                successCount++;
            }
        }

        Log.d(TAG, "Successfully processed " + successCount + "/" + total + " notes");

        // 只有在所有笔记都处理成功后才更新同步时间
        if (successCount == total) {
            // 使用云端最新修改时间更新 lastSyncTime
            if (maxModifiedTime.get() > 0) {
                updateLastSyncTime(maxModifiedTime.get());
                Log.d(TAG, "Updated last sync time to: " + maxModifiedTime.get());
            }
            return true;
        } else {
            Log.w(TAG, "Some notes failed to process, not updating sync time");
            return false;
        }
    }

    /**
     * 处理下载的单条笔记
     *
     * @return 是否处理成功
     */
    private boolean processDownloadedNote(NotesRepository repo, CloudNote cloudNote, String userId) {
        final CountDownLatch findLatch = new CountDownLatch(1);
        final AtomicReference<WorkingNote> localNoteRef = new AtomicReference<>();

        String cloudNoteId = cloudNote.getCloudNoteId();
        if (cloudNoteId != null && !cloudNoteId.isEmpty()) {
            repo.findByCloudNoteId(cloudNoteId, new NotesRepository.Callback<WorkingNote>() {
                @Override
                public void onSuccess(WorkingNote result) {
                    localNoteRef.set(result);
                    findLatch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to find note by cloudNoteId: " + cloudNoteId, e);
                    findLatch.countDown();
                }
            });
        } else {
            repo.findNoteByNoteId(cloudNote.getNoteId(), new NotesRepository.Callback<WorkingNote>() {
                @Override
                public void onSuccess(WorkingNote result) {
                    localNoteRef.set(result);
                    findLatch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to find note by noteId: " + cloudNote.getNoteId(), e);
                    findLatch.countDown();
                }
            });
        }

        try {
            boolean completed = findLatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "查询本地笔记超时: " + cloudNote.getCloudNoteId());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        WorkingNote localNote = localNoteRef.get();

        if (localNote == null) {
            // 本地不存在，插入新笔记
            Log.d(TAG, "Inserting new note from cloud: cloudNoteId=" + cloudNote.getCloudNoteId());
            WorkingNote newNote = cloudNote.toWorkingNote(mContext, userId);
            if (newNote != null) {
                newNote.saveNote();
                return true;
            }
            return false;
        } else {
            // 本地已存在，检查版本
            if (cloudNote.getModifiedTime() > localNote.getModifiedDate()) {
                if (localNote.getLocalModified() == 0) {
                    // 本地未修改，直接覆盖
                    Log.d(TAG, "Updating local note from cloud: cloudNoteId=" + cloudNote.getCloudNoteId());
                    localNote.updateFrom(cloudNote);
                    localNote.saveNote();
                    return true;
                } else {
                    // 双方都修改过，记录冲突
                    Log.d(TAG, "Conflict detected for note: cloudNoteId=" + cloudNote.getCloudNoteId());
                    mConflicts.add(new Conflict(localNote, cloudNote));
                    return true;
                }
            }
            return true;
        }
    }

    /**
     * 更新同步标志
     */
    private void updateSyncFlags() {
        long currentTime = System.currentTimeMillis();
        mPrefs.edit().putLong(KEY_LAST_SYNC, currentTime).apply();
        Log.d(TAG, "Sync time updated: " + currentTime);
    }

    /**
     * 更新最后同步时间
     */
    private void updateLastSyncTime(long syncTime) {
        mPrefs.edit().putLong(KEY_LAST_SYNC, syncTime).apply();
        Log.d(TAG, "Saved last sync time: " + syncTime);
    }

    /**
     * 标记已完成首次同步
     */
    private void markFirstSyncCompleted() {
        mPrefs.edit().putBoolean(KEY_IS_FIRST_SYNC, false).apply();
        Log.d(TAG, "First sync marked as completed");
    }

    /**
     * 检查是否是首次同步
     */
    public boolean isFirstSync() {
        return mPrefs.getBoolean(KEY_IS_FIRST_SYNC, true);
    }

    /**
     * 重置同步状态（用于重新安装后强制全量同步）
     */
    public void resetSyncState() {
        mPrefs.edit()
            .remove(KEY_LAST_SYNC)
            .putBoolean(KEY_IS_FIRST_SYNC, true)
            .apply();
        Log.d(TAG, "Sync state reset");
    }

    /**
     * 获取最后同步时间
     */
    public long getLastSyncTime() {
        return mPrefs.getLong(KEY_LAST_SYNC, 0);
    }

    /**
     * 获取待解决冲突列表
     */
    public List<Conflict> getPendingConflicts() {
        return new ArrayList<>(mConflicts);
    }

    /**
     * 清除所有冲突
     */
    public void clearAllConflicts() {
        mConflicts.clear();
        Log.d(TAG, "All conflicts cleared");
    }
}
