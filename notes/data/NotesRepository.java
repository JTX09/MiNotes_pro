/*
 * Copyright (c) 2025, Modern Notes Project
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

package net.micode.notes.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.Note;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.sync.SyncConstants;
import net.micode.notes.tool.ResourceParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 笔记数据仓库
 * <p>
 * 负责数据访问逻辑，统一管理Content Provider和缓存
 * 提供笔记的增删改查、搜索、统计等功能
 * </p>
 * <p>
 * 使用Executor进行后台线程数据访问，避免阻塞UI线程
 * </p>
 *
 * @see Note
 * @see Notes
 */
public class NotesRepository {

    /**
     * 笔记信息类
     * <p>
     * 存储从数据库查询的笔记基本信息
     * </p>
     */
    public static class NoteInfo {
        public long id;
        public String title;
        public String snippet;
        public long parentId;
        public long createdDate;
        public long modifiedDate;
        public int type;
        public int localModified;
        public int bgColorId;
        public boolean isPinned; // 新增置顶字段
        public boolean isLocked; // 新增锁定字段
        public int notesCount;

        public NoteInfo() {}

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public long getParentId() {
            return parentId;
        }

        public void setParentId(long parentId) {
            this.parentId = parentId;
        }

        public String getNoteDataValue() {
            return snippet;
        }

        public String getSnippet() {
            return snippet;
        }

        public int getNotesCount() {
            return notesCount;
        }
    }
    private static final String TAG = "NotesRepository";

    private final ContentResolver contentResolver;
    private final ExecutorService executor;
    private final Context context;

    // 选择条件常量
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + " = ?";
    private static final String ROOT_FOLDER_SELECTION = "(" +
        NoteColumns.TYPE + "<>" + Notes.TYPE_SYSTEM + " AND " +
        NoteColumns.PARENT_ID + "=?) OR (" +
        NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND " +
        NoteColumns.NOTES_COUNT + ">0)";

    /**
     * 数据访问回调接口
     * <p>
     * 统一的数据访问结果回调机制
     * </p>
     *
     * @param <T> 返回数据类型
     */
    public interface Callback<T> {
        /**
         * 成功回调
         *
         * @param result 返回的结果数据
         */
        void onSuccess(T result);

        /**
         * 失败回调
         *
         * @param error 异常对象
         */
        void onError(Exception error);
    }

    /**
     * 从 Cursor 创建 NoteInfo 对象
     *
     * @param cursor 数据库游标
     * @return NoteInfo 对象
     */
    private NoteInfo noteFromCursor(Cursor cursor) {
        NoteInfo noteInfo = new NoteInfo();
        noteInfo.id = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));

        // Read TITLE and SNIPPET
        String dbTitle = "";
        int titleIndex = cursor.getColumnIndex(NoteColumns.TITLE);
        if (titleIndex != -1) {
            dbTitle = cursor.getString(titleIndex);
        }

        noteInfo.snippet = cursor.getString(cursor.getColumnIndexOrThrow(NoteColumns.SNIPPET));

        // Prioritize TITLE, fallback to SNIPPET
        if (dbTitle != null && !dbTitle.trim().isEmpty()) {
            noteInfo.title = dbTitle;
        } else {
            noteInfo.title = noteInfo.snippet;
        }

        noteInfo.parentId = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.PARENT_ID));
        noteInfo.createdDate = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.CREATED_DATE));
        noteInfo.modifiedDate = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.MODIFIED_DATE));
        noteInfo.type = cursor.getInt(cursor.getColumnIndexOrThrow(NoteColumns.TYPE));
        noteInfo.localModified = cursor.getInt(cursor.getColumnIndexOrThrow(NoteColumns.LOCAL_MODIFIED));

        int bgColorIdIndex = cursor.getColumnIndex(NoteColumns.BG_COLOR_ID);
        if (bgColorIdIndex != -1 && !cursor.isNull(bgColorIdIndex)) {
            noteInfo.bgColorId = cursor.getInt(bgColorIdIndex);
        } else {
            noteInfo.bgColorId = 0;
        }

        int topIndex = cursor.getColumnIndex(NoteColumns.TOP);
        if (topIndex != -1) {
            noteInfo.isPinned = cursor.getInt(topIndex) > 0;
        }

        int lockedIndex = cursor.getColumnIndex(NoteColumns.LOCKED);
        if (lockedIndex != -1) {
            noteInfo.isLocked = cursor.getInt(lockedIndex) > 0;
        } else {
            noteInfo.isLocked = false;
        }

        int countIndex = cursor.getColumnIndex(NoteColumns.NOTES_COUNT);
        if (countIndex != -1) {
            noteInfo.notesCount = cursor.getInt(countIndex);
        }
        
        return noteInfo;
    }

    /**
     * 构造函数
     * <p>
     * 初始化ContentResolver和线程池
     * </p>
     *
     * @param contentResolver Content解析器
     */
    public NotesRepository(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
        this.context = null;
        // 使用单线程Executor确保数据访问的顺序性
        this.executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        Log.d(TAG, "NotesRepository initialized");
    }

    public NotesRepository(Context context) {
        this.context = context.getApplicationContext();
        this.contentResolver = context.getContentResolver();
        // 使用单线程Executor确保数据访问的顺序性
        this.executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        Log.d(TAG, "NotesRepository initialized");
    }

    /**
     * 获取指定文件夹的笔记列表
     * <p>
     * 支持根文件夹（显示所有笔记）和子文件夹两种模式
     * </p>
     *
     * @param folderId 文件夹ID，{@link Notes#ID_ROOT_FOLDER} 表示根文件夹
     * @param callback 回调接口
     */
    public void getNotes(long folderId, Callback<List<NoteInfo>> callback) {
        executor.execute(() -> {
            try {
                // Modified to only return notes (no folders) as per new UI requirement
                List<NoteInfo> notes = queryNotesOnly(folderId);
                callback.onSuccess(notes);
                Log.d(TAG, "Successfully loaded notes for folder: " + folderId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load notes for folder: " + folderId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 获取指定文件夹下的子文件夹
     *
     * @param folderId 父文件夹ID
     * @param callback 回调接口
     */
    public void getSubFolders(long folderId, Callback<List<NoteInfo>> callback) {
        executor.execute(() -> {
            try {
                List<NoteInfo> folders = querySubFolders(folderId);
                callback.onSuccess(folders);
                Log.d(TAG, "Successfully loaded sub-folders for folder: " + folderId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sub-folders for folder: " + folderId, e);
                callback.onError(e);
            }
        });
    }

    private List<NoteInfo> querySubFolders(long folderId) {
        List<NoteInfo> folders = new ArrayList<>();
        String selection;
        String[] selectionArgs;

        if (folderId == Notes.ID_ROOT_FOLDER) {
            selection = "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND " + NoteColumns.PARENT_ID + "=?) OR (" +
                        NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND " + NoteColumns.NOTES_COUNT + ">0)";
            selectionArgs = new String[]{String.valueOf(Notes.ID_ROOT_FOLDER)};
        } else {
            selection = NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER;
            selectionArgs = new String[]{String.valueOf(folderId)};
        }

        Cursor cursor = contentResolver.query(
            Notes.CONTENT_NOTE_URI,
            null,
            selection,
            selectionArgs,
            NoteColumns.MODIFIED_DATE + " DESC"
        );

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    folders.add(noteFromCursor(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return folders;
    }

    private List<NoteInfo> queryNotesOnly(long folderId) {
        List<NoteInfo> normalNotes = new ArrayList<>();
        String selection;
        String[] selectionArgs;

        if (folderId == Notes.ID_ALL_NOTES_FOLDER) {
            // Query ALL notes (except trash and system folders)
            // We want all notes where TYPE=NOTE and PARENT_ID != TRASH
            selection = NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + 
                        NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER;
            selectionArgs = null;
        } else if (folderId == Notes.ID_TEMPLATE_FOLDER) {
            // Special case for template folder: show all templates regardless of category
            selection = NoteColumns.TYPE + "=" + Notes.TYPE_TEMPLATE;
            selectionArgs = null;
        } else if (folderId == Notes.ID_ROOT_FOLDER) {
            selection = "(" + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID + "=?)";
            selectionArgs = new String[]{String.valueOf(Notes.ID_ROOT_FOLDER)};
        } else {
            // In a sub-folder, show both normal notes and templates if they exist there
            selection = NoteColumns.PARENT_ID + "=? AND (" + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + 
                        " OR " + NoteColumns.TYPE + "=" + Notes.TYPE_TEMPLATE + ")";
            selectionArgs = new String[]{String.valueOf(folderId)};
        }

        Cursor cursor = contentResolver.query(
            Notes.CONTENT_NOTE_URI,
            null,
            selection,
            selectionArgs,
            NoteColumns.MODIFIED_DATE + " DESC"
        );

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    normalNotes.add(noteFromCursor(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        
        // Sort: Pinned first, then Modified Date DESC
        normalNotes.sort((a, b) -> {
            if (a.isPinned != b.isPinned) {
                return a.isPinned ? -1 : 1;
            }
            return Long.compare(b.modifiedDate, a.modifiedDate);
        });

        return normalNotes;
    }

    /**
     * 查询笔记列表（内部方法） - Deprecated logic kept for reference but unused by main flow now
     *
     * @param folderId 文件夹ID
     * @return 笔记列表（包含文件夹和便签）
     */
    private List<NoteInfo> queryNotes(long folderId) {
        List<NoteInfo> notes = new ArrayList<>();
        List<NoteInfo> folders = new ArrayList<>();
        List<NoteInfo> normalNotes = new ArrayList<>();

        String selection;
        String[] selectionArgs;

        if (folderId == Notes.ID_ROOT_FOLDER) {
            // 根文件夹：显示所有文件夹和便签 (排除系统文件夹和待办任务)
            selection = "(" + NoteColumns.TYPE + "<>" + Notes.TYPE_SYSTEM + " AND " + NoteColumns.TYPE + "<>" + Notes.TYPE_TASK + " AND " + NoteColumns.PARENT_ID + "=?) OR (" +
                        NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND " + NoteColumns.NOTES_COUNT + ">0)";
            selectionArgs = new String[]{String.valueOf(Notes.ID_ROOT_FOLDER)};
        } else {
            // 子文件夹：显示该文件夹下的文件夹和便签 (排除系统文件夹和待办任务)
            selection = NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "<>" + Notes.TYPE_SYSTEM + " AND " + NoteColumns.TYPE + "<>" + Notes.TYPE_TASK;
            selectionArgs = new String[]{String.valueOf(folderId)};
        }

        Cursor cursor = contentResolver.query(
            Notes.CONTENT_NOTE_URI,
            null,
            selection,
            selectionArgs,
            NoteColumns.MODIFIED_DATE + " DESC"
        );

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    NoteInfo note = noteFromCursor(cursor);
                    if (note.type == Notes.TYPE_FOLDER) {
                        // 文件夹单独收集
                        folders.add(note);
                    } else if (note.type == Notes.TYPE_NOTE) {
                        // 便签收集
                        normalNotes.add(note);
                    } else if (note.type == Notes.TYPE_SYSTEM && note.id == Notes.ID_CALL_RECORD_FOLDER) {
                        // 通话记录文件夹
                        folders.add(note);
                    }
                }
                Log.d(TAG, "Query returned " + folders.size() + " folders and " + normalNotes.size() + " notes");
            } finally {
                cursor.close();
            }
        }

        // 文件夹按修改时间倒序排列
        folders.sort((a, b) -> Long.compare(b.modifiedDate, a.modifiedDate));
        // 便签按修改时间倒序排列
        normalNotes.sort((a, b) -> {
            // 首先按置顶状态排序（置顶在前）
            if (a.isPinned != b.isPinned) {
                return a.isPinned ? -1 : 1;
            }
            // 其次按修改时间倒序排列
            return Long.compare(b.modifiedDate, a.modifiedDate);
        });

        // 合并：文件夹在前，便签在后
        notes.addAll(folders);
        notes.addAll(normalNotes);

        return notes;
    }

    /**
     * 查询单个笔记信息（异步版本）
     *
     * @param noteId 笔记ID
     * @param callback 回调接口
     */
    public void getNoteInfo(long noteId, Callback<NoteInfo> callback) {
        executor.execute(() -> {
            try {
                NoteInfo noteInfo = getFolderInfo(noteId);
                callback.onSuccess(noteInfo);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 查询单个文件夹信息
     *
     * @param folderId 文件夹ID
     * @return 文件夹信息，如果不存在返回null
     */
    public NoteInfo getFolderInfo(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) {
            NoteInfo root = new NoteInfo();
            root.id = Notes.ID_ROOT_FOLDER;
            root.title = "我的便签";
            root.snippet = "我的便签";
            root.type = Notes.TYPE_FOLDER;
            return root;
        }

        if (folderId == Notes.ID_TEMPLATE_FOLDER) {
            NoteInfo root = new NoteInfo();
            root.id = Notes.ID_TEMPLATE_FOLDER;
            root.title = "笔记模板";
            root.snippet = "笔记模板";
            root.type = Notes.TYPE_FOLDER; // Treat as folder for UI
            return root;
        }

        String selection = NoteColumns.ID + "=?";
        String[] selectionArgs = new String[]{String.valueOf(folderId)};

        Cursor cursor = contentResolver.query(
            Notes.CONTENT_NOTE_URI,
            null,
            selection,
            selectionArgs,
            null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return noteFromCursor(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * 查询文件夹的父文件夹ID（异步版本）
     *
     * @param folderId 文件夹ID
     * @param callback 回调接口，返回父文件夹ID
     */
    public void getParentFolderId(long folderId, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                long parentId = getParentFolderId(folderId);
                callback.onSuccess(parentId);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 查询文件夹的父文件夹ID
     *
     * @param folderId 文件夹ID
     * @return 父文件夹ID，如果不存在返回根文件夹ID
     */
    public long getParentFolderId(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER || folderId == Notes.ID_CALL_RECORD_FOLDER) {
            return Notes.ID_ROOT_FOLDER;
        }

        NoteInfo folder = getFolderInfo(folderId);
        if (folder != null) {
            return folder.parentId;
        }
        return Notes.ID_ROOT_FOLDER;
    }

    /**
     * 获取文件夹路径（从根到当前）
     *
     * @param folderId 当前文件夹ID
     * @return 文件夹路径列表（从根到当前）
     */
    public List<NoteInfo> getFolderPath(long folderId) {
        List<NoteInfo> path = new ArrayList<>();
        long currentId = folderId;

        while (currentId != Notes.ID_ROOT_FOLDER) {
            NoteInfo folder = getFolderInfo(currentId);
            if (folder == null) {
                break;
            }
            path.add(0, folder); // 添加到列表头部
            currentId = folder.parentId;
        }

        // 添加根文件夹
        NoteInfo root = new NoteInfo();
        root.id = Notes.ID_ROOT_FOLDER;
        root.title = "我的便签";
        root.snippet = "我的便签";
        root.type = Notes.TYPE_FOLDER;
        path.add(0, root);

        return path;
    }

    /**
     * 获取文件夹路径（异步版本）
     *
     * @param folderId 当前文件夹ID
     * @param callback 回调接口，返回文件夹路径列表
     */
    public void getFolderPath(long folderId, Callback<List<NoteInfo>> callback) {
        executor.execute(() -> {
            try {
                List<NoteInfo> path = getFolderPath(folderId);
                callback.onSuccess(path);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 创建新文件夹
     *
     * @param parentId 父文件夹ID
     * @param name 文件夹名称
     * @param callback 回调接口，返回新文件夹的ID
     */
    public void createFolder(long parentId, String name, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                long currentTime = System.currentTimeMillis();

                values.put(NoteColumns.PARENT_ID, parentId);
                values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                values.put(NoteColumns.SNIPPET, name);
                values.put(NoteColumns.TITLE, name);
                values.put(NoteColumns.CREATED_DATE, currentTime);
                values.put(NoteColumns.MODIFIED_DATE, currentTime);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);
                values.put(NoteColumns.NOTES_COUNT, 0);

                Uri uri = contentResolver.insert(Notes.CONTENT_NOTE_URI, values);

                Long folderId = 0L;
                if (uri != null) {
                    try {
                        folderId = ContentUris.parseId(uri);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse folder ID from URI", e);
                    }
                }

                callback.onSuccess(folderId);
                Log.d(TAG, "Successfully created folder: " + name + " with ID: " + folderId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create folder: " + name, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 创建新笔记
     * <p>
     * 在指定文件夹下创建一个空笔记
     * </p>
     *
     * @param folderId 父文件夹ID
     * @param callback 回调接口，返回新笔记的ID
     */
    public void createNote(long folderId, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                long currentTime = System.currentTimeMillis();

                int type = Notes.TYPE_NOTE;
                if (folderId == Notes.ID_TEMPLATE_FOLDER) {
                    type = Notes.TYPE_TEMPLATE;
                } else if (folderId > 0) {
                    // Check if folder is under templates
                    NoteInfo folder = getFolderInfo(folderId);
                    if (folder != null && folder.parentId == Notes.ID_TEMPLATE_FOLDER) {
                        type = Notes.TYPE_TEMPLATE;
                    }
                }

                values.put(NoteColumns.PARENT_ID, folderId);
                values.put(NoteColumns.TYPE, type);
                values.put(NoteColumns.CREATED_DATE, currentTime);
                values.put(NoteColumns.MODIFIED_DATE, currentTime);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);
                values.put(NoteColumns.SNIPPET, "");

                // 设置默认背景颜色（支持随机背景设置）
                if (context != null) {
                    values.put(NoteColumns.BG_COLOR_ID, ResourceParser.getDefaultBgId(context));
                }

                Uri uri = contentResolver.insert(Notes.CONTENT_NOTE_URI, values);

                Long noteId = 0L;
                if (uri != null) {
                    try {
                        noteId = ContentUris.parseId(uri);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse note ID from URI", e);
                    }
                }

                if (noteId > 0) {
                    callback.onSuccess(noteId);
                    Log.d(TAG, "Successfully created note with ID: " + noteId);
                } else {
                    callback.onError(new IllegalStateException("Failed to create note, invalid ID returned"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create note", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 更新笔记内容
     * <p>
     * 更新笔记的标题和内容，自动更新修改时间和本地修改标志
     * </p>
     *
     * @param noteId 笔记ID
     * @param content 笔记内容
     * @param callback 回调接口，返回影响的行数
     */
    public void updateNote(long noteId, String content, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                long currentTime = System.currentTimeMillis();

                values.put(NoteColumns.SNIPPET, extractSnippet(content));
                values.put(NoteColumns.MODIFIED_DATE, currentTime);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    // 查询现有的文本数据记录
                    Cursor cursor = contentResolver.query(
                        Notes.CONTENT_DATA_URI,
                        new String[]{DataColumns.ID},
                        DataColumns.NOTE_ID + " = ? AND " + DataColumns.MIME_TYPE + " = ?",
                        new String[]{String.valueOf(noteId), TextNote.CONTENT_ITEM_TYPE},
                        null
                    );

                    long dataId = 0;
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                dataId = cursor.getLong(cursor.getColumnIndexOrThrow(DataColumns.ID));
                            }
                        } finally {
                            cursor.close();
                        }
                    }

                    // 更新或插入文本数据
                    ContentValues dataValues = new ContentValues();
                    dataValues.put(DataColumns.CONTENT, content);

                    if (dataId > 0) {
                        // 更新现有记录
                        Uri dataUri = ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId);
                        int dataRows = contentResolver.update(dataUri, dataValues, null, null);
                        if (dataRows > 0) {
                            callback.onSuccess(rows);
                            Log.d(TAG, "Successfully updated note: " + noteId);
                        } else {
                            callback.onError(new RuntimeException("Failed to update note data"));
                        }
                    } else {
                        // 插入新记录
                        dataValues.put(DataColumns.NOTE_ID, noteId);
                        dataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                        Uri dataUri = contentResolver.insert(Notes.CONTENT_DATA_URI, dataValues);
                        if (dataUri != null) {
                            callback.onSuccess(rows);
                            Log.d(TAG, "Successfully updated note: " + noteId);
                        } else {
                            callback.onError(new RuntimeException("Failed to insert note data"));
                        }
                    }
                } else {
                    callback.onError(new RuntimeException("No note found with ID: " + noteId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update note: " + noteId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 删除笔记
     * <p>
     * 将笔记移动到回收站文件夹，并保存原始位置
     * </p>
     *
     * @param noteId 笔记ID
     * @param callback 回调接口，返回影响的行数
     */
    public void deleteNote(long noteId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                // 1. 获取当前父文件夹ID
                long currentParentId = getParentFolderId(noteId);
                
                ContentValues values = new ContentValues();
                values.put(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
                values.put(NoteColumns.ORIGIN_PARENT_ID, currentParentId);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    callback.onSuccess(rows);
                    Log.d(TAG, "Successfully moved note to trash: " + noteId + ", origin: " + currentParentId);
                } else {
                    callback.onError(new RuntimeException("No note found with ID: " + noteId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete note: " + noteId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 批量删除笔记（逻辑删除，移动到回收站）
     *
     * @param noteIds 笔记ID列表
     * @param callback 回调接口，返回影响的行数
     */
    public void deleteNotes(List<Long> noteIds, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onError(new IllegalArgumentException("Note IDs list is empty"));
                    return;
                }

                int totalRows = 0;
                for (Long noteId : noteIds) {
                    // 1. 获取当前父文件夹ID
                    long currentParentId = getParentFolderId(noteId);

                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
                    values.put(NoteColumns.ORIGIN_PARENT_ID, currentParentId);
                    values.put(NoteColumns.LOCAL_MODIFIED, 1);

                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                    int rows = contentResolver.update(uri, values, null, null);
                    totalRows += rows;
                }

                if (totalRows > 0) {
                    callback.onSuccess(totalRows);
                    Log.d(TAG, "Successfully moved " + totalRows + " notes to trash");
                } else {
                    callback.onError(new RuntimeException("No notes were deleted"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to batch delete notes", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 恢复笔记（从回收站移回原始位置或根目录）
     *
     * @param noteIds 笔记ID列表
     * @param callback 回调接口
     */
    public void restoreNotes(List<Long> noteIds, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onError(new IllegalArgumentException("Note IDs list is empty"));
                    return;
                }

                int totalRows = 0;

                for (Long noteId : noteIds) {
                    // 1. 查询原始父文件夹ID
                    long originParentId = Notes.ID_ROOT_FOLDER;
                    Cursor cursor = contentResolver.query(
                        ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                        new String[]{NoteColumns.ORIGIN_PARENT_ID},
                        null,
                        null,
                        null
                    );
                    
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                int idx = cursor.getColumnIndex(NoteColumns.ORIGIN_PARENT_ID);
                                if (idx != -1) {
                                    originParentId = cursor.getLong(idx);
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                    
                    // 如果原始位置无效（例如0或仍是回收站），则恢复到根目录
                    if (originParentId == Notes.ID_TRASH_FOLER || originParentId == 0) {
                        originParentId = Notes.ID_ROOT_FOLDER;
                    }

                    // 2. 恢复
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.PARENT_ID, originParentId);
                    values.put(NoteColumns.ORIGIN_PARENT_ID, 0); // Reset origin
                    values.put(NoteColumns.LOCAL_MODIFIED, 1);

                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                    int rows = contentResolver.update(uri, values, null, null);
                    totalRows += rows;
                }

                if (totalRows > 0) {
                    callback.onSuccess(totalRows);
                    Log.d(TAG, "Successfully restored " + totalRows + " notes");
                } else {
                    callback.onError(new RuntimeException("No notes were restored"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore notes", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 永久删除笔记（物理删除）
     *
     * @param noteIds 笔记ID列表
     * @param callback 回调接口
     */
    public void deleteNotesForever(List<Long> noteIds, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onError(new IllegalArgumentException("Note IDs list is empty"));
                    return;
                }

                int totalRows = 0;
                for (Long noteId : noteIds) {
                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                    int rows = contentResolver.delete(uri, null, null);
                    totalRows += rows;
                }

                if (totalRows > 0) {
                    callback.onSuccess(totalRows);
                    Log.d(TAG, "Successfully permanently deleted " + totalRows + " notes");
                } else {
                    callback.onError(new RuntimeException("No notes were deleted"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete notes forever", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 搜索笔记
     * <p>
     * 根据关键字在标题和内容中搜索笔记
     * </p>
     *
     * @param keyword 搜索关键字
     * @param callback 回调接口
     */
    public void searchNotes(String keyword, Callback<List<NoteInfo>> callback) {
        executor.execute(() -> {
            try {
                if (keyword == null || keyword.trim().isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                String selection = "(" + NoteColumns.TYPE + " <> ?) AND (" +
                    NoteColumns.TITLE + " LIKE ? OR " +
                    NoteColumns.SNIPPET + " LIKE ? OR " +
                    NoteColumns.ID + " IN (SELECT " + DataColumns.NOTE_ID +
                    " FROM data WHERE " + DataColumns.CONTENT + " LIKE ?))";

                String[] selectionArgs = new String[]{
                    String.valueOf(Notes.TYPE_SYSTEM),
                    "%" + keyword + "%",
                    "%" + keyword + "%",
                    "%" + keyword + "%"
                };

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    NoteColumns.MODIFIED_DATE + " DESC"
                );

                List<NoteInfo> notes = new ArrayList<>();
                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            notes.add(noteFromCursor(cursor));
                        }
                        Log.d(TAG, "Search returned " + cursor.getCount() + " results for: " + keyword);
                    } finally {
                        cursor.close();
                    }
                }

                callback.onSuccess(notes);
            } catch (Exception e) {
                Log.e(TAG, "Failed to search notes: " + keyword, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 获取笔记统计信息
     * <p>
     * 统计指定文件夹下的笔记数量
     * </p>
     *
     * @param folderId 文件夹ID
     * @param callback 回调接口
     */
    public void countNotes(long folderId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                String selection;
                String[] selectionArgs;

                if (folderId == Notes.ID_ROOT_FOLDER) {
                    selection = NoteColumns.TYPE + " != ?";
                    selectionArgs = new String[]{String.valueOf(Notes.TYPE_FOLDER)};
                } else {
                    selection = NoteColumns.PARENT_ID + " = ?";
                    selectionArgs = new String[]{String.valueOf(folderId)};
                }

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    new String[]{"COUNT(*) AS count"},
                    selection,
                    selectionArgs,
                    null
                );

                int count = 0;
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            count = cursor.getInt(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }

                callback.onSuccess(count);
                Log.d(TAG, "Counted " + count + " notes in folder: " + folderId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to count notes in folder: " + folderId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 获取文件夹列表
     * <p>
     * 查询所有文件夹类型的笔记
     * </p>
     *
     * @param callback 回调接口
     */
    public void getFolders(Callback<List<NoteInfo>> callback) {
        executor.execute(() -> {
            try {
                String selection = NoteColumns.TYPE + " = ?";
                String[] selectionArgs = new String[]{
                    String.valueOf(Notes.TYPE_FOLDER)
                };

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    NoteColumns.MODIFIED_DATE + " DESC"
                );

                List<NoteInfo> folders = new ArrayList<>();
                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            folders.add(noteFromCursor(cursor));
                        }
                        Log.d(TAG, "Found " + cursor.getCount() + " folders");
                    } finally {
                        cursor.close();
                    }
                }

                callback.onSuccess(folders);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load folders", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 批量移动笔记到指定文件夹
     * <p>
     * 将笔记从当前文件夹移动到目标文件夹
     * </p>
     *
     * @param noteIds 要移动的笔记ID列表
     * @param targetFolderId 目标文件夹ID
     * @param callback 回调接口
     */
    public void moveNotes(List<Long> noteIds, long targetFolderId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onError(new IllegalArgumentException("Note IDs list is empty"));
                    return;
                }

                int totalRows = 0;
                for (Long noteId : noteIds) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.PARENT_ID, targetFolderId);
                    values.put(NoteColumns.LOCAL_MODIFIED, 1);

                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                    int rows = contentResolver.update(uri, values, null, null);
                    totalRows += rows;
                }

                if (totalRows > 0) {
                    callback.onSuccess(totalRows);
                    Log.d(TAG, "Successfully moved " + totalRows + " notes to folder: " + targetFolderId);
                } else {
                    callback.onError(new RuntimeException("No notes were moved"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to move notes", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 批量更新笔记置顶状态
     *
     * @param noteIds 笔记ID列表
     * @param isPinned 是否置顶
     * @param callback 回调接口
     */
    public void batchTogglePin(List<Long> noteIds, boolean isPinned, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onError(new IllegalArgumentException("Note IDs list is empty"));
                    return;
                }

                int totalRows = 0;
                ContentValues values = new ContentValues();
                values.put(NoteColumns.TOP, isPinned ? 1 : 0);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                for (Long noteId : noteIds) {
                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                    int rows = contentResolver.update(uri, values, null, null);
                    totalRows += rows;
                }

                if (totalRows > 0) {
                    callback.onSuccess(totalRows);
                    Log.d(TAG, "Successfully updated pin state for " + totalRows + " notes");
                } else {
                    callback.onError(new RuntimeException("No notes were updated"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update pin state", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 批量更新笔记锁定状态
     *
     * @param noteIds 笔记ID列表
     * @param isLocked 是否锁定
     * @param callback 回调接口
     */
    public void batchLock(List<Long> noteIds, boolean isLocked, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onError(new IllegalArgumentException("Note IDs list is empty"));
                    return;
                }

                int totalRows = 0;
                ContentValues values = new ContentValues();
                values.put(NoteColumns.LOCKED, isLocked ? 1 : 0);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                for (Long noteId : noteIds) {
                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                    int rows = contentResolver.update(uri, values, null, null);
                    totalRows += rows;
                }

                if (totalRows > 0) {
                    callback.onSuccess(totalRows);
                    Log.d(TAG, "Successfully updated lock state for " + totalRows + " notes");
                } else {
                    callback.onError(new RuntimeException("No notes were updated"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update lock state", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 解锁所有笔记
     * <p>
     * 将所有笔记的锁定状态重置为未锁定
     * </p>
     *
     * @param callback 回调接口
     */
    public void unlockAllNotes(Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.LOCKED, 0);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                // Update all notes where locked is 1
                String selection = NoteColumns.LOCKED + " = ?";
                String[] selectionArgs = new String[]{"1"};

                int rows = contentResolver.update(Notes.CONTENT_NOTE_URI, values, selection, selectionArgs);

                callback.onSuccess(rows);
                Log.d(TAG, "Successfully unlocked " + rows + " notes");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unlock all notes", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 从内容中提取摘要
     *
     * @param content 笔记内容
     * @return 摘要文本（最多100个字符）
     */
    private String extractSnippet(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // Convert HTML to plain text to remove tags and resolve entities
        String plainText;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            plainText = android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            plainText = android.text.Html.fromHtml(content).toString();
        }
        
        // Remove extra whitespace
        plainText = plainText.trim();

        int maxLength = 100;
        return plainText.length() > maxLength
            ? plainText.substring(0, maxLength)
            : plainText;
    }

    /**
     * 重命名文件夹
     * <p>
     * 修改文件夹名称
     * </p>
     *
     * @param folderId 文件夹ID
     * @param newName  新名称
     * @param callback 回调接口，返回影响的行数
     */
    public void renameFolder(long folderId, String newName, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                // 检查是否是系统文件夹（禁止重命名）
                if (folderId <= 0) {
                    callback.onError(new IllegalArgumentException("System folder cannot be renamed"));
                    return;
                }

                // 检查名称是否为空
                if (newName == null || newName.trim().isEmpty()) {
                    callback.onError(new IllegalArgumentException("Folder name cannot be empty"));
                    return;
                }

                ContentValues values = new ContentValues();
                // 同时更新 TITLE 和 SNIPPET，保持一致性（文件夹名存储在SNIPPET中）
                values.put(NoteColumns.TITLE, newName);
                values.put(NoteColumns.SNIPPET, newName);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    callback.onSuccess(rows);
                    Log.d(TAG, "Successfully renamed folder: " + folderId + " to: " + newName);
                } else {
                    callback.onError(new RuntimeException("No folder found with ID: " + folderId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to rename folder: " + folderId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 重命名笔记
     * <p>
     * 修改笔记标题
     * </p>
     *
     * @param noteId 笔记ID
     * @param newName  新标题
     * @param callback 回调接口，返回影响的行数
     */
    public void renameNote(long noteId, String newName, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                // 检查名称是否为空
                if (newName == null || newName.trim().isEmpty()) {
                    callback.onError(new IllegalArgumentException("Note title cannot be empty"));
                    return;
                }

                ContentValues values = new ContentValues();
                // 仅更新 TITLE，保留原始 SNIPPET（内容预览）
                values.put(NoteColumns.TITLE, newName);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    callback.onSuccess(rows);
                    Log.d(TAG, "Successfully renamed note: " + noteId + " to: " + newName);
                } else {
                    callback.onError(new RuntimeException("No note found with ID: " + noteId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to rename note: " + noteId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 移动文件夹到目标文件夹
     * <p>
     * 将文件夹移动到新的父文件夹下
     * </p>
     *
     * @param folderId 文件夹ID
     * @param newParentId 目标父文件夹ID
     * @param callback 回调接口，返回影响的行数
     */
    public void moveFolder(long folderId, long newParentId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                // 检查是否是系统文件夹（禁止移动）
                if (folderId <= 0) {
                    callback.onError(new IllegalArgumentException("System folder cannot be moved"));
                    return;
                }

                // 检查目标文件夹是否有效（不能是系统文件夹）
                if (newParentId < 0 && newParentId != Notes.ID_TRASH_FOLER) {
                    callback.onError(new IllegalArgumentException("Invalid target folder"));
                    return;
                }

                ContentValues values = new ContentValues();
                values.put(NoteColumns.PARENT_ID, newParentId);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    callback.onSuccess(rows);
                    Log.d(TAG, "Successfully moved folder: " + folderId + " to parent: " + newParentId);
                } else {
                    callback.onError(new RuntimeException("No folder found with ID: " + folderId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to move folder: " + folderId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 删除文件夹（移到回收站）
     * <p>
     * 将文件夹移动到回收站文件夹
     * </p>
     *
     * @param folderId 文件夹ID
     * @param callback 回调接口，返回影响的行数
     */
    public void deleteFolder(long folderId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                // 检查是否是系统文件夹（禁止删除）
                if (folderId <= 0) {
                    callback.onError(new IllegalArgumentException("System folder cannot be deleted"));
                    return;
                }

                ContentValues values = new ContentValues();
                values.put(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, folderId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    callback.onSuccess(rows);
                    Log.d(TAG, "Successfully moved folder to trash: " + folderId);
                } else {
                    callback.onError(new RuntimeException("No folder found with ID: " + folderId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete folder: " + folderId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 关闭Executor
     * <p>
     * 在不再需要数据访问时调用，释放线程池资源
     * </p>
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            Log.d(TAG, "Executor shutdown");
        }
    }

    /**
     * 应用模板
     *
     * @param templateId 模板笔记ID
     * @param targetFolderId 目标文件夹ID
     * @param callback 回调
     */
    public void applyTemplate(long templateId, long targetFolderId, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                // 1. 获取模板内容
                String content = getNoteContent(templateId);
                String title = getNoteTitle(templateId);

                // 2. 创建新笔记
                ContentValues values = new ContentValues();
                long currentTime = System.currentTimeMillis();

                values.put(NoteColumns.PARENT_ID, targetFolderId);
                values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                values.put(NoteColumns.CREATED_DATE, currentTime);
                values.put(NoteColumns.MODIFIED_DATE, currentTime);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);
                values.put(NoteColumns.SNIPPET, extractSnippet(content));
                values.put(NoteColumns.TITLE, title); // Copy title (or maybe empty?)

                Uri uri = contentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                Long newNoteId = 0L;
                if (uri != null) {
                    newNoteId = ContentUris.parseId(uri);
                }

                if (newNoteId > 0) {
                    // 3. 插入内容
                    ContentValues dataValues = new ContentValues();
                    dataValues.put(DataColumns.NOTE_ID, newNoteId);
                    dataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    dataValues.put(DataColumns.CONTENT, content);
                    dataValues.put(NoteColumns.CREATED_DATE, currentTime);
                    dataValues.put(NoteColumns.MODIFIED_DATE, currentTime);
                    contentResolver.insert(Notes.CONTENT_DATA_URI, dataValues);

                    callback.onSuccess(newNoteId);
                } else {
                    callback.onError(new RuntimeException("Failed to create note from template"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 保存为模板
     *
     * @param sourceNoteId 源笔记ID
     * @param categoryId 模板分类文件夹ID
     * @param templateName 模板名称
     * @param callback 回调
     */
    public void createTemplate(long sourceNoteId, long categoryId, String templateName, Callback<Long> callback) {
        executor.execute(() -> {
            try {
                // 1. 获取源内容
                String content = getNoteContent(sourceNoteId);

                // 2. 创建模板笔记
                ContentValues values = new ContentValues();
                long currentTime = System.currentTimeMillis();

                values.put(NoteColumns.PARENT_ID, categoryId);
                values.put(NoteColumns.TYPE, Notes.TYPE_TEMPLATE);
                values.put(NoteColumns.CREATED_DATE, currentTime);
                values.put(NoteColumns.MODIFIED_DATE, currentTime);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);
                values.put(NoteColumns.SNIPPET, extractSnippet(content));
                values.put(NoteColumns.TITLE, templateName);

                Uri uri = contentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                Long newNoteId = 0L;
                if (uri != null) {
                    newNoteId = ContentUris.parseId(uri);
                }

                if (newNoteId > 0) {
                    // 3. 插入内容
                    ContentValues dataValues = new ContentValues();
                    dataValues.put(DataColumns.NOTE_ID, newNoteId);
                    dataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    dataValues.put(DataColumns.CONTENT, content);
                    dataValues.put(NoteColumns.CREATED_DATE, currentTime);
                    dataValues.put(NoteColumns.MODIFIED_DATE, currentTime);
                    contentResolver.insert(Notes.CONTENT_DATA_URI, dataValues);

                    callback.onSuccess(newNoteId);
                } else {
                    callback.onError(new RuntimeException("Failed to create template"));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 获取笔记完整内容（异步版本）
     *
     * @param noteId 笔记ID
     * @param callback 回调接口
     */
    public void getNoteContent(long noteId, Callback<String> callback) {
        executor.execute(() -> {
            try {
                String content = getNoteContent(noteId);
                callback.onSuccess(content);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private String getNoteContent(long noteId) {
        String content = "";
        Cursor cursor = contentResolver.query(
            Notes.CONTENT_DATA_URI,
            new String[]{DataColumns.CONTENT},
            DataColumns.NOTE_ID + " = ? AND " + DataColumns.MIME_TYPE + " = ?",
            new String[]{String.valueOf(noteId), TextNote.CONTENT_ITEM_TYPE},
            null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    content = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        return content;
    }

    private String getNoteTitle(long noteId) {
        String title = "";
        Cursor cursor = contentResolver.query(
                Notes.CONTENT_NOTE_URI,
                new String[]{NoteColumns.TITLE},
                NoteColumns.ID + " = ?",
                new String[]{String.valueOf(noteId)},
                null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    title = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        return title;
    }

    // ==================== Cloud Sync Methods ====================

    /**
     * 获取未同步的笔记列表
     * <p>
     * 查询所有 LOCAL_MODIFIED = 1 的笔记
     * </p>
     *
     * @param callback 回调接口，返回未同步笔记列表
     */
    public void getUnsyncedNotes(Callback<List<NoteInfo>> callback) {
        executor.execute(() -> {
            try {
                String selection = NoteColumns.LOCAL_MODIFIED + " = ?";
                String[] selectionArgs = {"1"};

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    NoteColumns.MODIFIED_DATE + " DESC"
                );

                List<NoteInfo> notes = new ArrayList<>();
                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            notes.add(noteFromCursor(cursor));
                        }
                        Log.d(TAG, "Found " + notes.size() + " unsynced notes");
                    } finally {
                        cursor.close();
                    }
                }

                callback.onSuccess(notes);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get unsynced notes", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 标记笔记为已同步
     * <p>
     * 更新 LOCAL_MODIFIED = 0 和 SYNC_STATUS = 2
     * </p>
     *
     * @param noteId 笔记ID
     */
    public void markAsSynced(long noteId) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.LOCAL_MODIFIED, 0);
                values.put(NoteColumns.SYNC_STATUS, 2);
                values.put(NoteColumns.LAST_SYNC_TIME, System.currentTimeMillis());

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    Log.d(TAG, "Marked note as synced: " + noteId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark note as synced: " + noteId, e);
            }
        });
    }

    /**
     * 更新笔记同步状态
     *
     * @param noteId 笔记ID
     * @param status 同步状态 (0=未同步, 1=同步中, 2=已同步, 3=冲突)
     */
    public void updateSyncStatus(long noteId, int status) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.SYNC_STATUS, status);

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    Log.d(TAG, "Updated sync status for note " + noteId + " to " + status);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update sync status: " + noteId, e);
            }
        });
    }

    /**
     * 获取最后同步时间
     *
     * @param callback 回调接口，返回最后同步时间（毫秒）
     */
    public void getLastSyncTime(Callback<Long> callback) {
        executor.execute(() -> {
            try {
                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    new String[]{"MAX(" + NoteColumns.LAST_SYNC_TIME + ") AS last_sync"},
                    null,
                    null,
                    null
                );

                long lastSyncTime = 0;
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            lastSyncTime = cursor.getLong(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }

                callback.onSuccess(lastSyncTime);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get last sync time", e);
                callback.onError(e);
            }
        });
    }

    // ==================== 云同步相关方法 ====================

    /**
     * 查询所有本地修改过的笔记（LOCAL_MODIFIED = 1）
     * <p>
     * 根据当前登录用户过滤，只返回该用户的笔记
     * </p>
     *
     * @param cloudUserId 当前用户的云端ID
     * @param callback 回调接口，返回本地修改过的笔记列表
     */
    public void getLocalModifiedNotes(String cloudUserId, Callback<List<WorkingNote>> callback) {
        executor.execute(() -> {
            try {
                // 同时过滤 LOCAL_MODIFIED = 1 和 cloud_user_id = 当前用户
                String selection = NoteColumns.LOCAL_MODIFIED + " = ? AND " + NoteColumns.CLOUD_USER_ID + " = ?";
                String[] selectionArgs = new String[] { "1", cloudUserId };
                String sortOrder = NoteColumns.MODIFIED_DATE + " DESC";

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    sortOrder
                );

                List<WorkingNote> notes = new ArrayList<>();
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));
                        WorkingNote note = WorkingNote.load(context, id);
                        notes.add(note);
                    }
                    cursor.close();
                }

                Log.d(TAG, "Found " + notes.size() + " locally modified notes for user: " + cloudUserId);
                callback.onSuccess(notes);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get local modified notes for user: " + cloudUserId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 查询所有本地修改过的笔记（LOCAL_MODIFIED = 1）- 不过滤用户（向后兼容）
     *
     * @param callback 回调接口，返回本地修改过的笔记列表
     * @deprecated 请使用 {@link #getLocalModifiedNotes(String, Callback)}
     */
    @Deprecated
    public void getLocalModifiedNotes(Callback<List<WorkingNote>> callback) {
        executor.execute(() -> {
            try {
                String selection = NoteColumns.LOCAL_MODIFIED + " = ?";
                String[] selectionArgs = new String[] { "1" };
                String sortOrder = NoteColumns.MODIFIED_DATE + " DESC";

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    sortOrder
                );

                List<WorkingNote> notes = new ArrayList<>();
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));
                        WorkingNote note = WorkingNote.load(context, id);
                        notes.add(note);
                    }
                    cursor.close();
                }

                Log.d(TAG, "Found " + notes.size() + " locally modified notes");
                callback.onSuccess(notes);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get local modified notes", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 标记笔记为已同步
     * 更新: LOCAL_MODIFIED=0, SYNC_STATUS=2, LAST_SYNC_TIME=now
     *
     * @param noteId 笔记ID
     * @param callback 回调接口
     */
    public void markNoteSynced(long noteId, Callback<Void> callback) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.LOCAL_MODIFIED, 0);
                values.put(NoteColumns.SYNC_STATUS, SyncConstants.SYNC_STATUS_SYNCED);
                values.put(NoteColumns.LAST_SYNC_TIME, System.currentTimeMillis());

                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                int rows = contentResolver.update(uri, values, null, null);

                if (rows > 0) {
                    Log.d(TAG, "Marked note " + noteId + " as synced");
                }

                callback.onSuccess(null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark note as synced: " + noteId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 根据noteId查找笔记
     *
     * @param noteId 笔记ID（字符串形式）
     * @param callback 回调接口，返回找到的笔记或null
     */
    public void findNoteByNoteId(String noteId, Callback<WorkingNote> callback) {
        executor.execute(() -> {
            try {
                long id = Long.parseLong(noteId);
                Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id);

                Cursor cursor = contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                );

                WorkingNote note = null;
                if (cursor != null && cursor.moveToFirst()) {
                    long noteIdFromCursor = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));
                    note = WorkingNote.load(context, noteIdFromCursor);
                    cursor.close();
                }

                callback.onSuccess(note);
            } catch (Exception e) {
                Log.e(TAG, "Failed to find note by noteId: " + noteId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 根据cloudNoteId查找笔记
     *
     * @param cloudNoteId 云端笔记ID（UUID）
     * @param callback 回调接口，返回找到的笔记或null
     */
    public void findByCloudNoteId(String cloudNoteId, Callback<WorkingNote> callback) {
        executor.execute(() -> {
            try {
                if (cloudNoteId == null || cloudNoteId.isEmpty()) {
                    callback.onSuccess(null);
                    return;
                }

                String selection = NoteColumns.CLOUD_NOTE_ID + " = ?";
                String[] selectionArgs = new String[] { cloudNoteId };

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    null
                );

                WorkingNote note = null;
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        long noteId = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));
                        note = WorkingNote.load(context, noteId);
                    }
                    cursor.close();
                }

                Log.d(TAG, "findByCloudNoteId: " + cloudNoteId + " found=" + (note != null));
                callback.onSuccess(note);
            } catch (Exception e) {
                Log.e(TAG, "Failed to find note by cloudNoteId: " + cloudNoteId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 查询指定云端用户ID的笔记
     *
     * @param cloudUserId 云端用户ID
     * @param callback 回调接口，返回笔记列表
     */
    public void getNotesByCloudUserId(String cloudUserId, Callback<List<WorkingNote>> callback) {
        executor.execute(() -> {
            try {
                String selection = NoteColumns.CLOUD_USER_ID + " = ?";
                String[] selectionArgs = new String[] { cloudUserId };

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    null
                );

                List<WorkingNote> notes = new ArrayList<>();
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long noteId = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));
                        WorkingNote note = WorkingNote.load(context, noteId);
                        notes.add(note);
                    }
                    cursor.close();
                }

                callback.onSuccess(notes);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get notes by cloudUserId: " + cloudUserId, e);
                callback.onError(e);
            }
        });
    }

    /**
     * 更新笔记的云端用户ID（用于匿名用户迁移）
     *
     * @param oldUserId 旧的云端用户ID
     * @param newUserId 新的云端用户ID
     * @param callback 回调接口，返回更新的笔记数量
     */
    public void updateCloudUserId(String oldUserId, String newUserId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.CLOUD_USER_ID, newUserId);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);

                String selection = NoteColumns.CLOUD_USER_ID + " = ?";
                String[] selectionArgs = new String[] { oldUserId };

                int rows = contentResolver.update(
                    Notes.CONTENT_NOTE_URI,
                    values,
                    selection,
                    selectionArgs
                );

                Log.d(TAG, "Updated " + rows + " notes from " + oldUserId + " to " + newUserId);
                callback.onSuccess(rows);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cloudUserId", e);
                callback.onError(e);
            }
        });
    }

    /**
     * 批量标记笔记为已同步（带事务支持）
     *
     * @param noteIds 笔记ID列表
     * @param callback 回调接口
     */
    public void batchMarkNotesSynced(List<Long> noteIds, Callback<Integer> callback) {
        executor.execute(() -> {
            int successCount = 0;
            Exception lastError = null;

            for (Long noteId : noteIds) {
                try {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.LOCAL_MODIFIED, 0);
                values.put(NoteColumns.SYNC_STATUS, SyncConstants.SYNC_STATUS_SYNCED);
                    values.put(NoteColumns.LAST_SYNC_TIME, System.currentTimeMillis());

                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                    int rows = contentResolver.update(uri, values, null, null);

                    if (rows > 0) {
                        successCount++;
                        Log.d(TAG, "Marked note " + noteId + " as synced");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to mark note as synced: " + noteId, e);
                    lastError = e;
                }
            }

            Log.d(TAG, "Batch sync completed: " + successCount + "/" + noteIds.size() + " notes marked as synced");

            if (successCount == noteIds.size()) {
                callback.onSuccess(successCount);
            } else if (successCount > 0) {
                callback.onSuccess(successCount);
            } else {
                callback.onError(lastError != null ? lastError : new Exception("All batch operations failed"));
            }
        });
    }

    /**
     * 新用户接管设备上的所有笔记
     * <p>
     * 将设备上所有笔记（无论之前的cloud_user_id是谁）的cloud_user_id更新为新用户，
     * 并标记为需要同步。这样新用户登录后可以把设备上的所有笔记上传到云端。
     * </p>
     *
     * @param newUserId 新用户的云端ID
     * @param callback 回调接口，返回接管的笔记数量
     */
    public void takeoverAllNotes(String newUserId, Callback<Integer> callback) {
        executor.execute(() -> {
            try {
                // 1. 获取设备上所有笔记（排除系统文件夹）
                String selection = NoteColumns.TYPE + " != ?";
                String[] selectionArgs = new String[] { String.valueOf(Notes.TYPE_SYSTEM) };

                Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    selection,
                    selectionArgs,
                    null
                );

                int takeoverCount = 0;
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long noteId = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));

                        // 更新笔记的cloud_user_id为新用户，并标记为本地修改
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.CLOUD_USER_ID, newUserId);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        values.put(NoteColumns.SYNC_STATUS, 0);

                        Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                        int rows = contentResolver.update(uri, values, null, null);

                        if (rows > 0) {
                            takeoverCount++;
                        }
                    }
                    cursor.close();
                }

                Log.d(TAG, "Takeover completed: " + takeoverCount + " notes now belong to " + newUserId);
                callback.onSuccess(takeoverCount);
            } catch (Exception e) {
                Log.e(TAG, "Failed to takeover notes for user: " + newUserId, e);
                callback.onError(e);
            }
        });
    }
}
