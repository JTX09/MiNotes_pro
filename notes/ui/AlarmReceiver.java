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

import android.content.BroadcastReceiver; // 广播接收器基类，用于接收系统广播
import android.content.Context;           // 应用上下文，提供访问应用环境和资源的接口
import android.content.Intent;            // 意图，用于组件间通信

/**
 * 闹钟接收器
 * 
 * 这个类继承自BroadcastReceiver，用于接收由AlarmManager设置的闹钟触发事件。
 * 当笔记的提醒时间到达时，AlarmManager会发送一个广播，这个接收器会接收该广播
 * 并启动闹钟提醒界面(AlarmAlertActivity)来显示提醒信息。
 * 
 * 工作流程：
 * 1. AlarmInitReceiver为每个设置了提醒时间的笔记设置系统闹钟
 * 2. 当提醒时间到达时，系统发送广播
 * 3. AlarmReceiver接收广播并启动AlarmAlertActivity显示提醒
 */
public class AlarmReceiver extends BroadcastReceiver {
    
    /**
     * 接收闹钟广播后的处理方法
     * 
     * 当闹钟时间到达时，系统会发送广播，此方法会被调用。
     * 它会将接收到的Intent重新定向到AlarmAlertActivity，并添加FLAG_ACTIVITY_NEW_TASK标志
     * 确保即使在非UI上下文中也能启动Activity。
     * 
     * @param context 应用上下文，用于启动Activity
     * @param intent 接收到的闹钟广播Intent，包含触发闹钟的笔记ID等信息
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 将Intent的目标组件设置为AlarmAlertActivity
        // 这样当启动Activity时就会显示闹钟提醒界面
        intent.setClass(context, AlarmAlertActivity.class);
        
        // 添加FLAG_ACTIVITY_NEW_TASK标志
        // 这是必需的，因为从非Activity上下文(如BroadcastReceiver)启动Activity时，
        // 必须指定这个标志，表示启动一个新的任务栈
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        // 启动AlarmAlertActivity显示闹钟提醒
        // 原始Intent中包含了触发闹钟的笔记ID等信息，AlarmAlertActivity会使用这些信息
        // 来显示相应的笔记内容和提醒信息
        context.startActivity(intent);
    }
}