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

package net.micode.notes.tool;

/**
 * Google Tasks 字符串常量工具类
 * <p>
 * 定义与 Google Tasks 同步相关的所有 JSON 字段名称和常量。
 * 包括操作类型、实体类型、文件夹名称等常量定义。
 * </p>
 */
public class GTaskStringUtils {

    /** 操作 ID */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    /** 操作列表 */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    /** 操作类型 */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    /** 创建操作类型 */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    /** 获取所有操作类型 */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    /** 移动操作类型 */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    /** 更新操作类型 */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    /** 创建者 ID */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    /** 子实体 */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    /** 客户端版本 */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    /** 完成状态 */
    public final static String GTASK_JSON_COMPLETED = "completed";

    /** 当前列表 ID */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    /** 默认列表 ID */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    /** 删除标记 */
    public final static String GTASK_JSON_DELETED = "deleted";

    /** 目标列表 */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    /** 目标父节点 */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    /** 目标父节点类型 */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    /** 实体增量 */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    /** 实体类型 */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    /** 获取已删除标记 */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    /** ID */
    public final static String GTASK_JSON_ID = "id";

    /** 索引 */
    public final static String GTASK_JSON_INDEX = "index";

    /** 最后修改时间 */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    /** 最新同步点 */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    /** 列表 ID */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    /** 列表集合 */
    public final static String GTASK_JSON_LISTS = "lists";

    /** 名称 */
    public final static String GTASK_JSON_NAME = "name";

    /** 新 ID */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    /** 笔记集合 */
    public final static String GTASK_JSON_NOTES = "notes";

    /** 父节点 ID */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    /** 前一个兄弟节点 ID */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    /** 结果集合 */
    public final static String GTASK_JSON_RESULTS = "results";

    /** 源列表 */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    /** 任务集合 */
    public final static String GTASK_JSON_TASKS = "tasks";

    /** 类型 */
    public final static String GTASK_JSON_TYPE = "type";

    /** 分组类型 */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    /** 任务类型 */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    /** 用户信息 */
    public final static String GTASK_JSON_USER = "user";

    /** MIUI 文件夹前缀 */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /** 默认文件夹名称 */
    public final static String FOLDER_DEFAULT = "Default";

    /** 通话记录文件夹名称 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /** 元数据文件夹名称 */
    public final static String FOLDER_META = "METADATA";

    /** 元数据 GTask ID 头 */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /** 元数据笔记头 */
    public final static String META_HEAD_NOTE = "meta_note";

    /** 元数据头 */
    public final static String META_HEAD_DATA = "meta_data";

    /** 元数据笔记名称 */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";
}
