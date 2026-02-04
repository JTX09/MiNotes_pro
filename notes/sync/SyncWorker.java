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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ExistingPeriodicWorkPolicy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 同步Worker
 * <p>
 * 使用WorkManager执行后台同步任务，定期同步笔记到云端。
 * </p>
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private static final String WORK_NAME = "cloudSync";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting background sync work");

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};

        SyncManager.getInstance().syncNotes(new SyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Background sync completed successfully");
                success[0] = true;
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Background sync failed: " + error);
                success[0] = false;
                latch.countDown();
            }
        });

        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sync work interrupted", e);
            return Result.retry();
        }

        return success[0] ? Result.success() : Result.retry();
    }

    /**
     * 初始化定期同步任务
     *
     * @param context 应用上下文
     */
    public static void initialize(Context context) {
        Log.d(TAG, "Initializing periodic sync work");

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                syncWork);

        Log.d(TAG, "Periodic sync work scheduled (30 minutes interval)");
    }

    /**
     * 取消定期同步任务
     *
     * @param context 应用上下文
     */
    public static void cancel(Context context) {
        Log.d(TAG, "Canceling periodic sync work");
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
