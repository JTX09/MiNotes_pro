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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人信息查询工具类
 * <p>
 * 提供根据电话号码查询联系人姓名的功能，使用缓存机制提高查询效率。
 * 通过Android系统的ContactsContract Provider查询联系人信息。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>根据电话号码查询联系人姓名</li>
 * <li>使用HashMap缓存已查询的联系人信息，避免重复查询</li>
 * <li>支持国际号码格式的匹配</li>
 * </ul>
 * </p>
 * <p>
 * 使用场景：
 * 当笔记中包含电话号码时，使用此类查询对应的联系人姓名并显示。
 * </p>
 * 
 * @see ContactsContract
 * @see PhoneNumberUtils
 */
public class Contact {
    /**
     * 联系人信息缓存
     * <p>
     * 使用HashMap存储已查询的电话号码和对应的联系人姓名，
     * 避免重复查询系统联系人数据库，提高性能。
     * </p>
     * Key: 电话号码
     * Value: 联系人姓名
     */
    private static HashMap<String, String> sContactCache;
    /**
     * 日志标签
     */
    private static final String TAG = "Contact";

    /**
     * 查询联系人的SQL选择条件
     * <p>
     * 使用PHONE_NUMBERS_EQUAL函数进行号码匹配，支持国际号码格式。
     * 只查询电话号码类型的数据（Phone.CONTENT_ITEM_TYPE）。
     * 使用min_match='+'进行最小匹配，提高查询效率。
     * </p>
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名
     * <p>
     * 首先检查缓存中是否已存在该号码对应的联系人姓名，
     * 如果存在则直接返回，否则查询系统联系人数据库。
     * 查询结果会被缓存以提高后续查询效率。
     * </p>
     * 
     * @param context 应用上下文，用于访问ContentResolver
     * @param phoneNumber 要查询的电话号码
     * @return 联系人姓名，如果未找到则返回null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 初始化缓存
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 检查缓存中是否已存在
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 构建查询条件，使用toCallerIDMinMatch进行号码最小匹配
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 处理查询结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
