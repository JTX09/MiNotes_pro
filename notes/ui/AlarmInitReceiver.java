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

import android.app.AlarmManager;      // 系统闹钟管理器，用于设置和管理系统级闹钟
import android.app.PendingIntent;    // 延迟意图，用于在指定时间触发操作
import android.content.BroadcastReceiver; // 广播接收器基类，用于接收系统广播
import android.content.ContentUris;   // 用于处理内容URI的工具类
import android.content.Context;      // 应用上下文，提供访问应用环境和资源的接口
import android.content.Intent;       // 意图，用于组件间通信
import android.database.Cursor;      // 数据库游标，用于遍历查询结果

import net.micode.notes.data.Notes;          // 笔记数据相关类
import net.micode.notes.data.Notes.NoteColumns; // 笔记表列定义

/**
 * 闹钟初始化接收器
 * 
 * 这个类继承自BroadcastReceiver，用于在系统启动或应用需要时重新初始化所有未触发的笔记提醒闹钟。
 * 它会查询数据库中所有设置了提醒时间且未过期的笔记，并为每个笔记设置系统闹钟。
 * 
 * 主要触发时机：
 * 1. 系统启动完成时（接收BOOT_COMPLETED广播）
 * 2. 应用安装或更新后可能需要手动触发
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * 数据库查询投影，指定需要从笔记表中获取的列
     * 只需要ID和提醒日期两列，用于设置闹钟
     */
    private static final String [] PROJECTION = new String [] {
        NoteColumns.ID,            // 笔记ID
        NoteColumns.ALERTED_DATE   // 提醒日期
    };

    // 列索引常量，用于从查询结果中获取对应列的数据
    private static final int COLUMN_ID                = 0; // ID列在结果集中的索引
    private static final int COLUMN_ALERTED_DATE      = 1; // 提醒日期列在结果集中的索引

    /**
     * 接收广播后的处理方法
     * 
     * 当接收到广播（通常是系统启动完成广播）时，此方法会被调用。
     * 它会查询所有未过期的笔记提醒，并为每个笔记设置系统闹钟。
     * 
     * @param context 应用上下文，用于访问系统服务和资源
     * @param intent 接收到的广播意图
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前系统时间，作为查询条件
        long currentDate = System.currentTimeMillis();
        
        // 查询所有提醒时间晚于当前时间的笔记
        // 查询条件：提醒日期 > 当前时间 AND 笔记类型 = 普通笔记
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,  // 指定查询的列
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE, // 查询条件
                new String[] { String.valueOf(currentDate) }, // 查询参数
                null); // 排序方式，null表示默认排序

        // 处理查询结果
        if (c != null) {
            // 如果有查询结果，遍历所有符合条件的笔记
            if (c.moveToFirst()) {
                do {
                    // 获取笔记的提醒时间
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    
                    // 创建一个指向AlarmReceiver的Intent，用于在闹钟触发时接收广播
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 将笔记ID作为URI数据附加到Intent中，这样AlarmReceiver就能知道是哪个笔记的闹钟触发了
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));
                    
                    // 创建PendingIntent，它封装了上述Intent，可以在指定时间触发
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, PendingIntent.FLAG_IMMUTABLE);
                    
                    // 获取系统闹钟服务
                    AlarmManager alermManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
                    
                    // 设置闹钟
                    // 使用RTC_WAKEUP模式，即使设备处于睡眠状态也会唤醒设备并触发广播
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);
                } while (c.moveToNext()); // 移动到下一条记录
            }
            // 关闭游标，释放资源
            c.close();
        }
    }
}