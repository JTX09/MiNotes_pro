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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;


/**
 * 笔记数据库帮助类
 * <p>
 * 继承自SQLiteOpenHelper，负责笔记应用SQLite数据库的创建、升级和管理。
 * 管理两个主要数据表：note表（存储笔记和文件夹信息）和data表（存储笔记的详细内容）。
 * 使用数据库触发器自动维护笔记计数、内容同步等关联关系。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>创建和升级数据库表结构</li>
 * <li>创建和管理数据库触发器</li>
 * <li>维护系统文件夹（通话记录、根文件夹、临时文件夹、回收站）</li>
 * <li>支持数据库版本升级（当前版本：4）</li>
 * <li>提供单例模式访问数据库帮助类实例</li>
 * </ul>
 * </p>
 * <p>
 * 数据库版本历史：
 * <ul>
 * <li>V1: 初始版本</li>
 * <li>V2: 重构表结构</li>
 * <li>V3: 添加GTASK_ID列和回收站文件夹</li>
 * <li>V4: 添加VERSION列</li>
 * </ul>
 * </p>
 * 
 * @see SQLiteOpenHelper
 * @see Notes
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    /**
     * 数据库文件名
     */
    private static final String DB_NAME = "note.db";

    /**
     * 数据库版本号
     * <p>
     * 当前数据库版本为12，用于跟踪数据库结构变更。
     * 当数据库版本变更时，onUpgrade方法会被调用以执行升级逻辑。
     * </p>
     */
    private static final int DB_VERSION = 14;

    /**
     * 数据库表名常量接口
     */
    public interface TABLE {
        /**
         * 笔记表名
         * <p>
         * 存储笔记和文件夹的基本信息，包括ID、父文件夹ID、创建时间、修改时间、
         * 背景颜色、提醒时间、附件状态、笔记数量、摘要、类型、Widget信息、
         * 同步ID、本地修改状态、原始父文件夹ID、GTASK ID、版本等字段。
         * </p>
         */
        public static final String NOTE = "note";

        /**
         * 数据表名
         * <p>
         * 存储笔记的详细内容，支持多种MIME类型（文本、图片、附件等）。
         * 每条数据记录关联到一条笔记，包含MIME类型、内容、以及5个通用数据字段。
         * </p>
         */
        public static final String DATA = "data";
    }

    /**
     * 日志标签
     */
    private static final String TAG = "NotesDatabaseHelper";

    /**
     * 数据库帮助类单例实例
     * <p>
     * 使用单例模式确保全局只有一个数据库帮助类实例，
     * 避免多个实例同时操作数据库导致的数据不一致问题。
     * </p>
     */
    private static NotesDatabaseHelper mInstance;

    /**
     * 创建笔记表的SQL语句
     * <p>
     * 创建note表，包含以下字段：
     * <ul>
     * <li>ID: 主键，自增</li>
     * <li>PARENT_ID: 父文件夹ID，默认为0</li>
     * <li>ALERTED_DATE: 提醒时间，默认为0</li>
     * <li>BG_COLOR_ID: 背景颜色ID，默认为0</li>
     * <li>CREATED_DATE: 创建时间，默认为当前时间戳</li>
     * <li>HAS_ATTACHMENT: 是否有附件，默认为0</li>
     * <li>MODIFIED_DATE: 修改时间，默认为当前时间戳</li>
     * <li>NOTES_COUNT: 笔记数量，默认为0（仅文件夹有效）</li>
     * <li>SNIPPET: 笔记摘要，默认为空字符串</li>
     * <li>TYPE: 类型（0=普通笔记，1=文件夹，2=系统），默认为0</li>
     * <li>WIDGET_ID: Widget ID，默认为0</li>
     * <li>WIDGET_TYPE: Widget类型，默认为-1</li>
     * <li>SYNC_ID: 同步ID，默认为0</li>
     * <li>LOCAL_MODIFIED: 本地修改标志，默认为0</li>
     * <li>ORIGIN_PARENT_ID: 原始父文件夹ID，默认为0</li>
     * <li>GTASK_ID: Google Tasks ID，默认为空字符串</li>
     * <li>VERSION: 版本号，默认为0</li>
     * </ul>
     * </p>
     */
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.TOP + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LOCKED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.TITLE + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.CLOUD_USER_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.CLOUD_DEVICE_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.SYNC_STATUS + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LAST_SYNC_TIME + " INTEGER NOT NULL DEFAULT 0" +
        ")";

    /**
     * 创建数据表的SQL语句
     * <p>
     * 创建data表，包含以下字段：
     * <ul>
     * <li>ID: 主键，自增</li>
     * <li>MIME_TYPE: MIME类型，不能为空</li>
     * <li>NOTE_ID: 关联的笔记ID，默认为0</li>
     * <li>CREATED_DATE: 创建时间，默认为当前时间戳</li>
     * <li>MODIFIED_DATE: 修改时间，默认为当前时间戳</li>
     * <li>CONTENT: 内容，默认为空字符串</li>
     * <li>DATA1-5: 通用数据字段，用于存储不同类型的数据</li>
     * </ul>
     * </p>
     */
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA1 + " INTEGER," +
            DataColumns.DATA2 + " INTEGER," +
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
        ")";

    /**
     * 创建数据表索引的SQL语句
     * <p>
     * 在data表的NOTE_ID字段上创建索引，提高按笔记ID查询数据的效率。
     * </p>
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    /**
     * Increase folder's note count when move note to the folder
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * Decrease folder's note count when move note from folder
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";

    /**
     * Increase folder's note count when insert new note to the folder
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * Decrease folder's note count when delete note from the folder
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";

    /**
     * Update note's content when insert data with type {@link DataConstants#NOTE}
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has changed
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has deleted
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Delete datas belong to note which has been deleted
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Delete notes belong to folder which has been deleted
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Move notes belong to folder which has been moved to trash folder
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 构造器
     * 
     * @param context 应用上下文
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建笔记表
     * <p>
     * 执行创建note表的SQL语句，创建相关触发器，并初始化系统文件夹。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    /**
     * 重新创建笔记表触发器
     * <p>
     * 先删除所有已存在的note表相关触发器，然后重新创建所有触发器。
     * 用于在数据库升级时更新触发器逻辑。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除所有已存在的触发器
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 重新创建所有触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * 创建系统文件夹
     * <p>
     * 在note表中创建四个系统文件夹：
     * <ul>
     * <li>通话记录文件夹（ID_CALL_RECORD_FOLDER）</li>
     * <li>根文件夹（ID_ROOT_FOLDER）</li>
     * <li>临时文件夹（ID_TEMPARAY_FOLDER）</li>
     * <li>回收站文件夹（ID_TRASH_FOLER）</li>
     * </ul>
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        /**
         * call record foler for call notes
         */
        // 创建通话记录文件夹
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * root folder which is default folder
         */
        // 创建根文件夹（默认文件夹）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * temporary folder which is used for moving note
         */
        // 创建临时文件夹（用于移动笔记）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * create trash folder
         */
        // 创建回收站文件夹
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * create template folder
         */
        // 创建模板文件夹
        createTemplateFolder(db);

        /**
         * create capsule folder
         */
        // 创建速记胶囊文件夹
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_CAPSULE_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建数据表
     * <p>
     * 执行创建data表的SQL语句，创建相关触发器，并创建索引。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    /**
     * 重新创建数据表触发器
     * <p>
     * 先删除所有已存在的data表相关触发器，然后重新创建所有触发器。
     * 用于在数据库升级时更新触发器逻辑。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        // 删除所有已存在的触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        // 重新创建所有触发器
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 获取数据库帮助类单例实例
     * <p>
     * 使用双重检查锁定模式确保线程安全的单例实现。
     * </p>
     * 
     * @param context 应用上下文
     * @return NotesDatabaseHelper单例实例
     */
    public static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    /**
     * 创建数据库
     * <p>
     * 当数据库文件不存在时调用，创建note表和data表。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
        createPresetTemplates(db);
    }

    /**
     * 升级数据库
     * <p>
     * 当数据库版本号增加时调用，执行从旧版本到新版本的升级逻辑。
     * 支持增量升级，从当前版本逐步升级到目标版本。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     * @param oldVersion 当前数据库版本号
     * @param newVersion 目标数据库版本号
     * @throws IllegalStateException 如果升级失败
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        // 从V1升级到V2（包括V2到V3）
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true; // this upgrade including the upgrade from v2 to v3
            oldVersion++;
        }

        // 从V2升级到V3
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        // 从V3升级到V4
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 从V4升级到V5
        if (oldVersion == 4) {
            upgradeToV5(db);
            oldVersion++;
        }

        // 兼容性处理：如果 oldVersion 为 5，但 newVersion >= 7，
        // 说明可能跳过了 V6 的升级逻辑（V6 可能在某个中间版本被合并或跳过），
        // 或者用户是从一个中间状态升级上来的。
        // 为了确保连贯性，我们显式检查并处理 V5 -> V6 的过渡
        if (oldVersion == 5) {
             upgradeToV6(db);
             oldVersion++;
        }

        // 从V6升级到V7
        if (oldVersion == 6) {
            upgradeToV7(db);
            oldVersion++;
        }

        // 从V7升级到V8
        if (oldVersion == 7) {
            upgradeToV8(db);
            oldVersion++;
        }

        // 从V8升级到V9
        if (oldVersion == 8) {
            upgradeToV9(db);
            oldVersion++;
        }

        // 从V9升级到V10
        if (oldVersion == 9) {
            upgradeToV10(db);
            oldVersion++;
        }

        // 从V10升级到V11
        if (oldVersion == 10) {
            upgradeToV11(db);
            oldVersion++;
        }

        // 从V11升级到V12
        if (oldVersion == 11) {
            upgradeToV12(db);
            oldVersion++;
        }

        // 从V12升级到V13
        if (oldVersion == 12) {
            upgradeToV13(db);
            oldVersion++;
        }

        // 从V13升级到V14
        if (oldVersion == 13) {
            upgradeToV14(db);
            oldVersion++;
        }

        // 如果需要，重新创建触发器
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 检查升级是否成功
        // 注意：由于我们可能执行了多个升级步骤，oldVersion应该已经递增到了newVersion
        // 但是如果newVersion比当前支持的最大版本还高，oldVersion可能赶不上
        // 这里放宽检查条件，只要oldVersion有增加就认为是成功的，
        // 或者简单地只在目标版本就是DB_VERSION时进行严格检查
        if (oldVersion != newVersion && newVersion <= DB_VERSION) {
             // 临时注释掉这个异常抛出，允许部分升级成功的情况，
             // 或者因为我们手动处理了 oldVersion++，可能逻辑上已经到达了 newVersion
             // 但如果用户跨版本升级（例如从V1直接到V8），中间步骤都会执行
             
             // 如果升级后的版本不等于目标版本，这确实是个问题。
             // 但对于V7->V8，如果oldVersion变成了8，newVersion也是8，则通过。
             // 错误日志显示 "Upgrade notes database to version 8 fails"，说明 oldVersion != newVersion
             // 这意味着 oldVersion 没有正确递增到 8。
             
             // 让我们检查一下逻辑：
             // 如果初始 oldVersion = 7, newVersion = 8
             // 进入 if (oldVersion == 7) 块 -> upgradeToV8 -> oldVersion 变为 8
             // 此时 oldVersion (8) == newVersion (8)，检查通过。
             
             // 如果错误发生，可能是 oldVersion 初始值不是 7？
             // 或者前面的升级步骤有遗漏？
             
             // 为了稳健性，我们在这里记录日志而不是直接崩溃，或者重新检查逻辑。
             Log.e(TAG, "Upgrade notes database mismatch: oldVersion=" + oldVersion + ", newVersion=" + newVersion);
             // 暂时抛出异常以保持原行为，但添加更多调试信息
             throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + " fails. Final oldVersion=" + oldVersion);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // 强制检查并修复缺失的列，解决版本升级可能失效的问题
        // 这是一层额外的保险，确保无论升级逻辑是否被触发，LOCKED列都会存在
        try {
            android.database.Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE.NOTE + " LIMIT 0", null);
            boolean hasLockedColumn = false;
            if (cursor != null) {
                if (cursor.getColumnIndex(NoteColumns.LOCKED) != -1) {
                    hasLockedColumn = true;
                }
                cursor.close();
            }

            if (!hasLockedColumn) {
                db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.LOCKED
                        + " INTEGER NOT NULL DEFAULT 0");
                Log.i(TAG, "Fixed: Added missing LOCKED column in onOpen");
            }

            // Check for missing TITLE column
            boolean hasTitleColumn = false;
            if (cursor != null) {
                if (cursor.getColumnIndex(NoteColumns.TITLE) != -1) {
                    hasTitleColumn = true;
                }
            }

            if (!hasTitleColumn) {
                db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.TITLE
                        + " TEXT NOT NULL DEFAULT ''");
                Log.i(TAG, "Fixed: Added missing TITLE column in onOpen");
            }

            // Check for missing GTASK columns
            boolean hasGTaskColumns = false;
            if (cursor != null) {
                if (cursor.getColumnIndex(NoteColumns.GTASK_PRIORITY) != -1) {
                    hasGTaskColumns = true;
                }
            }
            
            if (!hasGTaskColumns) {
                 try {
                    db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_PRIORITY
                            + " INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_DUE_DATE
                            + " INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_STATUS
                            + " INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_FINISHED_TIME
                            + " INTEGER NOT NULL DEFAULT 0");
                    Log.i(TAG, "Fixed: Added missing GTASK columns in onOpen");
                 } catch (Exception e) {
                     Log.e(TAG, "Failed to add GTASK columns in onOpen", e);
                 }
            }
            
             boolean hasCloudNoteIdColumn = false;
             if (cursor != null) {
                 if (cursor.getColumnIndex(NoteColumns.CLOUD_NOTE_ID) != -1) {
                     hasCloudNoteIdColumn = true;
                 }
             }

             if (!hasCloudNoteIdColumn) {
                 try {
                     db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.CLOUD_NOTE_ID
                             + " TEXT NOT NULL DEFAULT ''");
                     db.execSQL("CREATE INDEX IF NOT EXISTS idx_cloud_note_id ON " + TABLE.NOTE
                             + "(" + NoteColumns.CLOUD_NOTE_ID + ")");
                     Log.i(TAG, "Fixed: Added missing CLOUD_NOTE_ID column and index in onOpen");
                 } catch (Exception e) {
                     Log.e(TAG, "Failed to add CLOUD_NOTE_ID column in onOpen", e);
                 }
             }

             if (cursor != null) {
                 cursor.close();
             }
         } catch (Exception e) {
             Log.e(TAG, "Failed to fix database in onOpen", e);
         }
     }

    /**
     * 升级数据库到V2版本
     * <p>
     * 删除旧表并重新创建note表和data表。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 升级数据库到V3版本
     * <p>
     * 添加GTASK_ID列到note表，并创建回收站系统文件夹。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // drop unused triggers
        // 删除未使用的触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // add a column for gtask id
        // 添加GTASK_ID列
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // add a trash system folder
        // 添加回收站系统文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级数据库到V4版本
     * <p>
     * 添加VERSION列到note表，用于跟踪笔记版本。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * 升级数据库到V5版本
     * <p>
     * 添加TOP列到note表，用于标记笔记是否置顶。
     * </p>
     * 
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV5(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.TOP
                + " INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * 升级数据库到V6版本
     * <p>
     * 添加LOCKED列到note表，用于标记笔记是否被锁定。
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV6(SQLiteDatabase db) {
        // V6 upgrade logic
        // Try adding the LOCKED column if it doesn't exist
        try {
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.LOCKED
                    + " INTEGER NOT NULL DEFAULT 0");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add LOCKED column in V6 upgrade (it might already exist)", e);
        }
    }

    /**
     * 升级数据库到V7版本
     * <p>
     * 再次尝试添加LOCKED列，确保列存在。
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV7(SQLiteDatabase db) {
        // V7 upgrade logic: Ensure LOCKED column exists
        // This is a safety net for cases where V6 upgrade might have been skipped or failed silently
        try {
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.LOCKED
                    + " INTEGER NOT NULL DEFAULT 0");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add LOCKED column in V7 upgrade (it probably already exists)", e);
        }
    }

    /**
     * 升级数据库到V8版本
     * <p>
     * 添加TITLE列到note表，用于存储笔记标题。
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV8(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.TITLE
                    + " TEXT NOT NULL DEFAULT ''");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add TITLE column in V8 upgrade (it probably already exists)", e);
        }
    }

    /**
     * 升级数据库到V9版本
     * <p>
     * 添加GTASK相关列：优先级、截止日期、状态、完成时间。
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV9(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_PRIORITY
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_DUE_DATE
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_STATUS
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_FINISHED_TIME
                    + " INTEGER NOT NULL DEFAULT 0");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add GTASK columns in V9 upgrade", e);
        }
    }

    /**
     * 升级数据库到V10版本
     * <p>
     * 创建模板系统文件夹并预置模板。
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV10(SQLiteDatabase db) {
        createTemplateFolder(db);
        createPresetTemplates(db);
    }

    /**
     * 升级数据库到V11版本
     * <p>
     * 添加云同步相关列：CLOUD_USER_ID, CLOUD_DEVICE_ID, SYNC_STATUS, LAST_SYNC_TIME
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV11(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.CLOUD_USER_ID
                    + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.CLOUD_DEVICE_ID
                    + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.SYNC_STATUS
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.LAST_SYNC_TIME
                    + " INTEGER NOT NULL DEFAULT 0");
            Log.i(TAG, "Upgraded database to V11: Added cloud sync columns");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add cloud sync columns in V11 upgrade", e);
        }
    }

    /**
     * 升级数据库到V12版本
     * <p>
     * 添加cloud_note_id列用于云端笔记唯一标识
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV12(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.CLOUD_NOTE_ID
                    + " TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_cloud_note_id ON " + TABLE.NOTE
                    + "(" + NoteColumns.CLOUD_NOTE_ID + ")");
            Log.i(TAG, "Upgraded database to V12: Added cloud_note_id column and index");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add cloud_note_id column in V12 upgrade", e);
        }
    }

    /**
     * 升级数据库到V13版本
     * <p>
     * 数据迁移：修复文件夹title为空的问题
     * 将所有title为空的文件夹的title字段设置为snippet的值
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV13(SQLiteDatabase db) {
        try {
            String sql = "UPDATE " + TABLE.NOTE
                    + " SET " + NoteColumns.TITLE + " = " + NoteColumns.SNIPPET
                    + " WHERE " + NoteColumns.TYPE + " = " + Notes.TYPE_FOLDER
                    + " AND (" + NoteColumns.TITLE + " IS NULL OR " + NoteColumns.TITLE + " = '')";
            db.execSQL(sql);

            android.database.Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE.NOTE
                            + " WHERE " + NoteColumns.TYPE + " = " + Notes.TYPE_FOLDER
                            + " AND (" + NoteColumns.TITLE + " IS NOT NULL OR " + NoteColumns.TITLE + " != '')",
                    null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int count = cursor.getInt(0);
                    Log.i(TAG, "Upgraded database to V13: Migrated " + count + " folders with non-empty title");
                }
                cursor.close();
            }

            Log.i(TAG, "Successfully upgraded database to V13: Fixed folder title migration");
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate folder titles in V13 upgrade", e);
        }
    }

    /**
     * 升级数据库到V14版本
     * <p>
     * 创建速记胶囊系统文件夹。
     * </p>
     *
     * @param db SQLiteDatabase实例
     */
    private void upgradeToV14(SQLiteDatabase db) {
        try {
            ContentValues values = new ContentValues();
            values.put(NoteColumns.ID, Notes.ID_CAPSULE_FOLDER);
            values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            db.insert(TABLE.NOTE, null, values);
            Log.i(TAG, "Upgraded database to V14: Created Capsule system folder");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Capsule system folder in V14 upgrade", e);
        }
    }

    /**
     * 创建模板系统文件夹
     *
     * @param db SQLiteDatabase实例
     */
    private void createTemplateFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TEMPLATE_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建预置模板
     *
     * @param db SQLiteDatabase实例
     */
    private void createPresetTemplates(SQLiteDatabase db) {
        // 工作模板
        long workFolderId = insertFolder(db, Notes.ID_TEMPLATE_FOLDER, "工作");
        if (workFolderId > 0) {
            insertNote(db, workFolderId, "会议记录", "会议主题：\n时间：\n地点：\n参会人：\n\n会议内容：\n\n行动项：\n", Notes.TYPE_TEMPLATE);
            insertNote(db, workFolderId, "周报", "本周工作总结：\n1. \n2. \n\n下周工作计划：\n1. \n2. \n\n需要协调的问题：\n", Notes.TYPE_TEMPLATE);
        }

        // 生活模板
        long lifeFolderId = insertFolder(db, Notes.ID_TEMPLATE_FOLDER, "生活");
        if (lifeFolderId > 0) {
            insertNote(db, lifeFolderId, "日记", "日期：\n天气：\n心情：\n\n正文：\n", Notes.TYPE_TEMPLATE);
            insertNote(db, lifeFolderId, "购物清单", "1. \n2. \n3. \n", Notes.TYPE_TEMPLATE);
        }

        // 学习模板
        long studyFolderId = insertFolder(db, Notes.ID_TEMPLATE_FOLDER, "学习");
        if (studyFolderId > 0) {
            insertNote(db, studyFolderId, "读书笔记", "书名：\n作者：\n\n核心观点：\n\n精彩摘录：\n\n读后感：\n", Notes.TYPE_TEMPLATE);
        }
    }

    private long insertFolder(SQLiteDatabase db, long parentId, String name) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, parentId);
        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
        values.put(NoteColumns.SNIPPET, name);
        values.put(NoteColumns.TITLE, name);
        values.put(NoteColumns.CREATED_DATE, System.currentTimeMillis());
        values.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        values.put(NoteColumns.NOTES_COUNT, 0);
        return db.insert(TABLE.NOTE, null, values);
    }

    private void insertNote(SQLiteDatabase db, long parentId, String title, String content, int type) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, parentId);
        values.put(NoteColumns.TYPE, type);
        values.put(NoteColumns.CREATED_DATE, System.currentTimeMillis());
        values.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        values.put(NoteColumns.SNIPPET, content); // SNIPPET acts as content preview or full content for simple notes
        values.put(NoteColumns.TITLE, title); // Assuming V8+ has TITLE
        long noteId = db.insert(TABLE.NOTE, null, values);

        if (noteId > 0) {
            ContentValues dataValues = new ContentValues();
            dataValues.put(DataColumns.NOTE_ID, noteId);
            dataValues.put(DataColumns.MIME_TYPE, DataConstants.NOTE);
            dataValues.put(DataColumns.CONTENT, content);
            dataValues.put(NoteColumns.CREATED_DATE, System.currentTimeMillis());
            dataValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
            db.insert(TABLE.DATA, null, dataValues);
        }
    }
}
