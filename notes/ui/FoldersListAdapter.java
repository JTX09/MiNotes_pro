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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器
 * <p>
 * 继承自CursorAdapter，用于将数据库中的文件夹数据绑定到ListView中显示。
 * 主要用于笔记移动功能中显示可选择的文件夹列表。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>显示所有可用文件夹</li>
 * <li>处理根文件夹的特殊显示</li>
 * <li>提供获取文件夹名称的方法</li>
 * </ul>
 * </p>
 * 
 * @see NotesListActivity
 */
public class FoldersListAdapter extends CursorAdapter {
    // 数据库查询投影，指定需要从笔记表中获取的列
    public static final String [] PROJECTION = {
        NoteColumns.ID,
        NoteColumns.SNIPPET,
        NoteColumns.TITLE
    };

    // 列索引常量，用于从查询结果中获取对应列的数据
    public static final int ID_COLUMN   = 0;
    public static final int SNIPPET_COLUMN = 1;
    public static final int TITLE_COLUMN = 2;

    /**
     * 构造器
     * 
     * 初始化文件夹列表适配器
     * 
     * @param context 应用上下文
     * @param c 数据库游标，包含文件夹数据
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
    }

    /**
     * 创建新的列表项视图
     * 
     * 创建一个新的FolderListItem视图对象
     * 
     * @param context 应用上下文
     * @param cursor 数据库游标，包含当前项的数据
     * @param parent 父视图
     * @return 新创建的FolderListItem视图对象
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
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
        if (view instanceof FolderListItem) {
            // 如果是根文件夹，显示特殊文本；否则显示文件夹名称
            if (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) {
                ((FolderListItem) view).bind(context.getString(R.string.menu_move_parent_folder));
            } else {
                // 优先使用TITLE，fallback到SNIPPET
                String folderName = "";
                if (cursor.getColumnCount() > TITLE_COLUMN) {
                    folderName = cursor.getString(TITLE_COLUMN);
                }
                if (folderName == null || folderName.trim().isEmpty()) {
                    folderName = cursor.getString(SNIPPET_COLUMN);
                }
                ((FolderListItem) view).bind(folderName);
            }
        }
    }

    /**
     * 获取指定位置的文件夹名称
     * 
     * @param context 应用上下文，用于获取根文件夹的显示文本
     * @param position 列表项位置，从0开始
     * @return 文件夹名称，如果是根文件夹则返回特殊显示文本
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        if (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) {
            return context.getString(R.string.menu_move_parent_folder);
        }
        // 优先使用TITLE，fallback到SNIPPET
        String folderName = "";
        if (cursor.getColumnCount() > TITLE_COLUMN) {
            folderName = cursor.getString(TITLE_COLUMN);
        }
        if (folderName == null || folderName.trim().isEmpty()) {
            folderName = cursor.getString(SNIPPET_COLUMN);
        }
        return folderName;
    }

    /**
     * 文件夹列表项视图
     * <p>
     * 自定义的LinearLayout，用于显示文件夹列表中的单个文件夹项。
     * </p>
     */
    private class FolderListItem extends LinearLayout {
        // 文件夹名称文本视图
        private TextView mName;

        /**
         * 构造器
         * 
         * 初始化文件夹列表项视图
         * 
         * @param context 应用上下文
         */
        public FolderListItem(Context context) {
            super(context);
            // 加载布局文件
            inflate(context, R.layout.folder_list_item, this);
            // 获取文件夹名称文本视图
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 绑定文件夹名称到视图
         * 
         * @param name 要显示的文件夹名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
