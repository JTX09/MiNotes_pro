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

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析工具类
 * <p>
 * 提供笔记背景颜色、字体大小、Widget 样式等资源的解析和获取功能。
 * 支持多种颜色主题和字体大小的配置。
 * </p>
 */
public class ResourceParser {

    /** 黄色背景 */
    public static final int YELLOW           = 0;
    
    /** 蓝色背景 */
    public static final int BLUE             = 1;
    
    /** 白色背景 */
    public static final int WHITE            = 2;
    
    /** 绿色背景 */
    public static final int GREEN            = 3;
    
    /** 红色背景 */
    public static final int RED              = 4;
    
    // New Presets
    public static final int MIDNIGHT_BLACK   = 5;
    public static final int EYE_CARE_GREEN   = 6;
    public static final int WARM             = 7;
    public static final int COOL             = 8;
    
    // Gradient Presets
    public static final int SUNSET           = 9;
    public static final int OCEAN            = 10;
    public static final int FOREST           = 11;
    public static final int LAVENDER         = 12;

    /** 自定义颜色按钮 ID (用于 UI 显示) */
    public static final int CUSTOM_COLOR_BUTTON_ID = -100;
    /** 壁纸按钮 ID (用于 UI 显示) */
    public static final int WALLPAPER_BUTTON_ID = -101;

    /** 默认背景颜色 */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    public static int getNoteBgColor(Context context, int id) {
        if (id < 0) {
            return id; // Custom color (ARGB)
        }
        switch (id) {
            case YELLOW: return context.getColor(R.color.bg_yellow);
            case BLUE:   return context.getColor(R.color.bg_blue);
            case WHITE:  return context.getColor(R.color.bg_white);
            case GREEN:  return context.getColor(R.color.bg_green);
            case RED:    return context.getColor(R.color.bg_red);
            case MIDNIGHT_BLACK: return context.getColor(R.color.bg_midnight_black);
            case EYE_CARE_GREEN: return context.getColor(R.color.bg_eye_care_green);
            case WARM:   return context.getColor(R.color.bg_warm);
            case COOL:   return context.getColor(R.color.bg_cool);
            default: return context.getColor(R.color.bg_white);
        }
    }


    /** 小号字体 */
    public static final int TEXT_SMALL       = 0;
    
    /** 中号字体 */
    public static final int TEXT_MEDIUM      = 1;
    
    /** 大号字体 */
    public static final int TEXT_LARGE       = 2;
    
    /** 超大号字体 */
    public static final int TEXT_SUPER       = 3;

    /** 默认字体大小 */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 笔记背景资源类
     * <p>
     * 提供笔记编辑页面的背景颜色资源。
     * 包含编辑区域背景和标题栏背景两种资源。
     * </p>
     */
    public static class NoteBgResources {
        /** 编辑区域背景资源数组 */
        private final static int [] BG_EDIT_RESOURCES = new int [] {
            R.drawable.edit_yellow,
            R.drawable.edit_blue,
            R.drawable.edit_white,
            R.drawable.edit_green,
            R.drawable.edit_red,
            R.color.bg_midnight_black,
            R.color.bg_eye_care_green,
            R.color.bg_warm,
            R.color.bg_cool,
            R.drawable.preset_sunset,
            R.drawable.preset_ocean,
            R.drawable.preset_forest,
            R.drawable.preset_lavender
        };

        /** 标题栏背景资源数组 */
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
            R.drawable.edit_title_yellow,
            R.drawable.edit_title_blue,
            R.drawable.edit_title_white,
            R.drawable.edit_title_green,
            R.drawable.edit_title_red
        };

        /**
         * 获取笔记编辑区域背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 背景资源 ID
         */
        public static int getNoteBgResource(int id) {
            if (id >= BG_EDIT_RESOURCES.length || id < 0) {
                return R.drawable.edit_white;
            }
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 获取笔记标题栏背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 标题栏背景资源 ID
         */
        public static int getNoteTitleBgResource(int id) {
            if (id >= BG_EDIT_TITLE_RESOURCES.length || id < 0) {
                return R.drawable.edit_title_white;
            }
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认背景颜色 ID
     * <p>
     * 根据用户设置返回默认背景颜色。
     * 如果用户启用了随机背景颜色，则随机返回一个颜色 ID。
     * </p>
     * 
     * @param context 应用上下文
     * @return 背景颜色 ID（0-4）
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 随机选择背景颜色
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 笔记列表项背景资源类
     * <p>
     * 提供笔记列表项的背景颜色资源。
     * 包含首项、中间项、末项和单项四种样式。
     * </p>
     */
    public static class NoteItemBgResources {
        /** 首项背景资源数组 */
        private final static int [] BG_FIRST_RESOURCES = new int [] {
            R.drawable.list_yellow_up,
            R.drawable.list_blue_up,
            R.drawable.list_white_up,
            R.drawable.list_green_up,
            R.drawable.list_red_up
        };

        /** 中间项背景资源数组 */
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
            R.drawable.list_yellow_middle,
            R.drawable.list_blue_middle,
            R.drawable.list_white_middle,
            R.drawable.list_green_middle,
            R.drawable.list_red_middle
        };

        /** 末项背景资源数组 */
        private final static int [] BG_LAST_RESOURCES = new int [] {
            R.drawable.list_yellow_down,
            R.drawable.list_blue_down,
            R.drawable.list_white_down,
            R.drawable.list_green_down,
            R.drawable.list_red_down,
        };

        /** 单项背景资源数组 */
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
            R.drawable.list_yellow_single,
            R.drawable.list_blue_single,
            R.drawable.list_white_single,
            R.drawable.list_green_single,
            R.drawable.list_red_single
        };

        /**
         * 获取笔记列表首项背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 首项背景资源 ID
         */
        public static int getNoteBgFirstRes(int id) {
            if (id >= BG_FIRST_RESOURCES.length || id < 0) return R.drawable.list_white_up;
            return BG_FIRST_RESOURCES[id];
        }

        /**
         * 获取笔记列表末项背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 末项背景资源 ID
         */
        public static int getNoteBgLastRes(int id) {
            if (id >= BG_LAST_RESOURCES.length || id < 0) return R.drawable.list_white_down;
            return BG_LAST_RESOURCES[id];
        }

        /**
         * 获取笔记列表单项背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 单项背景资源 ID
         */
        public static int getNoteBgSingleRes(int id) {
            if (id >= BG_SINGLE_RESOURCES.length || id < 0) return R.drawable.list_white_single;
            return BG_SINGLE_RESOURCES[id];
        }

        /**
         * 获取笔记列表中间项背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 中间项背景资源 ID
         */
        public static int getNoteBgNormalRes(int id) {
            if (id >= BG_NORMAL_RESOURCES.length || id < 0) return R.drawable.list_white_middle;
            return BG_NORMAL_RESOURCES[id];
        }

        /**
         * 获取文件夹背景资源 ID
         * 
         * @return 文件夹背景资源 ID
         */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * Widget 背景资源类
     * <p>
     * 提供桌面 Widget 的背景颜色资源。
     * 支持 2x2 和 4x4 两种尺寸的 Widget。
     * </p>
     */
    public static class WidgetBgResources {
        /** 2x2 Widget 背景资源数组 */
        private final static int [] BG_2X_RESOURCES = new int [] {
            R.drawable.widget_2x_yellow,
            R.drawable.widget_2x_blue,
            R.drawable.widget_2x_white,
            R.drawable.widget_2x_green,
            R.drawable.widget_2x_red,
        };

        /**
         * 获取 2x2 Widget 背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 2x2 Widget 背景资源 ID
         */
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        /** 4x4 Widget 背景资源数组 */
        private final static int [] BG_4X_RESOURCES = new int [] {
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        };

        /**
         * 获取 4x4 Widget 背景资源 ID
         * 
         * @param id 背景颜色 ID（0-4）
         * @return 4x4 Widget 背景资源 ID
         */
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 文本外观资源类
     * <p>
     * 提供笔记文本的字体样式资源。
     * 支持四种字体大小：小、中、大、超大。
     * </p>
     */
    public static class TextAppearanceResources {
        /** 文本外观样式资源数组 */
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
            R.style.TextAppearanceNormal,
            R.style.TextAppearanceMedium,
            R.style.TextAppearanceLarge,
            R.style.TextAppearanceSuper
        };

        /**
         * 获取文本外观样式资源 ID
         * <p>
         * 如果 ID 超出范围，则返回默认字体大小。
         * </p>
         * 
         * @param id 字体大小 ID（0-3）
         * @return 文本外观样式资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: 修复在 SharedPreferences 中存储资源 ID 的 bug。
             * ID 可能大于资源数组的长度，在这种情况下，
             * 返回 {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取文本外观资源数量
         * 
         * @return 资源数量
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}
