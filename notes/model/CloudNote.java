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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.data.Notes;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 云端笔记数据模型
 * <p>
 * 用于表示从云数据库下载的笔记数据
 * </p>
 */
public class CloudNote {

    private static final String TAG = "CloudNote";

    private String mNoteId;
    private String mCloudNoteId;
    private String mTitle;
    private String mContent;
    private String mParentId;
    private int mType;
    private long mCreatedTime;
    private long mModifiedTime;
    private int mVersion;
    private String mDeviceId;

    /**
     * 从JSON构造CloudNote
     */
    public CloudNote(JSONObject json) throws JSONException {
        mCloudNoteId = json.optString("cloudNoteId", "");
        mNoteId = json.optString("noteId", "");
        mTitle = json.optString("title", "");
        mContent = json.optString("content", "");
        mParentId = json.optString("parentId", "0");
        mType = json.optInt("type", 0);
        mCreatedTime = json.optLong("createdTime", System.currentTimeMillis());
        mModifiedTime = json.optLong("modifiedTime", System.currentTimeMillis());
        mVersion = json.optInt("version", 1);
        mDeviceId = json.optString("deviceId", "");
    }

    /**
     * 从WorkingNote构造CloudNote（用于上传）
     */
    public CloudNote(WorkingNote note, String deviceId) {
        mCloudNoteId = note.getCloudNoteId() != null ? note.getCloudNoteId() : "";
        mNoteId = String.valueOf(note.getNoteId());
        mTitle = note.getTitle();
        mContent = note.getContent();
        mParentId = String.valueOf(note.getFolderId());
        mType = note.getType();
        mCreatedTime = System.currentTimeMillis();
        mModifiedTime = note.getModifiedDate();
        mVersion = 1;
        mDeviceId = deviceId;
    }

    /**
     * 转换为WorkingNote
     * 注意：这会创建一个新的本地笔记或更新现有笔记
     * 修复：先检查本地是否存在该云端ID的笔记，决定是创建还是更新
     */
    public WorkingNote toWorkingNote(Context context, String userId) {
        try {
            long noteId = Long.parseLong(mNoteId);

            // 先检查本地是否存在该云端ID的笔记
            boolean existsInLocal = noteExistsInDatabase(context, noteId);

            WorkingNote note;
            if (existsInLocal) {
                // 本地存在，加载并更新
                note = WorkingNote.load(context, noteId);
                Log.d(TAG, "Updating existing note from cloud: " + noteId);
            } else {
                // 本地不存在，创建新笔记（不指定ID，让数据库生成）
                note = WorkingNote.createEmptyNote(context, 0);
                Log.d(TAG, "Creating new note from cloud, cloud ID: " + noteId);
            }

            note.setType(mType);
            note.setTitle(mTitle);
            note.setContent(mContent);
            note.setFolderId(Long.parseLong(mParentId));
            note.setModifiedDate(mModifiedTime);
            note.setCloudUserId(userId);

            note.setCloudNoteId(mCloudNoteId);
            note.setSyncStatus(net.micode.notes.sync.SyncConstants.SYNC_STATUS_SYNCED);
            note.setLocalModified(0);
            note.setLastSyncTime(System.currentTimeMillis());

            return note;
        } catch (Exception e) {
            Log.e(TAG, "转换WorkingNote失败", e);
            return null;
        }
    }

    /**
     * 检查指定ID的笔记是否在本地数据库存在
     * @param context 应用上下文
     * @param noteId 笔记ID（云端ID）
     * @return 如果存在返回 true，否则返回 false
     */
    private boolean noteExistsInDatabase(Context context, long noteId) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                    new String[]{Notes.NoteColumns.ID},
                    null,
                    null,
                    null
            );
            boolean exists = cursor != null && cursor.getCount() > 0;
            Log.d(TAG, "Note exists in local DB: " + noteId + " - " + exists);
            return exists;
        } catch (Exception e) {
            Log.e(TAG, "Failed to check note existence: " + noteId, e);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * 转换为WorkingNote（向后兼容，使用默认userId）
     * @deprecated 请使用 {@link #toWorkingNote(Context, String)}
     */
    @Deprecated
    public WorkingNote toWorkingNote(Context context) {
        return toWorkingNote(context, "");
    }

    /**
     * 转换为JSON（用于上传）
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        if (mCloudNoteId != null && !mCloudNoteId.isEmpty()) {
            json.put("cloudNoteId", mCloudNoteId);
        }
        json.put("noteId", mNoteId);
        json.put("title", mTitle);
        json.put("content", mContent);
        json.put("parentId", mParentId);
        json.put("type", mType);
        json.put("createdTime", mCreatedTime);
        json.put("modifiedTime", mModifiedTime);
        json.put("version", mVersion);
        json.put("deviceId", mDeviceId);
        return json;
    }

    // Getters
    public String getCloudNoteId() { return mCloudNoteId; }
    public String getNoteId() { return mNoteId; }
    public String getTitle() { return mTitle; }
    public String getContent() { return mContent; }
    public String getParentId() { return mParentId; }
    public int getType() { return mType; }
    public long getCreatedTime() { return mCreatedTime; }
    public long getModifiedTime() { return mModifiedTime; }
    public int getVersion() { return mVersion; }
    public String getDeviceId() { return mDeviceId; }
}
