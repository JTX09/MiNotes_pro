package net.micode.notes.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import android.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.databinding.NoteListBinding;
import net.micode.notes.viewmodel.NotesListViewModel;

public class NotesListFragment extends Fragment implements 
        NoteInfoAdapter.OnNoteItemClickListener,
        NoteInfoAdapter.OnNoteItemLongClickListener,
        NoteInfoAdapter.OnSwipeMenuClickListener {

    private static final String TAG = "NotesListFragment";
    private static final String PREF_KEY_IS_STAGGERED = "is_staggered";

    private NotesListViewModel viewModel;
    private NoteListBinding binding;
    private NoteInfoAdapter adapter;
    
    private static final int REQUEST_CODE_OPEN_NODE = 102;
    private static final int REQUEST_CODE_NEW_NODE = 103;
    private static final int REQUEST_CODE_VERIFY_PASSWORD_FOR_OPEN = 107;

    private NotesRepository.NoteInfo pendingNote;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = NoteListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViewModel();
        initViews(view);
        observeViewModel();
    }

    private void initViewModel() {
        NotesRepository repository = new NotesRepository(requireContext().getContentResolver());
        // Use requireActivity() to share ViewModel with Activity (for Sidebar filtering)
        viewModel = new ViewModelProvider(requireActivity(),
            new ViewModelProvider.Factory() {
                @Override
                public <T extends androidx.lifecycle.ViewModel> T create(Class<T> modelClass) {
                    return (T) new NotesListViewModel(requireActivity().getApplication(), repository);
                }
            }).get(NotesListViewModel.class);
    }

    private void initViews(View view) {
        adapter = new NoteInfoAdapter(requireContext());
        binding.notesList.setAdapter(adapter);
        
        // Restore layout preference
        boolean isStaggered = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(PREF_KEY_IS_STAGGERED, true);
        setLayoutManager(isStaggered);
        
        adapter.setOnNoteItemClickListener(this);
        adapter.setOnNoteItemLongClickListener(this);
        adapter.setOnSwipeMenuClickListener(this);

        // Fix FAB: Enable creating new notes
        binding.btnNewNote.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), NoteEditActivity.class);
            intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
            intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, viewModel.getCurrentFolderId());
            startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
        });
    }

    private void setLayoutManager(boolean isStaggered) {
        if (isStaggered) {
            StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
            layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
            binding.notesList.setLayoutManager(layoutManager);
        } else {
            binding.notesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
    }

    public boolean toggleLayout() {
        boolean isStaggered = binding.notesList.getLayoutManager() instanceof StaggeredGridLayoutManager;
        boolean newIsStaggered = !isStaggered;
        
        setLayoutManager(newIsStaggered);
        
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putBoolean(PREF_KEY_IS_STAGGERED, newIsStaggered)
                .apply();
        
        return newIsStaggered;
    }
    
    public boolean isStaggeredLayout() {
         return binding.notesList.getLayoutManager() instanceof StaggeredGridLayoutManager;
    }

    private void observeViewModel() {
        viewModel.getNotesLiveData().observe(getViewLifecycleOwner(), notes -> {
            adapter.setNotes(notes);
        });
        
        viewModel.getIsSelectionMode().observe(getViewLifecycleOwner(), isSelection -> {
            adapter.setSelectionMode(isSelection);
        });
        
        viewModel.getSelectedIdsLiveData().observe(getViewLifecycleOwner(), selectedIds -> {
            adapter.setSelectedIds(selectedIds);
        });
    }
    
    @Override
    public void onNoteItemClick(int position, long noteId) {
        if (Boolean.TRUE.equals(viewModel.getIsSelectionMode().getValue())) {
            boolean isSelected = viewModel.getSelectedIdsLiveData().getValue() != null && 
                                 viewModel.getSelectedIdsLiveData().getValue().contains(noteId);
            viewModel.toggleNoteSelection(noteId, !isSelected);
            return;
        }

        if (viewModel.getNotesLiveData().getValue() != null && position < viewModel.getNotesLiveData().getValue().size()) {
            NotesRepository.NoteInfo note = viewModel.getNotesLiveData().getValue().get(position);
            if (note.type == Notes.TYPE_FOLDER) {
                viewModel.enterFolder(note.getId());
            } else if (note.type == Notes.TYPE_TEMPLATE) {
                // Apply template: create a new note based on this template
                viewModel.applyTemplate(note.getId(), new net.micode.notes.data.NotesRepository.Callback<Long>() {
                    @Override
                    public void onSuccess(Long newNoteId) {
                        // Create a temporary NoteInfo to open the editor
                        net.micode.notes.data.NotesRepository.NoteInfo newNote = new net.micode.notes.data.NotesRepository.NoteInfo();
                        newNote.setId(newNoteId);
                        newNote.setParentId(Notes.ID_ROOT_FOLDER);
                        newNote.type = Notes.TYPE_NOTE;
                        
                        requireActivity().runOnUiThread(() -> {
                            openNoteEditor(newNote);
                            Toast.makeText(requireContext(), "已根据模板创建新笔记", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "应用模板失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } else {
                if (note.isLocked) {
                    pendingNote = note;
                    Intent intent = new Intent(getActivity(), PasswordActivity.class);
                    intent.setAction(PasswordActivity.ACTION_CHECK_PASSWORD);
                    startActivityForResult(intent, REQUEST_CODE_VERIFY_PASSWORD_FOR_OPEN);
                } else {
                    openNoteEditor(note);
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VERIFY_PASSWORD_FOR_OPEN && resultCode == android.app.Activity.RESULT_OK) {
            if (pendingNote != null) {
                openNoteEditor(pendingNote);
                pendingNote = null;
            }
        }
    }

    @Override
    public void onNoteItemLongClick(int position, long noteId) {
        if (!Boolean.TRUE.equals(viewModel.getIsSelectionMode().getValue())) {
            viewModel.setIsSelectionMode(true);
            viewModel.toggleNoteSelection(noteId, true);
        } else {
             boolean isSelected = viewModel.getSelectedIdsLiveData().getValue() != null && 
                                  viewModel.getSelectedIdsLiveData().getValue().contains(noteId);
             viewModel.toggleNoteSelection(noteId, !isSelected);
        }
    }

    // Deprecated Context Menu
    private void showContextMenu(NotesRepository.NoteInfo note) {
        // ... kept for reference or removed
    }

    private void openNoteEditor(NotesRepository.NoteInfo note) {
        Intent intent = new Intent(getActivity(), NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, note.getParentId());
        intent.putExtra(Intent.EXTRA_UID, note.getId());
        startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshNotes();
    }

    // Swipe Menu Callbacks
    @Override
    public void onSwipeEdit(long itemId) {
        android.util.Log.d(TAG, "onSwipeEdit called for itemId: " + itemId);
        if (viewModel.getNotesLiveData().getValue() != null) {
            boolean found = false;
            for (NotesRepository.NoteInfo note : viewModel.getNotesLiveData().getValue()) {
                if (note.getId() == itemId) {
                    found = true;
                    if (note.type == Notes.TYPE_FOLDER) {
                        onSwipeRename(itemId);
                    } else {
                        openNoteEditor(note);
                    }
                    break;
                }
            }
            if (!found) {
                Toast.makeText(requireContext(), "未找到笔记 (ID: " + itemId + ")", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(requireContext(), "数据未加载", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onSwipePin(long itemId) { viewModel.toggleNoteSelection(itemId, true); viewModel.toggleSelectedNotesPin(); viewModel.setIsSelectionMode(false); }
    @Override public void onSwipeMove(long itemId) { /* Show move dialog */ }
    @Override public void onSwipeDelete(long itemId) { viewModel.deleteNote(itemId); }
    
    @Override 
    public void onSwipeRename(long itemId) { 
        // Show rename dialog for folder
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        
        // Find current name
        String currentName = "";
        if (viewModel.getNotesLiveData().getValue() != null) {
            for (NotesRepository.NoteInfo note : viewModel.getNotesLiveData().getValue()) {
                if (note.getId() == itemId) {
                    currentName = note.snippet;
                    break;
                }
            }
        }
        input.setText(currentName);

        new AlertDialog.Builder(requireContext())
            .setTitle("重命名文件夹")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    viewModel.renameFolder(itemId, newName);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    @Override public void onSwipeRestore(long itemId) { viewModel.toggleNoteSelection(itemId, true); viewModel.restoreSelectedNotes(); viewModel.setIsSelectionMode(false); }
    @Override public void onSwipePermanentDelete(long itemId) { viewModel.toggleNoteSelection(itemId, true); viewModel.deleteSelectedNotesForever(); viewModel.setIsSelectionMode(false); }
}
