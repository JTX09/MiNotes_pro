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

package net.micode.notes.model;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;


/**
 * 笔记数据模型类
 * <p>
 * 负责管理笔记的基本信息和数据内容，支持笔记的创建、修改和同步操作。
 * 包含笔记元数据（如创建时间、修改时间、父文件夹等）和笔记数据（文本、通话记录等）。
 * </p>
 */
public class Note {
    /** 笔记差异值，用于记录需要同步的字段变更 */
    private ContentValues mNoteDiffValues;
    
    /** 笔记数据对象，包含文本数据和通话数据 */
    private NoteData mNoteData;
    
    /** 日志标签 */
    private static final String TAG = "Note";
    
    /**
     * 创建新笔记 ID
     * <p>
     * 在数据库中创建一条新笔记记录，并返回其 ID。
     * 初始化笔记的创建时间、修改时间、类型和父文件夹 ID。
     * </p>
     *
     * @param context 应用上下文
     * @param folderId 父文件夹 ID
     * @return 新创建的笔记 ID，失败时返回 0
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        return getNewNoteId(context, folderId, Notes.TYPE_NOTE);
    }

    /**
     * 创建新笔记 ID（支持指定类型）
     * <p>
     * 在数据库中创建一条新笔记记录，并返回其 ID。
     * 初始化笔记的创建时间、修改时间、类型和父文件夹 ID。
     * </p>
     *
     * @param context 应用上下文
     * @param folderId 父文件夹 ID
     * @param type 笔记类型：0=普通笔记, 1=文件夹, 2=系统, 3=待办
     * @return 新创建的笔记 ID，失败时返回 0
     */
    public static synchronized long getNewNoteId(Context context, long folderId, int type) {
        // 在数据库中创建新笔记
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, type);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从 URI 中提取笔记 ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造函数
     * <p>
     * 初始化笔记差异值和笔记数据对象。
     * </p>
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置笔记属性值
     * <p>
     * 设置笔记的指定属性值，并标记为本地修改。
     * </p>
     * 
     * @param key 属性键名
     * @param value 属性值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置文本数据
     * <p>
     * 设置笔记的文本数据内容。
     * </p>
     * 
     * @param key 数据键名
     * @param value 数据值
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据 ID
     * <p>
     * 设置笔记文本数据的数据库记录 ID。
     * </p>
     * 
     * @param id 文本数据 ID
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据 ID
     * 
     * @return 文本数据 ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话数据 ID
     * <p>
     * 设置笔记通话数据的数据库记录 ID。
     * </p>
     * 
     * @param id 通话数据 ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话数据
     * <p>
     * 设置笔记的通话数据内容。
     * </p>
     * 
     * @param key 数据键名
     * @param value 数据值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 检查是否本地修改
     * <p>
     * 检查笔记是否有本地未同步的修改。
     * </p>
     * 
     * @return 如果有本地修改返回 true，否则返回 false
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 同步笔记到数据库
     * <p>
     * 将笔记的本地修改同步到数据库。
     * 更新笔记元数据和数据内容。
     * </p>
     * 
     * @param context 应用上下文
     * @param noteId 笔记 ID
     * @return 如果同步成功返回 true，否则返回 false
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {
            return true;
        }

        /**
         * 理论上，数据变更后应更新 {@link NoteColumns#LOCAL_MODIFIED} 和
         * {@link NoteColumns#MODIFIED_DATE}。为数据安全，即使更新失败也更新笔记数据信息
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // 不返回，继续执行
        }
        mNoteDiffValues.clear();

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 笔记数据内部类
     * <p>
     * 管理笔记的文本数据和通话数据。
     * 支持数据的增删改查和批量同步操作。
     * </p>
     */
    private class NoteData {
        /** 文本数据 ID */
        private long mTextDataId;

        /** 文本数据值 */
        private ContentValues mTextDataValues;

        /** 通话数据 ID */
        private long mCallDataId;

        /** 通话数据值 */
        private ContentValues mCallDataValues;

        /** 日志标签 */
        private static final String TAG = "NoteData";

        /**
         * 构造函数
         * <p>
         * 初始化文本数据和通话数据的 ContentValues 对象。
         * </p>
         */
        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 检查是否本地修改
         * <p>
         * 检查文本数据或通话数据是否有本地未同步的修改。
         * </p>
         * 
         * @return 如果有本地修改返回 true，否则返回 false
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据 ID
         * <p>
         * 设置文本数据的数据库记录 ID。
         * </p>
         * 
         * @param id 文本数据 ID，必须大于 0
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话数据 ID
         * <p>
         * 设置通话数据的数据库记录 ID。
         * </p>
         * 
         * @param id 通话数据 ID，必须大于 0
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话数据
         * <p>
         * 设置笔记的通话数据内容，并标记为本地修改。
         * </p>
         * 
         * @param key 数据键名
         * @param value 数据值
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据
         * <p>
         * 设置笔记的文本数据内容，并标记为本地修改。
         * </p>
         * 
         * @param key 数据键名
         * @param value 数据值
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将数据推送到 ContentResolver
         * <p>
         * 将文本数据和通话数据的修改同步到数据库。
         * 支持新增和更新操作。
         * </p>
         * 
         * @param context 应用上下文
         * @param noteId 笔记 ID
         * @return 笔记 URI，失败时返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * 安全性检查
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本数据
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 新增文本数据
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新现有文本数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // 处理通话数据
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 新增通话数据
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新现有通话数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 批量执行更新操作
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    if (results == null || results.length == 0 || results[0] == null) {
                        return null;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
        }

        // ==================== 云同步相关方法 ====================

        void setCloudUserId(String userId) {
            mNoteDiffValues.put(NoteColumns.CLOUD_USER_ID, userId);
        }

        String getCloudUserId() {
            return mNoteDiffValues.getAsString(NoteColumns.CLOUD_USER_ID);
        }

        void setCloudDeviceId(String deviceId) {
            mNoteDiffValues.put(NoteColumns.CLOUD_DEVICE_ID, deviceId);
        }

        String getCloudDeviceId() {
            return mNoteDiffValues.getAsString(NoteColumns.CLOUD_DEVICE_ID);
        }

        void setSyncStatus(int status) {
            mNoteDiffValues.put(NoteColumns.SYNC_STATUS, status);
        }

        int getSyncStatus() {
            Integer status = mNoteDiffValues.getAsInteger(NoteColumns.SYNC_STATUS);
            return status != null ? status : 0;
        }

        void setLastSyncTime(long time) {
            mNoteDiffValues.put(NoteColumns.LAST_SYNC_TIME, time);
        }

        long getLastSyncTime() {
            Long time = mNoteDiffValues.getAsLong(NoteColumns.LAST_SYNC_TIME);
            return time != null ? time : 0;
        }

        void setLocalModified(int modified) {
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, modified);
        }

        int getLocalModified() {
            Integer modified = mNoteDiffValues.getAsInteger(NoteColumns.LOCAL_MODIFIED);
            return modified != null ? modified : 0;
        }
    }
}
