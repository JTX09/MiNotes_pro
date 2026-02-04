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

package net.micode.notes.viewmodel;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesDatabaseHelper;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesRepository;
import net.micode.notes.ui.SidebarFragment.FolderTreeItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件夹列表ViewModel
 * <p>
 * 管理文件夹树的数据和业务逻辑
 * 提供文件夹树的查询和构建功能
 * </p>
 */
public class FolderListViewModel extends AndroidViewModel {
    private static final String TAG = "FolderListViewModel";

    private MutableLiveData<List<FolderTreeItem>> folderTreeLiveData;
    private NotesDatabaseHelper dbHelper;
    private NotesRepository repository;
    private long currentFolderId = Notes.ID_ROOT_FOLDER; // 当前文件夹ID
    private Set<Long> expandedFolderIds = new HashSet<>(); // 已展开的文件夹ID集合

    public FolderListViewModel(@NonNull Application application) {
        super(application);
        dbHelper = NotesDatabaseHelper.getInstance(application);
        repository = new NotesRepository(application.getContentResolver());
        folderTreeLiveData = new MutableLiveData<>();
    }

    /**
     * 获取当前文件夹ID
     */
    public long getCurrentFolderId() {
        return currentFolderId;
    }

    /**
     * 设置当前文件夹ID
     */
    public void setCurrentFolderId(long folderId) {
        this.currentFolderId = folderId;
    }

    /**
     * 切换文件夹展开/收起状态
     * @param folderId 文件夹ID
     */
    public void toggleFolderExpand(long folderId) {
        android.util.Log.d(TAG, "toggleFolderExpand: folderId=" + folderId);
        android.util.Log.d(TAG, "Before toggle, expandedFolders: " + expandedFolderIds);

        if (expandedFolderIds.contains(folderId)) {
            expandedFolderIds.remove(folderId);
            android.util.Log.d(TAG, "Collapsed folder: " + folderId);
        } else {
            expandedFolderIds.add(folderId);
            android.util.Log.d(TAG, "Expanded folder: " + folderId);
        }

        android.util.Log.d(TAG, "After toggle, expandedFolders: " + expandedFolderIds);

        // 重新加载文件夹树
        loadFolderTree();
    }

    /**
     * 检查文件夹是否已展开
     * @param folderId 文件夹ID
     * @return 是否已展开
     */
    public boolean isFolderExpanded(long folderId) {
        return expandedFolderIds.contains(folderId);
    }

    /**
     * 获取文件夹树LiveData
     */
    public LiveData<List<FolderTreeItem>> getFolderTree() {
        return folderTreeLiveData;
    }

    /**
     * 加载文件夹树数据
     */
    public void loadFolderTree() {
        new Thread(() -> {
            List<FolderTreeItem> folderTree = buildFolderTree();
            folderTreeLiveData.postValue(folderTree);
        }).start();
    }

    /**
     * 构建文件夹树
     * <p>
     * 从数据库中查询所有文件夹，并构建层级结构
     * </p>
     * @return 文件夹树列表
     */
    private List<FolderTreeItem> buildFolderTree() {
        // 查询所有文件夹（不包括系统文件夹）
        List<Map<String, Object>> folders = queryAllFolders();

        android.util.Log.d(TAG, "QueryAllFolders returned " + folders.size() + " folders");

        // 构建文件夹映射表（方便查找父文件夹）
        Map<Long, FolderNode> folderMap = new HashMap<>();
        List<FolderNode> rootFolders = new ArrayList<>();

        // 创建文件夹节点
        for (Map<String, Object> folder : folders) {
            long id = (Long) folder.get(NoteColumns.ID);
            String name = (String) folder.get(NoteColumns.SNIPPET);
            long parentId = (Long) folder.get(NoteColumns.PARENT_ID);
            int noteCount = ((Number) folder.get(NoteColumns.NOTES_COUNT)).intValue();

            android.util.Log.d(TAG, "Folder: id=" + id + ", name=" + name + ", parentId=" + parentId);

            FolderNode node = new FolderNode(id, name, parentId, noteCount);
            folderMap.put(id, node);

            // 如果是顶级文件夹（父文件夹为根），添加到根列表
            if (parentId == Notes.ID_ROOT_FOLDER) {
                rootFolders.add(node);
                android.util.Log.d(TAG, "Added root folder: " + name);
            }
        }

        android.util.Log.d(TAG, "Root folders count: " + rootFolders.size());

        // 构建父子关系
        for (FolderNode node : folderMap.values()) {
            if (node.parentId != Notes.ID_ROOT_FOLDER) {
                FolderNode parent = folderMap.get(node.parentId);
                if (parent != null) {
                    parent.children.add(node);
                }
            }
        }

        // 转换为扁平列表（用于RecyclerView显示）
        List<FolderTreeItem> folderTree = new ArrayList<>();
        // 检查根文件夹是否展开
        boolean rootExpanded = expandedFolderIds.contains(Notes.ID_ROOT_FOLDER);
        android.util.Log.d(TAG, "Root expanded: " + rootExpanded);
        buildFolderTreeList(rootFolders, folderTree, 0, rootExpanded);

        android.util.Log.d(TAG, "Final folder tree size: " + folderTree.size());

        return folderTree;
    }

    /**
     * 递归构建文件夹树列表
     * 只显示已展开文件夹的子文件夹
     * 顶层文件夹始终显示，无论根文件夹是否展开
     * @param nodes 文件夹节点列表
     * @param folderTree 文件夹树列表（输出）
     * @param level 当前层级
     * @param forceExpandChildren 是否强制展开子文件夹（用于顶层）
     */
    private void buildFolderTreeList(List<FolderNode> nodes, List<FolderTreeItem> folderTree, int level, boolean forceExpandChildren) {
        for (FolderNode node : nodes) {
            // 顶级文件夹始终显示（level=0）
            // 移除了之前的条件判断，让所有顶级文件夹都能显示
            folderTree.add(new FolderTreeItem(
                    node.id,
                    node.name,
                    level,
                    !node.children.isEmpty(),
                    node.noteCount
            ));

            // 只有当父文件夹在 expandedFolderIds 中时，才递归处理子文件夹
            // 有子节点（!node.children.isEmpty()）才检查展开状态
            if (!node.children.isEmpty() && expandedFolderIds.contains(node.id)) {
                buildFolderTreeList(node.children, folderTree, level + 1, false);
            }
        }
    }

    /**
     * 查询所有文件夹
     * @return 文件夹列表
     */
    private List<Map<String, Object>> queryAllFolders() {
        List<Map<String, Object>> folders = new ArrayList<>();

        // 查询所有文件夹类型的笔记
        String selection = NoteColumns.TYPE + " = ?";
        String[] selectionArgs = new String[]{
                String.valueOf(Notes.TYPE_FOLDER)
        };

        Cursor cursor = null;
        try {
            cursor = dbHelper.getReadableDatabase().query(
                    TABLE.NOTE,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    NoteColumns.MODIFIED_DATE + " DESC"
            );

            android.util.Log.d(TAG, "Query executed, cursor: " + (cursor != null ? cursor.getCount() : "null"));

            if (cursor != null) {
                android.util.Log.d(TAG, "Column names: " + java.util.Arrays.toString(cursor.getColumnNames()));

                while (cursor.moveToNext()) {
                    Map<String, Object> folder = new HashMap<>();
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID));

                    // 优先使用TITLE，fallback到SNIPPET
                    String name = "";
                    int titleIndex = cursor.getColumnIndex(NoteColumns.TITLE);
                    if (titleIndex != -1) {
                        name = cursor.getString(titleIndex);
                    }
                    if (name == null || name.trim().isEmpty()) {
                        name = cursor.getString(cursor.getColumnIndexOrThrow(NoteColumns.SNIPPET));
                    }

                    // 尝试获取parent_id，可能列名不对
                    int parentIdIndex = cursor.getColumnIndex(NoteColumns.PARENT_ID);
                    long parentId = -1;
                    if (parentIdIndex != -1) {
                        parentId = cursor.getLong(parentIdIndex);
                    }

                    // 尝试获取notes_count
                    int notesCountIndex = cursor.getColumnIndex(NoteColumns.NOTES_COUNT);
                    int noteCount = 0;
                    if (notesCountIndex != -1) {
                        noteCount = cursor.getInt(notesCountIndex);
                    }

                    android.util.Log.d(TAG, "Folder data: id=" + id + ", name=" + name + ", parentId=" + parentId + ", noteCount=" + noteCount);

                    folder.put(NoteColumns.ID, id);
                    folder.put(NoteColumns.TITLE, name);
                    folder.put(NoteColumns.SNIPPET, name);
                    folder.put(NoteColumns.PARENT_ID, parentId);
                    folder.put(NoteColumns.NOTES_COUNT, noteCount);

                    folders.add(folder);
                }
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error querying folders", e);
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return folders;
    }

    /**
     * FolderNode
     * 文件夹节点，用于构建文件夹树
     */
    private static class FolderNode {
        public long id;
        public String name;
        public long parentId;
        public int noteCount;
        public List<FolderNode> children = new ArrayList<>();

        public FolderNode(long id, String name, long parentId, int noteCount) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
            this.noteCount = noteCount;
        }
    }
}
