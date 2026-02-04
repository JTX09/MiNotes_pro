package net.micode.notes.model;

public interface Command {
    void execute();
    void undo();
}
