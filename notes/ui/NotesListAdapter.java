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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


/**
 * 笔记列表适配器
 * 
 * 这个类继承自CursorAdapter，用于将数据库中的笔记数据绑定到ListView中显示。
 * 它支持笔记的选择模式、批量操作以及与桌面小部件的关联。
 * 
 * 主要功能：
 * 1. 将笔记数据绑定到NotesListItem视图
 * 2. 支持多选模式和批量选择操作
 * 3. 获取选中的笔记ID和关联的桌面小部件信息
 * 4. 统计笔记数量和选中数量
 * 
 * @see NotesListItem
 * @see NoteItemData
 */
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";
    // 应用上下文
    private Context mContext;
    // 记录选中状态的Map，key为位置，value为是否选中
    private HashMap<Integer, Boolean> mSelectedIndex;
    // 笔记总数
    private int mNotesCount;
    // 是否处于选择模式
    private boolean mChoiceMode;

    /**
     * 桌面小部件属性类
     * 
     * 用于存储桌面小部件的ID和类型信息
     */
    public static class AppWidgetAttribute {
        // 桌面小部件ID
        public int widgetId;
        // 桌面小部件类型
        public int widgetType;
    };

    /**
     * 构造器
     * 
     * 初始化笔记列表适配器，创建选中状态Map和计数器
     * 
     * @param context 应用上下文，不能为 null
     */
    public NotesListAdapter(Context context) {
        super(context, null);
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    /**
     * 创建新的列表项视图
     * 
     * @param context 应用上下文
     * @param cursor 数据库游标，包含当前项的数据
     * @param parent 父视图
     * @return 新创建的NotesListItem视图对象
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    /**
     * 绑定数据到视图
     * 
     * 将数据库游标中的数据绑定到已存在的视图上
     * 
     * @param view 需要绑定数据的视图
     * @param context 应用上下文
     * @param cursor 数据库游标，包含当前项的数据
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 设置指定位置的选中状态
     * 
     * @param position 列表项位置，从0开始
     * @param checked 是否选中
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    /**
     * 判断是否处于选择模式
     * 
     * @return 如果处于选择模式返回true，否则返回false
     */
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /**
     * 设置选择模式
     * 
     * @param mode true表示进入选择模式，false表示退出选择模式
     */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /**
     * 全选或取消全选所有笔记
     * 
     * @param checked true表示全选，false表示取消全选
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 获取所有选中项的笔记ID集合
     * 
     * @return 包含所有选中笔记ID的HashSet集合，如果没有选中项则返回空集合
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }

        return itemSet;
    }

    /**
     * 获取所有选中项关联的桌面小部件集合
     * 
     * @return 包含所有选中笔记关联的桌面小部件属性的HashSet集合，如果游标无效则返回null
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                    /**
                     * Don't close cursor here, only the adapter could close it
                     */
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取选中项的数量
     * 
     * @return 选中项的数量，如果没有选中项则返回0
     */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断是否已全选所有笔记
     * 
     * @return 如果所有笔记都被选中且至少有一个笔记则返回true，否则返回false
     */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    /**
     * 判断指定位置的项是否被选中
     * 
     * @param position 列表项位置，从0开始
     * @return 如果该项被选中返回true，否则返回false
     */
    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    /**
     * 当内容发生变化时调用
     * 
     * 重新计算笔记数量
     */
    @Override
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    /**
     * 更换游标
     * 
     * @param cursor 新的数据库游标
     */
    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}
