package net.micode.notes.model;

import android.text.Editable;
import android.widget.EditText;

public class NoteCommand implements Command {
    private final EditText mEditor;
    private final int mStart;
    private final CharSequence mBefore;
    private final CharSequence mAfter;

    public NoteCommand(EditText editor, int start, CharSequence before, CharSequence after) {
        mEditor = editor;
        mStart = start;
        mBefore = before.toString();
        mAfter = after.toString();
    }

    @Override
    public void execute() {
        // Redo: replace 'before' with 'after'
        Editable text = mEditor.getText();
        int end = mStart + mBefore.length();
        if (end <= text.length()) {
            text.replace(mStart, end, mAfter);
            mEditor.setSelection(mStart + mAfter.length());
        }
    }

    @Override
    public void undo() {
        // Undo: replace 'after' with 'before'
        Editable text = mEditor.getText();
        int end = mStart + mAfter.length();
        if (end <= text.length()) {
            text.replace(mStart, end, mBefore);
            mEditor.setSelection(mStart + mBefore.length());
        }
    }
}
