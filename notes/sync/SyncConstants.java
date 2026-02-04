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

/**
 * 同步常量定义
 * <p>
 * 定义云同步功能中使用的所有常量，包括同步状态、错误码等。
 * </p>
 */
public class SyncConstants {
    
    private SyncConstants() {
        // Utility class, prevent instantiation
    }
    
    /**
     * 同步状态：未同步
     */
    public static final int SYNC_STATUS_NOT_SYNCED = 0;
    
    /**
     * 同步状态：同步中
     */
    public static final int SYNC_STATUS_SYNCING = 1;
    
    /**
     * 同步状态：已同步
     */
    public static final int SYNC_STATUS_SYNCED = 2;
    
    /**
     * 同步状态：冲突
     */
    public static final int SYNC_STATUS_CONFLICT = 3;
    
    /**
     * 同步广播Action
     */
    public static final String ACTION_SYNC = "com.micode.notes.ACTION_SYNC";
}
