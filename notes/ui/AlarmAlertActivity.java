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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

import net.micode.notes.tool.LocaleHelper;

/**
 * 闹钟提醒活动
 * 
 * 这个类负责显示笔记提醒的闹钟界面，当笔记设置的提醒时间到达时，
 * 由AlarmReceiver启动此活动，显示笔记内容摘要并播放闹钟声音。
 * 
 * 主要功能：
 * 1. 在锁屏状态下显示闹钟界面
 * 2. 显示笔记内容摘要
 * 3. 播放系统闹钟声音
 * 4. 提供操作选项（关闭提醒或查看笔记）
 * 
 * @see NoteEditActivity
 * @see net.micode.notes.tool.DataUtils
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }
    // 当前提醒的笔记ID
    private long mNoteId;
    // 笔记内容摘要
    private String mSnippet;
    // 摘要预览最大长度
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    // 媒体播放器，用于播放闹钟声音
    MediaPlayer mPlayer;
    // 笔记类型
    private int mNoteType;

    /**
     * 活动创建时的初始化方法
     * 
     * 设置窗口属性，获取笔记信息，检查笔记是否存在，
     * 如果存在则显示提醒对话框并播放闹钟声音
     * 
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 请求无标题窗口
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // 添加FLAG_SHOW_WHEN_LOCKED标志，使活动可以在锁屏界面上显示
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕当前是关闭状态，添加以下标志
        if (!isScreenOn()) {
            // 保持屏幕常亮
            // 打开屏幕
            // 允许在屏幕亮起时锁定
            // 设置窗口布局包含系统装饰区域
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        // 获取启动此活动的Intent
        Intent intent = getIntent();

        try {
            // 从Intent中解析出笔记ID
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            // 通过笔记ID获取笔记内容摘要
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            // 获取笔记类型
            mNoteType = DataUtils.getNoteTypeById(this.getContentResolver(), mNoteId);
            // 如果摘要超过最大长度，截取并添加省略号
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        // 初始化媒体播放器
        mPlayer = new MediaPlayer();
        // 检查笔记是否在数据库中存在且可见
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, mNoteType)) {
            // 显示操作对话框
            showActionDialog();
            // 播放闹钟声音
            playAlarmSound();
        } else {
            // 如果笔记不存在，直接关闭活动
            finish();
        }
    }

    /**
     * 检查屏幕是否处于开启状态
     * 
     * @return 如果屏幕开启返回true，否则返回false
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放闹钟声音
     * 
     * 获取系统默认的闹钟铃声，设置音频流类型，
     * 并循环播放闹钟声音
     */
    private void playAlarmSound() {
        // 获取系统默认的闹钟铃声URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取受静音模式影响的音频流类型
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 如果闹钟音频流受静音模式影响，使用受影响的流类型
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            // 否则使用标准闹钟音频流
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            // 设置音频源
            mPlayer.setDataSource(this, url);
            // 准备播放
            mPlayer.prepare();
            // 设置循环播放
            mPlayer.setLooping(true);
            // 开始播放
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示操作对话框
     * 
     * 创建一个AlertDialog，显示笔记摘要和操作按钮
     * 当屏幕开启时，显示"查看笔记"按钮
     */
    private void showActionDialog() {
        // 创建AlertDialog构建器
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        // 设置对话框标题为应用名称
        dialog.setTitle(R.string.app_name);
        // 设置对话框内容为笔记摘要
        dialog.setMessage(mSnippet);
        // 添加"确定"按钮，点击事件由当前类处理
        dialog.setPositiveButton(R.string.notealert_ok, this);
        // 如果屏幕是开启状态，添加"查看笔记"按钮
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        // 显示对话框并设置关闭监听器
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击事件处理
     * 
     * 处理用户在提醒对话框中的按钮点击操作，根据点击的按钮执行相应的操作
     * 
     * @param dialog 触发点击事件的对话框对象，不能为 null
     * @param which 点击的按钮ID，取值为 DialogInterface.BUTTON_POSITIVE（确定按钮）
     *              或 DialogInterface.BUTTON_NEGATIVE（查看笔记按钮）
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            // 如果点击了"查看笔记"按钮（负按钮）
            case DialogInterface.BUTTON_NEGATIVE:
                Intent intent;
                if (mNoteType == Notes.TYPE_TASK) {
                    // 如果是待办任务，跳转到笔记编辑活动（任务功能已合并）
                    intent = new Intent(this, NoteEditActivity.class);
                    intent.putExtra(Intent.EXTRA_UID, mNoteId);
                } else {
                    // 创建跳转到笔记编辑活动的Intent
                    intent = new Intent(this, NoteEditActivity.class);
                    // 设置动作为查看
                    intent.setAction(Intent.ACTION_VIEW);
                    // 传递笔记ID
                    intent.putExtra(Intent.EXTRA_UID, mNoteId);
                }
                // 启动活动
                startActivity(intent);
                break;
            // 默认情况（点击"确定"按钮）
            default:
                break;
        }
    }

    /**
     * 对话框关闭事件处理
     * 
     * 当对话框被关闭时（无论是点击按钮还是外部点击），
     * 停止闹钟声音并关闭当前活动
     * 
     * @param dialog 被关闭的对话框对象，不能为 null
     */
    public void onDismiss(DialogInterface dialog) {
        // 停止闹钟声音
        stopAlarmSound();
        // 关闭当前活动
        finish();
    }

    /**
     * 停止闹钟声音
     * 
     * 停止媒体播放器，释放资源并将播放器对象置空
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            // 停止播放
            mPlayer.stop();
            // 释放资源
            mPlayer.release();
            // 将播放器对象置空
            mPlayer = null;
        }
    }
}