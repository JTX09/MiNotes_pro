package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;


/**
 * 数据工具类
 * <p>
 * 提供笔记数据的批量操作、查询和统计功能。
 * 支持批量删除、移动笔记，以及各种数据查询操作。
 * </p>
 */
public class DataUtils {
    /** 日志标签 */
    public static final String TAG = "DataUtils";
    
    /**
     * 批量删除笔记
     * <p>
     * 从数据库中批量删除指定 ID 的笔记。
     * 跳过系统根文件夹，不允许删除系统文件夹。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param ids 要删除的笔记 ID 集合
     * @return 如果删除成功返回 true，否则返回 false
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            if(id == Notes.ID_ROOT_FOLDER) {
                // 跳过系统根文件夹
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }
        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 移动笔记到指定文件夹
     * <p>
     * 将笔记从源文件夹移动到目标文件夹，并记录原始父文件夹 ID。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param id 笔记 ID
     * @param srcFolderId 源文件夹 ID
     * @param desFolderId 目标文件夹 ID
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 批量移动笔记到指定文件夹
     * <p>
     * 将多个笔记批量移动到目标文件夹。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param ids 要移动的笔记 ID 集合
     * @param folderId 目标文件夹 ID
     * @return 如果移动成功返回 true，否则返回 false
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
            long folderId) {
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }

        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户文件夹数量
     * <p>
     * 统计除系统文件夹外的所有用户文件夹数量。
     * 排除回收站文件夹。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @return 用户文件夹数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        Cursor cursor =resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)},
                null);

        int count = 0;
        if(cursor != null) {
            if(cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查笔记是否在数据库中可见
     * <p>
     * 检查指定 ID 和类型的笔记是否在数据库中存在且可见（不在回收站）。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param noteId 笔记 ID
     * @param type 笔记类型
     * @return 如果笔记可见返回 true，否则返回 false
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        String selection;
        String[] selectionArgs;
        
        if (type == Notes.TYPE_NOTE) {
            // If checking for a regular note, also allow templates as they are essentially notes
            selection = "(" + NoteColumns.TYPE + "=? OR " + NoteColumns.TYPE + "=?) AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER;
            selectionArgs = new String[] {String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.TYPE_TEMPLATE)};
        } else {
            selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER;
            selectionArgs = new String [] {String.valueOf(type)};
        }

        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                selection,
                selectionArgs,
                null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查笔记是否存在于数据库
     * <p>
     * 检查指定 ID 的笔记是否在数据库中存在。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param noteId 笔记 ID
     * @return 如果笔记存在返回 true，否则返回 false
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查数据是否存在于数据库
     * <p>
     * 检查指定 ID 的笔记数据是否在数据库中存在。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param dataId 数据 ID
     * @return 如果数据存在返回 true，否则返回 false
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查可见文件夹名称是否存在
     * <p>
     * 检查指定名称的文件夹是否在可见区域存在（不在回收站）。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param name 文件夹名称
     * @return 如果文件夹名称存在返回 true，否则返回 false
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean exist = false;
        if(cursor != null) {
            if(cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取文件夹中的 Widget 信息
     * <p>
     * 获取指定文件夹下所有笔记关联的 Widget 信息。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param folderId 文件夹 ID
     * @return Widget 属性集合，如果没有则返回 null
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);
                        widget.widgetType = c.getInt(1);
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据笔记 ID 获取通话号码
     * <p>
     * 查询指定笔记 ID 关联的通话记录中的电话号码。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param noteId 笔记 ID
     * @return 电话号码，如果未找到则返回空字符串
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String [] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话日期获取笔记 ID
     * <p>
     * 查询指定电话号码和通话日期对应的笔记 ID。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param phoneNumber 电话号码
     * @param callDate 通话日期（毫秒时间戳）
     * @return 笔记 ID，如果未找到则返回 0
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                + CallNote.PHONE_NUMBER + ",?)",
                new String [] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据笔记 ID 获取摘要
     * <p>
     * 查询指定笔记 ID 的摘要内容。
     * </p>
     * 
     * @param resolver ContentResolver 对象
     * @param noteId 笔记 ID
     * @return 笔记摘要
     * @throws IllegalArgumentException 如果笔记不存在
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String [] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String [] { String.valueOf(noteId)},
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }
    
    /**
     * 根据笔记 ID 获取类型
     * 
     * @param resolver ContentResolver 对象
     * @param noteId 笔记 ID
     * @return 笔记类型
     * @throws IllegalArgumentException 如果笔记不存在
     */
    public static int getNoteTypeById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String [] { NoteColumns.TYPE },
                NoteColumns.ID + "=?",
                new String [] { String.valueOf(noteId)},
                null);

        if (cursor != null) {
            int type = -1;
            if (cursor.moveToFirst()) {
                type = cursor.getInt(0);
            }
            cursor.close();
            return type;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化摘要内容
     * <p>
     * 去除摘要首尾空格，并截取到第一个换行符之前的内容。
     * </p>
     * 
     * @param snippet 原始摘要内容
     * @return 格式化后的摘要内容
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            // 去除首尾空格
            snippet = snippet.trim();
            // 截取到第一个换行符之前的内容
            int index = snippet.indexOf('\n');
            if (index != -1) {
                snippet = snippet.substring(0, index);
            }
        }
        return snippet;
    }
}
