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

package net.micode.notes.data;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;


/**
 * 笔记Content Provider
 * <p>
 * 继承自ContentProvider，提供对笔记数据的增删改查（CRUD）操作。
 * 管理note表和data表的数据访问，支持URI匹配、数据查询、插入、更新和删除操作。
 * 同时提供搜索建议功能，支持全局搜索笔记内容。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>URI路由匹配，支持多种URI模式</li>
 * <li>笔记和数据的查询操作</li>
 * <li>笔记和数据的插入操作</li>
 * <li>笔记和数据的更新操作</li>
 * <li>笔记和数据的删除操作</li>
 * <li>全局搜索和搜索建议功能</li>
 * <li>数据变更通知</li>
 * <li>笔记版本号自动递增</li>
 * </ul>
 * </p>
 * <p>
 * 支持的URI模式：
 * <ul>
 * <li>content://micode_notes/note - 查询所有笔记</li>
 * <li>content://micode_notes/note/# - 查询指定ID的笔记</li>
 * <li>content://micode_notes/data - 查询所有数据</li>
 * <li>content://micode_notes/data/# - 查询指定ID的数据</li>
 * <li>content://micode_notes/search - 搜索笔记</li>
 * <li>content://micode_notes/search_suggest_query - 搜索建议</li>
 * </ul>
 * </p>
 * 
 * @see ContentProvider
 * @see NotesDatabaseHelper
 * @see Notes
 */
public class NotesProvider extends ContentProvider {
    /**
     * URI匹配器
     * <p>
     * 用于匹配不同的URI模式，将请求路由到对应的处理逻辑。
     * 支持笔记、数据、搜索等多种URI模式。
     * </p>
     */
    private static final UriMatcher mMatcher;

    /**
     * 数据库帮助类实例
     * <p>
     * 用于获取可读和可写的SQLiteDatabase实例。
     * </p>
     */
    private NotesDatabaseHelper mHelper;

    /**
     * 日志标签
     */
    private static final String TAG = "NotesProvider";

    /**
     * 笔记URI匹配码
     */
    private static final int URI_NOTE            = 1;
    /**
     * 笔记项URI匹配码
     */
    private static final int URI_NOTE_ITEM       = 2;
    /**
     * 数据URI匹配码
     */
    private static final int URI_DATA            = 3;
    /**
     * 数据项URI匹配码
     */
    private static final int URI_DATA_ITEM       = 4;

    /**
     * 搜索URI匹配码
     */
    private static final int URI_SEARCH          = 5;
    /**
     * 搜索建议URI匹配码
     */
    private static final int URI_SEARCH_SUGGEST  = 6;

    /**
     * URI匹配器初始化块
     * <p>
     * 初始化UriMatcher，注册所有支持的URI模式。
     * </p>
     */
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * 搜索结果投影
     * <p>
     * 定义搜索建议返回的列，包括笔记ID、文本内容、图标、Intent动作等。
     * 使用TRIM和REPLACE函数去除换行符和空白字符，以便更好地显示搜索结果。
     * </p>
     * <p>
     * x'0A'代表SQLite中的换行符'\n'。对于搜索结果中的标题和内容，
     * 我们会去除换行符和空白字符，以显示更多信息。
     * </p>
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 笔记摘要搜索查询SQL语句
     * <p>
     * 搜索note表中SNIPPET字段包含指定关键词的笔记。
     * 排除回收站中的笔记（PARENT_ID不等于ID_TRASH_FOLER）。
     * 只搜索普通笔记（TYPE等于TYPE_NOTE）。
     * </p>
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND (" + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " OR " + NoteColumns.TYPE + "=" + Notes.TYPE_TEMPLATE + ")";

    /**
     * 创建Content Provider
     * <p>
     * 初始化数据库帮助类实例。
     * </p>
     * 
     * @return true表示创建成功
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据
     * <p>
     * 根据URI模式查询对应的数据表，支持笔记、数据、搜索等多种查询模式。
     * 对于搜索模式，使用LIKE模糊匹配查询笔记摘要。
     * </p>
     * 
     * @param uri 查询的URI
     * @param projection 要查询的列数组
     * @param selection 查询条件
     * @param selectionArgs 查询条件参数
     * @param sortOrder 排序方式
     * @return 查询结果的Cursor对象
     * @throws IllegalArgumentException 如果URI模式不支持或搜索时指定了不允许的参数
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 查询所有笔记
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_NOTE_ITEM:
                // 查询指定ID的笔记
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                // 查询所有数据
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_DATA_ITEM:
                // 查询指定ID的数据
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索笔记或搜索建议
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 从URI路径中获取搜索关键词
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 从查询参数中获取搜索关键词
                    searchString = uri.getQueryParameter("pattern");
                }

                // 搜索关键词为空时返回null
                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 使用模糊匹配搜索笔记摘要
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 设置通知URI，当数据变更时通知观察者
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据
     * <p>
     * 根据URI模式向对应的数据表插入数据，支持笔记和数据的插入。
     * 插入成功后通知相关URI的观察者。
     * </p>
     * 
     * @param uri 插入数据的URI
     * @param values 要插入的数据值
     * @return 插入数据的URI（包含新增记录的ID）
     * @throws IllegalArgumentException 如果URI模式不支持
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入笔记
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                // 插入数据
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // Notify the note uri
        // 通知笔记URI的观察者
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // Notify the data uri
        // 通知数据URI的观察者
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据
     * <p>
     * 根据URI模式删除对应的数据表中的数据，支持笔记和数据的删除。
     * 删除笔记时，不允许删除系统文件夹（ID小于等于0）。
     * 删除成功后通知相关URI的观察者。
     * </p>
     * 
     * @param uri 删除数据的URI
     * @param selection 删除条件
     * @param selectionArgs 删除条件参数
     * @return 删除的记录数
     * @throws IllegalArgumentException 如果URI模式不支持
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除笔记（排除系统文件夹）
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 删除指定ID的笔记
                id = uri.getPathSegments().get(1);
                /**
                 * ID that smaller than 0 is system folder which is not allowed to
                 * trash
                 * ID小于等于0的是系统文件夹，不允许删除
                 */
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                // 删除数据
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                // 删除指定ID的数据
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 删除成功后通知观察者
        if (count > 0) {
            if (deleteData) {
                // 删除数据时通知笔记URI
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新数据
     * <p>
     * 根据URI模式更新对应的数据表中的数据，支持笔记和数据的更新。
     * 更新笔记时自动递增笔记的版本号。
     * 更新成功后通知相关URI的观察者。
     * </p>
     * 
     * @param uri 更新数据的URI
     * @param values 要更新的数据值
     * @param selection 更新条件
     * @param selectionArgs 更新条件参数
     * @return 更新的记录数
     * @throws IllegalArgumentException 如果URI模式不支持
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新笔记（递增版本号）
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                // 更新指定ID的笔记（递增版本号）
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                // 更新数据
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                // 更新指定ID的数据
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 更新成功后通知观察者
        if (count > 0) {
            if (updateData) {
                // 更新数据时通知笔记URI
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 解析查询条件
     * <p>
     * 将查询条件与ID条件组合，用于构建完整的SQL WHERE子句。
     * </p>
     * 
     * @param selection 原始查询条件
     * @return 组合后的查询条件字符串
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 递增笔记版本号
     * <p>
     * 更新指定笔记的VERSION字段，使其值加1。
     * 用于跟踪笔记的修改历史，支持同步功能。
     * </p>
     * 
     * @param id 笔记ID，如果小于等于0则使用selection条件
     * @param selection 查询条件
     * @param selectionArgs 查询条件参数
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        // 构建WHERE子句
        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 替换查询条件中的占位符
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * 获取数据MIME类型
     * <p>
     * 返回指定URI对应的数据MIME类型。
     * </p>
     * 
     * @param uri 数据URI
     * @return MIME类型字符串
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }
}
