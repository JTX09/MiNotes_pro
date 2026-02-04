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

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 笔记编辑文本框
 * <p>
 * 自定义的EditText，用于笔记编辑界面，支持多行文本编辑、链接识别和上下文菜单。
 * 提供了与NoteEditActivity的交互接口，用于处理删除和回车事件。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>支持多行文本编辑，每行是一个独立的EditText</li>
 * <li>识别并处理URL、电话号码、邮件地址等链接</li>
 * <li>处理删除和回车事件，通知监听器</li>
 * <li>支持文本选择和上下文菜单</li>
 * </ul>
 * </p>
 * 
 * @see NoteEditActivity
 */
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.text.style.ImageSpan;
import net.micode.notes.tool.RichTextHelper;
import net.micode.notes.tool.SmartParser;
import net.micode.notes.tool.SmartURLSpan;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.app.AlertDialog;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.content.DialogInterface;

public class NoteEditText extends EditText implements ScaleGestureDetector.OnScaleGestureListener {
    // 日志标签
    private static final String TAG = "NoteEditText";
    // 当前EditText的索引
    private int mIndex;
    // 删除前的光标位置
    private int mSelectionStartBeforeDelete;

    // Scale Gesture Detector
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;
    private ImageSpan mSelectedImageSpan;
    private int mInitialWidth;
    private int mInitialHeight;

    // 电话号码URI方案
    private static final String SCHEME_TEL = "tel:" ;
    // HTTP URI方案
    private static final String SCHEME_HTTP = "http:" ;
    // 邮件URI方案
    private static final String SCHEME_EMAIL = "mailto:" ;

    // URI方案与上下文菜单资源ID的映射
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
        sSchemaActionResMap.put(SmartParser.SCHEME_TIME, R.string.note_link_time);
        sSchemaActionResMap.put(SmartParser.SCHEME_GEO, R.string.note_link_geo);
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mSelectedImageSpan != null) {
            float scaleFactor = detector.getScaleFactor();
            int newWidth = (int) (mInitialWidth * scaleFactor);
            int newHeight = (int) (mInitialHeight * scaleFactor);
            
            // Constrain size
            int maxWidth = getResources().getDisplayMetrics().widthPixels;
            if (newWidth > maxWidth) {
                newWidth = maxWidth;
                newHeight = (int) (mInitialHeight * (maxWidth / (float) mInitialWidth));
            }
            if (newWidth < 100) newWidth = 100;
            if (newHeight < 100) newHeight = 100;

            if (mSelectedImageSpan.getDrawable() != null) {
                mSelectedImageSpan.getDrawable().setBounds(0, 0, newWidth, newHeight);
                // Force layout update
                invalidate();
                requestLayout();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        float x = detector.getFocusX();
        float y = detector.getFocusY();

        x += getScrollX();
        y += getScrollY();
        x -= getTotalPaddingLeft();
        y -= getTotalPaddingTop();

        Layout layout = getLayout();
        if (layout != null) {
            int line = layout.getLineForVertical((int) y);
            int offset = layout.getOffsetForHorizontal(line, x);

            if (getText() instanceof Spanned) {
                Spanned spanned = (Spanned) getText();
                ImageSpan[] spans = spanned.getSpans(offset, offset, ImageSpan.class);
                if (spans.length > 0) {
                    mSelectedImageSpan = spans[0];
                    if (mSelectedImageSpan.getDrawable() != null) {
                        Rect bounds = mSelectedImageSpan.getDrawable().getBounds();
                        mInitialWidth = bounds.width();
                        mInitialHeight = bounds.height();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (mSelectedImageSpan != null && mSelectedImageSpan.getDrawable() != null) {
            Rect bounds = mSelectedImageSpan.getDrawable().getBounds();
            RichTextHelper.updateImageSpanSize(this, mSelectedImageSpan, bounds.width(), bounds.height());
            mSelectedImageSpan = null;
        }
    }

    /**
     * 文本视图变更监听器接口
     * <p>
     * 由NoteEditActivity实现，用于处理EditText的删除、回车和文本变更事件。
     * </p>
     * 
     * @see NoteEditActivity
     */
    public interface OnTextViewChangeListener {
        /**
         * 当按下删除键且文本为空时调用
         * 
         * @param index 当前EditText的索引
         * @param text 当前EditText中的文本内容
         */
        void onEditTextDelete(int index, String text);

        /**
         * 当按下回车键时调用
         * 
         * @param index 当前EditText的索引
         * @param text 当前EditText中的文本内容
         */
        void onEditTextEnter(int index, String text);

        /**
         * 当文本内容变更时调用
         * 
         * @param index 当前EditText的索引
         * @param hasText 是否有文本内容
         */
        void onTextChange(int index, boolean hasText);
    }

    // 文本视图变更监听器
    private OnTextViewChangeListener mOnTextViewChangeListener;

    /**
     * 构造器
     * 
     * @param context 应用上下文
     */
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
        init(context);
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, this);
        setLinkTextColor(getResources().getColor(R.color.primary_color)); // 设置链接颜色
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 触发智能解析
                SmartParser.parse(getContext(), s);
            }
        });
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float x = e.getX();
                float y = e.getY();

                x += getScrollX();
                y += getScrollY();
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();

                Layout layout = getLayout();
                if (layout != null) {
                    int line = layout.getLineForVertical((int) y);
                    int offset = layout.getOffsetForHorizontal(line, x);

                    if (getText() instanceof Spanned) {
                        Spanned spanned = (Spanned) getText();
                        ImageSpan[] spans = spanned.getSpans(offset, offset, ImageSpan.class);
                        if (spans.length > 0) {
                            showResizeDialog(spans[0]);
                            return true;
                        }
                    }
                }
                return super.onDoubleTap(e);
            }
        });
    }

    private void showResizeDialog(final ImageSpan imageSpan) {
        if (imageSpan.getDrawable() == null) return;
        
        final Rect bounds = imageSpan.getDrawable().getBounds();
        final int originalWidth = bounds.width();
        final int originalHeight = bounds.height();
        final float aspectRatio = (float) originalHeight / originalWidth;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Resize Image");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final TextView label = new TextView(getContext());
        label.setText("Scale: 100%");
        layout.addView(label);

        final SeekBar seekBar = new SeekBar(getContext());
        seekBar.setMax(200); // 0 to 200%
        seekBar.setProgress(100);
        layout.addView(seekBar);

        builder.setView(layout);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Minimum 10%
                if (progress < 10) progress = 10;
                
                float scale = progress / 100f;
                int newWidth = (int) (originalWidth * scale);
                int newHeight = (int) (newWidth * aspectRatio);
                
                label.setText("Scale: " + progress + "%");
                
                // Live preview
                imageSpan.getDrawable().setBounds(0, 0, newWidth, newHeight);
                invalidate();
                requestLayout();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Rect finalBounds = imageSpan.getDrawable().getBounds();
                RichTextHelper.updateImageSpanSize(NoteEditText.this, imageSpan, finalBounds.width(), finalBounds.height());
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Revert
                imageSpan.getDrawable().setBounds(0, 0, originalWidth, originalHeight);
                invalidate();
                requestLayout();
            }
        });

        builder.show();
    }

    /**
     * 设置当前EditText的索引
     * 
     * @param index EditText的索引值
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 设置文本视图变更监听器
     * 
     * @param listener 文本视图变更监听器对象
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    /**
     * 构造器
     * 
     * @param context 应用上下文
     * @param attrs XML属性集
     */
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
        init(context);
    }

    /**
     * 构造器
     * 
     * @param context 应用上下文
     * @param attrs XML属性集
     * @param defStyle 默认样式
     */
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * 处理触摸事件
     * 
     * 根据触摸位置设置文本选择光标的位置
     * 
     * @param event 触摸事件对象
     * @return 如果事件被处理返回true，否则返回false
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mScaleDetector != null) {
            mScaleDetector.onTouchEvent(event);
            if (mScaleDetector.isInProgress()) {
                return true;
            }
        }
        
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 获取触摸坐标
                int x = (int) event.getX();
                int y = (int) event.getY();
                // 减去内边距，得到内容区域的坐标
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                // 加上滚动偏移量
                x += getScrollX();
                y += getScrollY();

                Layout layout = getLayout();
                // 获取触摸点所在的行号
                int line = layout.getLineForVertical(y);
                // 获取触摸点在行中的字符偏移量
                int off = layout.getOffsetForHorizontal(line, x);
                // 设置文本选择光标位置
                Selection.setSelection(getText(), off);

                // 检查是否有 ClickableSpan（如智能链接）
                if (getText() instanceof Spannable) {
                    Spannable spannable = (Spannable) getText();
                    ClickableSpan[] links = spannable.getSpans(off, off, ClickableSpan.class);
                    if (links.length != 0) {
                        links[0].onClick(this);
                        return true;
                    }
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 处理按键按下事件
     * 
     * 处理删除键和回车键的按下事件
     * 
     * @param keyCode 按键代码
     * @param event 按键事件对象
     * @return 如果事件被处理返回true，否则返回false
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                // 如果设置了监听器，返回false让onKeyUp处理
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                // 记录删除前的光标位置
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 处理按键抬起事件
     * 
     * 处理删除键和回车键的抬起事件，通知监听器执行相应操作
     * 
     * @param keyCode 按键代码
     * @param event 按键事件对象
     * @return 如果事件被处理返回true，否则返回false
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                // 处理删除键
                if (mOnTextViewChangeListener != null) {
                    // 如果光标在开头且不是第一个EditText，删除当前EditText
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                // 处理回车键
                if (mOnTextViewChangeListener != null) {
                    int selectionStart = getSelectionStart();
                    // 获取光标后的文本
                    String text = getText().subSequence(selectionStart, length()).toString();
                    // 保留光标前的文本
                    setText(getText().subSequence(0, selectionStart));
                    // 通知监听器创建新的EditText
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变更时的处理
     * 
     * 当失去焦点且文本为空时，通知监听器
     * 
     * @param focused 是否获得焦点
     * @param direction 焦点移动方向
     * @param previouslyFocusedRect 之前获得焦点的视图矩形
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                // 失去焦点且文本为空
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 创建上下文菜单
     * 
     * 如果选中的文本包含URL链接，添加相应的菜单项
     * 
     * @param menu 上下文菜单对象
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            // 获取选区的起始和结束位置
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            // 获取选区内的所有URLSpan
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                // 根据URL类型确定菜单项文本
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                // 添加菜单项
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // 点击菜单项时打开链接
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}
