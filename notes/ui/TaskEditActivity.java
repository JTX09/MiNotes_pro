package net.micode.notes.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.model.Task;

import java.util.Calendar;

public class TaskEditActivity extends BaseActivity {

    private EditText contentEdit;
    private ImageView alarmBtn;
    private ImageView tagBtn;
    private Button doneBtn;

    private Task task;
    private long taskId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);

        contentEdit = findViewById(R.id.task_edit_content);
        alarmBtn = findViewById(R.id.btn_alarm);
        tagBtn = findViewById(R.id.btn_tag);
        doneBtn = findViewById(R.id.btn_done);

        Intent intent = getIntent();
        taskId = intent.getLongExtra(Intent.EXTRA_UID, 0);

        if (taskId > 0) {
            loadTask();
        } else {
            task = new Task();
        }

        setupListeners();
    }

    private void loadTask() {
        new Thread(() -> {
            Cursor cursor = getContentResolver().query(
                Notes.CONTENT_NOTE_URI,
                null,
                NoteColumns.ID + "=?",
                new String[]{String.valueOf(taskId)},
                null
            );

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    task = Task.fromCursor(cursor);
                    runOnUiThread(() -> {
                        contentEdit.setText(task.snippet);
                        contentEdit.setSelection(task.snippet.length());
                    });
                } else {
                    task = new Task();
                }
                cursor.close();
            } else {
                task = new Task();
            }
        }).start();
    }

    private void setupListeners() {
        doneBtn.setOnClickListener(v -> {
            saveTask();
            setResult(RESULT_OK);
            finish();
        });

        alarmBtn.setOnClickListener(v -> {
            showAlarmDialog();
        });

        tagBtn.setOnClickListener(v -> {
            showTagDialog();
        });
    }

    @Override
    public void onBackPressed() {
        if (saveTask()) {
            setResult(RESULT_OK);
        }
        super.onBackPressed();
    }

    private boolean saveTask() {
        String content = contentEdit.getText().toString();
        if (content.trim().length() == 0) {
            if (task.id == 0) {
                return false;
            }
        }

        task.snippet = content;
        task.save(this);
        
        // Register Alarm if needed.
        if (task.alertDate > 0 && task.alertDate > System.currentTimeMillis()) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(android.content.ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, task.id));
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, task.alertDate, pendingIntent);
        }
        return true;
    }

    private void showAlarmDialog() {
        final Calendar c = Calendar.getInstance();
        if (task.alertDate > 0) {
            c.setTimeInMillis(task.alertDate);
        }

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(this, (view1, hourOfDay, minute) -> {
                c.set(Calendar.HOUR_OF_DAY, hourOfDay);
                c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0);
                
                task.alertDate = c.getTimeInMillis();
                Toast.makeText(this, "Alarm set", Toast.LENGTH_SHORT).show();

            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();

        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTagDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_task_tag, null);
        
        RadioGroup priorityGroup = view.findViewById(R.id.priority_group);
        TextView dateText = view.findViewById(R.id.date_text);
        Button dateBtn = view.findViewById(R.id.btn_set_date);
        
        if (task.priority == Task.PRIORITY_HIGH) priorityGroup.check(R.id.priority_high);
        else if (task.priority == Task.PRIORITY_NORMAL) priorityGroup.check(R.id.priority_mid);
        else priorityGroup.check(R.id.priority_low);
        
        final Calendar c = Calendar.getInstance();
        if (task.dueDate > 0) {
            c.setTimeInMillis(task.dueDate);
            dateText.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", c));
        } else {
            dateText.setText("No Due Date");
        }
        
        dateBtn.setOnClickListener(v -> {
             new DatePickerDialog(this, (dView, year, month, dayOfMonth) -> {
                c.set(Calendar.YEAR, year);
                c.set(Calendar.MONTH, month);
                c.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                new TimePickerDialog(this, (tView, hourOfDay, minute) -> {
                    c.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    c.set(Calendar.MINUTE, minute);
                    
                    task.dueDate = c.getTimeInMillis();
                    dateText.setText(android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", c));
                    
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();

            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        
        new AlertDialog.Builder(this)
            .setTitle("Set Tag")
            .setView(view)
            .setPositiveButton("OK", (dialog, which) -> {
                int id = priorityGroup.getCheckedRadioButtonId();
                if (id == R.id.priority_high) task.priority = Task.PRIORITY_HIGH;
                else if (id == R.id.priority_mid) task.priority = Task.PRIORITY_NORMAL;
                else task.priority = Task.PRIORITY_LOW;
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
