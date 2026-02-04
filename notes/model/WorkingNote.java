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

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;


/**
 * 工作笔记类
 * <p>
 * 表示正在编辑或查看的笔记对象，提供笔记的加载、保存和修改功能。
 * 支持文本笔记和通话记录笔记两种类型，包含笔记的所有属性和设置监听器。
 * </p>
 */
public class WorkingNote {
    /** 底层笔记对象 */
    private Note mNote;
    
    /** 笔记 ID */
    private long mNoteId;
    
    /** 笔记内容 */
    private String mContent;
    
    /** 笔记模式 */
    private int mMode;

    /** 笔记标题 */
    private String mTitle;

    /** 提醒日期 */
    private long mAlertDate;

    /** 修改日期 */
    private long mModifiedDate;

    /** 背景颜色 ID */
    private int mBgColorId;

    /** Widget ID */
    private int mWidgetId;

    /** Widget 类型 */
    private int mWidgetType;

    /** 父文件夹 ID */
    private long mFolderId;

    /** 笔记类型: 0=普通笔记, 1=文件夹, 2=系统, 3=待办 */
    private int mType;

    /** 应用上下文 */
    private Context mContext;

    /** 同步状态 */
    private int mSyncStatus;

    /** 最后同步时间 */
    private long mLastSyncTime;

    /** 本地修改标记 */
    private int mLocalModified;

    /** 云端用户ID */
    private String mCloudUserId;

    /** 云端设备ID */
    private String mCloudDeviceId;

    /** 云端笔记ID */
    private String mCloudNoteId;

    /** 日志标签 */
    private static final String TAG = "WorkingNote";

    /** 是否已删除 */
    private boolean mIsDeleted;

    /** 笔记设置变更监听器 */
    private NoteSettingChangedListener mNoteSettingStatusListener;

    /** 数据查询投影 - 笔记数据 */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
            DataColumns.DATA5,
    };

    /** 数据查询投影 - 笔记元数据 */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.LOCAL_MODIFIED,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.TITLE,
            NoteColumns.TYPE,
            NoteColumns.SNIPPET
    };

    /** 数据 ID 列索引 */
    private static final int DATA_ID_COLUMN = 0;

    /** 数据内容列索引 */
    private static final int DATA_CONTENT_COLUMN = 1;

    /** 数据 MIME 类型列索引 */
    private static final int DATA_MIME_TYPE_COLUMN = 2;

    /** 数据模式列索引 */
    private static final int DATA_MODE_COLUMN = 3;

    /** 笔记父 ID 列索引 */
    private static final int NOTE_PARENT_ID_COLUMN = 0;

    /** 笔记提醒日期列索引 */
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;

    /** 笔记背景颜色 ID 列索引 */
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;

    /** 笔记 Widget ID 列索引 */
    private static final int NOTE_WIDGET_ID_COLUMN = 3;

    /** 笔记 Widget 类型列索引 */
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;

    /** 笔记修改日期列索引 */
    private static final int NOTE_MODIFIED_DATE_COLUMN = 6;

    /** 笔记类型列索引 */
    private static final int NOTE_TYPE_COLUMN = 8;

    /** 云端笔记ID列索引 */
    private static final int NOTE_CLOUD_NOTE_ID_COLUMN = 9;

    /**
     * 新建笔记构造函数
     * <p>
     * 创建一个新的空笔记对象，初始化所有属性为默认值。
     * </p>
     * 
     * @param context 应用上下文
     * @param folderId 父文件夹 ID
     */
    // New note construct
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mTitle = "";
        mContent = "";
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mLocalModified = 1; // 新建笔记需要同步
        
        // Determine type based on folder
        if (folderId == Notes.ID_TEMPLATE_FOLDER) {
            mType = Notes.TYPE_TEMPLATE;
        } else if (folderId > 0) {
            // Check if parent is template folder
            int parentType = net.micode.notes.tool.DataUtils.getNoteTypeById(context.getContentResolver(), folderId);
            if (parentType == Notes.TYPE_FOLDER) {
                // We need to check the folder's parent
                long parentId = 0;
                android.database.Cursor c = context.getContentResolver().query(
                        android.content.ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId),
                        new String[] { NoteColumns.PARENT_ID }, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        parentId = c.getLong(0);
                    }
                    c.close();
                }
                if (parentId == Notes.ID_TEMPLATE_FOLDER) {
                    mType = Notes.TYPE_TEMPLATE;
                } else {
                    mType = Notes.TYPE_NOTE;
                }
            } else {
                mType = Notes.TYPE_NOTE;
            }
        } else {
            mType = Notes.TYPE_NOTE;
        }
    }

    /**
     * 已有笔记构造函数
     * <p>
     * 从数据库加载现有笔记数据，初始化笔记对象。
     * </p>
     * 
     * @param context 应用上下文
     * @param noteId 笔记 ID
     * @param folderId 父文件夹 ID
     */
    // Existing note construct
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    /**
     * 加载笔记元数据
     * <p>
     * 从数据库加载笔记的基本信息，包括父文件夹、背景颜色、Widget 信息等。
     * </p>
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
                mType = cursor.getInt(NOTE_TYPE_COLUMN);

                // Load title
                int titleIndex = cursor.getColumnIndex(NoteColumns.TITLE);
                if (titleIndex != -1) {
                    mTitle = cursor.getString(titleIndex);
                } else {
                    mTitle = "";
                }

                // If it's a folder and title is empty, try loading from snippet
                if (mType == Notes.TYPE_FOLDER && TextUtils.isEmpty(mTitle)) {
                    int snippetIndex = cursor.getColumnIndex(NoteColumns.SNIPPET);
                    if (snippetIndex != -1) {
                        mTitle = cursor.getString(snippetIndex);
                    }
                }

                // Load cloud note id
                int cloudNoteIdIndex = cursor.getColumnIndex(NoteColumns.CLOUD_NOTE_ID);
                if (cloudNoteIdIndex != -1) {
                    mCloudNoteId = cursor.getString(cloudNoteIdIndex);
                } else {
                    mCloudNoteId = "";
                }
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();
    }

    /**
     * 加载笔记数据内容
     * <p>
     * 从数据库加载笔记的详细数据，包括文本内容和通话记录。
     * </p>
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                    String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        // 加载文本笔记数据
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                        
                        // 加载壁纸路径
                        int wallpaperIndex = cursor.getColumnIndex(DataColumns.DATA5);
                        if (wallpaperIndex != -1) {
                            String path = cursor.getString(wallpaperIndex);
                            if (!TextUtils.isEmpty(path)) {
                                mWallpaperPath = path;
                            } else {
                                mWallpaperPath = null;
                            }
                        }
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        // 加载通话记录数据
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 创建空笔记
     * <p>
     * 创建一个新的空笔记对象，并设置默认属性。
     * </p>
     *
     * @param context 应用上下文
     * @param folderId 父文件夹 ID
     * @param widgetId Widget ID
     * @param widgetType Widget 类型
     * @param defaultBgColorId 默认背景颜色 ID
     * @return 新创建的 WorkingNote 对象
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 创建空笔记（简化版）
     * <p>
     * 创建一个新的空笔记对象，用于云同步。
     * </p>
     *
     * @param context 应用上下文
     * @param noteId 笔记 ID
     * @return 新创建的 WorkingNote 对象
     */
    public static WorkingNote createEmptyNote(Context context, long noteId) {
        WorkingNote note = new WorkingNote(context, noteId, 0);
        return note;
    }

    /**
     * 加载已有笔记
     * <p>
     * 从数据库加载指定 ID 的笔记。
     * </p>
     * 
     * @param context 应用上下文
     * @param id 笔记 ID
     * @return 加载的 WorkingNote 对象
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 保存笔记
     * <p>
     * 将笔记的修改保存到数据库。
     * 如果笔记不存在则创建新笔记，否则更新现有笔记。
     * 如果有 Widget 则更新 Widget 内容。
     * </p>
     * 
     * @return 如果保存成功返回 true，否则返回 false
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            if (!existInDatabase()) {
            // 创建新笔记
            if ((mNoteId = Note.getNewNoteId(mContext, mFolderId, mType)) == 0) {
                Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.setNoteValue(NoteColumns.MODIFIED_DATE, String.valueOf(System.currentTimeMillis()));
            if (mTitle != null) {
                mNote.setNoteValue(NoteColumns.TITLE, mTitle);
            }

            // 同步笔记数据
            mNote.syncNote(mContext, mNoteId);

            /**
             * 如果存在该笔记的 Widget，则更新 Widget 内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 检查笔记是否存在于数据库
     * 
     * @return 如果笔记 ID 大于 0 返回 true，否则返回 false
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 检查是否值得保存
     * <p>
     * 判断笔记是否有需要保存的修改。
     * </p>
     * 
     * @return 如果值得保存返回 true，否则返回 false
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent) && TextUtils.isEmpty(mWallpaperPath))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置笔记设置变更监听器
     * 
     * @param l 监听器对象
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置提醒日期
     * <p>
     * 设置笔记的提醒日期，并通知监听器。
     * </p>
     * 
     * @param date 提醒日期（毫秒时间戳）
     * @param set 是否设置提醒
     */
    public void setTitle(String title) {
        mTitle = title;
        mNote.setNoteValue(NoteColumns.TITLE, mTitle);
        // 只有文件夹需要将标题同步到SNIPPET字段（文件夹名存储在SNIPPET中以保持兼容性）
        // 普通便签的SNIPPET应由内容触发器自动维护
        if (mType == Notes.TYPE_FOLDER) {
            mNote.setNoteValue(NoteColumns.SNIPPET, mTitle);
        }
    }

    public String getTitle() {
        return mTitle;
    }

    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记删除
     * <p>
     * 标记笔记为删除状态，并更新 Widget。
     * </p>
     * 
     * @param mark 是否标记为删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置背景颜色 ID
     * <p>
     * 设置笔记的背景颜色，并通知监听器。
     * </p>
     * 
     * @param id 背景颜色 ID
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    // Wallpaper Support
    private String mWallpaperPath;

    public void setWallpaper(String path) {
        if (!TextUtils.equals(mWallpaperPath, path)) {
            mWallpaperPath = path;
            mNote.setTextData(DataColumns.DATA5, mWallpaperPath);
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged(); // Reuse this to trigger refresh
            }
        }
    }

    public String getWallpaperPath() {
        return mWallpaperPath;
    }

    /**
     * 设置清单模式
     * <p>
     * 设置笔记的编辑模式（普通模式或清单模式），并通知监听器。
     * </p>
     * 
     * @param mode 模式值
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 设置 Widget 类型
     * 
     * @param type Widget 类型
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置 Widget ID
     * 
     * @param id Widget ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置工作文本
     * <p>
     * 设置笔记的文本内容。
     * </p>
     * 
     * @param text 文本内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 转换为通话记录笔记
     * <p>
     * 将笔记转换为通话记录类型，设置电话号码和通话日期。
     * </p>
     * 
     * @param phoneNumber 电话号码
     * @param callDate 通话日期（毫秒时间戳）
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    /**
     * 检查是否有提醒
     * 
     * @return 如果有提醒返回 true，否则返回 false
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    /**
     * 获取笔记内容
     * 
     * @return 笔记内容字符串
     */
    public String getContent() {
        return mContent;
    }

    /**
     * 获取提醒日期
     * 
     * @return 提醒日期（毫秒时间戳）
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取修改日期
     * 
     * @return 修改日期（毫秒时间戳）
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取背景颜色资源 ID
     * 
     * @return 背景颜色资源 ID
     */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    /**
     * 获取背景颜色 ID
     * 
     * @return 背景颜色 ID
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取标题背景资源 ID
     * 
     * @return 标题背景资源 ID
     */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    /**
     * 获取清单模式
     * 
     * @return 清单模式值
     */
    public int getCheckListMode() {
        return mMode;
    }

    /**
     * 获取笔记 ID
     * 
     * @return 笔记 ID
     */
    public long getNoteId() {
        return mNoteId;
    }

    /**
     * 获取父文件夹 ID
     * 
     * @return 父文件夹 ID
     */
    public long getFolderId() {
        return mFolderId;
    }

    /**
     * 获取 Widget ID
     * 
     * @return Widget ID
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取 Widget 类型
     *
     * @return Widget 类型
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    // ==================== 云同步相关方法 ====================

    /**
     * 设置云端用户ID
     */
    public void setCloudUserId(String userId) {
        mCloudUserId = userId;
    }

    /**
     * 获取云端用户ID
     */
    public String getCloudUserId() {
        return mCloudUserId;
    }

    /**
     * 设置云端设备ID
     */
    public void setCloudDeviceId(String deviceId) {
        mCloudDeviceId = deviceId;
    }

    /**
     * 获取云端设备ID
     */
    public String getCloudDeviceId() {
        return mCloudDeviceId;
    }

    /**
     * 设置同步状态
     * @param status 0=未同步, 1=同步中, 2=已同步, 3=冲突
     */
    public void setSyncStatus(int status) {
        mSyncStatus = status;
    }

    /**
     * 获取同步状态
     */
    public int getSyncStatus() {
        return mSyncStatus;
    }

    /**
     * 设置最后同步时间
     */
    public void setLastSyncTime(long time) {
        mLastSyncTime = time;
    }

    /**
     * 获取最后同步时间
     */
    public long getLastSyncTime() {
        return mLastSyncTime;
    }

    /**
     * 设置本地修改标记
     * @param modified 0=未修改, 1=已修改
     */
    public void setLocalModified(int modified) {
        mLocalModified = modified;
    }

    /**
     * 获取本地修改标记
     */
    public int getLocalModified() {
        return mLocalModified;
    }

    /**
     * 设置笔记内容
     */
    public void setContent(String content) {
        mContent = content;
        mNote.setTextData(DataColumns.CONTENT, mContent);
    }

    /**
     * 设置文件夹ID
     */
    public void setFolderId(long folderId) {
        mFolderId = folderId;
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(mFolderId));
    }

    /**
     * 设置修改日期
     */
    public void setModifiedDate(long date) {
        mModifiedDate = date;
        mNote.setNoteValue(NoteColumns.MODIFIED_DATE, String.valueOf(mModifiedDate));
    }

    /**
     * 设置笔记类型
     * @param type 0=普通笔记, 1=文件夹, 2=系统, 3=待办
     */
    public void setType(int type) {
        mType = type;
        mNote.setNoteValue(NoteColumns.TYPE, String.valueOf(mType));
    }

    /**
     * 获取笔记类型
     * @return 0=普通笔记, 1=文件夹, 2=系统, 3=待办
     */
    public int getType() {
        return mType;
    }

    /**
     * 获取云端笔记ID
     * @return 云端笔记ID（UUID），未上传时为空字符串
     */
    public String getCloudNoteId() {
        return mCloudNoteId;
    }

    /**
     * 设置云端笔记ID
     * @param cloudNoteId 云端笔记ID（UUID）
     */
    public void setCloudNoteId(String cloudNoteId) {
        mCloudNoteId = cloudNoteId;
        mNote.setNoteValue(NoteColumns.CLOUD_NOTE_ID, mCloudNoteId != null ? mCloudNoteId : "");
    }

    /**
     * 从CloudNote更新当前笔记
     * 用于云端下载后更新本地笔记
     */
    public void updateFrom(CloudNote cloudNote) {
        setTitle(cloudNote.getTitle());
        setContent(cloudNote.getContent());
        setFolderId(Long.parseLong(cloudNote.getParentId()));
        setModifiedDate(cloudNote.getModifiedTime());
        setType(cloudNote.getType());
        setCloudNoteId(cloudNote.getCloudNoteId());
        setSyncStatus(net.micode.notes.sync.SyncConstants.SYNC_STATUS_SYNCED);
        setLastSyncTime(System.currentTimeMillis());
        setLocalModified(0);
        saveNote();
    }

    /**
     * 笔记设置变更监听器接口
     * <p>
     * 定义笔记设置变更时的回调方法，用于通知 UI 更新。
     * </p>
     */
    public interface NoteSettingChangedListener {
        /**
         * 当前笔记背景颜色变更时调用
         */
        void onBackgroundColorChanged();

        /**
         * 用户设置闹钟时调用
         * 
         * @param date 提醒日期
         * @param set 是否设置提醒
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 用户从 Widget 创建笔记时调用
         */
        void onWidgetChanged();

        /**
         * 在清单模式和普通模式之间切换时调用
         * 
         * @param oldMode 变更前的模式
         * @param newMode 变更后的模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}
