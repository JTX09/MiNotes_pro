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

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.databinding.DatetimePickerBinding;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * 日期时间选择器
 * <p>
 * 继承自FrameLayout，提供日期和时间选择的自定义视图组件。
 * 使用NumberPicker组件实现日期、小时、分钟和上午/下午的选择功能。
 * 支持24小时制和12小时制两种显示模式。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>显示日期选择器（显示前后3天，共7天）</li>
 * <li>显示小时选择器（24小时制：0-23，12小时制：1-12）</li>
 * <li>显示分钟选择器（0-59）</li>
 * <li>显示上午/下午选择器（仅12小时制）</li>
 * <li>支持设置日期时间变更监听器</li>
 * <li>支持启用/禁用状态切换</li>
 * </ul>
 * </p>
 * 
 * @see NumberPicker
 * @see OnDateTimeChangedListener
 */
public class DateTimePicker extends FrameLayout {

    private static final boolean DEFAULT_ENABLE_STATE = true;

    private static final int HOURS_IN_HALF_DAY = 12;
    private static final int HOURS_IN_ALL_DAY = 24;
    private static final int DAYS_IN_ALL_WEEK = 7;
    private static final int DATE_SPINNER_MIN_VAL = 0;
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;
    private static final int MINUT_SPINNER_MIN_VAL = 0;
    private static final int MINUT_SPINNER_MAX_VAL = 59;
    private static final int AMPM_SPINNER_MIN_VAL = 0;
    private static final int AMPM_SPINNER_MAX_VAL = 1;

    private final NumberPicker mDateSpinner;
    private final NumberPicker mHourSpinner;
    private final NumberPicker mMinuteSpinner;
    private final NumberPicker mAmPmSpinner;
    private final DatetimePickerBinding binding;
    private Calendar mDate;

    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    private boolean mIsAm;

    private boolean mIs24HourView;

    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    private boolean mInitialising;

    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    /**
     * 日期变更监听器
     * <p>
     * 监听日期选择器的值变化，更新内部日期对象并通知外部监听器。
     * </p>
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 根据选择器的变化调整日期
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            updateDateControl();
            onDateTimeChanged();
        }
    };

    /**
     * 小时变更监听器
     * <p>
     * 监听小时选择器的值变化，处理跨日情况（如从23点变为0点或从0点变为23点），
     * 在12小时制下处理上午/下午的切换。
     * </p>
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance();
            // 处理12小时制下的跨日情况
            if (!mIs24HourView) {
                // 从下午11点变为12点，日期加1天
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                // 从12点变为下午11点，日期减1天
                } else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
                // 切换上午/下午
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                        oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                }
            } else {
                // 处理24小时制下的跨日情况
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }
            // 计算新的小时数
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();
            // 如果日期发生变化，更新年月日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟变更监听器
     * <p>
     * 监听分钟选择器的值变化，处理跨小时情况（如从59分变为0分或从0分变为59分）。
     * </p>
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;
            // 从最大值变为最小值，小时加1
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;
            // 从最小值变为最大值，小时减1
            } else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;
            }
            // 如果跨小时，更新小时和日期
            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());
                updateDateControl();
                // 更新上午/下午状态
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                    updateAmPmControl();
                } else {
                    mIsAm = true;
                    updateAmPmControl();
                }
            }
            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /**
     * 上午/下午变更监听器
     * <p>
     * 监听上午/下午选择器的值变化，切换上午/下午时调整小时数。
     * </p>
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mIsAm = !mIsAm;
            // 切换上午/下午，调整小时数
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            updateAmPmControl();
            onDateTimeChanged();
        }
    };

    /**
     * 日期时间变更监听器接口
     * <p>
     * 用于监听日期时间选择器的值变化，当用户修改日期、小时或分钟时回调。
     * </p>
     */
    public interface OnDateTimeChangedListener {
        /**
         * 当日期时间发生变化时调用
         * 
         * @param view 日期时间选择器实例
         * @param year 年份
         * @param month 月份（0-11）
         * @param dayOfMonth 日（1-31）
         * @param hourOfDay 小时（0-23）
         * @param minute 分钟（0-59）
         */
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                int dayOfMonth, int hourOfDay, int minute);
    }

    /**
     * 构造器
     * 
     * 创建日期时间选择器，使用当前系统时间作为初始值。
     * 根据系统设置自动判断是否使用24小时制显示。
     * 
     * @param context 应用上下文
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /**
     * 构造器
     * 
     * 创建日期时间选择器，使用指定的时间作为初始值。
     * 根据系统设置自动判断是否使用24小时制显示。
     * 
     * @param context 应用上下文
     * @param date 初始日期时间，以毫秒为单位的时间戳
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 构造器
     * 
     * 创建日期时间选择器，使用指定的时间和显示模式作为初始值。
     * 初始化所有NumberPicker组件并设置监听器。
     * 
     * @param context 应用上下文
     * @param date 初始日期时间，以毫秒为单位的时间戳
     * @param is24HourView 是否使用24小时制显示，true表示24小时制，false表示12小时制
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance();
        mInitialising = true;
        // 判断当前是否为下午
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;
        // 加载布局
        binding = DatetimePickerBinding.inflate(LayoutInflater.from(context), this, true);

        // 初始化日期选择器
        mDateSpinner = binding.date;
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // 初始化小时选择器
        mHourSpinner = binding.hour;
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);
        // 初始化分钟选择器
        mMinuteSpinner = binding.minute;
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // 初始化上午/下午选择器
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();
        mAmPmSpinner = binding.amPm;
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 更新控件到初始状态
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        // 设置24小时制显示模式
        set24HourView(is24HourView);

        // 设置当前时间
        setCurrentDate(date);

        // 设置启用状态
        setEnabled(isEnabled());

        // 设置内容描述
        mInitialising = false;
    }

    /**
     * 设置启用状态
     * 
     * 设置所有NumberPicker组件的启用状态，控制用户是否可以修改日期时间。
     * 
     * @param enabled true表示启用，false表示禁用
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    /**
     * 获取启用状态
     * 
     * @return true表示已启用，false表示已禁用
     */
    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * 获取当前日期时间（毫秒）
     * 
     * @return 当前日期时间，以毫秒为单位的时间戳
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 设置当前日期时间
     * 
     * @param date 要设置的日期时间，以毫秒为单位的时间戳
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 设置当前日期时间
     * 
     * @param year 年份
     * @param month 月份（0-11）
     * @param dayOfMonth 日（1-31）
     * @param hourOfDay 小时（0-23）
     * @param minute 分钟（0-59）
     */
    public void setCurrentDate(int year, int month,
            int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    /**
     * 获取当前年份
     * 
     * @return 当前年份
     */
    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    /**
     * 设置当前年份
     * 
     * @param year 要设置的年份
     */
    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前月份
     * 
     * @return 当前月份（0-11）
     */
    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    /**
     * 设置当前月份
     * 
     * @param month 要设置的月份（0-11）
     */
    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前日
     * 
     * @return 当前日（1-31）
     */
    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 设置当前日
     * 
     * @param dayOfMonth 要设置的日（1-31）
     */
    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前小时（24小时制）
     * 
     * @return 当前小时（0-23）
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取当前小时（根据显示模式）
     * 
     * 在24小时制下返回0-23，在12小时制下返回1-12
     * 
     * @return 当前小时
     */
    private int getCurrentHour() {
        if (mIs24HourView){
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    /**
     * 设置当前小时（24小时制）
     * 
     * @param hourOfDay 要设置的小时（0-23）
     */
    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
        if (!mIs24HourView) {
            // 处理12小时制下的上午/下午状态
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    /**
     * 获取当前分钟
     * 
     * @return 当前分钟（0-59）
     */
    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    /**
     * 设置当前分钟
     * 
     * @param minute 要设置的分钟（0-59）
     */
    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    /**
     * 判断是否为24小时制显示
     * 
     * @return true表示24小时制，false表示12小时制
     */
    public boolean is24HourView () {
        return mIs24HourView;
    }

    /**
     * 设置显示模式
     * 
     * @param is24HourView true表示使用24小时制，false表示使用12小时制
     */
    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        // 根据显示模式显示或隐藏上午/下午选择器
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);
        int hour = getCurrentHourOfDay();
        updateHourControl();
        setCurrentHour(hour);
        updateAmPmControl();
    }

    /**
     * 更新日期选择器显示
     * 
     * 根据当前日期更新日期选择器的显示值，显示前后3天，共7天的日期。
     * 每个日期的格式为"MM.dd EEEE"（月.日 星期）。
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        // 设置为当前日期的前4天
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);
        mDateSpinner.setDisplayedValues(null);
        // 生成7天的日期显示值
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        // 设置当前选中项为中间项
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);
        mDateSpinner.invalidate();
    }

    /**
     * 更新上午/下午选择器显示
     * 
     * 根据当前显示模式和上午/下午状态更新上午/下午选择器的可见性和选中值。
     * 在24小时制下隐藏上午/下午选择器，在12小时制下显示并设置当前选中值。
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            // 24小时制下隐藏上午/下午选择器
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            // 12小时制下显示上午/下午选择器
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新小时选择器范围
     * 
     * 根据当前显示模式更新小时选择器的最小值和最大值。
     * 24小时制：0-23，12小时制：1-12。
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /**
     * 设置日期时间变更监听器
     * 
     * @param callback 日期时间变更监听器，如果为null则不执行任何操作
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 触发日期时间变更事件
     * 
     * 如果设置了监听器，则通知监听器日期时间已发生变化。
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}
