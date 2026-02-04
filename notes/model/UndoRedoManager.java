package net.micode.notes.model;

import java.util.Stack;

public class UndoRedoManager {
    private static final int MAX_STACK_SIZE = 20;
    private final Stack<Command> mUndoStack = new Stack<>();
    private final Stack<Command> mRedoStack = new Stack<>();

    public void addCommand(Command command) {
        mUndoStack.push(command);
        if (mUndoStack.size() > MAX_STACK_SIZE) {
            mUndoStack.remove(0);
        }
        mRedoStack.clear();
    }

    public void undo() {
        if (!mUndoStack.isEmpty()) {
            Command command = mUndoStack.pop();
            command.undo();
            mRedoStack.push(command);
        }
    }

    public void redo() {
        if (!mRedoStack.isEmpty()) {
            Command command = mRedoStack.pop();
            command.execute();
            mUndoStack.push(command);
            if (mUndoStack.size() > MAX_STACK_SIZE) {
                mUndoStack.remove(0);
            }
        }
    }

    public boolean canUndo() {
        return !mUndoStack.isEmpty();
    }

    public boolean canRedo() {
        return !mRedoStack.isEmpty();
    }

    public void clear() {
        mUndoStack.clear();
        mRedoStack.clear();
    }
}
