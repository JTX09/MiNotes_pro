package net.micode.notes.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskListActivity extends BaseActivity implements TaskListAdapter.OnTaskItemClickListener {

    private RecyclerView recyclerView;
    private TaskListAdapter adapter;
    private FloatingActionButton fab;
    private static final int REQUEST_EDIT_TASK = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_list);
        
        // Initialize Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            // Remove default title
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }
        
        // Setup Navigation Icon (Back Button) - REMOVED as per new requirement
        // toolbar.setNavigationOnClickListener(v -> {
        //    finish();
        //    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        // });

        recyclerView = findViewById(R.id.task_list_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskListAdapter(this, this);
        recyclerView.setAdapter(adapter);

        fab = findViewById(R.id.btn_new_task);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(TaskListActivity.this, TaskEditActivity.class);
            startActivityForResult(intent, REQUEST_EDIT_TASK);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTasks();
    }

    private void loadTasks() {
        new Thread(() -> {
            Cursor cursor = getContentResolver().query(
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
            
            android.util.Log.d("TaskListActivity", "Loaded tasks count: " + tasks.size());

            runOnUiThread(() -> adapter.setTasks(tasks));
        }).start();
    }

    @Override
    public void onItemClick(Task task) {
        Intent intent = new Intent(this, TaskEditActivity.class);
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
            task.save(this);
            runOnUiThread(() -> loadTasks()); // Reload to sort
        }).start();
    }
    
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Removed menu_notes as per requirement, keeping empty or future menus
        // getMenuInflater().inflate(R.menu.task_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_TASK && resultCode == RESULT_OK) {
            loadTasks();
        }
    }
}