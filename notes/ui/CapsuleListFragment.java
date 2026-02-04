package net.micode.notes.ui;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.model.Note;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CapsuleListFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private CapsuleAdapter mAdapter;
    private TextView mEmptyView;
    private Toolbar mToolbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_capsule_list, container, false);
        mRecyclerView = view.findViewById(R.id.capsule_list);
        mEmptyView = view.findViewById(R.id.tv_empty);
        mToolbar = view.findViewById(R.id.toolbar);
        
        setupToolbar();
        
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new CapsuleAdapter();
        mRecyclerView.setAdapter(mAdapter);
        
        return view;
    }

    private void setupToolbar() {
        if (mToolbar != null && getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(mToolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setTitle("速记胶囊");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCapsules();
    }

    private void loadCapsules() {
        new Thread(() -> {
            if (getContext() == null) return;
            
            // Query notes in CAPSULE folder. 
            // Join with Data table to get DATA3 (source package)
            Cursor cursor = getContext().getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    null,
                    Notes.NoteColumns.PARENT_ID + "=?",
                    new String[]{String.valueOf(Notes.ID_CAPSULE_FOLDER)},
                    Notes.NoteColumns.MODIFIED_DATE + " DESC"
            );

            List<CapsuleItem> items = new ArrayList<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Notes.NoteColumns.ID));
                    String snippet = cursor.getString(cursor.getColumnIndexOrThrow(Notes.NoteColumns.SNIPPET));
                    long modifiedDate = cursor.getLong(cursor.getColumnIndexOrThrow(Notes.NoteColumns.MODIFIED_DATE));
                    
                    // Try to get source from projection (if joined) or query separately
                    String source = "";
                    try {
                        int sourceIdx = cursor.getColumnIndex(Notes.DataColumns.DATA3);
                        if (sourceIdx != -1) {
                            source = cursor.getString(sourceIdx);
                        }
                    } catch (Exception e) {
                        // Not joined, ignore for now or lazy load
                    }
                    
                    items.add(new CapsuleItem(id, snippet, modifiedDate, source));
                }
                cursor.close();
            }

            // Update UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    mAdapter.setItems(items);
                    mEmptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        }).start();
    }

    private void openNoteEditor(long noteId) {
        Intent intent = new Intent(getActivity(), NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, noteId);
        startActivity(intent);
    }

    private static class CapsuleItem {
        long id;
        String summary;
        long time;
        String source;

        public CapsuleItem(long id, String summary, long time, String source) {
            this.id = id;
            this.summary = summary;
            this.time = time;
            this.source = source;
        }
    }

    private class CapsuleAdapter extends RecyclerView.Adapter<CapsuleAdapter.ViewHolder> {
        private List<CapsuleItem> mItems = new ArrayList<>();

        public void setItems(List<CapsuleItem> items) {
            mItems = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_capsule, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CapsuleItem item = mItems.get(position);
            holder.tvSummary.setText(item.summary);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            holder.tvTime.setText(sdf.format(new Date(item.time)));
            
            if (item.source != null && !item.source.isEmpty()) {
                holder.tvSource.setText(item.source);
                holder.tvSource.setVisibility(View.VISIBLE);
            } else {
                holder.tvSource.setVisibility(View.GONE);
            }
            
            holder.itemView.setOnClickListener(v -> {
                openNoteEditor(item.id);
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSummary, tvTime, tvSource;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSummary = itemView.findViewById(R.id.tv_summary);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvSource = itemView.findViewById(R.id.tv_source);
            }
        }
    }
}
