package net.micode.notes.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.tool.SearchHistoryManager;

import java.util.ArrayList;
import java.util.List;

public class NoteSearchActivity extends BaseActivity implements SearchView.OnQueryTextListener, NoteSearchAdapter.OnItemClickListener {

    private SearchView mSearchView;
    private RecyclerView mRecyclerView;
    private TextView mTvNoResult;
    private NoteSearchAdapter mAdapter;
    private NotesRepository mRepository;
    private SearchHistoryManager mHistoryManager;

    private TextView mBtnShowHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_search);

        mRepository = new NotesRepository(getContentResolver());
        mHistoryManager = new SearchHistoryManager(this);

        initViews();
        // Initial state: search is empty, show history button if there is history, or just show list
        // Requirement: "history option below search bar"
        showHistoryOption();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        mSearchView = findViewById(R.id.search_view);
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setFocusable(true);
        mSearchView.setIconified(false);
        mSearchView.requestFocusFromTouch();

        mBtnShowHistory = findViewById(R.id.btn_show_history);
        mBtnShowHistory.setOnClickListener(v -> showHistoryList());

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new NoteSearchAdapter(this, this);
        mRecyclerView.setAdapter(mAdapter);

        mTvNoResult = findViewById(R.id.tv_no_result);
    }

    private void showHistoryOption() {
        // Show the "History" button, hide the list
        mBtnShowHistory.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.GONE);
        mTvNoResult.setVisibility(View.GONE);
    }

    private void showHistoryList() {
        List<String> history = mHistoryManager.getHistory();
        if (history.isEmpty()) {
             // If no history, maybe show a toast or empty state?
             // But for now, let's just show the empty list which is fine
        }
        List<Object> data = new ArrayList<>(history);
        mAdapter.setData(data, null);
        
        mBtnShowHistory.setVisibility(View.GONE); // Hide button when showing list
        mTvNoResult.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    private void performSearch(String query) {
        if (TextUtils.isEmpty(query)) {
            showHistoryOption();
            return;
        }

        // Hide history button when searching
        mBtnShowHistory.setVisibility(View.GONE);

        mRepository.searchNotes(query, new NotesRepository.Callback<List<NotesRepository.NoteInfo>>() {
            @Override
            public void onSuccess(List<NotesRepository.NoteInfo> result) {
                runOnUiThread(() -> {
                    List<Object> data = new ArrayList<>(result);
                    mAdapter.setData(data, query);
                    if (data.isEmpty()) {
                        mTvNoResult.setVisibility(View.VISIBLE);
                        mRecyclerView.setVisibility(View.GONE);
                    } else {
                        mTvNoResult.setVisibility(View.GONE);
                        mRecyclerView.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    Toast.makeText(NoteSearchActivity.this, "Search failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (!TextUtils.isEmpty(query)) {
            mHistoryManager.addHistory(query);
            performSearch(query);
            mSearchView.clearFocus(); // Hide keyboard
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (TextUtils.isEmpty(newText)) {
            showHistoryOption();
        } else {
            performSearch(newText);
        }
        return true;
    }

    @Override
    public void onNoteClick(NotesRepository.NoteInfo note) {
        // Save history when user clicks a result
        String query = mSearchView.getQuery().toString();
        if (!TextUtils.isEmpty(query)) {
            mHistoryManager.addHistory(query);
        }

        if (note.type == Notes.TYPE_TEMPLATE) {
            // Apply template: create a new note based on this template
            mRepository.applyTemplate(note.getId(), Notes.ID_ROOT_FOLDER, new NotesRepository.Callback<Long>() {
                @Override
                public void onSuccess(Long newNoteId) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(NoteSearchActivity.this, NoteEditActivity.class);
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.putExtra(Intent.EXTRA_UID, newNoteId);
                        startActivity(intent);
                        Toast.makeText(NoteSearchActivity.this, "已根据模板创建新笔记", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(NoteSearchActivity.this, "应用模板失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            Intent intent = new Intent(this, NoteEditActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, note.getId());
            // Pass search keyword for highlighting in editor
            intent.putExtra(android.app.SearchManager.EXTRA_DATA_KEY, String.valueOf(note.getId()));
            intent.putExtra(android.app.SearchManager.USER_QUERY, query);
            startActivity(intent);
        }
    }

    @Override
    public void onHistoryClick(String keyword) {
        mSearchView.setQuery(keyword, true);
    }

    @Override
    public void onHistoryDelete(String keyword) {
        mHistoryManager.removeHistory(keyword);
        // Refresh history view if we are currently showing history (search box is empty)
        if (TextUtils.isEmpty(mSearchView.getQuery())) {
            showHistoryList();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRepository != null) {
            mRepository.shutdown();
        }
    }
}
