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

import android.net.Uri;
/**
 * 笔记数据常量定义类
 * <p>
 * 定义了笔记应用中使用的所有常量、接口和内部类，包括：
 * <ul>
 * <li>Content Provider的Authority和URI</li>
 * <li>笔记类型常量（普通笔记、文件夹、系统文件夹）</li>
 * <li>系统文件夹ID常量</li>
 * <li>Intent Extra键常量</li>
 * <li>Widget类型常量</li>
 * <li>笔记数据列接口（NoteColumns、DataColumns）</li>
 * <li>文本笔记和通话记录笔记内部类</li>
 * </ul>
 * </p>
 * <p>
 * 该类主要用于定义数据库表结构和Content Provider的契约，
 * 提供统一的常量访问接口，方便应用各模块使用。
 * </p>
 */
public class Notes {
    /**
     * Content Provider的Authority
     */
    public static final String AUTHORITY = "micode_notes";
    /**
     * 日志标签
     */
    public static final String TAG = "Notes";
    /**
     * 普通笔记类型
     */
    public static final int TYPE_NOTE     = 0;
    /**
     * 文件夹类型
     */
    public static final int TYPE_FOLDER   = 1;
    /**
     * 系统类型
     */
    public static final int TYPE_SYSTEM   = 2;
    /**
     * 待办任务类型
     */
    public static final int TYPE_TASK     = 3;

    /**
     * 模板笔记类型
     */
    public static final int TYPE_TEMPLATE = 4;

    /**
     * 以下ID是系统文件夹的标识符
     * {@link Notes#ID_ROOT_FOLDER } 是默认文件夹
     * {@link Notes#ID_TEMPARAY_FOLDER } 用于不属于任何文件夹的笔记
     * {@link Notes#ID_CALL_RECORD_FOLDER} 用于存储通话记录
     */
    public static final int ID_ROOT_FOLDER = 0;
    /**
     * 临时文件夹ID，用于不属于任何文件夹的笔记
     */
    public static final int ID_TEMPARAY_FOLDER = -1;
    /**
     * 通话记录文件夹ID，用于存储通话记录
     */
    public static final int ID_CALL_RECORD_FOLDER = -2;
    /**
     * 回收站文件夹ID，用于存储已删除的笔记
     */
    public static final int ID_TRASH_FOLER = -3;
    /**
     * Template folder ID
     */
    public static final int ID_TEMPLATE_FOLDER = -4;

    /**
     * Capsule folder ID for global quick notes
     */
    public static final int ID_CAPSULE_FOLDER = -5;

    /**
     * ID for "All Notes" virtual folder
     */
    public static final int ID_ALL_NOTES_FOLDER = -10;

    /**
     * Intent Extra键：提醒日期
     */
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    /**
     * Intent Extra键：背景颜色ID
     */
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    /**
     * Intent Extra键：Widget ID
     */
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    /**
     * Intent Extra键：Widget类型
     */
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    /**
     * Intent Extra键：文件夹ID
     */
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    /**
     * Intent Extra键：通话日期
     */
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    /**
     * 无效的Widget类型
     */
    public static final int TYPE_WIDGET_INVALIDE      = -1;
    /**
     * 2x2 Widget类型
     */
    public static final int TYPE_WIDGET_2X            = 0;
    /**
     * 4x4 Widget类型
     */
    public static final int TYPE_WIDGET_4X            = 1;

    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    /**
     * Uri to query all notes and folders
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * Uri to query data
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    public interface NoteColumns {
        /**
         * The unique ID for a row
         * <P> Type: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * The parent's id for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * Created data for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date
         * <P> Type: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";


        /**
         * Alert date
         * <P> Type: INTEGER (long) </P>
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * Folder's name or text content of note
         * <P> Type: TEXT </P>
         */
        public static final String SNIPPET = "snippet";

        /**
         * Note's widget id
         * <P> Type: INTEGER (long) </P>
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * Note's widget type
         * <P> Type: INTEGER (long) </P>
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * Note's background color's id
         * <P> Type: INTEGER (long) </P>
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * For text note, it doesn't has attachment, for multi-media
         * note, it has at least one attachment
         * <P> Type: INTEGER </P>
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * Folder's count of notes
         * <P> Type: INTEGER (long) </P>
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * The file type: folder or note
         * <P> Type: INTEGER </P>
         */
        public static final String TYPE = "type";

        /**
         * The last sync id
         * <P> Type: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * Sign to indicate local modified or not
         * <P> Type: INTEGER </P>
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * Original parent id before moving into temporary folder
         * <P> Type : INTEGER </P>
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * The gtask id
         * <P> Type : TEXT </P>
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * The version code
         * <P> Type : INTEGER (long) </P>
         */
        public static final String VERSION = "version";

        /**
         * Sign to indicate the note is pinned to top or not
         * <P> Type : INTEGER </P>
         */
        public static final String TOP = "top";

        /**
         * Sign to indicate the note is locked or not
         * <P> Type : INTEGER </P>
         */
        public static final String LOCKED = "locked";

        /**
         * Note's title
         * <P> Type : TEXT </P>
         */
        public static final String TITLE = "title";

        /**
         * Task Priority: 0=Low/None, 1=Medium, 2=High
         * <P> Type : INTEGER </P>
         */
        public static final String GTASK_PRIORITY = "gtask_priority";

        /**
         * Task Due Date (Timestamp)
         * <P> Type : INTEGER (long) </P>
         */
        public static final String GTASK_DUE_DATE = "gtask_due_date";

        /**
         * Task Status: 0=Active, 1=Completed
         * <P> Type : INTEGER </P>
         */
        public static final String GTASK_STATUS = "gtask_status";

        /**
         * Task Finished Time (Timestamp)
         * <P> Type : INTEGER (long) </P>
         */
        public static final String GTASK_FINISHED_TIME = "gtask_finished_time";

        /**
         * Cloud User ID for sync
         * <P> Type : TEXT </P>
         */
        public static final String CLOUD_USER_ID = "cloud_user_id";

        /**
         * Cloud Device ID for sync
         * <P> Type : TEXT </P>
         */
        public static final String CLOUD_DEVICE_ID = "cloud_device_id";

        /**
         * Sync Status: 0=Not synced, 1=Syncing, 2=Synced, 3=Conflict
         * <P> Type : INTEGER </P>
         */
        public static final String SYNC_STATUS = "sync_status";

        /**
         * Last Sync Time (Timestamp)
         * <P> Type : INTEGER (long) </P>
         */
        public static final String LAST_SYNC_TIME = "last_sync_time";

        /**
         * Cloud Note ID for sync (UUID)
         * <P> Type : TEXT </P>
         */
        public static final String CLOUD_NOTE_ID = "cloud_note_id";
    }

    public interface DataColumns {
        /**
         * The unique ID for a row
         * <P> Type: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * The MIME type of the item represented by this row.
         * <P> Type: Text </P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * The reference id to note that this data belongs to
         * <P> Type: INTEGER (long) </P>
         */
        public static final String NOTE_ID = "note_id";

        /**
         * Created data for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date
         * <P> Type: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * Data's content
         * <P> Type: TEXT </P>
         */
        public static final String CONTENT = "content";


        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         */
        public static final String DATA1 = "data1";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         */
        public static final String DATA2 = "data2";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        public static final String DATA3 = "data3";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        public static final String DATA4 = "data4";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        public static final String DATA5 = "data5";
    }

    public static final class TextNote implements DataColumns {
        /**
         * Mode to indicate the text in check list mode or not
         * <P> Type: Integer 1:check list mode 0: normal mode </P>
         */
        public static final String MODE = DATA1;

        public static final int MODE_CHECK_LIST = 1;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    public static final class CallNote implements DataColumns {
        /**
         * Call date for this record
         * <P> Type: INTEGER (long) </P>
         */
        public static final String CALL_DATE = DATA1;

        /**
         * Phone number for this record
         * <P> Type: TEXT </P>
         */
        public static final String PHONE_NUMBER = DATA3;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}
