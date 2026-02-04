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

import net.micode.notes.BuildConfig;

/**
 * 阿里云EMAS配置
 * <p>
 * 存储阿里云EMAS服务的配置信息，从BuildConfig读取（由local.properties生成）
 * 敏感信息不再硬编码在源码中，避免泄露风险
 * </p>
 */
public class AliyunConfig {

    /**
     * 阿里云EMAS AppKey (从BuildConfig读取)
     */
    public static final String APP_KEY = BuildConfig.ALIYUN_APP_KEY;

    /**
     * 阿里云EMAS AppSecret (从BuildConfig读取)
     */
    public static final String APP_SECRET = BuildConfig.ALIYUN_APP_SECRET;

    /**
     * 阿里云EMAS Serverless Space ID (从BuildConfig读取)
     */
    public static final String SPACE_ID = BuildConfig.ALIYUN_SPACE_ID;

    /**
     * 服务端点（EMAS Serverless HTTP触发器）(从BuildConfig读取)
     */
    public static final String ENDPOINT = BuildConfig.ALIYUN_ENDPOINT;

    /**
     * API基础路径
     */
    public static final String BASE_URL = ENDPOINT + "/api";

    private AliyunConfig() {
        // Utility class, prevent instantiation
    }
}
