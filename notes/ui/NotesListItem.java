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

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;
import net.micode.notes.data.FontManager;


/**
 * 笔记列表项视图
 * <p>
 * 自定义的 LinearLayout，表示笔记列表中的单个笔记项。
 * 该视图显示笔记信息，包括标题、时间、通话名称（针对通话记录）和提醒图标。
 * 支持在多选模式下显示复选框。
 * </p>
 */
public class NotesListItem extends LinearLayout {
    private ImageView mAlert;
    private TextView mTitle;
    private TextView mTime;
    private TextView mCallName;
    private NoteItemData mItemData;
    private CheckBox mCheckBox;

    /**
     * 构造函数
     * @param context 用于加载布局的上下文对象
     */
    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this);
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 绑定笔记数据到视图项
     * @param context 用于访问资源的上下文对象
     * @param data 包含要显示的笔记信息的 NoteItemData 对象
     * @param choiceMode 列表是否处于多选模式（显示复选框）
     * @param checked 该项是否被选中（仅在多选模式下有意义）
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        if (choiceMode && (data.getType() == Notes.TYPE_NOTE || data.getType() == Notes.TYPE_TEMPLATE)) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            FontManager.getInstance(context).applyFont(mTitle);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record);
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context,R.style.TextAppearanceSecondaryItem);
            FontManager.getInstance(context).applyFont(mTitle);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        } else {
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            FontManager.getInstance(context).applyFont(mTitle);

            if (data.getType() == Notes.TYPE_FOLDER) {
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                                data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                // 优先显示标题，如果标题为空则显示摘要
                String title = data.getTitle();
                if (!android.text.TextUtils.isEmpty(title)) {
                    mTitle.setText(title);
                } else {
                    mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                }
                
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        setBackground(data);
    }

    /**
     * 根据笔记项的位置和类型设置合适的背景资源
     * @param data 包含笔记背景颜色和位置信息的 NoteItemData 对象
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();
        int resId;
        if (data.getType() == Notes.TYPE_NOTE || data.getType() == Notes.TYPE_TEMPLATE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                resId = NoteItemBgResources.getNoteBgSingleRes(id);
            } else if (data.isLast()) {
                resId = NoteItemBgResources.getNoteBgLastRes(id);
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                resId = NoteItemBgResources.getNoteBgFirstRes(id);
            } else {
                resId = NoteItemBgResources.getNoteBgNormalRes(id);
            }
        } else {
            resId = NoteItemBgResources.getFolderBgRes();
        }

        setBackgroundResource(resId);

        // Apply tint for new colors
        if ((data.getType() == Notes.TYPE_NOTE || data.getType() == Notes.TYPE_TEMPLATE) && (id >= net.micode.notes.tool.ResourceParser.MIDNIGHT_BLACK || id < 0)) {
            int color = net.micode.notes.tool.ResourceParser.getNoteBgColor(getContext(), id);
            if (getBackground() != null) {
                getBackground().setTint(color);
                getBackground().setTintMode(android.graphics.PorterDuff.Mode.MULTIPLY);
            }
        } else {
            // Ensure no tint for legacy colors (if view is recycled)
            if (getBackground() != null) {
                getBackground().clearColorFilter();
            }
        }
    }

    /**
     * 获取绑定到该视图项的笔记数据
     * @return 包含该笔记信息的 NoteItemData 对象
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}
