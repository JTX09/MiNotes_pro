package net.micode.notes;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import net.micode.notes.data.Notes;
import net.micode.notes.databinding.ActivityMainBinding;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.ui.BaseActivity;
import net.micode.notes.ui.SidebarFragment;

/**
 * 主活动类
 * <p>
 * 应用的主入口，负责启动笔记列表界面
 * 支持边到边显示模式，自动适配系统栏的边距。
 * </p>
 */
public class MainActivity extends BaseActivity implements SidebarFragment.OnSidebarItemSelectedListener {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;

    /**
     * 创建活动
     * <p>
     * 初始化活动界面，启用边到边显示模式，并设置窗口边距监听器。
     * </p>
     *
     * @param savedInstanceState 保存的实例状态，用于恢复活动状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 启用边到边显示模式
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化DrawerLayout
        if (binding.drawerLayout != null) {
            // 设置侧栏在左侧
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);

            // 设置监听器：侧栏关闭时更新状态
            binding.drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    // 侧栏滑动时
                }

                @Override
                public void onDrawerOpened(View drawerView) {
                    // 侧栏打开时
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    // 侧栏关闭时
                }

                @Override
                public void onDrawerStateChanged(int newState) {
                    // 侧栏状态改变时
                }
            });
        }

        // 设置窗口边距监听器，自动适配系统栏
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent, (v, insets) -> {
            // 获取系统栏边距
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 设置视图内边距以适配系统栏
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 启动NotesListActivity作为主界面
        Intent intent = new Intent(this, net.micode.notes.ui.NotesListActivity.class);
        startActivity(intent);
    }

    // ==================== SidebarFragment.OnSidebarItemSelectedListener 实现 ====================

    @Override
    public void onFolderSelected(long folderId) {
        Log.d(TAG, "Folder selected: " + folderId);
        // 打开侧栏中的文件夹：不关闭侧栏，直接切换视图
        // 这个回调通常用于侧栏中的文件夹项双击
        // 实际跳转逻辑应该在NotesListActivity中处理
        closeSidebar();
    }

    @Override
    public void onTrashSelected() {
        Log.d(TAG, "Trash selected");
        // TODO: 实现跳转到回收站
        // 关闭侧栏
        closeSidebar();
    }

    @Override
    public void onSyncSelected() {
        Log.d(TAG, "Sync selected");
        // TODO: 实现同步功能
    }

    @Override
    public void onLoginSelected() {
        Log.d(TAG, "Login selected");
        Intent intent = new Intent(this, net.micode.notes.ui.LoginActivity.class);
        startActivity(intent);
    }

    @Override
    public void onLogoutSelected() {
        Log.d(TAG, "Logout selected");
        closeSidebar();
    }

    @Override
    public void onExportSelected() {
        Log.d(TAG, "Export selected");
        BackupUtils backupUtils = BackupUtils.getInstance(this);
        int state = backupUtils.exportToText();
        switch (state) {
            case BackupUtils.STATE_SUCCESS:
                Toast.makeText(this, getString(R.string.format_exported_file_location,
                        backupUtils.getExportedTextFileName(),
                        backupUtils.getExportedTextFileDir()), Toast.LENGTH_SHORT).show();
                break;
            case BackupUtils.STATE_SD_CARD_UNMOUONTED:
                Toast.makeText(this, R.string.error_sdcard_unmounted, Toast.LENGTH_SHORT).show();
                break;
            case BackupUtils.STATE_SYSTEM_ERROR:
            default:
                Toast.makeText(this, R.string.error_sdcard_export, Toast.LENGTH_SHORT).show();
                break;
        }
        closeSidebar();
    }

    @Override
    public void onTemplateSelected() {
        Log.d(TAG, "Template selected");
        // 跳转到模板列表（在 NotesListActivity 中处理）
        closeSidebar();
    }

    @Override
    public void onSettingsSelected() {
        Log.d(TAG, "Settings selected");
        // 打开设置界面
        Intent intent = new Intent(this, net.micode.notes.ui.NotesPreferenceActivity.class);
        startActivity(intent);
        // 关闭侧栏
        closeSidebar();
    }

    @Override
    public void onCapsuleSelected() {
        Log.d(TAG, "Capsule selected");
        Intent intent = new Intent(this, net.micode.notes.ui.CapsuleListActivity.class);
        startActivity(intent);
        closeSidebar();
    }

    @Override
    public void onCreateFolder() {
        Log.d(TAG, "Create folder");
        // 创建文件夹功能由SidebarFragment内部处理
        // 这里不需要做任何事情
    }

    @Override
    public void onCloseSidebar() {
        closeSidebar();
    }

    @Override
    public void onRenameFolder(long folderId) {
        Log.d(TAG, "Rename folder: " + folderId);
        // TODO: 文件夹操作主要由NotesListActivity处理
        // 这里可以添加跳转逻辑或通知NotesListActivity
        closeSidebar();
    }

    @Override
    public void onDeleteFolder(long folderId) {
        Log.d(TAG, "Delete folder: " + folderId);
        // TODO: 文件夹操作主要由NotesListActivity处理
        // 这里可以添加跳转逻辑或通知NotesListActivity
        closeSidebar();
    }

    // ==================== 私有方法 ====================

    /**
     * 关闭侧栏
     */
    private void closeSidebar() {
        if (binding.drawerLayout != null) {
            binding.drawerLayout.closeDrawer(Gravity.LEFT);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}