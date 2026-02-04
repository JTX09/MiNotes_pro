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

import android.content.Context;
import android.util.Log;

import com.alibaba.sdk.android.push.CloudPushService;
import com.alibaba.sdk.android.push.CommonCallback;
import com.alibaba.sdk.android.push.noonesdk.PushServiceFactory;

/**
 * 阿里云服务管理类
 * <p>
 * 管理阿里云EMAS服务的初始化和配置，包括推送服务、云数据库等。
 * </p>
 */
public class AliyunService {
    
    private static final String TAG = "AliyunService";
    private static AliyunService sInstance;
    private String mDeviceId;
    
    private AliyunService() {
        // Private constructor for singleton
    }
    
    /**
     * 获取AliyunService单例实例
     * 
     * @return AliyunService实例
     */
    public static synchronized AliyunService getInstance() {
        if (sInstance == null) {
            sInstance = new AliyunService();
        }
        return sInstance;
    }
    
    /**
     * 初始化阿里云服务
     * 
     * @param context 应用上下文
     */
    public void initialize(Context context) {
        Log.i(TAG, "Initializing AliyunService with AppKey: " + AliyunConfig.APP_KEY);
        
        try {
            // Initialize push service
            PushServiceFactory.init(context);
            CloudPushService pushService = PushServiceFactory.getCloudPushService();
            
            pushService.register(context, AliyunConfig.APP_KEY, AliyunConfig.APP_SECRET, new CommonCallback() {
                @Override
                public void onSuccess(String response) {
                    mDeviceId = pushService.getDeviceId();
                    Log.i(TAG, "Alibaba Cloud Push SDK registered successfully");
                    Log.i(TAG, "Device ID: " + mDeviceId);
                }
                
                @Override
                public void onFailed(String errorCode, String errorMessage) {
                    Log.e(TAG, "Alibaba Cloud Push SDK registration failed: " + errorCode + " - " + errorMessage);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AliyunService", e);
        }
    }
    
    /**
     * 获取设备ID
     * 
     * @return 设备ID
     */
    public String getDeviceId() {
        return mDeviceId;
    }
}
