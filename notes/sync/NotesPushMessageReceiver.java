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

package net.micode.notes.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 推送消息接收器
 * <p>
 * 接收云端推送的同步通知消息，触发本地同步操作。
 * </p>
 */
public class NotesPushMessageReceiver extends BroadcastReceiver {

    private static final String TAG = "NotesPushMessageReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received push message: " + action);

        if ("com.alibaba.push2.action.NOTIFICATION_OPENED".equals(action)) {
            handleNotificationOpened(context, intent);
        } else if ("com.alibaba.push2.action.MESSAGE_RECEIVED".equals(action)) {
            handleMessageReceived(context, intent);
        }
    }

    private void handleNotificationOpened(Context context, Intent intent) {
        Log.d(TAG, "Notification opened");
        // TODO: Handle notification open action
    }

    private void handleMessageReceived(Context context, Intent intent) {
        Log.d(TAG, "Message received");
        // Check if this is a sync message
        String messageAction = intent.getStringExtra("action");
        if ("sync".equals(messageAction)) {
            Log.d(TAG, "Sync action received, broadcasting sync intent");
            Intent syncIntent = new Intent(SyncConstants.ACTION_SYNC);
            context.sendBroadcast(syncIntent);
        }
    }
}
