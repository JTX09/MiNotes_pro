package net.micode.notes.capsule;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class ClipboardMonitorService extends AccessibilityService {
    
    private ClipboardManager mClipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener mClipListener;
    private long mLastClipTime = 0;
    private static final long MERGE_THRESHOLD = 2000; // 2 seconds

    @Override
    public void onCreate() {
        super.onCreate();
        mClipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // Register clipboard listener
        if (mClipboardManager != null) {
            mClipListener = () -> {
                handleClipChanged();
            };
            mClipboardManager.addPrimaryClipChangedListener(mClipListener);
        }
    }

    private void handleClipChanged() {
        long now = System.currentTimeMillis();
        if (now - mLastClipTime < MERGE_THRESHOLD) {
             // Notify CapsuleService to show "Merge" bubble
             // For now just show a toast or log
             // Intent intent = new Intent("net.micode.notes.capsule.ACTION_MERGE_SUGGESTION");
             // sendBroadcast(intent);
        }
        mLastClipTime = now;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                // Store current package name in CapsuleService
                CapsuleService.setCurrentSourcePackage(event.getPackageName().toString());
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mClipboardManager != null && mClipListener != null) {
            mClipboardManager.removePrimaryClipChangedListener(mClipListener);
        }
    }
}
