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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.net.Uri;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.NoteCommand;
import net.micode.notes.model.UndoRedoManager;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ImageExportHelper;
import net.micode.notes.tool.PdfExportHelper;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import net.micode.notes.databinding.DialogBackgroundSelectorBinding;
import net.micode.notes.databinding.DialogColorPickerBinding;
import net.micode.notes.databinding.NoteEditBinding;
import net.micode.notes.tool.RichTextHelper;
import net.micode.notes.tool.SmartParser;


import net.micode.notes.data.FontManager;

import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.io.File;
import java.util.ArrayList;

public class NoteEditActivity extends BaseActivity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    /**
     * 笔记头部视图持有者
     * <p>
     * 持有笔记编辑界面头部区域的UI组件引用，包括修改时间、提醒图标、提醒日期和背景颜色设置按钮。
     * </p>
     */
    private class HeadViewHolder {
        public TextView tvModified;

        public ImageView ivAlertIcon;

        public TextView tvAlertDate;

        public ImageView ibSetBgColor;
        
        public TextView tvCharCount;
        
        public EditText etTitle;
    }

    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    private HeadViewHolder mNoteHeaderHolder;

    private View mHeadViewPanel;

    private View mNoteBgColorSelector;

    private View mFontSizeSelector;

    private View mRichTextSelector;

    private EditText mNoteEditor;

    private View mNoteEditorPanel;

    private WorkingNote mWorkingNote;

    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;

    private MaterialToolbar toolbar;

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    private LinearLayout mEditTextList;

    private String mUserQuery;
    private Pattern mPattern;

    private NoteEditBinding binding;

    private UndoRedoManager mUndoRedoManager;
    private boolean mInUndoRedo = false;

    private NoteColorAdapter mColorAdapter;

    /**
     * 辅助方法：根据ID获取字体选择视图
     */
    private View getFontSelectorView(int viewId) {
        switch (viewId) {
            case R.id.iv_small_select:
                return binding.ivSmallSelect;
            case R.id.iv_medium_select:
                return binding.ivMediumSelect;
            case R.id.iv_large_select:
                return binding.ivLargeSelect;
            case R.id.iv_super_select:
                return binding.ivSuperSelect;
            default:
                throw new IllegalArgumentException("Unknown view ID: " + viewId);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 使用ViewBinding设置布局
        binding = NoteEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mUndoRedoManager = new UndoRedoManager();

        // 初始化Toolbar（使用MaterialToolbar，与列表页面一致）
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * 恢复活动状态
     * <p>
     * 当系统内存不足导致活动被杀死时，重新加载活动需要恢复之前的状态。
     * 从保存的实例状态中恢复笔记ID，并重新初始化活动状态。
     * </p>
     * @param savedInstanceState 包含之前保存状态的Bundle对象
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    /**
     * 初始化活动状态
     * <p>
     * 根据传入的Intent初始化活动状态，支持以下操作：
     * <ul>
     * <li>ACTION_VIEW: 查看现有笔记，支持从搜索结果打开</li>
     * <li>ACTION_INSERT_OR_EDIT: 创建新笔记或编辑笔记，支持通话记录笔记</li>
     * </ul>
     * </p>
     * @param intent 包含操作类型和参数的Intent对象
     * @return 初始化成功返回true，失败返回false
     */
    private boolean initActivityState(Intent intent) {
        /**
         * If the user specified the {@link Intent#ACTION_VIEW} but not provided with id,
         * then jump to the NotesListActivity
         */
        mWorkingNote = null;
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            /**
             * Starting from the searched result
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // Parse call-record note
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * 初始化资源
     * <p>
     * 初始化笔记编辑界面的所有UI组件引用和点击监听器
     * </p>
     */
    private void initResources() {
        mHeadViewPanel = binding.cvEditorSurface;
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = binding.tvModifiedDate;
        mNoteHeaderHolder.ivAlertIcon = binding.ivAlertIcon;
        mNoteHeaderHolder.tvAlertDate = binding.tvAlertDate;
        mNoteHeaderHolder.ibSetBgColor = binding.btnSetBgColor;
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        mNoteHeaderHolder.tvCharCount = binding.tvCharCount;
        mNoteHeaderHolder.etTitle = binding.etTitle;

        mNoteEditor = binding.noteEditView;
        mNoteEditorPanel = binding.cvEditorSurface;
        mNoteBgColorSelector = binding.noteBgColorSelector;

        mNoteEditor.addTextChangedListener(new TextWatcher() {
            private CharSequence mBeforeText;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!mInUndoRedo) {
                    mBeforeText = s.subSequence(start, start + count).toString();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mNoteHeaderHolder != null && mNoteHeaderHolder.tvCharCount != null) {
                    mNoteHeaderHolder.tvCharCount.setText(String.valueOf(s.length()) + " 字");
                }
                if (!mInUndoRedo) {
                    CharSequence afterText = s.subSequence(start, start + count).toString();
                    if (!TextUtils.equals(mBeforeText, afterText)) {
                        mUndoRedoManager.addCommand(new NoteCommand(mNoteEditor, start, mBeforeText, afterText));
                        invalidateOptionsMenu();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        
        // Title TextWatcher
        mNoteHeaderHolder.etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // We can mark note as modified here if needed, or just let saveNote handle it
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Update WorkingNote title
                // mWorkingNote.setTitle(s.toString()); 
                // Actually we should wait until saveNote to sync everything or sync immediately?
                // The original logic syncs content only on save. Let's do the same for title.
            }
        });

        // Initialize Color Adapter
        java.util.List<Integer> colors = java.util.Arrays.asList(
                ResourceParser.YELLOW,
                ResourceParser.BLUE,
                ResourceParser.WHITE,
                ResourceParser.GREEN,
                ResourceParser.RED,
                ResourceParser.MIDNIGHT_BLACK,
                ResourceParser.EYE_CARE_GREEN,
                ResourceParser.WARM,
                ResourceParser.COOL,
                ResourceParser.SUNSET,
                ResourceParser.OCEAN,
                ResourceParser.FOREST,
                ResourceParser.LAVENDER,
                ResourceParser.CUSTOM_COLOR_BUTTON_ID,
                ResourceParser.WALLPAPER_BUTTON_ID
        );
        mColorAdapter = new NoteColorAdapter(colors, ResourceParser.YELLOW, new NoteColorAdapter.OnColorClickListener() {
            @Override
            public void onColorClick(int colorId) {
                if (colorId == ResourceParser.CUSTOM_COLOR_BUTTON_ID) {
                    showColorPickerDialog();
                } else if (colorId == ResourceParser.WALLPAPER_BUTTON_ID) {
                    pickWallpaper();
                } else {
                    mWorkingNote.setBgColorId(colorId);
                    mWorkingNote.setWallpaper(null);
                    mNoteBgColorSelector.setVisibility(View.GONE);
                }
            }
        });

        mFontSizeSelector = binding.fontSizeSelector;
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view;
            switch (id) {
                case R.id.ll_font_small:
                    view = binding.llFontSmall;
                    break;
                case R.id.ll_font_normal:
                    view = binding.llFontNormal;
                    break;
                case R.id.ll_font_large:
                    view = binding.llFontLarge;
                    break;
                case R.id.ll_font_super:
                    view = binding.llFontSuper;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown view ID: " + id);
            }
            view.setOnClickListener(this);
        }

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            String fontSizeStr = mSharedPrefs.getString(PREFERENCE_FONT_SIZE, String.valueOf(ResourceParser.BG_DEFAULT_FONT_SIZE));
            mFontSizeId = Integer.parseInt(fontSizeStr);
        } catch (ClassCastException e) {
            mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        }
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        mEditTextList = binding.noteEditList;
        initRichTextToolbar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    private static final int REQUEST_CODE_PICK_IMAGE = 106;

    /**
     * 初始化笔记编辑界面
     * <p>
     * 设置笔记编辑界面的显示内容，包括：
     * <ul>
     * <li>根据字体大小设置文本外观</li>
     * <li>根据模式（普通/清单）显示笔记内容</li>
     * <li>设置背景颜色</li>
     * <li>显示修改时间和提醒信息</li>
     * </ul>
     * </p>
     */
    private void initNoteScreen() {
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        // Apply custom font
        FontManager.getInstance(this).applyFont(mNoteEditor);

        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            String content = mWorkingNote.getContent();
            if (content.contains("<") && content.contains(">")) {
                mNoteEditor.setText(RichTextHelper.fromHtml(content, this));
            } else {
                mNoteEditor.setText(getHighlightQueryResult(content, mUserQuery));
            }
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        mNoteHeaderHolder.etTitle.setText(mWorkingNote.getTitle());
        
        // Update Color Adapter selection
        if (mColorAdapter != null) {
            mColorAdapter.setSelectedColor(mWorkingNote.getBgColorId());
        }
        
        updateNoteBackgrounds();

        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker
         * is not ready
         */
        showAlertHeader();
    }

    /**
     * 显示提醒头部信息
     * <p>
     * 根据笔记是否设置了闹钟提醒，显示或隐藏提醒图标和提醒日期。
     * 如果提醒已过期，显示过期提示；否则显示相对时间。
     * </p>
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        };
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    /**
     * 保存活动实例状态
     * <p>
     * 在活动被系统销毁前保存当前笔记的ID，以便后续恢复。
     * 如果是新笔记且尚未保存到数据库，会先保存笔记以生成ID。
     * </p>
     * @param outState 用于保存状态的Bundle对象
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * 分发触摸事件
     * <p>
     * 处理触摸事件，当用户点击背景颜色选择器或字体大小选择器外部区域时，
     * 隐藏相应的选择器面板。
     * </p>
     * @param ev 触摸事件对象
     * @return 如果事件被处理返回true，否则返回false
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }

        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 检查触摸点是否在视图范围内
     * <p>
     * 判断给定的触摸事件坐标是否位于指定视图的显示区域内。
     * </p>
     * @param view 要检查的视图
     * @param ev 触摸事件对象
     * @return 如果触摸点在视图范围内返回true，否则返回false
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int []location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
                    return false;
                }
        return true;
    }

    /**
     * 活动暂停时保存笔记
     * <p>
     * 在活动暂停时自动保存笔记内容，并清除设置状态（如打开的颜色选择器）。
     * </p>
     */
    @Override
    protected void onPause() {
        super.onPause();
        if(saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    /**
     * 更新桌面小部件
     * <p>
     * 发送广播通知桌面小部件更新，根据笔记的小部件类型（2x或4x）发送相应的更新意图。
     * </p>
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 处理点击事件
     * <p>
     * 处理各种UI组件的点击事件，包括：
     * <ul>
     * <li>背景颜色设置按钮：显示颜色选择器</li>
     * <li>背景颜色选项：设置笔记背景颜色</li>
     * <li>字体大小选项：设置编辑器字体大小</li>
     * </ul>
     * </p>
     * @param v 被点击的视图
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            showBackgroundSelector();
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            View fontView = getFontSelectorView(sFontSelectorSelectionMap.get(mFontSizeId));
            if (fontView != null) {
                fontView.setVisibility(View.GONE);
            }
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            fontView = getFontSelectorView(sFontSelectorSelectionMap.get(mFontSizeId));
            if (fontView != null) {
                fontView.setVisibility(View.VISIBLE);
            }
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
                // Apply custom font again as setTextAppearance might reset it
                FontManager.getInstance(this).applyFont(mNoteEditor);
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    /**
     * 处理返回键按下事件
     * <p>
     * 如果当前有打开的设置面板（颜色选择器或字体选择器），先关闭面板；
     * 否则保存笔记并退出活动。
     * </p>
     */
    @Override
    public void onBackPressed() {
        if(clearSettingState()) {
            return;
        }

        saveNote();
        super.onBackPressed();
    }

    /**
     * 清除设置状态
     * <p>
     * 检查并关闭所有打开的设置面板（背景颜色选择器和字体大小选择器）。
     * </p>
     * @return 如果关闭了任何面板返回true，否则返回false
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /**
     * 背景颜色改变回调
     * <p>
     * 当笔记背景颜色改变时调用，更新UI显示：
     * <ul>
     * <li>显示选中颜色的指示器</li>
     * <li>更新编辑器面板背景</li>
     * <li>更新头部面板背景</li>
     * </ul>
     * </p>
     */
    public void onBackgroundColorChanged() {
        if (mColorAdapter != null) {
            mColorAdapter.setSelectedColor(mWorkingNote.getBgColorId());
        }
        updateNoteBackgrounds();
    }

    private void updateNoteBackgrounds() {
        int colorId = mWorkingNote.getBgColorId();
        String wallpaperPath = mWorkingNote.getWallpaperPath();

        if (wallpaperPath != null) {
            binding.ivNoteWallpaper.setVisibility(View.VISIBLE);
            binding.viewBgMask.setVisibility(View.VISIBLE);
            
            android.net.Uri uri = android.net.Uri.parse(wallpaperPath);
            try {
                java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                binding.ivNoteWallpaper.setImageBitmap(bitmap);
                
                // Dynamic Coloring with Palette
                androidx.palette.graphics.Palette.from(bitmap).generate(palette -> {
                    if (palette != null) {
                         applyPaletteColors(palette);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to load wallpaper", e);
                binding.ivNoteWallpaper.setVisibility(View.GONE);
                binding.viewBgMask.setVisibility(View.GONE);
                applyColorBackground(colorId);
            }
        } else {
            binding.ivNoteWallpaper.setVisibility(View.GONE);
            binding.viewBgMask.setVisibility(View.GONE);
            applyColorBackground(colorId);
            resetToolbarColors();
        }
        updateTextColor(colorId);
    }
    
    private void applyPaletteColors(androidx.palette.graphics.Palette palette) {
        int primaryColor = palette.getDominantColor(getResources().getColor(R.color.primary_color));
        int onPrimaryColor = getResources().getColor(R.color.on_primary_color);
        int mutedColor = palette.getMutedColor(android.graphics.Color.WHITE);
        
        // Ensure contrast for onPrimaryColor
        if (androidx.core.graphics.ColorUtils.calculateContrast(onPrimaryColor, primaryColor) < 3.0) {
             onPrimaryColor = isColorDark(primaryColor) ? android.graphics.Color.WHITE : android.graphics.Color.BLACK; 
        }

        binding.toolbar.setTitleTextColor(onPrimaryColor);
        if (binding.toolbar.getNavigationIcon() != null) {
            binding.toolbar.getNavigationIcon().setTint(onPrimaryColor);
        }
        
        getWindow().setStatusBarColor(primaryColor);
        
        // Update Card Surface - semi-transparent glass effect
        int surfaceColor = androidx.core.graphics.ColorUtils.setAlphaComponent(mutedColor, 230); // 90% opacity
        binding.cvEditorSurface.setCardBackgroundColor(surfaceColor);

        // Update input text color based on surface color
        int textColor = isColorDark(surfaceColor) ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;
        mNoteEditor.setTextColor(textColor);
        if (mNoteHeaderHolder != null && mNoteHeaderHolder.etTitle != null) {
            mNoteHeaderHolder.etTitle.setTextColor(textColor);
            mNoteHeaderHolder.etTitle.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(textColor, 128));
        }
        binding.tvCharCount.setTextColor(textColor);
        binding.tvModifiedDate.setTextColor(textColor);
    }
    
    private void resetToolbarColors() {
        int primaryColor = getResources().getColor(R.color.primary_color);
        int onPrimaryColor = getResources().getColor(R.color.on_primary_color);
        binding.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        binding.toolbar.setTitleTextColor(onPrimaryColor);
        if (binding.toolbar.getNavigationIcon() != null) {
            binding.toolbar.getNavigationIcon().setTint(onPrimaryColor);
        }
        getWindow().setStatusBarColor(primaryColor);
        
        // Reset Card Surface
        binding.cvEditorSurface.setCardBackgroundColor(android.graphics.Color.parseColor("#CCFFFFFF"));
    }

    private void updateTextColor(int colorId) {
        // Default to black for light backgrounds
        int textColor = android.graphics.Color.BLACK;

        if (colorId == ResourceParser.MIDNIGHT_BLACK) {
            textColor = android.graphics.Color.WHITE;
        } else if (colorId < 0) {
            // Custom color: Calculate luminance
            if (isColorDark(colorId)) {
                textColor = android.graphics.Color.WHITE;
            }
        }
        
        // If wallpaper is set, applyPaletteColors already handled text color.
        if (mWorkingNote.getWallpaperPath() == null) {
            mNoteEditor.setTextColor(textColor);
            if (mNoteHeaderHolder != null && mNoteHeaderHolder.etTitle != null) {
                mNoteHeaderHolder.etTitle.setTextColor(textColor);
                mNoteHeaderHolder.etTitle.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(textColor, 128));
            }
            binding.tvCharCount.setTextColor(textColor);
            binding.tvModifiedDate.setTextColor(textColor);
            
            // Adjust card surface opacity for pure colors
            if (colorId == ResourceParser.WHITE) {
                binding.cvEditorSurface.setCardBackgroundColor(android.graphics.Color.WHITE);
            } else {
                binding.cvEditorSurface.setCardBackgroundColor(android.graphics.Color.parseColor("#CCFFFFFF"));
            }
        }
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * android.graphics.Color.red(color) + 
                             0.587 * android.graphics.Color.green(color) + 
                             0.114 * android.graphics.Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    private void applyColorBackground(int colorId) {
        if (colorId < 0) {
            binding.noteEditRoot.setBackgroundColor(colorId);
        } else {
            binding.noteEditRoot.setBackgroundResource(ResourceParser.NoteBgResources.getNoteBgResource(colorId));
        }
    }

    /**
     * 准备选项菜单
     * <p>
     * 根据当前笔记的状态动态设置菜单项：
     * <ul>
     * <li>通话记录笔记使用特殊菜单</li>
     * <li>清单模式下切换菜单项标题</li>
     * <li>根据是否设置提醒显示/隐藏相应菜单项</li>
     * </ul>
     * </p>
     * @param menu 选项菜单对象
     * @return 返回true表示菜单已准备好
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
            MenuItem undoItem = menu.findItem(R.id.menu_undo);
            MenuItem redoItem = menu.findItem(R.id.menu_redo);
            MenuItem clearItem = menu.findItem(R.id.menu_clear_history);
            if (undoItem != null) {
                undoItem.setEnabled(mUndoRedoManager.canUndo());
            }
            if (redoItem != null) {
                redoItem.setEnabled(mUndoRedoManager.canRedo());
            }
            if (clearItem != null) {
                clearItem.setEnabled(mUndoRedoManager.canUndo() || mUndoRedoManager.canRedo());
            }
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    /**
     * 处理选项菜单项选择
     * <p>
     * 处理各种菜单项的点击事件，包括：
     * <ul>
     * <li>新建笔记：创建新笔记</li>
     * <li>删除笔记：显示确认对话框后删除当前笔记</li>
     * <li>字体大小：显示字体大小选择器</li>
     * <li>清单模式：切换普通/清单模式</li>
     * <li>分享：分享笔记内容到其他应用</li>
     * <li>发送到桌面：创建桌面小部件</li>
     * <li>设置提醒：设置闹钟提醒</li>
     * <li>删除提醒：删除已设置的提醒</li>
     * </ul>
     * </p>
     * @param item 被选中的菜单项
     * @return 返回true表示事件已处理
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_rich_text:
                if (mRichTextSelector.getVisibility() == View.VISIBLE) {
                    mRichTextSelector.setVisibility(View.GONE);
                } else {
                    mRichTextSelector.setVisibility(View.VISIBLE);
                    mFontSizeSelector.setVisibility(View.GONE);
                }
                break;
            case R.id.menu_undo:
                mInUndoRedo = true;
                mUndoRedoManager.undo();
                mInUndoRedo = false;
                invalidateOptionsMenu();
                showToast(R.string.undo_success);
                break;
            case R.id.menu_redo:
                mInUndoRedo = true;
                mUndoRedoManager.redo();
                mInUndoRedo = false;
                invalidateOptionsMenu();
                showToast(R.string.redo_success);
                break;
            case R.id.menu_clear_history:
                mUndoRedoManager.clear();
                invalidateOptionsMenu();
                showToast(R.string.menu_clear_history);
                break;
            case R.id.menu_new_note:
                createNewNote();
                break;
            case R.id.menu_save_as_template:
                saveAsTemplate();
                break;
            case R.id.menu_picture:
                pickImage();
                break;
            case R.id.menu_delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_note));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteCurrentNote();
                                finish();
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case R.id.menu_font_size:
                mFontSizeSelector.setVisibility(View.VISIBLE);
                View fontView = getFontSelectorView(sFontSelectorSelectionMap.get(mFontSizeId));
                if (fontView != null) {
                    fontView.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.menu_list_mode:
                mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                        TextNote.MODE_CHECK_LIST : 0);
                break;
            case R.id.menu_share:
                getWorkingText();
                sendTo(this, mWorkingNote.getContent());
                break;
            case R.id.menu_export:
                showExportDialog();
                break;
            case R.id.menu_send_to_desktop:
                sendToDesktop();
                break;
            case R.id.menu_alert:
                setReminder();
                break;
            case R.id.menu_delete_remind:
                mWorkingNote.setAlertDate(0, false);
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * 设置提醒
     * <p>
     * 显示日期时间选择对话框，让用户选择提醒时间。
     * 选择完成后设置笔记的提醒日期。
     * </p>
     */
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date	, true);
            }
        });
        d.show();
    }

    /**
     * 分享笔记到其他应用
     * <p>
     * 使用ACTION_SEND Intent将笔记内容分享到支持文本分享的应用。
     * </p>
     * @param context 上下文对象
     * @param info 要分享的文本内容
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    /**
     * 显示导出选项对话框
     */
    private void showExportDialog() {
        if (mWorkingNote.getNoteId() == 0) {
            saveNote();
        }
        getWorkingText();
        String content = mWorkingNote.getContent();
        if (TextUtils.isEmpty(content)) {
            showToast(R.string.error_note_empty_for_send_to_desktop);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("导出便签");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final RadioGroup group = new RadioGroup(this);
        RadioButton rbText = new RadioButton(this);
        rbText.setText("导出为文本 (.txt)");
        rbText.setId(View.generateViewId());
        group.addView(rbText);

        RadioButton rbImage = new RadioButton(this);
        rbImage.setText("导出为图片 (.png)");
        rbImage.setId(View.generateViewId());
        group.addView(rbImage);

        RadioButton rbPdf = new RadioButton(this);
        rbPdf.setText("导出为 PDF (.pdf)");
        rbPdf.setId(View.generateViewId());
        group.addView(rbPdf);

        group.check(rbText.getId());
        layout.addView(group);

        final CheckBox cbShare = new CheckBox(this);
        cbShare.setText("导出后立即分享");
        layout.addView(cbShare);

        builder.setView(layout);

        builder.setPositiveButton("开始导出", (dialog, which) -> {
            int checkedId = group.getCheckedRadioButtonId();
            boolean share = cbShare.isChecked();

            if (checkedId == rbText.getId()) {
                performExport(0, share);
            } else if (checkedId == rbImage.getId()) {
                performExport(1, share);
            } else if (checkedId == rbPdf.getId()) {
                performExport(2, share);
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void performExport(int format, boolean share) {
        String content = mWorkingNote.getContent();
        String title = getTitleFromContent(content);
        String noteId = String.valueOf(mWorkingNote.getNoteId());

        switch (format) {
            case 0: // Text
                BackupUtils backupUtils = BackupUtils.getInstance(this);
                int state = backupUtils.exportNoteToText(noteId, title);
                if (state == BackupUtils.STATE_SUCCESS) {
                    File file = new File(backupUtils.getExportedTextFileDir(), backupUtils.getExportedTextFileName());
                    showToast("已导出至下载目录: " + file.getName());
                    if (share) shareFile(file, "text/plain");
                } else {
                    showToast("导出文本失败");
                }
                break;
            case 1: // Image
                binding.svNoteEditScroll.post(() -> {
                    Bitmap bitmap = ImageExportHelper.viewToBitmap(binding.cvEditorSurface);
                    Uri uri = ImageExportHelper.saveBitmapToExternal(NoteEditActivity.this, bitmap, title);
                    if (uri != null) {
                        showToast("已导出图片至下载目录");
                        if (share) shareUri(uri, "image/png");
                    } else {
                        showToast("生成图片失败");
                    }
                });
                break;
            case 2: // PDF
                File pdfFile = PdfExportHelper.exportToPdf(this, title, content);
                if (pdfFile != null) {
                    showToast("已导出 PDF 至下载目录: " + pdfFile.getName());
                    if (share) shareFile(pdfFile, "application/pdf");
                } else {
                    showToast("导出 PDF 失败");
                }
                break;
        }
    }

    private String getTitleFromContent(String content) {
        String title = content.trim();
        int firstNewLine = title.indexOf('\n');
        if (firstNewLine > 0) {
            title = title.substring(0, firstNewLine);
        }
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }
        return title;
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        shareUri(uri, mimeType);
    }

    private void shareUri(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享便签"));
    }

    /**
     * 创建新笔记
     * <p>
     * 先保存当前编辑的笔记，然后启动新的NoteEditActivity创建新笔记。
     * 新笔记将创建在与当前笔记相同的文件夹中。
     * </p>
     */
    private void createNewNote() {
        // Firstly, save current editing notes
        saveNote();

        // For safety, start a new NoteEditActivity
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前笔记
     * <p>
     * 删除当前编辑的笔记。如果处于同步模式，将笔记移动到垃圾箱；
     * 否则直接从数据库中删除。
     * </p>
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            if (!isSyncMode()) {
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    /**
     * 检查是否处于同步模式
     * <p>
     * 检查是否配置了同步账户，如果配置了则处于同步模式。
     * </p>
     * @return 如果配置了同步账户返回true，否则返回false
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 闹钟提醒改变回调
     * <p>
     * 当笔记的闹钟提醒设置改变时调用。
     * 如果笔记尚未保存到数据库，先保存笔记。
     * 然后使用AlarmManager设置或取消闹钟。
     * </p>
     * @param date 提醒日期时间（毫秒）
     * @param set true表示设置提醒，false表示取消提醒
     */
    public void onClockAlertChanged(long date, boolean set) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            showAlertHeader();
            if(!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * 小部件改变回调
     * <p>
     * 当笔记的小部件设置改变时调用，更新桌面小部件显示。
     * </p>
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 编辑文本删除回调
     * <p>
     * 在清单模式下删除某个编辑项时调用。
     * 删除指定位置的编辑项，并更新后续项的索引。
     * </p>
     * @param index 要删除的编辑项索引
     * @param text 编辑项的文本内容
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;
        }

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        NoteEditText edit = null;
        if(index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * 编辑文本回车回调
     * <p>
     * 在清单模式下，当用户在某个编辑项中按下回车键时调用。
     * 在指定位置插入新的编辑项，并更新后续项的索引。
     * </p>
     * @param index 回车位置所在的编辑项索引
     * @param text 编辑项的文本内容
     */
    public void onEditTextEnter(int index, String text) {
        /**
         * Should not happen, check for debug
         */
        if(index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 切换到清单模式
     * <p>
     * 将普通文本编辑器切换到清单模式。
     * 将文本按行分割，每行创建一个清单项，包含复选框和编辑框。
     * </p>
     * @param text 要转换为清单的文本内容
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 高亮显示搜索结果
     * <p>
     * 在文本中高亮显示用户搜索的关键词。
     * 使用背景色标记匹配的文本。
     * </p>
     * @param fullText 完整的文本内容
     * @param userQuery 用户搜索的关键词
     * @return 带有高亮标记的Spannable对象
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);

        // 应用智能解析（时间、地点识别）
        SmartParser.parse(this, spannable);

        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 创建清单列表项视图
     * <p>
     * 创建清单模式下的单个列表项，包含复选框和编辑框。
     * 根据文本内容设置复选框状态和文本样式。
     * </p>
     * @param item 列表项的文本内容
     * @param index 列表项的索引
     * @return 列表项视图
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        // Apply custom font
        FontManager.getInstance(this).applyFont(edit);
        
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * 文本改变回调
     * <p>
     * 在清单模式下，当某个编辑项的文本内容改变时调用。
     * 根据是否有文本内容显示或隐藏复选框。
     * </p>
     * @param index 编辑项的索引
     * @param hasText 是否有文本内容
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if(hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * 清单模式改变回调
     * <p>
     * 当笔记的清单模式改变时调用。
     * 切换到清单模式时，将文本转换为清单项；
     * 切换到普通模式时，将清单项转换为文本。
     * </p>
     * @param oldMode 旧的模式
     * @param newMode 新的模式
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        mUndoRedoManager.clear();
        invalidateOptionsMenu();
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
            if (mNoteHeaderHolder != null && mNoteHeaderHolder.tvCharCount != null) {
                mNoteHeaderHolder.tvCharCount.setVisibility(View.VISIBLE);
                mNoteHeaderHolder.tvCharCount.setText(String.valueOf(mNoteEditor.getText().length()) + " 字");
            }
        }
    }

    /**
     * 获取工作文本
     * <p>
     * 从当前编辑器中获取文本内容并设置到WorkingNote。
     * 如果是清单模式，将所有清单项合并为文本，并标记已选中项。
     * </p>
     * @return 如果有已选中的清单项返回true，否则返回false
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 保存笔记
     * <p>
     * 将当前编辑的笔记保存到数据库。
     * 保存成功后设置RESULT_OK结果码，用于标识创建/编辑状态。
     * </p>
     * @return 保存成功返回true，失败返回false
     */
    private boolean saveNote() {
        getWorkingText();
        if (mNoteHeaderHolder != null && mNoteHeaderHolder.etTitle != null) {
            mWorkingNote.setTitle(mNoteHeaderHolder.etTitle.getText().toString());
        }
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * {@link #RESULT_OK} is used to identify the create/edit state
             */
            setResult(RESULT_OK);

            // 触发同步（如果用户已登录）
            triggerBackgroundSync();
        }
        return saved;
    }

    /**
     * 触发后台同步（如果用户已登录）
     */
    private void triggerBackgroundSync() {
        net.micode.notes.auth.UserAuthManager authManager = net.micode.notes.auth.UserAuthManager.getInstance(this);
        if (authManager.isLoggedIn()) {
            net.micode.notes.sync.SyncManager.getInstance().syncNotes(new net.micode.notes.sync.SyncManager.SyncCallback() {
                @Override
                public void onSuccess() {
                    Log.d("NoteEditActivity", "Background sync completed after save");
                }

                @Override
                public void onError(String error) {
                    Log.e("NoteEditActivity", "Background sync failed after save: " + error);
                }
            });
        }
    }

    /**
     * 发送到桌面
     * <p>
     * 将笔记创建为桌面快捷方式。
     * 如果笔记尚未保存到数据库，先保存笔记。
     * 快捷方式使用笔记内容的前10个字符作为标题。
     * </p>
     */
    private void sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成快捷方式图标标题
     * <p>
     * 从笔记内容中提取文本作为快捷方式标题。
     * 移除清单标记，并限制标题长度为10个字符。
     * </p>
     * @param content 笔记内容
     * @return 快捷方式标题
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    private void showBackgroundSelector() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogBackgroundSelectorBinding dialogBinding = DialogBackgroundSelectorBinding.inflate(getLayoutInflater());
        
        java.util.List<Integer> colors = java.util.Arrays.asList(
                ResourceParser.YELLOW,
                ResourceParser.BLUE,
                ResourceParser.WHITE,
                ResourceParser.GREEN,
                ResourceParser.RED,
                ResourceParser.MIDNIGHT_BLACK,
                ResourceParser.EYE_CARE_GREEN,
                ResourceParser.WARM,
                ResourceParser.COOL,
                ResourceParser.SUNSET,
                ResourceParser.OCEAN,
                ResourceParser.FOREST,
                ResourceParser.LAVENDER
        );
        
        NoteColorAdapter adapter = new NoteColorAdapter(colors, mWorkingNote.getBgColorId(), colorId -> {
            mWorkingNote.setBgColorId(colorId);
            mWorkingNote.setWallpaper(null); // Clear wallpaper when color selected
            dialog.dismiss();
        });
        dialogBinding.rvBackgroundOptions.setAdapter(adapter);
        
        dialogBinding.btnPickWallpaper.setOnClickListener(v -> {
            pickWallpaper();
            dialog.dismiss();
        });
        
        dialogBinding.btnCustomColor.setOnClickListener(v -> {
            showColorPickerDialog();
            dialog.dismiss();
        });
        
        dialog.setContentView(dialogBinding.getRoot());
        dialog.show();
    }

    private void showColorPickerDialog() {
        DialogColorPickerBinding dialogBinding = DialogColorPickerBinding.inflate(getLayoutInflater());
        
        int currentColor = android.graphics.Color.WHITE;
        if (mWorkingNote.getBgColorId() < 0) {
            currentColor = mWorkingNote.getBgColorId();
        }

        final int[] rgb = new int[]{
                android.graphics.Color.red(currentColor),
                android.graphics.Color.green(currentColor),
                android.graphics.Color.blue(currentColor)
        };

        dialogBinding.viewColorPreview.setBackgroundColor(android.graphics.Color.rgb(rgb[0], rgb[1], rgb[2]));
        dialogBinding.sbRed.setProgress(rgb[0]);
        dialogBinding.sbGreen.setProgress(rgb[1]);
        dialogBinding.sbBlue.setProgress(rgb[2]);

        android.widget.SeekBar.OnSeekBarChangeListener listener = new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (seekBar.getId() == R.id.sb_red) rgb[0] = progress;
                else if (seekBar.getId() == R.id.sb_green) rgb[1] = progress;
                else if (seekBar.getId() == R.id.sb_blue) rgb[2] = progress;
                dialogBinding.viewColorPreview.setBackgroundColor(android.graphics.Color.rgb(rgb[0], rgb[1], rgb[2]));
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        };

        dialogBinding.sbRed.setOnSeekBarChangeListener(listener);
        dialogBinding.sbGreen.setOnSeekBarChangeListener(listener);
        dialogBinding.sbBlue.setOnSeekBarChangeListener(listener);

        new AlertDialog.Builder(this)
                .setTitle("Custom Color")
                .setView(dialogBinding.getRoot())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int newColor = android.graphics.Color.rgb(rgb[0], rgb[1], rgb[2]);
                    newColor |= 0xFF000000; 
                    mWorkingNote.setBgColorId(newColor);
                    mWorkingNote.setWallpaper(null);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static final int REQUEST_CODE_PICK_WALLPAPER = 105;

    private void pickWallpaper() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_WALLPAPER);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    private void saveImageToPrivateStorage(android.net.Uri uri) {
        new Thread(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;

                // Create images directory if not exists
                java.io.File imagesDir = new java.io.File(getFilesDir(), "images");
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs();
                }

                // Create a unique file name
                String fileName = "img_" + System.currentTimeMillis() + ".jpg";
                java.io.File destFile = new java.io.File(imagesDir, fileName);

                java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                is.close();

                final String filePath = "file://" + destFile.getAbsolutePath();
                runOnUiThread(() -> {
                    RichTextHelper.insertImage(mNoteEditor, filePath);
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to copy image", e);
                runOnUiThread(() -> {
                    showToast(R.string.failed_sdcard_export); // Use generic failure message or add new one
                });
            }
        }).start();
    }

    private void saveWallpaperToPrivateStorage(android.net.Uri uri) {
        new Thread(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;

                // Create wallpapers directory if not exists
                java.io.File wallpapersDir = new java.io.File(getFilesDir(), "wallpapers");
                if (!wallpapersDir.exists()) {
                    wallpapersDir.mkdirs();
                }

                // Create a unique file name
                String fileName = "wp_" + System.currentTimeMillis() + ".jpg";
                java.io.File destFile = new java.io.File(wallpapersDir, fileName);

                java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                is.close();

                final String filePath = "file://" + destFile.getAbsolutePath();
                runOnUiThread(() -> {
                    mWorkingNote.setWallpaper(filePath);
                    mNoteBgColorSelector.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to copy wallpaper", e);
                runOnUiThread(() -> {
                    showToast(R.string.failed_sdcard_export);
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_WALLPAPER && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                saveWallpaperToPrivateStorage(uri);
            }
        } else if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                saveImageToPrivateStorage(uri);
            }
        }
    }

    /**
     * 显示Toast提示
     * <p>
     * 显示指定文本的Toast提示消息。
     * </p>
     * @param text 提示文本
     */
    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示Toast提示
     * <p>
     * 显示短时Toast提示消息。
     * </p>
     * @param resId 字符串资源ID
     */
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    /**
     * 显示Toast提示
     * <p>
     * 显示指定时长的Toast提示消息。
     * </p>
     * @param resId 字符串资源ID
     * @param duration 显示时长（Toast.LENGTH_SHORT或Toast.LENGTH_LONG）
     */
    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    private void initRichTextToolbar() {
        mRichTextSelector = binding.richTextSelector;
        binding.btnBold.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.applyBold(mNoteEditor); }
        });
        binding.btnItalic.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.applyItalic(mNoteEditor); }
        });
        binding.btnUnderline.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.applyUnderline(mNoteEditor); }
        });
        binding.btnStrikethrough.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.applyStrikethrough(mNoteEditor); }
        });
        binding.btnHeader.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { 
                final CharSequence[] items = {"H1 (Largest)", "H2", "H3", "H4", "H5", "H6 (Smallest)", "Normal"};
                AlertDialog.Builder builder = new AlertDialog.Builder(NoteEditActivity.this);
                builder.setTitle("Header Level");
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        int level = (item == 6) ? 0 : (item + 1);
                        RichTextHelper.applyHeading(mNoteEditor, level);
                    }
                });
                builder.show();
            }
        });
        binding.btnList.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.applyBullet(mNoteEditor); }
        });
        binding.btnQuote.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.applyQuote(mNoteEditor); }
        });
        binding.btnCode.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.applyCode(mNoteEditor); }
        });
        binding.btnLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.insertLink(NoteEditActivity.this, mNoteEditor); }
        });
        binding.btnDivider.setOnClickListener(new OnClickListener() {
            public void onClick(View v) { RichTextHelper.insertDivider(mNoteEditor); }
        });
        binding.btnColorText.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                final CharSequence[] items = {"Black", "Red", "Blue"};
                final int[] colors = {android.graphics.Color.BLACK, android.graphics.Color.RED, android.graphics.Color.BLUE};
                AlertDialog.Builder builder = new AlertDialog.Builder(NoteEditActivity.this);
                builder.setTitle("Text Color");
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        RichTextHelper.applyColor(mNoteEditor, colors[item], false);
                    }
                });
                builder.show();
            }
        });
        binding.btnColorFill.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                final CharSequence[] items = {"None", "Yellow", "Green", "Cyan"};
                final int[] colors = {android.graphics.Color.TRANSPARENT, android.graphics.Color.YELLOW, android.graphics.Color.GREEN, android.graphics.Color.CYAN};
                AlertDialog.Builder builder = new AlertDialog.Builder(NoteEditActivity.this);
                builder.setTitle("Background Color");
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        RichTextHelper.applyColor(mNoteEditor, colors[item], true);
                    }
                });
                builder.show();
            }
        });
    }

    private void saveAsTemplate() {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        final net.micode.notes.data.NotesRepository repository = new net.micode.notes.data.NotesRepository(getContentResolver());

        new Thread(() -> {
            android.database.Cursor cursor = getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    new String[]{net.micode.notes.data.Notes.NoteColumns.ID, net.micode.notes.data.Notes.NoteColumns.SNIPPET},
                    net.micode.notes.data.Notes.NoteColumns.PARENT_ID + "=? AND " + net.micode.notes.data.Notes.NoteColumns.TYPE + "=?",
                    new String[]{String.valueOf(Notes.ID_TEMPLATE_FOLDER), String.valueOf(Notes.TYPE_FOLDER)},
                    null);

            final java.util.List<String> folderNames = new java.util.ArrayList<>();
            final java.util.List<Long> folderIds = new java.util.ArrayList<>();

            if (cursor != null) {
                while(cursor.moveToNext()) {
                    folderIds.add(cursor.getLong(0));
                    folderNames.add(cursor.getString(1));
                }
                cursor.close();
            }

            runOnUiThread(() -> {
                if (folderNames.isEmpty()) {
                    Toast.makeText(this, "No template categories found", Toast.LENGTH_SHORT).show();
                    repository.shutdown();
                    return;
                }

                showSaveTemplateDialog(repository, folderNames, folderIds);
            });
        }).start();
    }

    private void showSaveTemplateDialog(final net.micode.notes.data.NotesRepository repository,
                                        final java.util.List<String> folderNames,
                                        final java.util.List<Long> folderIds) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save as Template");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        final EditText input = new EditText(this);
        input.setHint("Template Name");
        input.setText(mWorkingNote.getTitle());
        layout.addView(input);

        final TextView label = new TextView(this);
        label.setText("Select Category:");
        label.setPadding(0, 20, 0, 10);
        layout.addView(label);

        final android.widget.Spinner spinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, folderNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        layout.addView(spinner);

        builder.setView(layout);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String name = input.getText().toString();
            int position = spinner.getSelectedItemPosition();
            if (position >= 0 && position < folderIds.size()) {
                long categoryId = folderIds.get(position);
                repository.createTemplate(mWorkingNote.getNoteId(), categoryId, name, new net.micode.notes.data.NotesRepository.Callback<Long>() {
                    @Override
                    public void onSuccess(Long result) {
                        runOnUiThread(() -> {
                            Toast.makeText(NoteEditActivity.this, "Template Saved", Toast.LENGTH_SHORT).show();
                            repository.shutdown();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(NoteEditActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            repository.shutdown();
                        });
                    }
                });
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> repository.shutdown());
        builder.setOnCancelListener(dialog -> repository.shutdown());

        builder.show();
    }
}
