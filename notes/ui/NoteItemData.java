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
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 笔记项数据类
 * <p>
 * 用于封装笔记列表项的数据信息，从数据库游标中提取笔记的各项属性，
 * 并提供便捷的访问方法。该类支持普通笔记、文件夹和通话记录笔记等多种类型。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>从数据库游标中提取笔记数据</li>
 * <li>判断笔记在列表中的位置状态（首项、末项、唯一项等）</li>
 * <li>判断笔记是否跟随文件夹显示</li>
 * <li>处理通话记录笔记的特殊逻辑</li>
 * </ul>
 * </p>
 * 
 * @see NotesListItem
 * @see NotesListAdapter
 */
public class NoteItemData {
    // 数据库查询投影，指定需要从笔记表中获取的列
    static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.CREATED_DATE,
        NoteColumns.HAS_ATTACHMENT,
        NoteColumns.MODIFIED_DATE,
        NoteColumns.NOTES_COUNT,
        NoteColumns.PARENT_ID,
        NoteColumns.SNIPPET,
        NoteColumns.TYPE,
        NoteColumns.WIDGET_ID,
        NoteColumns.WIDGET_TYPE,
        NoteColumns.TOP, // 新增TOP字段
        NoteColumns.TITLE // 新增TITLE字段
    };

    // 列索引常量，用于从查询结果中获取对应列的数据
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;
    private static final int TOP_COLUMN                   = 12;
    private static final int TITLE_COLUMN                 = 13;

    // 笔记ID
    private long mId;
    // 提醒日期
    private long mAlertDate;
    // 背景颜色ID
    private int mBgColorId;
    // 创建日期
    private long mCreatedDate;
    // 是否有附件
    private boolean mHasAttachment;
    // 修改日期
    private long mModifiedDate;
    // 笔记数量（用于文件夹）
    private int mNotesCount;
    // 父文件夹ID
    private long mParentId;
    // 笔记摘要
    private String mSnippet;
    // 笔记标题
    private String mTitle;
    // 笔记类型
    private int mType;
    // 桌面小部件ID
    private int mWidgetId;
    // 桌面小部件类型
    private int mWidgetType;
    // 是否置顶
    private boolean mIsPinned;
    // 联系人名称（用于通话记录）
    private String mName;
    // 电话号码（用于通话记录）
    private String mPhoneNumber;

    // 是否为列表最后一项
    private boolean mIsLastItem;
    // 是否为列表第一项
    private boolean mIsFirstItem;
    // 是否为列表唯一一项
    private boolean mIsOnlyOneItem;
    // 是否为文件夹后跟随的单个笔记
    private boolean mIsOneNoteFollowingFolder;
    // 是否为文件夹后跟随的多个笔记之一
    private boolean mIsMultiNotesFollowingFolder;

    /**
     * 构造器
     * 
     * 从数据库游标中提取笔记数据并初始化各项属性。
     * 对于通话记录笔记，会额外获取联系人信息。
     * 
     * @param context 应用上下文，用于访问内容提供者和联系人信息
     * @param cursor 数据库游标，包含笔记数据，游标必须包含PROJECTION中指定的所有列
     */
    public NoteItemData(Context context, Cursor cursor) {
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        // 移除清单项的勾选标记，只保留文本内容
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);
        // 读取置顶状态
        if (cursor.getColumnCount() > TOP_COLUMN) {
            mIsPinned = cursor.getInt(TOP_COLUMN) > 0;
        } else {
            mIsPinned = false;
        }
        
        // 读取标题
        if (cursor.getColumnCount() > TITLE_COLUMN) {
            mTitle = cursor.getString(TITLE_COLUMN);
        } else {
            mTitle = "";
        }

        mPhoneNumber = "";
        // 如果是通话记录笔记，获取电话号码和联系人名称
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                // 如果找不到联系人，使用电话号码作为名称
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        if (mName == null) {
            mName = "";
        }
        // 检查当前项在列表中的位置状态
        checkPostion(cursor);
    }

    /**
     * 检查当前项在列表中的位置状态
     * 
     * 判断当前项是否为首项、末项、唯一项，以及是否跟随文件夹显示。
     * 
     * @param cursor 数据库游标，用于判断位置状态
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 如果是普通笔记或模板且不是第一项，检查前一项是否为文件夹
        if ((mType == Notes.TYPE_NOTE || mType == Notes.TYPE_TEMPLATE) && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                // 前一项是文件夹或系统文件夹
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    // 检查文件夹后是否还有更多笔记
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                // 移动回原位置
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    /**
     * 判断是否为文件夹后跟随的单个笔记
     * 
     * @return 如果是文件夹后跟随的单个笔记返回true，否则返回false
     */
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    /**
     * 判断是否为文件夹后跟随的多个笔记之一
     * 
     * @return 如果是文件夹后跟随的多个笔记之一返回true，否则返回false
     */
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    /**
     * 判断是否为列表最后一项
     * 
     * @return 如果是最后一项返回true，否则返回false
     */
    public boolean isLast() {
        return mIsLastItem;
    }

    /**
     * 获取通话记录的联系人名称
     * 
     * @return 联系人名称，如果不是通话记录或找不到联系人则返回空字符串
     */
    public String getCallName() {
        return mName;
    }

    /**
     * 判断是否为列表第一项
     * 
     * @return 如果是第一项返回true，否则返回false
     */
    public boolean isFirst() {
        return mIsFirstItem;
    }

    /**
     * 判断是否为列表唯一一项
     * 
     * @return 如果是唯一一项返回true，否则返回false
     */
    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    /**
     * 获取笔记ID
     * 
     * @return 笔记ID
     */
    public long getId() {
        return mId;
    }

    /**
     * 获取提醒日期
     * 
     * @return 提醒日期（毫秒时间戳），如果没有设置提醒则返回0
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取创建日期
     * 
     * @return 创建日期（毫秒时间戳）
     */
    public long getCreatedDate() {
        return mCreatedDate;
    }

    /**
     * 判断笔记是否有附件
     * 
     * @return 如果有附件返回true，否则返回false
     */
    public boolean hasAttachment() {
        return mHasAttachment;
    }

    /**
     * 获取修改日期
     * 
     * @return 修改日期（毫秒时间戳）
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取背景颜色ID
     * 
     * @return 背景颜色ID
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取父文件夹ID
     * 
     * @return 父文件夹ID
     */
    public long getParentId() {
        return mParentId;
    }

    /**
     * 获取笔记数量
     * 
     * @return 笔记数量（主要用于文件夹类型）
     */
    public int getNotesCount() {
        return mNotesCount;
    }

    /**
     * 获取文件夹ID
     * 
     * @return 文件夹ID（与getParentId相同）
     */
    public long getFolderId () {
        return mParentId;
    }

    /**
     * 获取笔记类型
     * 
     * @return 笔记类型，取值为Notes.TYPE_NOTE、Notes.TYPE_FOLDER或Notes.TYPE_SYSTEM
     */
    public int getType() {
        return mType;
    }

    /**
     * 获取桌面小部件类型
     * 
     * @return 桌面小部件类型
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 获取桌面小部件ID
     * 
     * @return 桌面小部件ID
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取笔记摘要
     * 
     * @return 笔记摘要文本（已移除清单项标记）
     */
    public String getSnippet() {
        return mSnippet;
    }

    /**
     * 获取笔记标题
     * 
     * @return 笔记标题
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * 判断是否设置了提醒
     * 
     * @return 如果设置了提醒返回true，否则返回false
     */
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    /**
     * 判断是否置顶
     * @return 如果置顶返回true
     */
    public boolean isPinned() {
        return mIsPinned;
    }

    /**
     * 判断是否为通话记录笔记
     * 
     * @return 如果是通话记录笔记且包含电话号码返回true，否则返回false
     */
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 从游标中获取笔记类型
     * 
     * 静态方法，直接从游标中读取类型列的值，无需创建NoteItemData对象
     * 
     * @param cursor 数据库游标，必须包含TYPE_COLUMN列
     * @return 笔记类型
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}
