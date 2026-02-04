package net.micode.notes.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.micode.notes.tool.ImageExportHelper;
import net.micode.notes.tool.PdfExportHelper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import net.micode.notes.R;
import net.micode.notes.auth.UserAuthManager;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.databinding.ActivityHomeBinding;
import net.micode.notes.sync.SyncManager;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.SecurityManager;
import net.micode.notes.viewmodel.NotesListViewModel;

/**
 * 笔记列表Activity
 * <p>
 * 采用 MVVM 架构，负责管理主界面容器、侧边栏和底部导航。
 * 使用 ViewBinding 访问视图。
 * </p>
 */
public class NotesListActivity extends BaseActivity implements SidebarFragment.OnSidebarItemSelectedListener {

    private static final String TAG = "NotesListActivity";
    private ActivityHomeBinding binding;
    private NotesListViewModel viewModel;
    private FolderAdapter folderAdapter;
    
    private static final int REQUEST_CODE_VERIFY_PASSWORD_FOR_LOCK = 106;

    // 同步广播接收器
    private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.micode.notes.ACTION_SYNC".equals(intent.getAction())) {
                Log.d(TAG, "Received sync broadcast, triggering sync");
                SyncManager.getInstance().syncNotes(new SyncManager.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Auto-sync completed successfully");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Auto-sync failed: " + error);
                    }
                });
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initViews();
        initViewModel();
        observeViewModel();
        
        // 初始化SyncManager
        SyncManager.getInstance().initialize(this);
    }

    private void initViews() {
        // Sidebar Button
        binding.btnSidebar.setOnClickListener(v -> {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // Layout Toggle Button
        binding.btnChangeLayout.setOnClickListener(v -> {
            NotesListFragment fragment = getNotesListFragment();
            if (fragment != null) {
                boolean isStaggered = fragment.toggleLayout();
                binding.btnChangeLayout.setImageResource(isStaggered ? R.drawable.ic_view_list : R.drawable.ic_view_grid);
            }
        });

        // Search Bar
        binding.tvSearchBar.setOnClickListener(v -> {
             startActivity(new Intent(this, NoteSearchActivity.class));
        });

        // Setup ViewPager
        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setUserInputEnabled(false); // Disable swipe to avoid conflict

        // Setup Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_notes) {
                binding.viewPager.setCurrentItem(0, false);
                binding.tvTitle.setText(R.string.app_name);
                binding.btnChangeLayout.setVisibility(View.VISIBLE);
                binding.tvSearchBar.setVisibility(View.VISIBLE);
                binding.rvFolderTabs.setVisibility(View.VISIBLE);
                updateLayoutButtonIcon();
                return true;
            } else if (itemId == R.id.nav_tasks) {
                binding.viewPager.setCurrentItem(1, false);
                binding.tvTitle.setText("待办");
                binding.btnChangeLayout.setVisibility(View.GONE);
                binding.tvSearchBar.setVisibility(View.GONE);
                binding.rvFolderTabs.setVisibility(View.GONE);
                return true;
            }
            return false;
        });

        // Selection Actions
        binding.btnCloseSelection.setOnClickListener(v -> viewModel.setIsSelectionMode(false));
        binding.btnSelectAll.setOnClickListener(v -> viewModel.selectAllNotes());

        binding.btnActionPin.setOnClickListener(v -> {
            viewModel.toggleSelectedNotesPin();
            viewModel.setIsSelectionMode(false);
        });

        binding.btnActionExport.setOnClickListener(v -> {
            exportSelectedNotes();
            viewModel.setIsSelectionMode(false);
        });

        binding.btnActionLock.setOnClickListener(v -> {
            SecurityManager securityManager = SecurityManager.getInstance(this);
            if (!securityManager.isPasswordSet()) {
                new AlertDialog.Builder(this)
                    .setTitle("设置密码")
                    .setMessage("使用加锁功能前请先设置密码")
                    .setPositiveButton("去设置", (d, w) -> showSetPasswordDialog())
                    .setNegativeButton("取消", null)
                    .show();
            } else {
                Intent intent = new Intent(this, PasswordActivity.class);
                intent.setAction(PasswordActivity.ACTION_CHECK_PASSWORD);
                startActivityForResult(intent, REQUEST_CODE_VERIFY_PASSWORD_FOR_LOCK);
            }
        });
        
        binding.btnActionDelete.setOnClickListener(v -> {
             new AlertDialog.Builder(this)
                 .setTitle("删除")
                 .setMessage("确定要删除选中的笔记吗？")
                 .setPositiveButton("删除", (d,w) -> {
                     viewModel.deleteSelectedNotes();
                     viewModel.setIsSelectionMode(false);
                 })
                 .setNegativeButton("取消", null)
                 .show();
        });
        
        binding.btnSelectionRestore.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("恢复笔记")
                .setMessage("确定要恢复选中的笔记吗？")
                .setPositiveButton("确定", (d, w) -> {
                    viewModel.restoreSelectedNotes();
                    viewModel.setIsSelectionMode(false);
                })
                .setNegativeButton("再想想", null)
                .show();
        });

        binding.btnSelectionDeleteForever.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("永久删除")
                .setMessage("确定要永久删除选中的笔记吗？此操作不可撤销。")
                .setPositiveButton("确定", (d, w) -> {
                    viewModel.deleteSelectedNotesForever();
                    viewModel.setIsSelectionMode(false);
                })
                .setNegativeButton("再想想", null)
                .show();
            dialog.setOnShowListener(d -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            });
        });
    }

    private void initViewModel() {
        NotesRepository repository = new NotesRepository(getContentResolver());
        // Share ViewModel with fragments using the activity scope
        viewModel = new ViewModelProvider(this,
            new ViewModelProvider.Factory() {
                @Override
                public <T extends androidx.lifecycle.ViewModel> T create(Class<T> modelClass) {
                    return (T) new NotesListViewModel(getApplication(), repository);
                }
            }).get(NotesListViewModel.class);

        // Setup Folder Tabs
        folderAdapter = new FolderAdapter(this);
        binding.rvFolderTabs.setAdapter(folderAdapter);
        folderAdapter.setOnFolderClickListener(folderId -> viewModel.enterFolder(folderId));
        
        // Initial load
        viewModel.loadNotes(Notes.ID_ALL_NOTES_FOLDER);
    }

    private void observeViewModel() {
        viewModel.getCurrentFolderIdLiveData().observe(this, folderId -> {
            updateTrashModeUI(folderId == Notes.ID_TRASH_FOLER);
            folderAdapter.setSelectedFolderId(folderId);
        });

        viewModel.getFoldersLiveData().observe(this, folders -> {
            folderAdapter.setFolders(folders);
        });
        
        viewModel.getIsSelectionMode().observe(this, isSelection -> {
            if (isSelection) {
                binding.headerContainer.setVisibility(View.GONE);
                binding.selectionHeader.setVisibility(View.VISIBLE);
                if (viewModel.isTrashMode()) {
                    binding.selectionBottomBar.setVisibility(View.GONE);
                    binding.btnSelectionRestore.setVisibility(View.VISIBLE);
                    binding.btnSelectionDeleteForever.setVisibility(View.VISIBLE);
                } else {
                    binding.selectionBottomBar.setVisibility(View.VISIBLE);
                    binding.btnSelectionRestore.setVisibility(View.GONE);
                    binding.btnSelectionDeleteForever.setVisibility(View.GONE);
                }
            } else {
                binding.headerContainer.setVisibility(View.VISIBLE);
                binding.selectionHeader.setVisibility(View.GONE);
                binding.selectionBottomBar.setVisibility(View.GONE);
                if (!viewModel.isTrashMode()) {
                    binding.bottomNavigation.setVisibility(View.VISIBLE);
                }
            }
        });
        
        viewModel.getSelectedIdsLiveData().observe(this, selectedIds -> {
            updateSelectionCount();
            updateSelectionActionUI();
        });

        viewModel.getSidebarRefreshNeeded().observe(this, needed -> {
            if (needed) {
                SidebarFragment fragment = (SidebarFragment) getSupportFragmentManager().findFragmentById(R.id.sidebar_fragment);
                if (fragment != null) {
                    fragment.refreshFolderTree();
                }
            }
        });
    }

    private void updateTrashModeUI(boolean isTrash) {
        if (isTrash) {
            binding.tvTitle.setText("回收站");
            binding.tvSearchBar.setVisibility(View.GONE);
            binding.rvFolderTabs.setVisibility(View.GONE);
            binding.bottomNavigation.setVisibility(View.GONE);
        } else {
            if (binding.bottomNavigation.getSelectedItemId() == R.id.nav_notes) {
                binding.tvTitle.setText(R.string.app_name);
                binding.tvSearchBar.setVisibility(View.VISIBLE);
                binding.rvFolderTabs.setVisibility(View.VISIBLE);
            } else {
                binding.tvTitle.setText("待办");
                binding.tvSearchBar.setVisibility(View.GONE);
                binding.rvFolderTabs.setVisibility(View.GONE);
            }
            binding.bottomNavigation.setVisibility(View.VISIBLE);
        }
    }

    private void updateSelectionCount() {
        int count = viewModel.getSelectedCount();
        binding.tvSelectionCount.setText("已选择 " + count + " 项");
    }

    private void updateSelectionActionUI() {
        boolean isAllPinned = viewModel.isAllSelectedPinned();
        binding.btnActionPin.setText(isAllPinned ? "取消置顶" : "置顶");
        
        boolean isAllLocked = viewModel.isAllSelectedLocked();
        binding.btnActionLock.setText(isAllLocked ? "解锁" : "加锁");
    }

    private void showSetPasswordDialog() {
        new AlertDialog.Builder(this)
            .setTitle("设置密码")
            .setItems(new String[]{"数字锁", "手势锁"}, (dialog, which) -> {
                int type = (which == 0) ? SecurityManager.TYPE_PIN : SecurityManager.TYPE_PATTERN;
                Intent intent = new Intent(this, PasswordActivity.class);
                intent.setAction(PasswordActivity.ACTION_SETUP_PASSWORD);
                intent.putExtra(PasswordActivity.EXTRA_PASSWORD_TYPE, type);
                startActivity(intent);
            })
            .show();
    }

    private NotesListFragment getNotesListFragment() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof NotesListFragment) {
                return (NotesListFragment) fragment;
            }
        }
        return null;
    }

    private void updateLayoutButtonIcon() {
        NotesListFragment fragment = getNotesListFragment();
        if (fragment != null) {
            boolean isStaggered = fragment.isStaggeredLayout();
            binding.btnChangeLayout.setImageResource(isStaggered ? R.drawable.ic_view_list : R.drawable.ic_view_grid);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.micode.notes.ACTION_SYNC");
        registerReceiver(syncReceiver, filter);
        
        // Auto-sync if needed
        UserAuthManager authManager = UserAuthManager.getInstance(this);
        if (authManager.isLoggedIn()) {
            long lastSync = SyncManager.getInstance().getLastSyncTime();
            if (System.currentTimeMillis() - lastSync > 30 * 60 * 1000) {
                SyncManager.getInstance().syncNotes(null);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(syncReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VERIFY_PASSWORD_FOR_LOCK && resultCode == RESULT_OK) {
            viewModel.toggleSelectedNotesLock();
            viewModel.setIsSelectionMode(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else if (Boolean.TRUE.equals(viewModel.getIsSelectionMode().getValue())) {
            viewModel.setIsSelectionMode(false);
        } else if (viewModel.navigateUp()) {
            // Handled
        } else {
            super.onBackPressed();
        }
    }

    // Sidebar Callbacks
    @Override public void onFolderSelected(long folderId) { binding.drawerLayout.closeDrawer(GravityCompat.START); viewModel.enterFolder(folderId); }
    @Override public void onTrashSelected() { binding.drawerLayout.closeDrawer(GravityCompat.START); viewModel.enterFolder(Notes.ID_TRASH_FOLER); }
    @Override public void onSyncSelected() { binding.drawerLayout.closeDrawer(GravityCompat.START); SyncManager.getInstance().syncNotes(null); }
    @Override public void onLoginSelected() { binding.drawerLayout.closeDrawer(GravityCompat.START); startActivity(new Intent(this, LoginActivity.class)); }
    @Override public void onLogoutSelected() { binding.drawerLayout.closeDrawer(GravityCompat.START); viewModel.refreshNotes(); }
    @Override public void onExportSelected() { 
        binding.drawerLayout.closeDrawer(GravityCompat.START); 
        viewModel.setIsSelectionMode(true);
        Toast.makeText(this, "请选择要导出的便签", Toast.LENGTH_SHORT).show();
    }
    @Override public void onTemplateSelected() { binding.drawerLayout.closeDrawer(GravityCompat.START); viewModel.enterFolder(Notes.ID_TEMPLATE_FOLDER); }
    @Override public void onSettingsSelected() { binding.drawerLayout.closeDrawer(GravityCompat.START); startActivity(new Intent(this, SettingsActivity.class)); }
    @Override public void onCapsuleSelected() { 
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        startActivity(new Intent(this, CapsuleListActivity.class));
    }
    @Override public void onCreateFolder() { 
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        SidebarFragment fragment = (SidebarFragment) getSupportFragmentManager().findFragmentById(R.id.sidebar_fragment);
        if (fragment != null) {
            fragment.showCreateFolderDialog();
        }
    }
    @Override public void onCloseSidebar() { binding.drawerLayout.closeDrawer(GravityCompat.START); }
    @Override public void onRenameFolder(long folderId) { 
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        // Show rename dialog
        viewModel.getFolderInfo(folderId, new NotesRepository.Callback<NotesRepository.NoteInfo>() {
            @Override
            public void onSuccess(NotesRepository.NoteInfo folderInfo) {
                runOnUiThread(() -> {
                    if (folderInfo != null) {
                        showRenameFolderDialog(folderId, folderInfo.snippet);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(NotesListActivity.this, "获取文件夹信息失败", Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    private void exportSelectedNotes() {
        java.util.List<Long> selectedIds = viewModel.getSelectedNoteIds();
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "请先选择要导出的便签", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("批量导出便签");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final RadioGroup group = new RadioGroup(this);
        RadioButton rbText = new RadioButton(this);
        rbText.setText("导出为文本 (.txt)");
        rbText.setId(View.generateViewId());
        group.addView(rbText);

        RadioButton rbImage = new RadioButton(this);
        rbImage.setText("导出为图片 (.png)");
        rbImage.setId(View.generateViewId());
        group.addView(rbImage);

        RadioButton rbPdf = new RadioButton(this);
        rbPdf.setText("导出为 PDF (.pdf)");
        rbPdf.setId(View.generateViewId());
        group.addView(rbPdf);

        group.check(rbText.getId());
        layout.addView(group);

        final CheckBox cbShare = new CheckBox(this);
        cbShare.setText("导出后立即分享");
        layout.addView(cbShare);

        builder.setView(layout);

        builder.setPositiveButton("开始导出", (dialog, which) -> {
            int checkedId = group.getCheckedRadioButtonId();
            boolean share = cbShare.isChecked();

            if (checkedId == rbText.getId()) {
                 performBatchExport(0, selectedIds, share);
             } else if (checkedId == rbImage.getId()) {
                 performBatchExport(1, selectedIds, share);
             } else if (checkedId == rbPdf.getId()) {
                 performBatchExport(2, selectedIds, share);
             }
         });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void performBatchExport(int format, java.util.List<Long> selectedIds, boolean share) {
        if (format == 0) { // Text (Combined)
            BackupUtils backupUtils = BackupUtils.getInstance(this);
            int state = backupUtils.exportNotesToText(selectedIds);
            if (state == BackupUtils.STATE_SUCCESS) {
                File file = new File(backupUtils.getExportedTextFileDir(), backupUtils.getExportedTextFileName());
                Toast.makeText(this, "已导出至下载目录: " + file.getName(), Toast.LENGTH_SHORT).show();
                if (share) shareFile(file, "text/plain");
            } else {
                Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            final ArrayList<Uri> uris = new ArrayList<>();
            final int total = selectedIds.size();
            final int[] successCount = {0};
            final String mimeType = (format == 1) ? "image/png" : "application/pdf";

            for (Long id : selectedIds) {
                viewModel.getNoteContent(id, new NotesRepository.Callback<String>() {
                    @Override
                    public void onSuccess(String content) {
                        if (!TextUtils.isEmpty(content)) {
                            String title = getTitleFromContent(content);
                            File exportedFile = null;
                            if (format == 1) { // Image
                                Bitmap bitmap = renderTextToBitmap(content);
                                Uri uri = ImageExportHelper.saveBitmapToExternal(NotesListActivity.this, bitmap, title);
                                if (uri != null) {
                                    successCount[0]++;
                                    uris.add(uri);
                                }
                            } else { // PDF
                                exportedFile = PdfExportHelper.exportToPdf(NotesListActivity.this, title, content);
                                if (exportedFile != null) {
                                    successCount[0]++;
                                    uris.add(androidx.core.content.FileProvider.getUriForFile(NotesListActivity.this, getPackageName() + ".fileprovider", exportedFile));
                                }
                            }
                        }
                        checkBatchFinished(successCount[0], total, uris, mimeType, share);
                    }

                    @Override
                    public void onError(Exception error) {
                        checkBatchFinished(successCount[0], total, uris, mimeType, share);
                    }
                });
            }
        }
    }

    private Bitmap renderTextToBitmap(String text) {
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(text);
        textView.setTextColor(Color.BLACK);
        textView.setBackgroundColor(Color.WHITE);
        textView.setTextSize(16);
        textView.setPadding(40, 40, 40, 40);
        textView.setWidth(800);
        
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        textView.measure(widthMeasureSpec, heightMeasureSpec);
        textView.layout(0, 0, textView.getMeasuredWidth(), textView.getMeasuredHeight());
        
        Bitmap bitmap = Bitmap.createBitmap(textView.getMeasuredWidth(), textView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        textView.draw(canvas);
        return bitmap;
    }

    private synchronized void checkBatchFinished(int success, int total, ArrayList<Uri> uris, String mimeType, boolean share) {
        // Since we are doing this sequentially or with callbacks, we need to track progress
        // For simplicity in this implementation, I'll just check if we have reached total count
        if (uris.size() + (total - success) >= total) {
            if (success > 0) {
                Toast.makeText(this, "成功导出 " + success + " 个文件至下载目录", Toast.LENGTH_SHORT).show();
                if (share) shareUris(uris, mimeType);
            } else {
                Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getTitleFromContent(String content) {
        if (TextUtils.isEmpty(content)) return "untitled";
        String title = content.trim();
        int firstNewLine = title.indexOf('\n');
        if (firstNewLine > 0) {
            title = title.substring(0, firstNewLine);
        }
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }
        return title;
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享便签"));
    }

    private void shareUris(ArrayList<Uri> uris, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType(mimeType);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享便签"));
    }

    private void showRenameFolderDialog(long folderId, String currentName) {
        final EditText input = new EditText(this);
        input.setText(currentName);
        input.setSelection(currentName.length());
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_rename_folder_title)
            .setView(input)
            .setPositiveButton(R.string.menu_rename, (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    viewModel.renameFolder(folderId, newName);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
    
    @Override public void onDeleteFolder(long folderId) { 
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        // Show delete confirmation
        viewModel.getFolderInfo(folderId, new NotesRepository.Callback<NotesRepository.NoteInfo>() {
            @Override
            public void onSuccess(NotesRepository.NoteInfo folderInfo) {
                runOnUiThread(() -> {
                    if (folderInfo != null) {
                        new AlertDialog.Builder(NotesListActivity.this)
                            .setTitle(R.string.dialog_delete_folder_title)
                            .setMessage(String.format(getString(R.string.dialog_delete_folder_with_notes), folderInfo.snippet, folderInfo.notesCount))
                            .setPositiveButton(R.string.menu_delete, (dialog, which) -> viewModel.deleteFolder(folderId))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(NotesListActivity.this, "获取文件夹信息失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private class MainPagerAdapter extends FragmentStateAdapter {
        public MainPagerAdapter(@NonNull AppCompatActivity activity) { super(activity); }
        @NonNull @Override public Fragment createFragment(int position) {
            return (position == 0) ? new NotesListFragment() : new TaskListFragment();
        }
        @Override public int getItemCount() { return 2; }
    }
}
