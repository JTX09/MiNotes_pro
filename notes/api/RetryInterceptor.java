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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp 重试拦截器
 *
 * <p>
 * 实现指数退避重试策略，对网络超时和临时错误进行自动重试。
 * 支持最多3次重试，每次重试间隔递增（1秒、2秒、4秒）。
 * </p>
 */
public class RetryInterceptor implements Interceptor {

    private static final String TAG = "RetryInterceptor";
    private static final int MAX_RETRY_COUNT = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = null;
        IOException exception = null;

        for (int retryCount = 0; retryCount <= MAX_RETRY_COUNT; retryCount++) {
            try {
                response = chain.proceed(request);

                // 如果响应成功，直接返回
                if (response.isSuccessful()) {
                    return response;
                }

                // 对于服务器错误 (5xx) 进行重试
                if (response.code() >= 500 && response.code() < 600) {
                    if (retryCount < MAX_RETRY_COUNT) {
                        Log.w(TAG, "Server error " + response.code() + ", retrying... (" + (retryCount + 1) + "/" + MAX_RETRY_COUNT + ")");
                        response.close();
                        waitBeforeRetry(retryCount);
                        continue;
                    }
                } else {
                    // 客户端错误 (4xx) 不重试
                    return response;
                }
            } catch (SocketTimeoutException | UnknownHostException e) {
                // 网络超时和主机不可达时重试
                exception = e;
                if (retryCount < MAX_RETRY_COUNT) {
                    Log.w(TAG, "Network error, retrying... (" + (retryCount + 1) + "/" + MAX_RETRY_COUNT + "): " + e.getMessage());
                    waitBeforeRetry(retryCount);
                } else {
                    throw e;
                }
            } catch (IOException e) {
                // 其他 IO 异常也尝试重试
                exception = e;
                if (retryCount < MAX_RETRY_COUNT) {
                    Log.w(TAG, "IO error, retrying... (" + (retryCount + 1) + "/" + MAX_RETRY_COUNT + "): " + e.getMessage());
                    waitBeforeRetry(retryCount);
                } else {
                    throw e;
                }
            }
        }

        // 如果所有重试都失败了
        if (exception != null) {
            throw exception;
        }

        return response;
    }

    /**
     * 指数退避等待
     */
    private void waitBeforeRetry(int retryCount) {
        long delay = INITIAL_RETRY_DELAY_MS * (1L << retryCount); // 1s, 2s, 4s
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
