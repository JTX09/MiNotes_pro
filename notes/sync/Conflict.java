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

import net.micode.notes.model.CloudNote;
import net.micode.notes.model.WorkingNote;

/**
 * 冲突数据模型
 * <p>
 * 表示本地笔记和云端笔记的冲突
 * </p>
 */
public class Conflict {

    private WorkingNote mLocalNote;
    private CloudNote mCloudNote;
    private String mNoteId;
    private long mConflictTime;
    private ConflictType mType;

    public enum ConflictType {
        BOTH_MODIFIED,     // 双方都修改过
        VERSION_MISMATCH   // 版本号不匹配
    }

    public Conflict(WorkingNote localNote, CloudNote cloudNote) {
        this(localNote, cloudNote, ConflictType.BOTH_MODIFIED);
    }

    public Conflict(WorkingNote localNote, CloudNote cloudNote, ConflictType type) {
        mLocalNote = localNote;
        mCloudNote = cloudNote;
        mNoteId = String.valueOf(localNote.getNoteId());
        mConflictTime = System.currentTimeMillis();
        mType = type;
    }

    public WorkingNote getLocalNote() {
        return mLocalNote;
    }

    public CloudNote getCloudNote() {
        return mCloudNote;
    }

    public String getNoteId() {
        return mNoteId;
    }

    public long getConflictTime() {
        return mConflictTime;
    }

    public ConflictType getType() {
        return mType;
    }

    /**
     * 获取冲突描述
     */
    public String getConflictDescription() {
        return "本地修改时间: " + mLocalNote.getModifiedDate() +
               "\n云端修改时间: " + mCloudNote.getModifiedTime();
    }

    /**
     * 获取本地标题预览
     */
    public String getLocalTitle() {
        return mLocalNote.getTitle();
    }

    /**
     * 获取云端标题预览
     */
    public String getCloudTitle() {
        return mCloudNote.getTitle();
    }

    /**
     * 获取本地内容预览（前100字符）
     */
    public String getLocalContentPreview() {
        String content = mLocalNote.getContent();
        if (content == null) return "";
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }

    /**
     * 获取云端内容预览（前100字符）
     */
    public String getCloudContentPreview() {
        String content = mCloudNote.getContent();
        if (content == null) return "";
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }
}
