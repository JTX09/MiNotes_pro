/*
 * Copyright (c) 2025, Modern Notes Project
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.NotesRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 笔记列表ViewModel
 * <p>
 * 负责笔记列表的业务逻辑，与UI层（Activity）解耦
 * 管理笔记列表的加载、创建、删除、搜索、移动等操作
 * </p>
 *
 * @see NotesRepository
 * @see net.micode.notes.model.Note
 */
public class NotesListViewModel extends AndroidViewModel {
    private static final String TAG = "NotesListViewModel";

    private final NotesRepository repository;

    // 笔记列表LiveData
    private final MutableLiveData<List<NotesRepository.NoteInfo>> notesLiveData = new MutableLiveData<>();

    // 文件夹列表LiveData (用于顶部Tab)
    private final MutableLiveData<List<NotesRepository.NoteInfo>> foldersLiveData = new MutableLiveData<>();

    // 加载状态LiveData
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // 错误消息LiveData
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // 选中的笔记ID集合
    private final HashSet<Long> selectedNoteIds = new HashSet<>();
    private final MutableLiveData<HashSet<Long>> selectedIdsLiveData = new MutableLiveData<>(new HashSet<>());

    // 是否处于多选模式
    private final MutableLiveData<Boolean> isSelectionMode = new MutableLiveData<>(false);

    // 当前文件夹ID
    private long currentFolderId = Notes.ID_ALL_NOTES_FOLDER; // Default to "All"
    
    // 当前文件夹ID LiveData (用于UI监听)
    private final MutableLiveData<Long> currentFolderIdLiveData = new MutableLiveData<>((long) Notes.ID_ALL_NOTES_FOLDER);

    // 文件夹路径LiveData（用于面包屑导航）
    private final MutableLiveData<List<NotesRepository.NoteInfo>> folderPathLiveData = new MutableLiveData<>();

    // 侧栏刷新通知LiveData（删除等操作后通知侧栏刷新）
    private final MutableLiveData<Boolean> sidebarRefreshNeeded = new MutableLiveData<>(false);

    // 文件夹导航历史（用于返回上一级）
    private final List<Long> folderHistory = new ArrayList<>();

    /**
     * 构造函数
     *
     * @param application 应用程序上下文
     * @param repository 笔记数据仓库
     */
    public NotesListViewModel(@NonNull Application application, NotesRepository repository) {
        super(application);
        this.repository = repository;
        Log.d(TAG, "ViewModel created");
    }

    /**
     * 获取笔记列表LiveData
     *
     * @return 笔记列表LiveData
     */
    public MutableLiveData<List<NotesRepository.NoteInfo>> getNotesLiveData() {
        return notesLiveData;
    }

    /**
     * 获取加载状态LiveData
     *
     * @return 加载状态LiveData
     */
    public MutableLiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * 获取错误消息LiveData
     *
     * @return 错误消息LiveData
     */
    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * 获取文件夹列表LiveData (顶部Tab)
     */
    public MutableLiveData<List<NotesRepository.NoteInfo>> getFoldersLiveData() {
        return foldersLiveData;
    }

    /**
     * 获取是否处于多选模式
     */
    public MutableLiveData<Boolean> getIsSelectionMode() {
        return isSelectionMode;
    }

    /**
     * 设置多选模式
     */
    public void setIsSelectionMode(boolean isSelection) {
        isSelectionMode.postValue(isSelection);
        if (!isSelection) {
            clearSelection();
        }
    }

    /**
     * 加载笔记列表
     * <p>
     * 从指定文件夹加载笔记列表，同时加载文件夹路径用于面包屑导航
     * </p>
     *
     * @param folderId 文件夹ID，{@link Notes#ID_ROOT_FOLDER} 表示根文件夹
     */
    public void loadNotes(long folderId) {
        this.currentFolderId = folderId;
        this.currentFolderIdLiveData.postValue(folderId);
        isLoading.postValue(true);
        errorMessage.postValue(null);
        
        // 退出选择模式
        setIsSelectionMode(false);

        // 加载文件夹路径
        repository.getFolderPath(folderId, new NotesRepository.Callback<List<NotesRepository.NoteInfo>>() {
            @Override
            public void onSuccess(List<NotesRepository.NoteInfo> path) {
                folderPathLiveData.postValue(path);
                
                // Determine if we are in template mode
                boolean isTemplate = (folderId == Notes.ID_TEMPLATE_FOLDER);
                if (!isTemplate && path != null) {
                    for (NotesRepository.NoteInfo info : path) {
                        if (info.getId() == Notes.ID_TEMPLATE_FOLDER) {
                            isTemplate = true;
                            break;
                        }
                    }
                }
                
                final boolean templateMode = isTemplate;
                long tabParentId = templateMode ? Notes.ID_TEMPLATE_FOLDER : Notes.ID_ROOT_FOLDER;

                // 加载子文件夹 (Category Tabs)
                repository.getSubFolders(tabParentId, new NotesRepository.Callback<List<NotesRepository.NoteInfo>>() {
                    @Override
                    public void onSuccess(List<NotesRepository.NoteInfo> folders) {
                        // Construct the display list with "All" and "Uncategorized"
                        List<NotesRepository.NoteInfo> displayFolders = new ArrayList<>();
                        
                        // 1. "All" / "All Templates" Folder (Virtual)
                        NotesRepository.NoteInfo allFolder = new NotesRepository.NoteInfo();
                        allFolder.setId(templateMode ? Notes.ID_TEMPLATE_FOLDER : Notes.ID_ALL_NOTES_FOLDER);
                        allFolder.snippet = getApplication().getString(templateMode ? R.string.folder_all_templates : R.string.folder_all); // Name
                        displayFolders.add(allFolder);
                        
                        // 2. Real Folders (from DB)
                        if (folders != null) {
                            displayFolders.addAll(folders);
                        }
                        
                        // 3. "Uncategorized" Folder (only for normal notes)
                        if (!templateMode) {
                            NotesRepository.NoteInfo uncategorizedFolder = new NotesRepository.NoteInfo();
                            uncategorizedFolder.setId(Notes.ID_ROOT_FOLDER);
                            uncategorizedFolder.snippet = getApplication().getString(R.string.folder_uncategorized); // Custom Name for Root
                            displayFolders.add(uncategorizedFolder);
                        }

                        foldersLiveData.postValue(displayFolders);
                    }
                    
                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Failed to load sub-folders", error);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Failed to load folder path", error);
            }
        });

        // 加载笔记 (No folders)
        repository.getNotes(folderId, new NotesRepository.Callback<List<NotesRepository.NoteInfo>>() {
            @Override
            public void onSuccess(List<NotesRepository.NoteInfo> notes) {
                isLoading.postValue(false);
                notesLiveData.postValue(notes);
                Log.d(TAG, "Successfully loaded " + notes.size() + " notes");
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "加载笔记失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 刷新笔记列表
     * <p>
     * 重新加载当前文件夹的笔记列表
     * </p>
     */
    public void refreshNotes() {
        loadNotes(currentFolderId);
    }

    /**
     * 创建新笔记
     * <p>
     * 在当前文件夹下创建一个空笔记，并刷新列表
     * </p>
     */
    public void createNote() {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        repository.createNote(currentFolderId, new NotesRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long noteId) {
                isLoading.postValue(false);
                Log.d(TAG, "Successfully created note with ID: " + noteId);
                refreshNotes();
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "创建笔记失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 删除单个笔记
     * <p>
     * 将笔记移动到回收站，并刷新列表
     * </p>
     *
     * @param noteId 笔记ID
     */
    public void deleteNote(long noteId) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        repository.deleteNote(noteId, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                selectedNoteIds.remove(noteId);
                refreshNotes();
                Log.d(TAG, "Successfully deleted note: " + noteId);
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "删除笔记失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 批量删除笔记
     * <p>
     * 将选中的所有笔记移动到回收站
     * </p>
     */
    public void deleteSelectedNotes() {
        if (selectedNoteIds.isEmpty()) {
            errorMessage.postValue("请先选择要删除的笔记");
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);

        List<Long> noteIds = new ArrayList<>(selectedNoteIds);
        repository.deleteNotes(noteIds, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                selectedNoteIds.clear();
                refreshNotes();
                Log.d(TAG, "Successfully deleted " + rowsAffected + " notes");
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "批量删除失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 搜索笔记
     * <p>
     * 根据关键字搜索笔记，更新笔记列表
     * </p>
     *
     * @param keyword 搜索关键字
     */
    public void searchNotes(String keyword) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        repository.searchNotes(keyword, new NotesRepository.Callback<List<NotesRepository.NoteInfo>>() {
            @Override
            public void onSuccess(List<NotesRepository.NoteInfo> notes) {
                isLoading.postValue(false);
                notesLiveData.postValue(notes);
                Log.d(TAG, "Search returned " + notes.size() + " results");
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "搜索失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    public MutableLiveData<HashSet<Long>> getSelectedIdsLiveData() {
        return selectedIdsLiveData;
    }

    public void notifySelectionChanged() {
        selectedIdsLiveData.postValue(new HashSet<>(selectedNoteIds));
    }

    /**
     * 切换笔记选中状态
     *
     * @param noteId 笔记ID
     * @param selected 是否选中
     */
    public void toggleNoteSelection(long noteId, boolean selected) {
        if (selected) {
            selectedNoteIds.add(noteId);
        } else {
            selectedNoteIds.remove(noteId);
        }
        notifySelectionChanged();
    }

    /**
     * 全选笔记
     * <p>
     * 选中当前列表中的所有笔记
     * </p>
     */
    public void selectAllNotes() {
        List<NotesRepository.NoteInfo> notes = notesLiveData.getValue();
        if (notes != null) {
            for (NotesRepository.NoteInfo note : notes) {
                selectedNoteIds.add(note.getId());
            }
        }
        notifySelectionChanged();
    }

    /**
     * 取消全选
     * <p>
     * 清空所有选中的笔记
     * </p>
     */
    public void deselectAllNotes() {
        selectedNoteIds.clear();
        notifySelectionChanged();
    }

    /**
     * 检查是否全选
     *
     * @return 如果所有笔记都被选中返回true
     */
    public boolean isAllSelected() {
        List<NotesRepository.NoteInfo> notes = notesLiveData.getValue();
        if (notes == null || notes.isEmpty()) {
            return false;
        }

        return notes.size() == selectedNoteIds.size();
    }

    /**
     * 获取选中的笔记数量
     *
     * @return 选中的笔记数量
     */
    public int getSelectedCount() {
        return selectedNoteIds.size();
    }

    /**
     * 获取选中的笔记ID列表
     *
     * @return 选中的笔记ID列表
     */
    public List<Long> getSelectedNoteIds() {
        return new ArrayList<>(selectedNoteIds);
    }

    /**
     * 获取当前文件夹ID
     *
     * @return 当前文件夹ID
     */
    public long getCurrentFolderId() {
        return currentFolderId;
    }

    /**
     * 获取当前文件夹ID LiveData
     */
    public MutableLiveData<Long> getCurrentFolderIdLiveData() {
        return currentFolderIdLiveData;
    }

    /**
     * 设置当前文件夹
     *
     * @param folderId 文件夹ID
     */
    public void setCurrentFolderId(long folderId) {
        this.currentFolderId = folderId;
    }

    /**
     * 获取文件夹路径LiveData
     *
     * @return 文件夹路径LiveData
     */
    public MutableLiveData<List<NotesRepository.NoteInfo>> getFolderPathLiveData() {
        return folderPathLiveData;
    }

    /**
     * 获取侧栏刷新通知LiveData
     *
     * @return 侧栏刷新通知LiveData
     */
    public MutableLiveData<Boolean> getSidebarRefreshNeeded() {
        return sidebarRefreshNeeded;
    }

    /**
     * 触发侧栏刷新
     */
    public void triggerSidebarRefresh() {
        sidebarRefreshNeeded.postValue(true);
    }

    /**
     * 进入指定文件夹
     *
     * @param folderId 文件夹ID
     */
    public void enterFolder(long folderId) {
        // 将当前文件夹添加到历史记录
        if (currentFolderId != Notes.ID_ROOT_FOLDER && currentFolderId != Notes.ID_CALL_RECORD_FOLDER) {
            folderHistory.add(currentFolderId);
        }
        loadNotes(folderId);
    }

    /**
     * 返回上一级文件夹
     *
     * @return 是否成功返回上一级
     */
    public boolean navigateUp() {
        if (!folderHistory.isEmpty()) {
            long parentFolderId = folderHistory.remove(folderHistory.size() - 1);
            loadNotes(parentFolderId);
            return true;
        }
        return false;
    }

    /**
     * 清除选择状态
     * <p>
     * 退出多选模式时调用
     * </p>
     */
    public void clearSelection() {
        selectedNoteIds.clear();
        notifySelectionChanged();
    }

    /**
     * 获取文件夹列表
     * <p>
     * 加载所有文件夹类型的笔记
     * </p>
     */
    public void loadFolders() {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        repository.getFolders(new NotesRepository.Callback<List<NotesRepository.NoteInfo>>() {
            @Override
            public void onSuccess(List<NotesRepository.NoteInfo> folders) {
                isLoading.postValue(false);
                notesLiveData.postValue(folders);
                Log.d(TAG, "Successfully loaded " + folders.size() + " folders");
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "加载文件夹失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 移动选中的笔记到指定文件夹
     * <p>
     * 批量移动笔记到目标文件夹
     * </p>
     *
     * @param targetFolderId 目标文件夹ID
     */
    public void moveSelectedNotesToFolder(long targetFolderId) {
        if (selectedNoteIds.isEmpty()) {
            errorMessage.postValue("请先选择要移动的笔记");
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);

        List<Long> noteIds = new ArrayList<>(selectedNoteIds);
        repository.moveNotes(noteIds, targetFolderId, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                selectedNoteIds.clear();
                refreshNotes();
                Log.d(TAG, "Successfully moved " + rowsAffected + " notes");
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "移动笔记失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 切换选中笔记的置顶状态
     */
    public void toggleSelectedNotesPin() {
        if (selectedNoteIds.isEmpty()) {
            errorMessage.postValue("请先选择要操作的笔记");
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);

        // 检查当前选中笔记的置顶状态
        List<NotesRepository.NoteInfo> allNotes = notesLiveData.getValue();
        if (allNotes == null) return;

        boolean hasUnpinned = false;
        for (NotesRepository.NoteInfo note : allNotes) {
            if (selectedNoteIds.contains(note.getId())) {
                if (!note.isPinned) {
                    hasUnpinned = true;
                    break;
                }
            }
        }

        // 如果有未置顶的，则全部置顶；否则全部取消置顶
        final boolean newPinState = hasUnpinned;
        List<Long> noteIds = new ArrayList<>(selectedNoteIds);

        repository.batchTogglePin(noteIds, newPinState, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                // 保持选中状态，方便用户查看
                refreshNotes();
                Log.d(TAG, "Successfully toggled pin state to " + newPinState);
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "置顶操作失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 检查选中的笔记是否全部已置顶
     *
     * @return 如果所有选中的笔记都已置顶返回true
     */
    public boolean isAllSelectedPinned() {
        if (selectedNoteIds.isEmpty()) return false;

        List<NotesRepository.NoteInfo> allNotes = notesLiveData.getValue();
        if (allNotes == null) return false;

        for (NotesRepository.NoteInfo note : allNotes) {
            if (selectedNoteIds.contains(note.getId())) {
                if (!note.isPinned) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 切换选中笔记的锁定状态
     */
    public void toggleSelectedNotesLock() {
        if (selectedNoteIds.isEmpty()) {
            errorMessage.postValue("请先选择要操作的笔记");
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);

        // 如果有未锁定的，则全部锁定；否则全部解锁
        final boolean newLockState = !isAllSelectedLocked();
        List<Long> noteIds = new ArrayList<>(selectedNoteIds);

        repository.batchLock(noteIds, newLockState, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                refreshNotes();
                Log.d(TAG, "Successfully toggled lock state to " + newLockState);
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "锁定操作失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 检查选中的笔记是否全部已锁定
     *
     * @return 如果所有选中的笔记都已锁定返回true
     */
    public boolean isAllSelectedLocked() {
        if (selectedNoteIds.isEmpty()) return false;

        List<NotesRepository.NoteInfo> allNotes = notesLiveData.getValue();
        if (allNotes == null) return false;

        for (NotesRepository.NoteInfo note : allNotes) {
            if (selectedNoteIds.contains(note.getId())) {
                if (!note.isLocked) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 恢复选中的笔记
     * <p>
     * 将选中的回收站笔记移回根目录
     * </p>
     */
    public void restoreSelectedNotes() {
        if (selectedNoteIds.isEmpty()) {
            errorMessage.postValue("请先选择要恢复的笔记");
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);

        List<Long> noteIds = new ArrayList<>(selectedNoteIds);
        repository.restoreNotes(noteIds, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                selectedNoteIds.clear();
                refreshNotes();
                Log.d(TAG, "Successfully restored " + rowsAffected + " notes");
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "恢复失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 永久删除选中的笔记
     * <p>
     * 物理删除选中的笔记
     * </p>
     */
    public void deleteSelectedNotesForever() {
        if (selectedNoteIds.isEmpty()) {
            errorMessage.postValue("请先选择要删除的笔记");
            return;
        }

        isLoading.postValue(true);
        errorMessage.postValue(null);

        List<Long> noteIds = new ArrayList<>(selectedNoteIds);
        repository.deleteNotesForever(noteIds, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                selectedNoteIds.clear();
                refreshNotes();
                Log.d(TAG, "Successfully permanently deleted " + rowsAffected + " notes");
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "永久删除失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 判断当前是否处于回收站模式
     *
     * @return 如果当前文件夹是回收站返回true
     */
    public boolean isTrashMode() {
        return currentFolderId == Notes.ID_TRASH_FOLER;
    }

    /**
     * 判断当前是否处于模板模式
     *
     * @return 如果当前文件夹是模板文件夹或其子文件夹返回true
     */
    public boolean isTemplateMode() {
        if (currentFolderId == Notes.ID_TEMPLATE_FOLDER) return true;
        List<NotesRepository.NoteInfo> path = folderPathLiveData.getValue();
        if (path != null) {
            for (NotesRepository.NoteInfo info : path) {
                if (info.getId() == Notes.ID_TEMPLATE_FOLDER) return true;
            }
        }
        return false;
    }

    /**
     * 应用模板
     *
     * @param templateId 模板笔记ID
     * @param callback 回调
     */
    public void applyTemplate(long templateId, NotesRepository.Callback<Long> callback) {
        // 应用模板到根目录（或者让用户选择，这里简化为根目录）
        // 实际上应该让用户选择，或者默认应用到当前上下文（如果是从新建笔记进入）
        // 这里假设是从模板列表点击进入，则应用到根目录（或默认目录）
        // 更好的逻辑是：applyTemplate(templateId, Notes.ID_ROOT_FOLDER)
        repository.applyTemplate(templateId, Notes.ID_ROOT_FOLDER, callback);
    }

    /**
     * 重命名文件夹
     * <p>
     * 重命名指定文件夹，并刷新侧栏
     * </p>
     *
     * @param folderId 文件夹ID
     * @param newName 新名称
     */
    public void renameFolder(long folderId, String newName) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        repository.renameFolder(folderId, newName, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                // 触发列表和侧栏刷新
                refreshNotes();
                sidebarRefreshNeeded.postValue(true);
                Log.d(TAG, "Successfully renamed folder: " + folderId);
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "重命名文件夹失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 重命名笔记
     * <p>
     * 修改笔记标题，并触发侧栏和列表刷新
     * </p>
     *
     * @param noteId 笔记ID
     * @param newName  新标题
     */
    public void renameNote(long noteId, String newName) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        repository.renameNote(noteId, newName, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                // 触发列表和侧栏刷新
                refreshNotes();
                sidebarRefreshNeeded.postValue(true);
                Log.d(TAG, "Successfully renamed note: " + noteId);
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "重命名笔记失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 删除文件夹
     * <p>
     * 将文件夹移动到回收站，并刷新侧栏
     * </p>
     *
     * @param folderId 文件夹ID
     */
    public void deleteFolder(long folderId) {
        isLoading.postValue(true);
        errorMessage.postValue(null);

        repository.deleteFolder(folderId, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                // 触发侧栏刷新
                sidebarRefreshNeeded.postValue(true);
                Log.d(TAG, "Successfully deleted folder: " + folderId);
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "删除文件夹失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }

    /**
     * 获取单个笔记的详细信息
     *
     * @param noteId 笔记ID
     * @param callback 回调接口
     */
    public void getNoteInfo(long noteId, NotesRepository.Callback<NotesRepository.NoteInfo> callback) {
        repository.getNoteInfo(noteId, callback);
    }

    /**
     * 获取单个笔记的完整内容
     *
     * @param noteId 笔记ID
     * @param callback 回调接口
     */
    public void getNoteContent(long noteId, NotesRepository.Callback<String> callback) {
        repository.getNoteContent(noteId, callback);
    }

    /**
     * 获取文件夹信息
     * <p>
     * 查询单个文件夹的详细信息
     * </p>
     *
     * @param folderId 文件夹ID
     * @param callback 回调接口
     */
    public void getFolderInfo(long folderId, NotesRepository.Callback<NotesRepository.NoteInfo> callback) {
        try {
            NotesRepository.NoteInfo folderInfo = repository.getFolderInfo(folderId);
            callback.onSuccess(folderInfo);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    /**
     * ViewModel销毁时的清理
     * <p>
     * 清理资源和状态
     * </p>
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        selectedNoteIds.clear();
        Log.d(TAG, "ViewModel cleared");
    }

    /**
     * 切换单个笔记的锁定状态
     *
     * @param noteId 笔记ID
     * @param isLocked 是否锁定
     */
    public void toggleNoteLock(long noteId, boolean isLocked) {
        List<Long> ids = new ArrayList<>();
        ids.add(noteId);
        
        isLoading.postValue(true);
        repository.batchLock(ids, isLocked, new NotesRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer rowsAffected) {
                isLoading.postValue(false);
                refreshNotes();
                Log.d(TAG, "Successfully toggled lock state for note: " + noteId);
            }

            @Override
            public void onError(Exception error) {
                isLoading.postValue(false);
                String message = "锁定操作失败: " + error.getMessage();
                errorMessage.postValue(message);
                Log.e(TAG, message, error);
            }
        });
    }
}