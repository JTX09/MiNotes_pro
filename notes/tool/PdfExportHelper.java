package net.micode.notes.tool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * PDF 导出工具类
 * <p>
 * 使用 Android PdfDocument API 将笔记导出为 PDF 文件。
 * </p>
 */
public class PdfExportHelper {
    private static final String TAG = "PdfExportHelper";

    /**
     * 将笔记导出为 PDF
     *
     * @param context 应用上下文
     * @param title   笔记标题
     * @param content 笔记内容
     * @return 生成的 PDF 文件，如果失败则返回 null
     */
    public static File exportToPdf(Context context, String title, String content) {
        PdfDocument document = new PdfDocument();
        // A4 纸张大小 (595 x 842 points)
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        TextPaint titlePaint = new TextPaint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(24);
        titlePaint.setFakeBoldText(true);

        TextPaint contentPaint = new TextPaint();
        contentPaint.setColor(Color.BLACK);
        contentPaint.setTextSize(14);

        int x = 50;
        int y = 50;

        // 绘制标题
        canvas.drawText(title, x, y, titlePaint);
        y += 40;

        // 绘制正文（自动换行）
        StaticLayout staticLayout;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            staticLayout = StaticLayout.Builder.obtain(content, 0, content.length(), contentPaint, pageInfo.getPageWidth() - 100)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0.0f, 1.2f)
                    .setIncludePad(false)
                    .build();
        } else {
            staticLayout = new StaticLayout(content, contentPaint, pageInfo.getPageWidth() - 100,
                    Layout.Alignment.ALIGN_NORMAL, 1.2f, 0.0f, false);
        }

        canvas.save();
        canvas.translate(x, y);
        staticLayout.draw(canvas);
        canvas.restore();

        document.finishPage(page);

        // 生成文件名
        String fileName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (fileName.isEmpty()) {
            fileName = "untitled";
        }
        fileName += "_" + System.currentTimeMillis() + ".pdf";

        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            document.writeTo(fos);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            file = null;
        } finally {
            document.close();
        }

        return file;
    }
}
