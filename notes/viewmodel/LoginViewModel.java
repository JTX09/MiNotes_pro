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

package net.micode.notes.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.micode.notes.auth.AnonymousAuthManager;
import net.micode.notes.auth.UserAuthManager;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.sync.SyncManager;

import java.util.List;

/**
 * 登录界面 ViewModel
 *
 * <p>
 * 管理登录相关的业务逻辑，包括用户认证、匿名数据迁移、首次登录全量同步等。
 * 遵循 MVVM 架构，将业务逻辑从 Activity 中分离。
 * </p>
 * <p>
 * 修复记录：
 * 1. 修复登录后缺少全量下载的问题 - 登录后强制执行全量同步
 * 2. 添加同步状态监听 - 同步完成后再通知登录成功
 * 3. 优化匿名数据迁移流程 - 迁移后执行全量同步确保数据完整
 * </p>
 */
public class LoginViewModel extends AndroidViewModel {

    private static final String TAG = "LoginViewModel";

    private final UserAuthManager mAuthManager;
    private final AnonymousAuthManager mAnonymousAuthManager;
    private final NotesRepository mNotesRepository;

    private final MutableLiveData<Boolean> mIsLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> mErrorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mLoginSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> mMigratedNotesCount = new MutableLiveData<>();
    private final MutableLiveData<String> mSyncStatus = new MutableLiveData<>();

    public LoginViewModel(@NonNull Application application) {
        super(application);
        mAuthManager = UserAuthManager.getInstance(application);
        mAnonymousAuthManager = AnonymousAuthManager.getInstance(application);
        mNotesRepository = new NotesRepository(application.getContentResolver());
    }

    public LiveData<Boolean> getIsLoading() {
        return mIsLoading;
    }

    public LiveData<String> getErrorMessage() {
        return mErrorMessage;
    }

    public LiveData<Boolean> getLoginSuccess() {
        return mLoginSuccess;
    }

    public LiveData<Integer> getMigratedNotesCount() {
        return mMigratedNotesCount;
    }

    public LiveData<String> getSyncStatus() {
        return mSyncStatus;
    }

    /**
     * 检查用户是否已登录
     */
    public boolean isLoggedIn() {
        return mAuthManager.isLoggedIn();
    }

    /**
     * 用户登录
     */
    public void login(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            mErrorMessage.setValue("请输入用户名和密码");
            return;
        }

        mIsLoading.setValue(true);
        mSyncStatus.setValue("正在登录...");

        mAuthManager.login(username, password, new UserAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId, String username) {
                mSyncStatus.postValue("登录成功，正在同步数据...");
                // 登录成功后执行数据迁移和全量同步
                migrateAnonymousDataAndSync(userId);
            }

            @Override
            public void onError(String error) {
                mIsLoading.postValue(false);
                mSyncStatus.postValue("登录失败");
                mErrorMessage.postValue("登录失败: " + error);
            }
        });
    }

    /**
     * 用户注册
     */
    public void register(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            mErrorMessage.setValue("请输入用户名和密码");
            return;
        }

        if (password.length() < 6) {
            mErrorMessage.setValue("密码长度至少6位");
            return;
        }

        mIsLoading.setValue(true);
        mSyncStatus.setValue("正在注册...");

        mAuthManager.register(username, password, new UserAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId, String username) {
                mSyncStatus.postValue("注册成功，正在同步数据...");
                // 注册成功后执行数据迁移和全量同步
                migrateAnonymousDataAndSync(userId);
            }

            @Override
            public void onError(String error) {
                mIsLoading.postValue(false);
                mSyncStatus.postValue("注册失败");
                mErrorMessage.postValue("注册失败: " + error);
            }
        });
    }

    /**
     * 新用户接管设备上的所有笔记并执行全量同步
     *
     * <p>关键逻辑：
     * 1. 新用户接管设备上所有笔记（无论之前属于谁）
     * 2. 将所有笔记标记为需要同步
     * 3. 执行全量同步，上传到云端
     * </p>
     */
    private void migrateAnonymousDataAndSync(String newUserId) {
        mSyncStatus.postValue("正在接管设备上的笔记...");

        // 新用户接管设备上所有笔记
        mNotesRepository.takeoverAllNotes(newUserId, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                Log.d(TAG, "Takeover completed: " + count + " notes now belong to " + newUserId);
                mMigratedNotesCount.postValue(count);
                // 接管完成后执行全量同步（上传所有笔记到云端）
                performFullSync();
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Failed to takeover notes", error);
                mMigratedNotesCount.postValue(0);
                // 即使接管失败也尝试同步
                performFullSync();
            }
        });
    }

    /**
     * 执行全量同步
     *
     * <p>关键逻辑：
     * 1. 首先上传设备上所有笔记到新用户的云端
     * 2. 然后下载云端其他笔记（如果有）
     * 这样新用户可以把设备上的所有内容都保存到云端。
     * </p>
     */
    private void performFullSync() {
        Log.d(TAG, "Performing full sync after login");
        mSyncStatus.postValue("正在上传笔记到云端...");

        // 初始化同步管理器
        SyncManager.getInstance().initialize(getApplication());

        // 重置同步状态
        SyncManager.getInstance().resetSyncState();

        // 第一步：上传所有本地笔记到云端
        SyncManager.getInstance().uploadAllNotes(new SyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Upload all notes completed");
                mSyncStatus.postValue("上传完成，正在下载云端笔记...");

                // 第二步：下载云端其他笔记（如果有）
                downloadCloudNotes();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Upload failed: " + error);
                // 即使上传失败也尝试下载
                downloadCloudNotes();
            }
        });
    }

    /**
     * 下载云端笔记
     */
    private void downloadCloudNotes() {
        SyncManager.getInstance().syncAllNotes(new SyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Download cloud notes completed");
                mSyncStatus.postValue("同步完成");
                mIsLoading.postValue(false);
                mLoginSuccess.postValue(true);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Download failed: " + error);
                mSyncStatus.postValue("同步完成（下载可能有部分失败）");
                mIsLoading.postValue(false);
                mLoginSuccess.postValue(true);
            }
        });
    }

    /**
     * 清除错误消息
     */
    public void clearError() {
        mErrorMessage.setValue(null);
    }
}
