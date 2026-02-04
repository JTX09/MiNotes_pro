/*
 * Copyright (c) 2025, Modern Notes Project
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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import androidx.core.view.GestureDetectorCompat;

/**
 * 支持滑动操作的布局
 * 包装列表项，支持左滑显示操作按钮
 */
public class SwipeMenuLayout extends FrameLayout {

    private static final String TAG = "SwipeMenuLayout";
    private static final int MENU_WIDTH_DP = 260; // 增加宽度以适应新按钮
    private static final int MIN_VELOCITY = 500;
    private static final int MAX_OVERSCROLL = 80;

    private View contentView;
    private View menuView;
    private int menuWidth;
    private int screenWidth;

    private OverScroller scroller;
    private VelocityTracker velocityTracker;
    private float lastX;
    private float downX;
    private float downY;
    private long downTime;
    private int currentState = STATE_CLOSE;
    private float currentScrollX = 0;
    private boolean isScrolling = false;
    private boolean longPressTriggered = false;

    private static final int STATE_CLOSE = 0;
    private static final int STATE_OPEN = 1;
    private static final int STATE_SWIPING = 2;
    private static final float TOUCH_SLOP = 10f;
    private static final int CLICK_TIME_THRESHOLD = 300;
    private static final int LONG_PRESS_TIME_THRESHOLD = 500;

    private Handler longPressHandler;
    private Runnable longPressRunnable;

    private OnMenuButtonClickListener menuButtonClickListener;

    private OnContentClickListener contentClickListener;

    private OnContentLongClickListener contentLongClickListener;

    private long itemId;

    private boolean swipeEnabled = true;

    public interface OnMenuButtonClickListener {
        void onEdit(long itemId);

        void onPin(long itemId);

        void onMove(long itemId);

        void onDelete(long itemId);

        void onRename(long itemId);

        void onRestore(long itemId);

        void onPermanentDelete(long itemId);
    }

    public interface OnContentLongClickListener {
        void onContentLongClick(long itemId);
    }

    public interface OnContentClickListener {
        void onContentClick(long itemId);
    }

    private boolean isFirstLayout = true;

    public SwipeMenuLayout(Context context) {
        super(context);
        init(context);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwipeMenuLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        scroller = new OverScroller(context);
        velocityTracker = VelocityTracker.obtain();
        menuWidth = (int) (MENU_WIDTH_DP * context.getResources().getDisplayMetrics().density);
        longPressHandler = new Handler(Looper.getMainLooper());
        longPressRunnable = () -> {
            if (!isScrolling && !longPressTriggered) {
                Log.d(TAG, "Long press triggered via Handler, itemId: " + itemId);
                longPressTriggered = true;
                if (contentLongClickListener != null) {
                    contentLongClickListener.onContentLongClick(itemId);
                }
            }
        };
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() != 2) {
            throw new IllegalStateException("SwipeMenuLayout must have exactly 2 children: content and menu");
        }

        contentView = getChildAt(0);
        menuView = getChildAt(1);

        setupMenuButtonListeners();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (contentView != null && menuView != null) {
            screenWidth = right - left;
            
            // 动态计算菜单宽度
            int visibleMenuWidth = 0;
            if (menuView instanceof ViewGroup) {
                ViewGroup menuGroup = (ViewGroup) menuView;
                for (int i = 0; i < menuGroup.getChildCount(); i++) {
                    View child = menuGroup.getChildAt(i);
                    if (child.getVisibility() == View.VISIBLE) {
                        visibleMenuWidth = Math.max(visibleMenuWidth, child.getMeasuredWidth());
                    }
                }
            }
            
            if (visibleMenuWidth > 0) {
                menuWidth = visibleMenuWidth;
            }

            if (isFirstLayout) {
                contentView.setTranslationX(0);
                int cardMarginEnd = (int) (8 * getContext().getResources().getDisplayMetrics().density);
                menuView.setTranslationX(screenWidth - cardMarginEnd);
                scroller.startScroll(0, 0, 0, 0);
                isFirstLayout = false;
            } else if (currentState == STATE_OPEN) {
                contentView.setTranslationX(-menuWidth);
                int cardMarginEnd = (int) (8 * getContext().getResources().getDisplayMetrics().density);
                menuView.setTranslationX(screenWidth - cardMarginEnd - menuWidth);
            }
        }
    }

    private void setupMenuButtonListeners() {
        if (menuView instanceof ViewGroup) {
            ViewGroup menuGroup = (ViewGroup) menuView;
            int childCount = menuGroup.getChildCount();

            Log.d(TAG, "setupMenuButtonListeners: menuGroup childCount=" + childCount);

            // menuView 是 FrameLayout，包含两个 include 的布局
            // 需要遍历每个菜单布局内部的按钮
            for (int i = 0; i < childCount; i++) {
                View menuLayout = menuGroup.getChildAt(i);
                Log.d(TAG, "Menu layout " + i + ": " + menuLayout.getClass().getSimpleName() + ", visibility=" + menuLayout.getVisibility());

                if (menuLayout instanceof ViewGroup) {
                    ViewGroup menuInnerGroup = (ViewGroup) menuLayout;
                    int buttonCount = menuInnerGroup.getChildCount();
                    Log.d(TAG, "Menu " + i + " has " + buttonCount + " buttons");

                    for (int j = 0; j < buttonCount; j++) {
                        View button = menuInnerGroup.getChildAt(j);
                        final View finalButton = button;
                        String tag = (String) button.getTag();
                        Log.d(TAG, "Button " + j + ": id=" + button.getId() + ", tag=" + tag);

                        button.setOnClickListener(v -> {
                            Log.d(TAG, "Button clicked: id=" + finalButton.getId() + ", tag=" + finalButton.getTag());
                            if (menuButtonClickListener != null) {
                                long itemId = getItemId();
                                Log.d(TAG, "menuButtonClickListener not null, itemId=" + itemId);
                                handleMenuButtonClick(finalButton, itemId);
                            } else {
                                Log.e(TAG, "menuButtonClickListener is NULL!");
                            }
                            closeMenu();
                        });
                    }
                }
            }
        } else {
            Log.e(TAG, "menuView is not a ViewGroup!");
        }
    }

    private void handleMenuButtonClick(View button, long itemId) {
        int id = button.getId();
        String actionType = (String) button.getTag();
        Log.d(TAG, "handleMenuButtonClick: id=" + id + ", actionType=" + actionType + ", itemId=" + itemId);
        if (actionType != null && menuButtonClickListener != null) {
            switch (actionType) {
                case "edit":
                    Log.d(TAG, "Calling onEdit");
                    menuButtonClickListener.onEdit(itemId);
                    break;
                case "pin":
                    Log.d(TAG, "Calling onPin");
                    menuButtonClickListener.onPin(itemId);
                    break;
                case "move":
                    Log.d(TAG, "Calling onMove");
                    menuButtonClickListener.onMove(itemId);
                    break;
                case "delete":
                    Log.d(TAG, "Calling onDelete");
                    menuButtonClickListener.onDelete(itemId);
                    break;
                case "rename":
                    Log.d(TAG, "Calling onRename");
                    menuButtonClickListener.onRename(itemId);
                    break;
                case "restore":
                    Log.d(TAG, "Calling onRestore");
                    menuButtonClickListener.onRestore(itemId);
                    break;
                case "permanent_delete":
                    Log.d(TAG, "Calling onPermanentDelete");
                    menuButtonClickListener.onPermanentDelete(itemId);
                    break;
                default:
                    Log.e(TAG, "Unknown actionType: " + actionType);
            }
        } else {
            if (actionType == null) {
                Log.e(TAG, "actionType is NULL!");
            }
            if (menuButtonClickListener == null) {
                Log.e(TAG, "menuButtonClickListener is NULL in handleMenuButtonClick!");
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!swipeEnabled) {
            return false;
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                lastX = ev.getX();
                downTime = System.currentTimeMillis();
                isScrolling = false;
                longPressTriggered = false;

                // 安排长按检测任务
                longPressHandler.removeCallbacks(longPressRunnable);
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIME_THRESHOLD);
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaX = ev.getX() - downX;
                float deltaY = ev.getY() - downY;

                // 检测滑动
                if (Math.abs(deltaX) > TOUCH_SLOP * 2 && Math.abs(deltaX) > Math.abs(deltaY) * 2) {
                    isScrolling = true;
                    longPressHandler.removeCallbacks(longPressRunnable);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 取消长按任务
                longPressHandler.removeCallbacks(longPressRunnable);
                break;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastX = event.getX();
                downTime = System.currentTimeMillis();
                isScrolling = false;
                longPressTriggered = false;

                // 安排长按检测任务
                longPressHandler.removeCallbacks(longPressRunnable);
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIME_THRESHOLD);
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastX;
                float deltaX = event.getX() - downX;
                float deltaY = event.getY() - downY;

                if (Math.abs(deltaX) > TOUCH_SLOP * 2 && Math.abs(deltaX) > Math.abs(deltaY) * 2) {
                    // 检测到滑动，取消长按任务
                    isScrolling = true;
                    longPressHandler.removeCallbacks(longPressRunnable);
                    currentScrollX += dx;
                    applyScroll(currentScrollX, true);
                }
                lastX = event.getX();
                break;

            case MotionEvent.ACTION_UP:
                // 取消长按任务
                longPressHandler.removeCallbacks(longPressRunnable);

                long upTime = System.currentTimeMillis();
                long duration = upTime - downTime;

                if (isScrolling) {
                    handleTouchRelease();
                } else if (!longPressTriggered && duration < LONG_PRESS_TIME_THRESHOLD) {
                    // 短按且未触发长按 = 点击
                    Log.d(TAG, "Content click detected, itemId: " + itemId);
                    if (contentClickListener != null) {
                        contentClickListener.onContentClick(itemId);
                    }
                }

                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                isScrolling = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                // 取消长按任务
                longPressHandler.removeCallbacks(longPressRunnable);

                if (isScrolling) {
                    handleTouchRelease();
                }

                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                isScrolling = false;
                break;
        }
        return true;
    }

    public void openMenu() {
        scroller = new OverScroller(getContext(), new OvershootInterpolator(0.5f));
        smoothScrollTo(-menuWidth);
        currentState = STATE_OPEN;
    }

    public void closeMenu() {
        scroller = new OverScroller(getContext(), new OvershootInterpolator(0.5f));
        smoothScrollTo(0);
        currentState = STATE_CLOSE;
    }

    public void toggleMenu() {
        if (currentState == STATE_OPEN) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    private void applyScroll(float scrollX, boolean allowElastic) {
        if (!allowElastic) {
            if (scrollX > 0) scrollX = 0;
            if (scrollX < -menuWidth) scrollX = -menuWidth;
        } else {
            if (scrollX > MAX_OVERSCROLL) {
                scrollX = MAX_OVERSCROLL;
            } else if (scrollX < -menuWidth - MAX_OVERSCROLL) {
                scrollX = -menuWidth - MAX_OVERSCROLL;
            }
        }

        contentView.setTranslationX(scrollX);
        
        // 优化定位：菜单紧贴卡片右边缘（考虑卡片右边距）
        // 假设卡片右边距为 8dp (对应 12dp marginHorizontal 的一半左右，或者从布局获取)
        int cardMarginEnd = (int) (8 * getContext().getResources().getDisplayMetrics().density);
        menuView.setTranslationX(scrollX + screenWidth - cardMarginEnd);
        
        currentScrollX = scrollX;
    }

    private void handleTouchRelease() {
        velocityTracker.computeCurrentVelocity(1000);
        float velocity = velocityTracker.getXVelocity();

        int targetX;
        if (Math.abs(velocity) > MIN_VELOCITY) {
            if (velocity > 0) {
                targetX = 0;
            } else {
                targetX = -menuWidth;
            }
        } else {
            if (Math.abs(currentScrollX) < menuWidth / 2) {
                targetX = 0;
            } else {
                targetX = -menuWidth;
            }
        }

        smoothScrollTo(targetX);
        currentState = (targetX == 0) ? STATE_CLOSE : STATE_OPEN;
    }

    private void smoothScrollTo(int x) {
        scroller.startScroll((int) currentScrollX, 0, x - (int) currentScrollX, 0, 300);
        invalidate();
        postInvalidate();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            currentScrollX = scroller.getCurrX();
            applyScroll(currentScrollX, false);
            requestAnimationInvalidation();
        }
    }

    private void requestAnimationInvalidation() {
        post(this::computeScroll);
    }

    public void setOnMenuButtonClickListener(OnMenuButtonClickListener listener) {
        this.menuButtonClickListener = listener;
    }

    public void setOnContentClickListener(OnContentClickListener listener) {
        this.contentClickListener = listener;
    }

    public void setOnContentLongClickListener(OnContentLongClickListener listener) {
        this.contentLongClickListener = listener;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public long getItemId() {
        return itemId;
    }

    public boolean isMenuOpen() {
        return currentState == STATE_OPEN;
    }

    public void setSwipeEnabled(boolean enabled) {
        this.swipeEnabled = enabled;
    }

    public boolean isSwipeEnabled() {
        return swipeEnabled;
    }
}
