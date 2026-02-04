package net.micode.notes.ui;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskListFragment extends Fragment implements TaskListAdapter.OnTaskItemClickListener {

    private RecyclerView recyclerView;
    private TaskListAdapter adapter;
    private FloatingActionButton fab;
    private static final int REQUEST_EDIT_TASK = 1001;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_task_list, container, false);
        
        // Hide Toolbar in fragment if Activity has one or Tabs
        // For now, let's keep it but remove navigation logic or hide it if needed
        View toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            // toolbar.setVisibility(View.GONE); // Optional: Hide if using main tabs
        }

        recyclerView = view.findViewById(R.id.task_list_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TaskListAdapter(getContext(), this);
        recyclerView.setAdapter(adapter);

        fab = view.findViewById(R.id.btn_new_task);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), TaskEditActivity.class);
            startActivityForResult(intent, REQUEST_EDIT_TASK);
        });
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTasks();
    }

    private void loadTasks() {
        new Thread(() -> {
            if (getContext() == null) return;
            Cursor cursor = getContext().getContentResolver().query(
                Notes.CONTENT_NOTE_URI,
                null,
                NoteColumns.TYPE + "=?",
                new String[]{String.valueOf(Notes.TYPE_TASK)},
                null
            );

            List<Task> tasks = new ArrayList<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    tasks.add(Task.fromCursor(cursor));
                }
                cursor.close();
            }
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> adapter.setTasks(tasks));
            }
        }).start();
    }

    @Override
    public void onItemClick(Task task) {
        Intent intent = new Intent(getActivity(), TaskEditActivity.class);
        intent.putExtra(Intent.EXTRA_UID, task.id);
        startActivityForResult(intent, REQUEST_EDIT_TASK);
    }

    @Override
    public void onCheckBoxClick(Task task) {
        task.status = (task.status == Task.STATUS_ACTIVE) ? Task.STATUS_COMPLETED : Task.STATUS_ACTIVE;
        if (task.status == Task.STATUS_COMPLETED) {
            task.finishedTime = System.currentTimeMillis();
        } else {
            task.finishedTime = 0;
        }
        
        new Thread(() -> {
            if (getContext() != null) {
                task.save(getContext());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> loadTasks());
                }
            }
        }).start();
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_TASK && resultCode == android.app.Activity.RESULT_OK) {
            loadTasks();
        }
    }
}
