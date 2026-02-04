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

package net.micode.notes.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import net.micode.notes.R;

/**
 * 文件夹操作对话框工具类
 * <p>
 * 提供重命名和删除文件夹的对话框
 * </p>
 */
public class FolderOperationDialogs {

    private static final int MAX_FOLDER_NAME_LENGTH = 50;

    /**
     * 显示重命名文件夹对话框
     *
     * @param activity    Activity实例
     * @param currentName 当前文件夹名称
     * @param listener    重命名监听器
     */
    public static void showRenameDialog(Context activity, String currentName,
                                     OnRenameListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.dialog_rename_folder_title);

        // 创建输入框
        final EditText input = new EditText(activity);
        input.setText(currentName);
        input.setHint(R.string.dialog_create_folder_hint);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_FOLDER_NAME_LENGTH)});
        input.setSelection(input.getText().length()); // 光标移到末尾

        builder.setView(input);

        builder.setPositiveButton(R.string.menu_rename, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                listener.onError(activity.getString(R.string.error_folder_name_empty));
                return;
            }
            if (newName.length() > MAX_FOLDER_NAME_LENGTH) {
                listener.onError(activity.getString(R.string.error_folder_name_too_long));
                return;
            }

            listener.onRename(newName);
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            listener.onCancel();
        });

        builder.show();
    }

    /**
     * 显示删除文件夹确认对话框
     *
     * @param activity   Activity实例
     * @param folderName 文件夹名称
     * @param noteCount  文件夹中的笔记数量
     * @param listener   删除监听器
     */
    public static void showDeleteFolderDialog(Context activity, String folderName,
                                           int noteCount, OnDeleteListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.dialog_delete_folder_title);

        // 创建自定义消息视图
        LayoutInflater inflater = LayoutInflater.from(activity);
        View messageView = inflater.inflate(R.layout.dialog_folder_delete, null);
        TextView messageText = messageView.findViewById(R.id.tv_delete_message);

        String message;
        if (noteCount > 0) {
            message = activity.getString(R.string.dialog_delete_folder_with_notes, folderName, noteCount);
        } else {
            message = activity.getString(R.string.dialog_delete_folder_empty, folderName);
        }
        messageText.setText(message);

        builder.setView(messageView);

        builder.setPositiveButton(R.string.menu_delete, (dialog, which) -> {
            listener.onDelete();
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            listener.onCancel();
        });

        builder.show();
    }

    /**
     * 重命名监听器接口
     */
    public interface OnRenameListener {
        /**
         * 重命名确认回调
         *
         * @param newName 新名称
         */
        void onRename(String newName);

        /**
         * 取消回调
         */
        default void onCancel() {
        }

        /**
         * 错误回调
         *
         * @param errorMessage 错误消息
         */
        default void onError(String errorMessage) {
        }
    }

    /**
     * 删除监听器接口
     */
    public interface OnDeleteListener {
        /**
         * 删除确认回调
         */
        void onDelete();

        /**
         * 取消回调
         */
        default void onCancel() {
        }
    }
}
