package net.micode.notes.capsule;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.util.Log;
import android.view.DragEvent;
import android.content.ClipData;
import android.content.ClipDescription;

import net.micode.notes.R;
import net.micode.notes.model.Note;
import net.micode.notes.data.Notes;

import android.view.GestureDetector;

public class CapsuleService extends Service {

    private static final String TAG = "CapsuleService";
    private WindowManager mWindowManager;
    private View mCollapsedView;
    private View mExpandedView;
    private EditText mEtContent;
    private WindowManager.LayoutParams mCollapsedParams;
    private WindowManager.LayoutParams mExpandedParams;
    private GestureDetector mGestureDetector;
    
    private Handler mHandler = new Handler();
    public static String currentSourcePackage = "";

    private static final String CHANNEL_ID = "CapsuleServiceChannel";

    public static final String ACTION_SAVE_SUCCESS = "net.micode.notes.capsule.ACTION_SAVE_SUCCESS";

    private final android.content.BroadcastReceiver mSaveReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SAVE_SUCCESS.equals(intent.getAction())) {
                highlightCapsule();
            }
        }
    };
    
    public static void setCurrentSourcePackage(String pkg) {
        currentSourcePackage = pkg;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(1, createNotification());
        
        initViews();
    }

    private void initViews() {
        // Collapsed View
        mCollapsedView = LayoutInflater.from(this).inflate(R.layout.layout_capsule_collapsed, null);
        
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        mCollapsedParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        mCollapsedParams.gravity = Gravity.TOP | Gravity.START;
        mCollapsedParams.x = 0;
        mCollapsedParams.y = 100;

        // Expanded View
        mExpandedView = LayoutInflater.from(this).inflate(R.layout.layout_capsule_expanded, null);
        
        mExpandedParams = new WindowManager.LayoutParams(
                dp2px(320),
                dp2px(400),
                layoutFlag,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mExpandedParams.dimAmount = 0.5f;
        mExpandedParams.gravity = Gravity.CENTER;

        // Setup Fields
        mCollapsedView.setClickable(true);
        mEtContent = mExpandedView.findViewById(R.id.et_content);

        // Setup Listeners
        setupCollapsedListener();
        setupExpandedListener();

        // Add Collapsed View initially
        try {
            mWindowManager.addView(mCollapsedView, mCollapsedParams);
            Log.d(TAG, "initViews: Collapsed view added");
        } catch (Exception e) {
            Log.e(TAG, "initViews: Failed to add collapsed view", e);
        }
    }

    private void setupCollapsedListener() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                Log.d(TAG, "onSingleTapConfirmed: Triggering showExpandedView");
                showExpandedView();
                return true;
            }
        });

        mCollapsedView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Let GestureDetector handle taps
                if (mGestureDetector.onTouchEvent(event)) {
                    return true;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        initialX = mCollapsedParams.x;
                        initialY = mCollapsedParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int Xdiff = (int) (event.getRawX() - initialTouchX);
                        int Ydiff = (int) (event.getRawY() - initialTouchY);
                        
                        // Move if dragged
                        if (Math.abs(Xdiff) > 10 || Math.abs(Ydiff) > 10) {
                            mCollapsedParams.x = initialX + Xdiff;
                            mCollapsedParams.y = initialY + Ydiff;
                            mWindowManager.updateViewLayout(mCollapsedView, mCollapsedParams);
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        return true;
                }
                return false;
            }
        });
        
        mCollapsedView.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    Log.d(TAG, "onDrag: ACTION_DRAG_STARTED");
                    if (event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                        event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
                         v.setAlpha(1.0f);
                         return true;
                    }
                    return false;
                case DragEvent.ACTION_DRAG_ENTERED:
                    Log.d(TAG, "onDrag: ACTION_DRAG_ENTERED");
                    v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start();
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    Log.d(TAG, "onDrag: ACTION_DRAG_EXITED");
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                    return true;
                case DragEvent.ACTION_DROP:
                    Log.d(TAG, "onDrag: ACTION_DROP");
                    ClipData.Item item = event.getClipData().getItemAt(0);
                    CharSequence text = item.getText();
                    if (text != null) {
                        saveNote(text.toString());
                    }
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    Log.d(TAG, "onDrag: ACTION_DRAG_ENDED");
                    v.setAlpha(0.8f);
                    return true;
            }
            return false;
        });
    }

    private void setupExpandedListener() {
        Button btnCancel = mExpandedView.findViewById(R.id.btn_cancel);
        Button btnSave = mExpandedView.findViewById(R.id.btn_save);

        btnCancel.setOnClickListener(v -> showCollapsedView());
        
        btnSave.setOnClickListener(v -> {
            String content = mEtContent.getText().toString();
            if (!content.isEmpty()) {
                saveNote(content);
                mEtContent.setText("");
                showCollapsedView();
            }
        });
    }

    private void showExpandedView() {
        mHandler.post(() -> {
            try {
                Log.d(TAG, "showExpandedView: Attempting to show expanded view");
                if (mCollapsedView != null && mCollapsedView.getParent() != null) {
                    Log.d(TAG, "showExpandedView: Removing collapsed view");
                    mWindowManager.removeViewImmediate(mCollapsedView);
                }
                
                if (mExpandedView != null && mExpandedView.getParent() == null) {
                    Log.d(TAG, "showExpandedView: Adding expanded view");
                    mWindowManager.addView(mExpandedView, mExpandedParams);
                    
                    TextView tvSource = mExpandedView.findViewById(R.id.tv_source);
                    if (currentSourcePackage != null && !currentSourcePackage.isEmpty()) {
                        tvSource.setText("Source: " + currentSourcePackage);
                        tvSource.setVisibility(View.VISIBLE);
                    } else {
                        tvSource.setVisibility(View.GONE);
                    }
                    
                    if (mEtContent != null) {
                        mEtContent.requestFocus();
                    }
                    Log.d(TAG, "showExpandedView: Expanded view added successfully");
                } else {
                    Log.w(TAG, "showExpandedView: Expanded view already has a parent or is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "showExpandedView: Error", e);
            }
        });
    }

    private void showCollapsedView() {
        mHandler.post(() -> {
            try {
                Log.d(TAG, "showCollapsedView: Attempting to show collapsed view");
                if (mExpandedView != null && mExpandedView.getParent() != null) {
                    Log.d(TAG, "showCollapsedView: Removing expanded view");
                    mWindowManager.removeViewImmediate(mExpandedView);
                }
                
                if (mCollapsedView != null && mCollapsedView.getParent() == null) {
                    Log.d(TAG, "showCollapsedView: Adding collapsed view");
                    mWindowManager.addView(mCollapsedView, mCollapsedParams);
                    Log.d(TAG, "showCollapsedView: Collapsed view added successfully");
                } else {
                    Log.w(TAG, "showCollapsedView: Collapsed view already has a parent or is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "showCollapsedView: Error", e);
            }
        });
    }

    private void saveNote(String content) {
        new Thread(() -> {
            try {
                // 1. Create new note in CAPSULE folder
                long noteId = Note.getNewNoteId(this, Notes.ID_CAPSULE_FOLDER);
                
                // 2. Create Note object
                Note note = new Note();
                note.setTextData(Notes.DataColumns.CONTENT, content);
                
                // Generate Summary (First 20 chars or first line)
                String summary = content.length() > 20 ? content.substring(0, 20) + "..." : content;
                int firstLineEnd = content.indexOf('\n');
                if (firstLineEnd > 0 && firstLineEnd < 20) {
                     summary = content.substring(0, firstLineEnd);
                }
                note.setNoteValue(Notes.NoteColumns.SNIPPET, summary);

                // Add Source Info if available
                if (currentSourcePackage != null && !currentSourcePackage.isEmpty()) {
                    note.setTextData(Notes.DataColumns.DATA3, currentSourcePackage);
                }
                
                boolean success = note.syncNote(this, noteId);
                
                mHandler.post(() -> {
                    if (success) {
                        Log.d(TAG, "saveNote: Success");
                        Toast.makeText(this, "Saved to Notes", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "saveNote: Failed");
                        Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "saveNote: Exception", e);
                mHandler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Capsule Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        return builder.setContentTitle("Global Capsule Running")
                .setContentText("Tap to configure")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mSaveReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Receiver not registered", e);
        }
        if (mCollapsedView != null && mCollapsedView.getParent() != null) {
            mWindowManager.removeView(mCollapsedView);
        }
        if (mExpandedView != null && mExpandedView.getParent() != null) {
            mWindowManager.removeView(mExpandedView);
        }
    }
    
    private void highlightCapsule() {
        if (mCollapsedView != null && mCollapsedView.getParent() != null) {
             mHandler.post(() -> {
                 mCollapsedView.animate().scaleX(1.5f).scaleY(1.5f).setDuration(200).withEndAction(() -> {
                     mCollapsedView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                 }).start();
             });
        }
    }
}
