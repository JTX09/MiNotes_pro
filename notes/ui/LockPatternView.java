package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义手势密码控件
 * <p>
 * 实现一个 3x3 的九宫格手势解锁视图。
 * 支持触摸绘制路径，并提供回调接口。
 * </p>
 */
public class LockPatternView extends View {

    private Paint mPaintNormal;
    private Paint mPaintSelected;
    private Paint mPaintError;
    private Paint mPaintPath;

    private Cell[][] mCells = new Cell[3][3];
    private List<Cell> mSelectedCells = new ArrayList<>();
    
    private float mRadius;
    private boolean mInputEnabled = true;
    private DisplayMode mDisplayMode = DisplayMode.Correct;
    
    private OnPatternListener mOnPatternListener;

    public enum DisplayMode {
        Correct, Animate, Wrong
    }

    public interface OnPatternListener {
        void onPatternStart();
        void onPatternCleared();
        void onPatternCellAdded(List<Cell> pattern);
        void onPatternDetected(List<Cell> pattern);
    }

    public static class Cell {
        int row;
        int column;
        float x;
        float y;

        public Cell(int row, int column) {
            this.row = row;
            this.column = column;
        }

        public int getIndex() {
            return row * 3 + column;
        }
        
        @Override
        public String toString() {
            return String.valueOf(getIndex());
        }
    }

    public LockPatternView(Context context) {
        this(context, null);
    }

    public LockPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaintNormal = new Paint();
        mPaintNormal.setAntiAlias(true);
        mPaintNormal.setColor(Color.LTGRAY);
        mPaintNormal.setStyle(Paint.Style.FILL);

        mPaintSelected = new Paint();
        mPaintSelected.setAntiAlias(true);
        mPaintSelected.setColor(Color.BLUE); // Default selection color
        mPaintSelected.setStyle(Paint.Style.FILL);

        mPaintError = new Paint();
        mPaintError.setAntiAlias(true);
        mPaintError.setColor(Color.RED);
        mPaintError.setStyle(Paint.Style.FILL);

        mPaintPath = new Paint();
        mPaintPath.setAntiAlias(true);
        mPaintPath.setStrokeWidth(10f);
        mPaintPath.setStyle(Paint.Style.STROKE);
        mPaintPath.setStrokeCap(Paint.Cap.ROUND);
        mPaintPath.setStrokeJoin(Paint.Join.ROUND);
        mPaintPath.setColor(Color.BLUE); // Default path color
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int width = w - getPaddingLeft() - getPaddingRight();
        int height = h - getPaddingTop() - getPaddingBottom();
        
        float cellWidth = width / 3f;
        float cellHeight = height / 3f;
        
        mRadius = Math.min(cellWidth, cellHeight) * 0.15f; // Radius of the dots

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mCells[i][j] = new Cell(i, j);
                mCells[i][j].x = getPaddingLeft() + j * cellWidth + cellWidth / 2;
                mCells[i][j].y = getPaddingTop() + i * cellHeight + cellHeight / 2;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw path
        if (!mSelectedCells.isEmpty()) {
            Path path = new Path();
            Cell first = mSelectedCells.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < mSelectedCells.size(); i++) {
                Cell cell = mSelectedCells.get(i);
                path.lineTo(cell.x, cell.y);
            }
            
            if (mDisplayMode == DisplayMode.Wrong) {
                mPaintPath.setColor(Color.RED);
            } else {
                mPaintPath.setColor(Color.BLUE); // Or Theme color
            }
            canvas.drawPath(path, mPaintPath);
        }

        // Draw cells
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Cell cell = mCells[i][j];
                drawCell(canvas, cell);
            }
        }
    }

    private void drawCell(Canvas canvas, Cell cell) {
        boolean isSelected = mSelectedCells.contains(cell);
        
        if (isSelected) {
            if (mDisplayMode == DisplayMode.Wrong) {
                canvas.drawCircle(cell.x, cell.y, mRadius, mPaintError);
            } else {
                canvas.drawCircle(cell.x, cell.y, mRadius, mPaintSelected);
            }
        } else {
            canvas.drawCircle(cell.x, cell.y, mRadius, mPaintNormal);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                clearPattern();
                if (mOnPatternListener != null) {
                    mOnPatternListener.onPatternStart();
                }
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_UP:
                if (mOnPatternListener != null) {
                    mOnPatternListener.onPatternDetected(mSelectedCells);
                }
                return true;
        }
        return false;
    }

    private void handleActionMove(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        Cell cell = detectCell(x, y);
        if (cell != null && !mSelectedCells.contains(cell)) {
            mSelectedCells.add(cell);
            if (mOnPatternListener != null) {
                mOnPatternListener.onPatternCellAdded(mSelectedCells);
            }
            invalidate();
        }
    }

    private Cell detectCell(float x, float y) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Cell cell = mCells[i][j];
                double dist = Math.sqrt(Math.pow(x - cell.x, 2) + Math.pow(y - cell.y, 2));
                // Use a larger detection radius than visual radius for better UX
                if (dist < mRadius * 3) { 
                    return cell;
                }
            }
        }
        return null;
    }

    public void setOnPatternListener(OnPatternListener listener) {
        mOnPatternListener = listener;
    }

    public void setDisplayMode(DisplayMode mode) {
        mDisplayMode = mode;
        invalidate();
    }

    public void clearPattern() {
        mSelectedCells.clear();
        mDisplayMode = DisplayMode.Correct;
        invalidate();
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCleared();
        }
    }

    public void setInputEnabled(boolean enabled) {
        mInputEnabled = enabled;
    }
    
    /**
     * 将模式列表转换为字符串 (e.g., "012")
     */
    public static String patternToString(List<Cell> pattern) {
        if (pattern == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Cell cell : pattern) {
            sb.append(cell.getIndex());
        }
        return sb.toString();
    }
}
