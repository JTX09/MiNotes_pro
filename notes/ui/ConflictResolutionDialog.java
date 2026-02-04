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

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.micode.notes.R;
import net.micode.notes.model.WorkingNote;

/**
 * 冲突解决对话框
 * <p>
 * 当本地笔记和云端笔记发生冲突时显示，让用户选择保留哪个版本。
 * </p>
 */
public class ConflictResolutionDialog extends DialogFragment {

    private static final String TAG = "ConflictResolutionDialog";
    private static final String ARG_LOCAL_TITLE = "local_title";
    private static final String ARG_LOCAL_CONTENT = "local_content";
    private static final String ARG_CLOUD_TITLE = "cloud_title";
    private static final String ARG_CLOUD_CONTENT = "cloud_content";

    private ConflictResolutionListener mListener;

    /**
     * 冲突解决监听器接口
     */
    public interface ConflictResolutionListener {
        void onChooseLocal();
        void onChooseCloud();
        void onMerge(String mergedTitle, String mergedContent);
    }

    /**
     * 创建冲突解决对话框实例
     *
     * @param localNote 本地笔记
     * @param cloudNote 云端笔记
     * @return ConflictResolutionDialog实例
     */
    public static ConflictResolutionDialog newInstance(WorkingNote localNote, WorkingNote cloudNote) {
        ConflictResolutionDialog dialog = new ConflictResolutionDialog();
        Bundle args = new Bundle();
        args.putString(ARG_LOCAL_TITLE, localNote.getTitle());
        args.putString(ARG_LOCAL_CONTENT, localNote.getContent());
        args.putString(ARG_CLOUD_TITLE, cloudNote.getTitle());
        args.putString(ARG_CLOUD_CONTENT, cloudNote.getContent());
        dialog.setArguments(args);
        return dialog;
    }

    /**
     * 设置冲突解决监听器
     *
     * @param listener 监听器
     */
    public void setConflictResolutionListener(ConflictResolutionListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_conflict_resolution, null);

        // Get arguments
        Bundle args = getArguments();
        if (args != null) {
            String localTitle = args.getString(ARG_LOCAL_TITLE, "");
            String localContent = args.getString(ARG_LOCAL_CONTENT, "");
            String cloudTitle = args.getString(ARG_CLOUD_TITLE, "");
            String cloudContent = args.getString(ARG_CLOUD_CONTENT, "");

            // Set content previews (first 100 chars)
            TextView tvLocalContent = view.findViewById(R.id.tv_local_content);
            tvLocalContent.setText(truncateContent(localTitle, localContent));

            TextView tvCloudContent = view.findViewById(R.id.tv_cloud_content);
            tvCloudContent.setText(truncateContent(cloudTitle, cloudContent));
        }

        // Setup buttons
        MaterialButton btnUseLocal = view.findViewById(R.id.btn_use_local);
        MaterialButton btnUseCloud = view.findViewById(R.id.btn_use_cloud);
        MaterialButton btnMerge = view.findViewById(R.id.btn_merge);

        btnUseLocal.setOnClickListener(v -> {
            Log.d(TAG, "User chose local version");
            if (mListener != null) {
                mListener.onChooseLocal();
            }
            dismiss();
        });

        btnUseCloud.setOnClickListener(v -> {
            Log.d(TAG, "User chose cloud version");
            if (mListener != null) {
                mListener.onChooseCloud();
            }
            dismiss();
        });

        btnMerge.setOnClickListener(v -> {
            Log.d(TAG, "User chose merge");
            showMergeDialog(args);
        });

        builder.setView(view);
        return builder.create();
    }

    private String truncateContent(String title, String content) {
        String fullText = title + "\n" + content;
        if (fullText.length() > 100) {
            return fullText.substring(0, 100) + "...";
        }
        return fullText;
    }

    /**
     * 显示合并编辑对话框
     */
    private void showMergeDialog(Bundle args) {
        if (args == null) return;

        String localTitle = args.getString(ARG_LOCAL_TITLE, "");
        String localContent = args.getString(ARG_LOCAL_CONTENT, "");
        String cloudTitle = args.getString(ARG_CLOUD_TITLE, "");
        String cloudContent = args.getString(ARG_CLOUD_CONTENT, "");

        // 智能合并：合并标题和内容
        String mergedTitle = mergeText(localTitle, cloudTitle);
        String mergedContent = mergeText(localContent, cloudContent);

        // 创建编辑对话框
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("合并笔记");

        // 创建输入布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 标题输入
        final android.widget.EditText etTitle = new android.widget.EditText(requireContext());
        etTitle.setHint("标题");
        etTitle.setText(mergedTitle);
        layout.addView(etTitle);

        // 内容输入
        final android.widget.EditText etContent = new android.widget.EditText(requireContext());
        etContent.setHint("内容");
        etContent.setText(mergedContent);
        etContent.setMinLines(5);
        layout.addView(etContent);

        builder.setView(layout);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String finalTitle = etTitle.getText().toString().trim();
            String finalContent = etContent.getText().toString().trim();

            if (mListener != null) {
                mListener.onMerge(finalTitle, finalContent);
            }
            dismiss();
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    /**
     * 合并两段文本
     * 如果内容相同返回其中一个，不同则合并
     */
    private String mergeText(String local, String cloud) {
        if (local == null || local.isEmpty()) return cloud;
        if (cloud == null || cloud.isEmpty()) return local;
        if (local.equals(cloud)) return local;

        // 简单的合并策略：用分隔符连接
        return local + "\n\n--- 云端版本 ---\n\n" + cloud;
    }
}
