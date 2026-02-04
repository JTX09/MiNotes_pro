package net.micode.notes.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.util.Log;
import net.micode.notes.data.Notes;
import net.micode.notes.model.Note;
import net.micode.notes.capsule.CapsuleService;

public class CapsuleActionActivity extends Activity {

    private static final String TAG = "CapsuleActionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String sourcePackage = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (getReferrer() != null) {
                sourcePackage = getReferrer().getAuthority(); // or getHost()
            }
        }

        if (text != null) {
            saveNote(text.toString(), sourcePackage);
            
            // Notify CapsuleService to animate (if running)
            Intent intent = new Intent("net.micode.notes.capsule.ACTION_SAVE_SUCCESS");
            sendBroadcast(intent);
        }

        finish();
    }

    private void saveNote(String content, String source) {
        new Thread(() -> {
            try {
                long noteId = Note.getNewNoteId(this, Notes.ID_CAPSULE_FOLDER);
                Note note = new Note();
                note.setTextData(Notes.DataColumns.CONTENT, content);
                
                String summary = content.length() > 20 ? content.substring(0, 20) + "..." : content;
                int firstLineEnd = content.indexOf('\n');
                if (firstLineEnd > 0 && firstLineEnd < 20) {
                     summary = content.substring(0, firstLineEnd);
                }
                note.setNoteValue(Notes.NoteColumns.SNIPPET, summary);

                if (source != null) {
                    note.setTextData(Notes.DataColumns.DATA3, source);
                }
                
                boolean success = note.syncNote(this, noteId);
                
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "已保存到胶囊", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
