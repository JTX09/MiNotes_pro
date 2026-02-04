package net.micode.notes.tool;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.view.View;
import android.widget.ScrollView;

import androidx.core.widget.NestedScrollView;
import androidx.core.content.FileProvider;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 图片导出工具类
 * <p>
 * 提供将 View 转换为图片并分享的功能。
 * </p>
 */
public class ImageExportHelper {
    private static final String TAG = "ImageExportHelper";

    /**
     * 将 View 转换为 Bitmap
     *
     * @param view 要转换的 View
     * @return 转换后的 Bitmap
     */
    public static Bitmap viewToBitmap(View view) {
        if (view instanceof ScrollView || view instanceof NestedScrollView) {
            int height = 0;
            if (view instanceof ScrollView) {
                ScrollView scrollView = (ScrollView) view;
                for (int i = 0; i < scrollView.getChildCount(); i++) {
                    height += scrollView.getChildAt(i).getHeight();
                }
            } else {
                NestedScrollView nestedScrollView = (NestedScrollView) view;
                for (int i = 0; i < nestedScrollView.getChildCount(); i++) {
                    height += nestedScrollView.getChildAt(i).getHeight();
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            view.draw(canvas);
            return bitmap;
        } else {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            view.draw(canvas);
            return bitmap;
        }
    }

    /**
     * 保存 Bitmap 到公用下载目录
     *
     * @param context 应用上下文
     * @param bitmap  要保存的 Bitmap
     * @param title   笔记标题
     * @return 生成的文件 Uri，如果保存失败则返回 null
     */
    public static Uri saveBitmapToExternal(Context context, Bitmap bitmap, String title) {
        String fileName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (fileName.isEmpty()) {
            fileName = "untitled";
        }
        fileName += "_" + System.currentTimeMillis() + ".png";

        File filedir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(filedir, fileName);

        try {
            if (!filedir.exists()) {
                filedir.mkdirs();
            }
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 保存 Bitmap 到缓存目录并返回 Uri
     *
     * @param context 应用上下文
     * @param bitmap  要保存的 Bitmap
     * @return 文件的 Uri，如果保存失败则返回 null
     */
    public static Uri saveBitmapToCache(Context context, Bitmap bitmap) {
        File cachePath = new File(context.getCacheDir(), "images");
        cachePath.mkdirs();
        try {
            File file = new File(cachePath, "note_export_" + System.currentTimeMillis() + ".png");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
