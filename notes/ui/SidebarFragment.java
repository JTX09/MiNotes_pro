/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.auth.UserAuthManager;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.sync.SyncManager;
import net.micode.notes.viewmodel.FolderListViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 现代化侧边栏 Fragment - 自定义布局版
 * <p>
 * 使用自定义 LinearLayout 替代 NavigationView
 * 文件夹树在"文件夹"菜单项位置直接展开
 * </p>
 */
public class SidebarFragment extends Fragment {

    private static final String TAG = "SidebarFragment";
    private static final int MAX_FOLDER_NAME_LENGTH = 50;

    // 菜单项
    private LinearLayout menuAllNotes;
    private LinearLayout menuTrash;
    private LinearLayout menuFolders;
    private LinearLayout menuSyncSettings;
    private LinearLayout menuTemplates;
    private LinearLayout menuExport;
    private LinearLayout menuSettings;
    private LinearLayout menuCapsule;
    private LinearLayout menuLogout;
    private View logoutDivider;

    // 文件夹树
    private LinearLayout folderTreeContainer;
    private RecyclerView rvFolderTree;
    private ImageButton btnCreateFolder;
    private ImageView ivFolderExpand;

    // 头部
    private LinearLayout headerNotLoggedIn;
    private LinearLayout headerLoggedIn;
    private View btnLoginPrompt;
    private TextView tvUsername;
    private TextView tvDeviceId;

    // ViewModel
    private FolderListViewModel viewModel;
    private FolderTreeAdapter adapter;

    // 回调接口
    private OnSidebarItemSelectedListener listener;

    // 状态
    private boolean isFolderTreeExpanded = false;

    public interface OnSidebarItemSelectedListener {
        void onFolderSelected(long folderId);
        void onTrashSelected();
        void onSyncSelected();
        void onLoginSelected();
        void onLogoutSelected();
        void onExportSelected();
        void onTemplateSelected();
        void onSettingsSelected();
        void onCapsuleSelected();
        void onCreateFolder();
        void onCloseSidebar();
        void onRenameFolder(long folderId);
        void onDeleteFolder(long folderId);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof OnSidebarItemSelectedListener) {
            listener = (OnSidebarItemSelectedListener) getParentFragment();
        } else if (context instanceof OnSidebarItemSelectedListener) {
            listener = (OnSidebarItemSelectedListener) context;
        } else {
            // throw new RuntimeException(context.toString() + " must implement OnSidebarItemSelectedListener");
            // Allow null listener for now to avoid crashes during refactoring
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FolderListViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sidebar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        initListeners();
        initFolderTree();
        observeViewModel();
        updateUserState();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUserState();
    }

    private void initViews(View view) {
        // 头部
        headerNotLoggedIn = view.findViewById(R.id.header_not_logged_in);
        headerLoggedIn = view.findViewById(R.id.header_logged_in);
        btnLoginPrompt = view.findViewById(R.id.btn_login_prompt);
        tvUsername = view.findViewById(R.id.tv_username);
        tvDeviceId = view.findViewById(R.id.tv_device_id);

        // 菜单项
        menuAllNotes = view.findViewById(R.id.menu_all_notes);
        menuTrash = view.findViewById(R.id.menu_trash);
        menuFolders = view.findViewById(R.id.menu_folders);
        menuSyncSettings = view.findViewById(R.id.menu_sync_settings);
        menuTemplates = view.findViewById(R.id.menu_templates);
        menuExport = view.findViewById(R.id.menu_export);
        menuSettings = view.findViewById(R.id.menu_settings);
        menuCapsule = view.findViewById(R.id.menu_capsule);
        menuLogout = view.findViewById(R.id.menu_logout);
        logoutDivider = view.findViewById(R.id.logout_divider);

        // 文件夹树
        folderTreeContainer = view.findViewById(R.id.folder_tree_container);
        rvFolderTree = view.findViewById(R.id.rv_folder_tree);
        btnCreateFolder = view.findViewById(R.id.btn_create_folder);
        ivFolderExpand = view.findViewById(R.id.iv_folder_expand);
    }

    private void initListeners() {
        if (headerNotLoggedIn != null) {
            headerNotLoggedIn.setOnClickListener(v -> {
                if (listener != null) listener.onLoginSelected();
            });
        }
        
        if (headerLoggedIn != null) {
            headerLoggedIn.setOnClickListener(v -> {
                Log.d(TAG, "Logged in header clicked");
            });
        }

        // 菜单项点击
        menuAllNotes.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFolderSelected(Notes.ID_ROOT_FOLDER);
                listener.onCloseSidebar();
            }
        });

        menuTrash.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrashSelected();
                listener.onCloseSidebar();
            }
        });

        menuFolders.setOnClickListener(v -> toggleFolderTree());

        menuSyncSettings.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SyncActivity.class);
            startActivity(intent);
            if (listener != null) listener.onCloseSidebar();
        });

        menuTemplates.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTemplateSelected();
                listener.onCloseSidebar();
            }
        });

        menuExport.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExportSelected();
                listener.onCloseSidebar();
            }
        });

        menuSettings.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSettingsSelected();
                listener.onCloseSidebar();
            }
        });

        if (menuCapsule != null) {
            menuCapsule.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCapsuleSelected();
                    listener.onCloseSidebar();
                }
            });
        }

        menuLogout.setOnClickListener(v -> showLogoutConfirmDialog());
    }

    private void toggleFolderTree() {
        isFolderTreeExpanded = !isFolderTreeExpanded;
        folderTreeContainer.setVisibility(isFolderTreeExpanded ? View.VISIBLE : View.GONE);
        
        // 旋转展开图标
        if (ivFolderExpand != null) {
            ivFolderExpand.setRotation(isFolderTreeExpanded ? 180 : 0);
        }
    }

    private void initFolderTree() {
        rvFolderTree.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FolderTreeAdapter(new ArrayList<>(), viewModel);
        adapter.setOnFolderItemClickListener(folderId -> {
            if (listener != null) {
                listener.onFolderSelected(folderId);
                listener.onCloseSidebar();
            }
        });
        adapter.setOnFolderItemLongClickListener(this::handleFolderItemLongClick);
        rvFolderTree.setAdapter(adapter);

        if (btnCreateFolder != null) {
            btnCreateFolder.setOnClickListener(v -> showCreateFolderDialog());
        }
    }

    private void observeViewModel() {
        viewModel.getFolderTree().observe(getViewLifecycleOwner(), folderItems -> {
            if (folderItems != null) {
                adapter.setData(folderItems);
                adapter.notifyDataSetChanged();
            }
        });
        viewModel.loadFolderTree();
    }

    public void updateUserState() {
        UserAuthManager authManager = UserAuthManager.getInstance(requireContext());
        boolean isLoggedIn = authManager.isLoggedIn();

        // 更新头部
        if (headerNotLoggedIn != null && headerLoggedIn != null) {
            if (isLoggedIn) {
                headerNotLoggedIn.setVisibility(View.GONE);
                headerLoggedIn.setVisibility(View.VISIBLE);

                String username = authManager.getUsername();
                String deviceId = authManager.getDeviceId();

                if (tvUsername != null) {
                    tvUsername.setText(username != null ? username : getString(R.string.drawer_default_username));
                }
                if (tvDeviceId != null) {
                    tvDeviceId.setText(deviceId != null ? "Device: " + deviceId.substring(0, Math.min(8, deviceId.length()))
                            : getString(R.string.drawer_default_device_id));
                }
            } else {
                headerNotLoggedIn.setVisibility(View.VISIBLE);
                headerLoggedIn.setVisibility(View.GONE);
            }
        }

        // 更新菜单项
        if (menuLogout != null) {
            menuLogout.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        }
        if (logoutDivider != null) {
            logoutDivider.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        }
    }

    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_logout_title)
                .setMessage(R.string.dialog_logout_message)
                .setPositiveButton(R.string.dialog_logout_confirm, (dialog, which) -> performLogout())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performLogout() {
        UserAuthManager authManager = UserAuthManager.getInstance(requireContext());
        authManager.logout();

        // 重置同步状态，清除上次同步时间
        SyncManager.getInstance().resetSyncState();

        updateUserState();

        if (listener != null) {
            listener.onLogoutSelected();
            listener.onCloseSidebar();
        }

        Toast.makeText(requireContext(), R.string.toast_logout_success, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "User logged out successfully");
    }

    private void handleFolderItemLongClick(long folderId) {
        if (folderId <= 0) return;
        
        PopupMenu popup = new PopupMenu(requireContext(), rvFolderTree);
        popup.getMenuInflater().inflate(R.menu.folder_context_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_rename && listener != null) {
                listener.onRenameFolder(folderId);
                return true;
            } else if (itemId == R.id.action_delete && listener != null) {
                listener.onDeleteFolder(folderId);
                return true;
            } else if (itemId == R.id.action_move) {
                Toast.makeText(requireContext(), "移动功能开发中", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        popup.show();
    }

    public void showCreateFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.dialog_create_folder_title);

        final EditText input = new EditText(requireContext());
        input.setHint(R.string.dialog_create_folder_hint);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_FOLDER_NAME_LENGTH)});

        builder.setView(input);

        builder.setPositiveButton(R.string.menu_create_folder, (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (TextUtils.isEmpty(folderName)) {
                Toast.makeText(requireContext(), R.string.error_folder_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            createFolder(folderName);
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void createFolder(String folderName) {
        NotesRepository repository = new NotesRepository(requireContext().getContentResolver());
        long parentId = viewModel.getCurrentFolderId();
        if (parentId == 0) parentId = Notes.ID_ROOT_FOLDER;
        
        repository.createFolder(parentId, folderName, new NotesRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long folderId) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), R.string.create_folder_success, Toast.LENGTH_SHORT).show();
                        viewModel.loadFolderTree();
                    });
                }
            }

            @Override
            public void onError(Exception error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                getString(R.string.error_create_folder) + ": " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    public void refreshFolderTree() {
        if (viewModel != null) viewModel.loadFolderTree();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    // ==================== FolderTreeAdapter ====================

    private static class FolderTreeAdapter extends RecyclerView.Adapter<FolderTreeAdapter.FolderViewHolder> {

        private List<FolderTreeItem> folderItems;
        private FolderListViewModel viewModel;
        private OnFolderItemClickListener folderItemClickListener;
        private OnFolderItemLongClickListener folderItemLongClickListener;

        public FolderTreeAdapter(List<FolderTreeItem> folderItems, FolderListViewModel viewModel) {
            this.folderItems = folderItems;
            this.viewModel = viewModel;
        }

        public void setData(List<FolderTreeItem> folderItems) {
            this.folderItems = folderItems;
        }

        public void setOnFolderItemClickListener(OnFolderItemClickListener listener) {
            this.folderItemClickListener = listener;
        }

        public void setOnFolderItemLongClickListener(OnFolderItemLongClickListener listener) {
            this.folderItemLongClickListener = listener;
        }

        @NonNull
        @Override
        public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.sidebar_folder_item, parent, false);
            return new FolderViewHolder(view, folderItemClickListener, folderItemLongClickListener);
        }

        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
            FolderTreeItem item = folderItems.get(position);
            boolean isExpanded = viewModel != null && viewModel.isFolderExpanded(item.folderId);
            holder.bind(item, isExpanded);
        }

        @Override
        public int getItemCount() {
            return folderItems.size();
        }

        static class FolderViewHolder extends RecyclerView.ViewHolder {
            private View indentView;
            private View ivFolderIcon;
            private TextView tvFolderName;
            private TextView tvNoteCount;
            private FolderTreeItem currentItem;

            public FolderViewHolder(@NonNull View itemView, OnFolderItemClickListener clickListener,
                                   OnFolderItemLongClickListener longClickListener) {
                super(itemView);
                indentView = itemView.findViewById(R.id.indent_view);
                ivFolderIcon = itemView.findViewById(R.id.iv_folder_icon);
                tvFolderName = itemView.findViewById(R.id.tv_folder_name);
                tvNoteCount = itemView.findViewById(R.id.tv_note_count);

                itemView.setOnClickListener(v -> {
                    if (clickListener != null && currentItem != null) {
                        clickListener.onFolderClick(currentItem.folderId);
                    }
                });

                itemView.setOnLongClickListener(v -> {
                    if (longClickListener != null && currentItem != null) {
                        longClickListener.onFolderLongClick(currentItem.folderId);
                        return true;
                    }
                    return false;
                });
            }

            public void bind(FolderTreeItem item, boolean isExpanded) {
                this.currentItem = item;

                int indent = item.level * 32;
                indentView.setLayoutParams(new LinearLayout.LayoutParams(indent, LinearLayout.LayoutParams.MATCH_PARENT));

                tvFolderName.setText(item.name);
                tvNoteCount.setText(String.format(itemView.getContext()
                        .getString(R.string.folder_note_count), item.noteCount));
            }
        }
    }

    public interface OnFolderItemClickListener {
        void onFolderClick(long folderId);
    }

    public interface OnFolderItemLongClickListener {
        void onFolderLongClick(long folderId);
    }

    public static class FolderTreeItem {
        public long folderId;
        public String name;
        public int level;
        public boolean hasChildren;
        public int noteCount;

        public FolderTreeItem(long folderId, String name, int level, boolean hasChildren, int noteCount) {
            this.folderId = folderId;
            this.name = name;
            this.level = level;
            this.hasChildren = hasChildren;
            this.noteCount = noteCount;
        }
    }
}
