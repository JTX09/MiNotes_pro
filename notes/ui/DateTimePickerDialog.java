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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框
 * <p>
 * 继承自AlertDialog，提供日期和时间选择的对话框界面。
 * 使用DateTimePicker组件作为主视图，支持设置24小时制或12小时制显示。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>显示日期和时间选择器</li>
 * <li>支持设置监听器获取用户选择的时间</li>
 * <li>动态更新对话框标题显示当前选择的时间</li>
 * <li>支持24小时制和12小时制切换</li>
 * </ul>
 * </p>
 * 
 * @see DateTimePicker
 * @see OnDateTimeSetListener
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    private Calendar mDate = Calendar.getInstance();
    private boolean mIs24HourView;
    private OnDateTimeSetListener mOnDateTimeSetListener;
    private DateTimePicker mDateTimePicker;

    /**
     * 日期时间设置监听器接口
     * <p>
     * 用于监听用户在对话框中点击确定按钮后的回调，获取用户选择的日期和时间。
     * </p>
     */
    public interface OnDateTimeSetListener {
        /**
         * 当用户点击确定按钮时调用
         * 
         * @param dialog 日期时间选择对话框实例
         * @param date 用户选择的日期时间，以毫秒为单位的时间戳
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造器
     * 
     * 创建日期时间选择对话框，初始化DateTimePicker组件并设置默认日期时间。
     * 根据系统设置自动判断是否使用24小时制显示。
     * 
     * @param context 应用上下文
     * @param date 初始日期时间，以毫秒为单位的时间戳
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        // 创建日期时间选择器组件
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);
        // 设置日期时间变更监听器
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                // 更新内部Calendar对象
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 更新对话框标题
                updateTitle(mDate.getTimeInMillis());
            }
        });
        // 设置初始日期时间
        mDate.setTimeInMillis(date);
        // 将秒数清零
        mDate.set(Calendar.SECOND, 0);
        // 设置选择器当前日期
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        // 设置确定按钮
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        // 设置取消按钮
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
        // 根据系统设置判断是否使用24小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        // 更新对话框标题
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否使用24小时制显示
     * <p>
     * 根据系统设置或用户偏好，判断是否使用24小时制显示时间。
     * 如果设置为true，将使用24小时制；如果设置为false，将使用12小时制。
     * </p>
     * 
     * @param is24HourView true表示使用24小时制，false表示使用12小时制
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置日期时间设置监听器
     * <p>
     * 当用户点击对话框的确定按钮时，调用此监听器的OnDateTimeSet方法，
     * 并传递用户选择的日期时间作为参数。
     * </p>             
     * 
     * @param callBack 日期时间设置监听器，当用户点击确定按钮时回调
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框标题
     * 
     * 根据指定的日期时间格式化字符串，并设置为对话框标题。
     * 显示格式包含年、月、日和时间，根据mIs24HourView决定是否使用24小时制。
     * 
     * @param date 要显示的日期时间，以毫秒为单位的时间戳
     */
    private void updateTitle(long date) {
        // 设置日期时间格式标志
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |
            DateUtils.FORMAT_SHOW_DATE |
            DateUtils.FORMAT_SHOW_TIME;
        // 根据是否24小时制设置相应的格式标志
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        // 格式化日期时间并设置为对话框标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 处理对话框按钮点击事件
     * 
     * 当用户点击确定按钮时，调用监听器的OnDateTimeSet方法，传递用户选择的日期时间。
     * 
     * @param arg0 触发事件的对话框
     * @param arg1 被点击的按钮ID
     */
    public void onClick(DialogInterface arg0, int arg1) {
        // 如果设置了监听器，通知监听器用户选择的日期时间
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }

}