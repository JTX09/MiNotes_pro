package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Paint;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.model.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TaskListAdapter extends RecyclerView.Adapter<TaskListAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private Context context;
    private OnTaskItemClickListener listener;

    public interface OnTaskItemClickListener {
        void onItemClick(Task task);
        void onCheckBoxClick(Task task);
    }

    public TaskListAdapter(Context context, OnTaskItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setTasks(List<Task> newTasks) {
        this.tasks = new ArrayList<>(newTasks);
        sortTasks();
        notifyDataSetChanged();
    }

    private void sortTasks() {
        Collections.sort(tasks, new Comparator<Task>() {
            @Override
            public int compare(Task t1, Task t2) {
                // 1. Status: Active (0) < Completed (1)
                if (t1.status != t2.status) {
                    return Integer.compare(t1.status, t2.status);
                }

                // 2. If both Active
                if (t1.status == Task.STATUS_ACTIVE) {
                    // Priority: High (2) > Mid (1) > Low (0) -> DESC
                    if (t1.priority != t2.priority) {
                        return Integer.compare(t2.priority, t1.priority);
                    }
                    
                    if (t1.dueDate != t2.dueDate) {
                        if (t1.dueDate == 0) return 1; // t1 no date -> bottom
                        if (t2.dueDate == 0) return -1; // t2 no date -> bottom
                        return Long.compare(t1.dueDate, t2.dueDate); // Early date first
                    }
                    
                    // Creation Date (Fallback)
                    return Long.compare(t2.createdDate, t1.createdDate);
                }

                // 3. If both Completed
                // DESC sort by finishedTime.
                return Long.compare(t2.finishedTime, t1.finishedTime);
            }
        });
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.task_list_item, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView content;
        TextView priority;
        TextView date;
        ImageView alarm;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.task_checkbox);
            content = itemView.findViewById(R.id.task_content);
            priority = itemView.findViewById(R.id.task_priority);
            date = itemView.findViewById(R.id.task_date);
            alarm = itemView.findViewById(R.id.task_alarm_icon);
            
            itemView.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onItemClick(tasks.get(getAdapterPosition()));
                }
            });
            
            checkBox.setOnClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onCheckBoxClick(tasks.get(getAdapterPosition()));
                }
            });
        }

        public void bind(Task task) {
            content.setText(task.snippet);
            checkBox.setChecked(task.status == Task.STATUS_COMPLETED);

            if (task.status == Task.STATUS_COMPLETED) {
                content.setPaintFlags(content.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                content.setAlpha(0.5f);
            } else {
                content.setPaintFlags(content.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                content.setAlpha(1.0f);
            }

            // Priority
            if (task.priority == Task.PRIORITY_HIGH) {
                priority.setVisibility(View.VISIBLE);
                priority.setText("HIGH");
                priority.setBackgroundColor(0xFFFFCDD2); // Light Red
                priority.setTextColor(0xFFB71C1C); // Dark Red
            } else if (task.priority == Task.PRIORITY_NORMAL) {
                priority.setVisibility(View.VISIBLE);
                priority.setText("MED");
                priority.setBackgroundColor(0xFFFFF9C4); // Light Yellow
                priority.setTextColor(0xFFF57F17); // Dark Yellow
            } else {
                priority.setVisibility(View.GONE);
            }

            // Due Date
            if (task.dueDate > 0) {
                date.setVisibility(View.VISIBLE);
                date.setText(DateFormat.format("MM/dd HH:mm", task.dueDate));
            } else {
                date.setVisibility(View.GONE);
            }

            // Alarm
            if (task.alertDate > 0) {
                alarm.setVisibility(View.VISIBLE);
            } else {
                alarm.setVisibility(View.GONE);
            }
        }
    }
}
