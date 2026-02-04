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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单类
 * <p>
 * 封装了PopupMenu和Button，提供下拉菜单功能。
 * 点击按钮时显示弹出菜单，支持设置菜单项点击监听器和标题。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>显示下拉菜单</li>
 * <li>设置菜单项点击监听器</li>
 * <li>查找菜单项</li>
 * <li>设置按钮标题</li>
 * </ul>
 * </p>
 */
public class DropdownMenu {
    // 下拉按钮
    private Button mButton;
    // 弹出菜单
    private PopupMenu mPopupMenu;
    // 菜单对象
    private Menu mMenu;

    /**
     * 构造器
     * 
     * 初始化下拉菜单，设置按钮背景、创建PopupMenu并加载菜单资源
     * 
     * @param context 应用上下文
     * @param button 触发下拉菜单的按钮
     * @param menuId 菜单资源ID，用于加载菜单项
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        // 设置下拉图标背景
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        // 创建弹出菜单
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        // 加载菜单资源
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        // 设置按钮点击监听器，点击时显示弹出菜单
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置菜单项点击监听器
     * 
     * @param listener 菜单项点击监听器
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 查找指定ID的菜单项
     * 
     * @param id 菜单项ID
     * @return 找到的菜单项对象，如果未找到则返回null
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮标题
     * 
     * @param title 要设置的标题文本
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
