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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * 原生滑动检测器
 * <p>
 * 实现左滑操作按钮的检测
 * </p>
 */
public class SwipeDetector extends GestureDetector.SimpleOnGestureListener {

    private static final float SWIPE_THRESHOLD = 100f; // 滑动阈值（像素）

    // 滑动状态
    private float downX;
    private float downY;
    private boolean isSwiping = false;

    // 监听器
    private SwipeListener swipeListener;

    /**
     * 滑动监听器接口
     */
    public interface SwipeListener {
        /**
         * 左滑开始
         */
        void onSwipeStart();

        /**
         * 滑动中
         *
         * @param distance 滑动距离（负值表示左滑）
         */
        void onSwipeMove(float distance);

        /**
         * 左滑结束
         *
         * @param distance 最终滑动距离
         */
        void onSwipeEnd(float distance);
    }

    /**
     * 构造函数
     *
     * @param context 上下文
     * @param listener 滑动监听器
     */
    public SwipeDetector(Context context, SwipeListener listener) {
        this.swipeListener = listener;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        downX = e.getX();
        downY = e.getY();
        isSwiping = false;
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!isSwiping) {
            isSwiping = true;
            if (swipeListener != null) {
                swipeListener.onSwipeStart();
            }
        }

        if (swipeListener != null) {
            swipeListener.onSwipeMove(distanceX);
        }
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (swipeListener != null) {
            swipeListener.onSwipeEnd(e2.getX() - downX);
        }
        return true;
    }

    /**
     * 获取滑动距离（负值表示左滑）
     *
     * @param currentX 当前X坐标
     * @return 滑动距离
     */
    public float getSwipeDistance(float currentX) {
        return currentX - downX;
    }

    /**
     * 是否达到滑动阈值
     *
     * @param distance 滑动距离
     * @return true如果达到阈值
     */
    public boolean isThresholdReached(float distance) {
        return Math.abs(distance) >= SWIPE_THRESHOLD;
    }

    /**
     * 是否是左滑
     *
     * @param distance 滑动距离
     * @return true如果是左滑
     */
    public boolean isSwipeLeft(float distance) {
        return distance < 0;
    }

    /**
     * 是否是右滑
     *
     * @param distance 滑动距离
     * @return true如果是右滑
     */
    public boolean isSwipeRight(float distance) {
        return distance > 0;
    }

    /**
     * 重置状态
     */
    public void reset() {
        downX = 0;
        downY = 0;
        isSwiping = false;
    }
}
