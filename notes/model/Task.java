package net.micode.notes.model;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

public class Task {
    private static final String TAG = "Task";

    public long id;
    public String snippet; // Content
    public long createdDate;
    public long modifiedDate;
    public int priority; // 0=Low, 1=Mid, 2=High
    public long dueDate;
    public int status; // 0=Active, 1=Completed
    public long finishedTime;
    public long alertDate;

    public static final int PRIORITY_LOW = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_HIGH = 2;

    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_COMPLETED = 1;

    public Task() {
        id = 0;
        snippet = "";
        createdDate = System.currentTimeMillis();
        modifiedDate = System.currentTimeMillis();
        priority = PRIORITY_LOW;
        dueDate = 0;
        status = STATUS_ACTIVE;
        finishedTime = 0;
        alertDate = 0;
    }

    public static Task fromCursor(Cursor cursor) {
        Task task = new Task();
        
        // Use getColumnIndex instead of getColumnIndexOrThrow for safety
        int idxId = cursor.getColumnIndex(NoteColumns.ID);
        if (idxId != -1) task.id = cursor.getLong(idxId);
        
        int idxSnippet = cursor.getColumnIndex(NoteColumns.SNIPPET);
        if (idxSnippet != -1) task.snippet = cursor.getString(idxSnippet);
        
        int idxCreated = cursor.getColumnIndex(NoteColumns.CREATED_DATE);
        if (idxCreated != -1) task.createdDate = cursor.getLong(idxCreated);
        
        int idxModified = cursor.getColumnIndex(NoteColumns.MODIFIED_DATE);
        if (idxModified != -1) task.modifiedDate = cursor.getLong(idxModified);
        
        int idxAlert = cursor.getColumnIndex(NoteColumns.ALERTED_DATE);
        if (idxAlert != -1) task.alertDate = cursor.getLong(idxAlert);
        
        int idxPriority = cursor.getColumnIndex(NoteColumns.GTASK_PRIORITY);
        if (idxPriority != -1) task.priority = cursor.getInt(idxPriority);
        
        int idxDueDate = cursor.getColumnIndex(NoteColumns.GTASK_DUE_DATE);
        if (idxDueDate != -1) task.dueDate = cursor.getLong(idxDueDate);
        
        int idxStatus = cursor.getColumnIndex(NoteColumns.GTASK_STATUS);
        if (idxStatus != -1) task.status = cursor.getInt(idxStatus);
        
        int idxFinished = cursor.getColumnIndex(NoteColumns.GTASK_FINISHED_TIME);
        if (idxFinished != -1) task.finishedTime = cursor.getLong(idxFinished);

        return task;
    }

    public Uri save(Context context) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.TYPE, Notes.TYPE_TASK);
        values.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        values.put(NoteColumns.ALERTED_DATE, alertDate);
        values.put(NoteColumns.GTASK_PRIORITY, priority);
        values.put(NoteColumns.GTASK_DUE_DATE, dueDate);
        values.put(NoteColumns.GTASK_STATUS, status);
        values.put(NoteColumns.GTASK_FINISHED_TIME, finishedTime);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        
        // Ensure snippet is updated in note table too, though trigger might handle it, 
        // explicit update is safer if trigger fails or data table logic changes.
        values.put(NoteColumns.SNIPPET, snippet);

        if (id == 0) {
            values.put(NoteColumns.CREATED_DATE, System.currentTimeMillis());
            values.put(NoteColumns.PARENT_ID, Notes.ID_ROOT_FOLDER);
            Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);
            if (uri != null) {
                id = ContentUris.parseId(uri);
                updateData(context);
            }
            return uri;
        } else {
            context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                values, null, null
            );
            updateData(context);
            return ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id);
        }
    }

    private void updateData(Context context) {
        ContentValues values = new ContentValues();
        values.put(DataColumns.NOTE_ID, id);
        values.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
        values.put(DataColumns.CONTENT, snippet);
        values.put(DataColumns.MODIFIED_DATE, System.currentTimeMillis());
        
        Cursor c = context.getContentResolver().query(Notes.CONTENT_DATA_URI, new String[]{DataColumns.ID}, 
            DataColumns.NOTE_ID + "=?", new String[]{String.valueOf(id)}, null);
        
        if (c != null) {
            if (c.moveToFirst()) {
                 long dataId = c.getLong(0);
                 context.getContentResolver().update(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), values, null, null);
            } else {
                 context.getContentResolver().insert(Notes.CONTENT_DATA_URI, values);
            }
            c.close();
        } else {
             context.getContentResolver().insert(Notes.CONTENT_DATA_URI, values);
        }
    }
}
