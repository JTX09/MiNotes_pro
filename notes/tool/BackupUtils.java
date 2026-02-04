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

package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;


/**
 * 备份工具类
 * <p>
 * 提供笔记数据导出为文本文件的功能。
 * 支持将笔记、文件夹、通话记录等数据导出到 SD 卡中。
 * 使用单例模式确保全局只有一个实例。
 * </p>
 */
public class BackupUtils {
    /** 日志标签 */
    private static final String TAG = "BackupUtils";
    // Singleton stuff
    /** 单例实例 */
    private static BackupUtils sInstance;

    /**
     * 获取备份工具类的单例实例
     * 
     * @param context 应用上下文
     * @return 备份工具类实例
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * 备份或恢复的状态常量
     * <p>
     * 以下状态常量用于表示备份或恢复操作的状态。
     * </p>
     */
    // Currently, the sdcard is not mounted
    /** SD 卡未挂载 */
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // The backup file not exist
    /** 备份文件不存在 */
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // The data is not well formated, may be changed by other programs
    /** 数据格式损坏，可能被其他程序修改 */
    public static final int STATE_DATA_DESTROIED               = 2;
    // Some run-time exception which causes restore or backup fails
    /** 系统错误，运行时异常导致备份或恢复失败 */
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // Backup or restore success
    /** 备份或恢复成功 */
    public static final int STATE_SUCCESS                      = 4;

    /** 文本导出对象 */
    private TextExport mTextExport;

    /**
     * 私有构造函数
     * 
     * @param context 应用上下文
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 检查外部存储是否可用
     * 
     * @return 如果外部存储已挂载且可读写则返回 true，否则返回 false
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 导出笔记数据为文本文件
     * 
     * @return 导出状态码，可能为 STATE_SD_CARD_UNMOUONTED、STATE_SYSTEM_ERROR 或 STATE_SUCCESS
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 导出单条笔记为文本文件
     *
     * @param noteId 笔记 ID
     * @param title 笔记标题（用于文件名）
     * @return 导出状态码
     */
    public int exportNoteToText(String noteId, String title) {
        return mTextExport.exportNoteToText(noteId, title);
    }

    /**
     * 批量导出指定笔记为文本文件
     *
     * @param noteIds 笔记 ID 列表
     * @return 导出状态码
     */
    public int exportNotesToText(java.util.List<Long> noteIds) {
        return mTextExport.exportNotesToText(noteIds);
    }

    /**
     * 获取导出的文本文件名
     * 
     * @return 导出的文本文件名
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出的文本文件目录
     * 
     * @return 导出的文本文件目录路径
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 文本导出内部类
     * <p>
     * 负责将笔记数据导出为可读的文本文件。
     * 支持导出文件夹、笔记和通话记录等不同类型的数据。
     * </p>
     */
    private static class TextExport {
        /** 笔记查询投影字段 */
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        /** 笔记 ID 列索引 */
        private static final int NOTE_COLUMN_ID = 0;

        /** 笔记修改日期列索引 */
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;

        /** 笔记摘要列索引 */
        private static final int NOTE_COLUMN_SNIPPET = 2;

        /** 数据查询投影字段 */
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,
                DataColumns.MIME_TYPE,
                DataColumns.DATA1,
                DataColumns.DATA2,
                DataColumns.DATA3,
                DataColumns.DATA4,
        };

        /** 数据内容列索引 */
        private static final int DATA_COLUMN_CONTENT = 0;

        /** 数据 MIME 类型列索引 */
        private static final int DATA_COLUMN_MIME_TYPE = 1;

        /** 通话日期列索引 */
        private static final int DATA_COLUMN_CALL_DATE = 2;

        /** 电话号码列索引 */
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        /** 导出文本格式数组 */
        private final String [] TEXT_FORMAT;
        /** 文件夹名称格式索引 */
        private static final int FORMAT_FOLDER_NAME          = 0;
        /** 笔记日期格式索引 */
        private static final int FORMAT_NOTE_DATE            = 1;
        /** 笔记内容格式索引 */
        private static final int FORMAT_NOTE_CONTENT         = 2;

        /** 应用上下文 */
        private Context mContext;
        /** 导出文件名 */
        private String mFileName;
        /** 导出文件目录 */
        private String mFileDirectory;

        /**
         * 构造函数
         * 
         * @param context 应用上下文
         */
        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 获取指定格式的文本
         * 
         * @param id 格式索引
         * @return 格式化字符串
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 导出指定文件夹及其笔记到文本
         * <p>
         * 查询属于该文件夹的所有笔记，并将每个笔记的内容导出到输出流中。
         * </p>
         * 
         * @param folderId 文件夹 ID
         * @param ps 输出流
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // Query notes belong to this folder
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                        folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // Print note's last modified date
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 导出指定笔记到输出流
         * <p>
         * 查询笔记的所有数据，根据 MIME 类型分别处理通话记录和普通笔记。
         * </p>
         * 
         * @param noteId 笔记 ID
         * @param ps 输出流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                        noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // Print phone number
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // Print call date
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // Print call attachment location
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            // print a line separator between note
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 导出单条笔记为文本文件
         *
         * @param noteId 笔记 ID
         * @param title 笔记标题
         * @return 导出状态码
         */
        public int exportNoteToText(String noteId, String title) {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportNotePrintStream(title);
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            exportNoteToText(noteId, ps);
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * 批量导出指定笔记为文本文件
         *
         * @param noteIds 笔记 ID 列表
         * @return 导出状态码
         */
        public int exportNotesToText(java.util.List<Long> noteIds) {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            for (Long noteId : noteIds) {
                Cursor noteCursor = mContext.getContentResolver().query(
                        net.micode.notes.data.Notes.CONTENT_NOTE_URI,
                        NOTE_PROJECTION,
                        net.micode.notes.data.Notes.NoteColumns.ID + "=?",
                        new String[]{String.valueOf(noteId)},
                        null);

                if (noteCursor != null) {
                    if (noteCursor.moveToFirst()) {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        exportNoteToText(String.valueOf(noteId), ps);
                    }
                    noteCursor.close();
                }
            }
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * 导出笔记数据为文本文件
         * <p>
         * 将所有笔记、文件夹和通话记录导出为用户可读的文本文件。
         * 首先导出文件夹及其笔记，然后导出根目录下的笔记。
         * </p>
         * 
         * @return 导出状态码，可能为 STATE_SD_CARD_UNMOUONTED、STATE_SYSTEM_ERROR 或 STATE_SUCCESS
         */
        public int exportToText() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }
            // First export folder and its notes
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // Print folder's name
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // Export notes in root's folder
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * 获取导出单条笔记文本文件的输出流
         *
         * @param title 笔记标题
         * @return PrintStream 对象，如果创建失败则返回 null
         */
        private PrintStream getExportNotePrintStream(String title) {
            File file = generateFileWithTitle(mContext, R.string.file_path, title);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }

        /**
         * 获取导出文本文件的输出流
         * <p>
         * 在 SD 卡上创建导出文件，并返回对应的 PrintStream。
         * </p>
         * 
         * @return PrintStream 对象，如果创建失败则返回 null
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在公用下载目录上生成指定标题的文本文件
     *
     * @param context 应用上下文
     * @param filePathResId 文件路径资源 ID (不再使用，改为公用下载目录)
     * @param title 笔记标题
     * @return 生成的文件对象，如果创建失败则返回 null
     */
    private static File generateFileWithTitle(Context context, int filePathResId, String title) {
        // 清理文件名中的非法字符
        String fileName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (TextUtils.isEmpty(fileName)) {
            fileName = "untitled";
        }
        fileName = fileName + "_" + System.currentTimeMillis() + ".txt";

        File filedir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(filedir, fileName);

        try {
            if (!filedir.exists()) {
                filedir.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 在公用下载目录上生成导出文本文件
     * <p>
     * 在指定的路径下创建导出文件，如果目录不存在则创建目录。
     * </p>
     * 
     * @param context 应用上下文
     * @param filePathResId 文件路径资源 ID (不再使用，改为公用下载目录)
     * @param fileNameFormatResId 文件名格式资源 ID
     * @return 生成的文件对象，如果创建失败则返回 null
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        File filedir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String fileName = context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis()));
        File file = new File(filedir, fileName);

        try {
            if (!filedir.exists()) {
                // 创建目录
                filedir.mkdirs();
            }
            if (!file.exists()) {
                // 创建文件
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}


