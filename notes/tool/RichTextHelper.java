package net.micode.notes.tool;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

public class RichTextHelper {

    private static final String TAG = "RichTextHelper";

    public static class NoteImageGetter implements Html.ImageGetter {
        private Context mContext;

        public NoteImageGetter(Context context) {
            mContext = context;
        }

        @Override
        public Drawable getDrawable(String source) {
            if (TextUtils.isEmpty(source)) {
                return null;
            }

            try {
                Uri uri = Uri.parse(source);
                String path = uri.getPath();
                if (path == null) {
                    return null;
                }
                
                // Parse dimensions from fragment
                int targetWidth = -1;
                int targetHeight = -1;
                String fragment = uri.getFragment();
                if (fragment != null) {
                    String[] params = fragment.split("&");
                    for (String param : params) {
                        String[] pair = param.split("=");
                        if (pair.length == 2) {
                            if ("w".equals(pair[0])) {
                                targetWidth = Integer.parseInt(pair[1]);
                            } else if ("h".equals(pair[0])) {
                                targetHeight = Integer.parseInt(pair[1]);
                            }
                        }
                    }
                }
                
                // Check if it's a content URI or file path
                // For simplicity in this project, we assume we saved it as file:// path
                // But source might come as /data/user/0/...
                
                // Decode bitmap with resizing
                // Calculate max width (e.g., screen width - padding)
                // For simplicity, let's assume a fixed max width or display metrics
                int maxWidth = mContext.getResources().getDisplayMetrics().widthPixels - 40; 
                
                Bitmap bitmap = decodeSampledBitmapFromFile(path, maxWidth, maxWidth);
                
                if (bitmap != null) {
                    BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(), bitmap);
                    
                    if (targetWidth > 0 && targetHeight > 0) {
                        // Use saved dimensions
                        drawable.setBounds(0, 0, targetWidth, targetHeight);
                    } else {
                        // Use default intrinsic dimensions
                        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                    }
                    return drawable;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load image: " + source, e);
            }
            return null;
        }

        private Bitmap decodeSampledBitmapFromFile(String pathName, int reqWidth, int reqHeight) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(pathName, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(pathName, options);
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            return inSampleSize;
        }
    }

    public static void applyBold(EditText editText) {
        applyStyleSpan(editText, Typeface.BOLD);
    }

    public static void applyItalic(EditText editText) {
        applyStyleSpan(editText, Typeface.ITALIC);
    }

    public static void applyUnderline(EditText editText) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) { int temp = start; start = end; end = temp; }

        Editable editable = editText.getText();
        UnderlineSpan[] spans = editable.getSpans(start, end, UnderlineSpan.class);
        if (spans != null && spans.length > 0) {
            for (UnderlineSpan span : spans) {
                editable.removeSpan(span);
            }
        } else {
            editable.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void applyStrikethrough(EditText editText) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) { int temp = start; start = end; end = temp; }

        Editable editable = editText.getText();
        StrikethroughSpan[] spans = editable.getSpans(start, end, StrikethroughSpan.class);
        if (spans != null && spans.length > 0) {
            for (StrikethroughSpan span : spans) {
                editable.removeSpan(span);
            }
        } else {
            editable.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applyStyleSpan(EditText editText, int style) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) { int temp = start; start = end; end = temp; }

        Editable editable = editText.getText();
        StyleSpan[] spans = editable.getSpans(start, end, StyleSpan.class);
        boolean exists = false;
        for (StyleSpan span : spans) {
            if (span.getStyle() == style) {
                editable.removeSpan(span);
                exists = true;
            }
        }

        if (!exists) {
            editable.setSpan(new StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void applyHeading(EditText editText, int level) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        // Expand to full line
        Editable text = editText.getText();
        String string = text.toString();
        
        // Find line start and end
        int lineStart = string.lastIndexOf('\n', start - 1) + 1;
        if (lineStart < 0) lineStart = 0;
        int lineEnd = string.indexOf('\n', end);
        if (lineEnd < 0) lineEnd = string.length();

        // Remove existing heading spans
        RelativeSizeSpan[] sizeSpans = text.getSpans(lineStart, lineEnd, RelativeSizeSpan.class);
        for (RelativeSizeSpan span : sizeSpans) {
            text.removeSpan(span);
        }
        StyleSpan[] styleSpans = text.getSpans(lineStart, lineEnd, StyleSpan.class);
        for (StyleSpan span : styleSpans) {
            if (span.getStyle() == Typeface.BOLD) {
                text.removeSpan(span);
            }
        }

        if (level > 0) {
            float scale = 1.0f;
            switch (level) {
                case 1: scale = 2.0f; break;
                case 2: scale = 1.5f; break;
                case 3: scale = 1.25f; break;
                case 4: scale = 1.1f; break;
                case 5: scale = 1.0f; break;
                case 6: scale = 0.8f; break;
            }
            text.setSpan(new RelativeSizeSpan(scale), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void applyBullet(EditText editText) {
        // Simple bullet implementation for now
        int start = editText.getSelectionStart();
        Editable text = editText.getText();
        String string = text.toString();
        int lineStart = string.lastIndexOf('\n', start - 1) + 1;
        if (lineStart < 0) lineStart = 0;
        
        // Check if already bulleted
        // Note: BulletSpan covers a paragraph. 
        int lineEnd = string.indexOf('\n', start);
        if (lineEnd < 0) lineEnd = string.length();

        BulletSpan[] spans = text.getSpans(lineStart, lineEnd, BulletSpan.class);
        if (spans != null && spans.length > 0) {
            for (BulletSpan span : spans) {
                text.removeSpan(span);
            }
        } else {
            text.setSpan(new BulletSpan(20, Color.BLACK), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void applyQuote(EditText editText) {
        int start = editText.getSelectionStart();
        Editable text = editText.getText();
        String string = text.toString();
        int lineStart = string.lastIndexOf('\n', start - 1) + 1;
        if (lineStart < 0) lineStart = 0;
        int lineEnd = string.indexOf('\n', start);
        if (lineEnd < 0) lineEnd = string.length();

        QuoteSpan[] spans = text.getSpans(lineStart, lineEnd, QuoteSpan.class);
        if (spans != null && spans.length > 0) {
            for (QuoteSpan span : spans) {
                text.removeSpan(span);
            }
        } else {
            text.setSpan(new QuoteSpan(Color.GRAY), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    
    public static void applyCode(EditText editText) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) { int temp = start; start = end; end = temp; }

        Editable editable = editText.getText();
        TypefaceSpan[] spans = editable.getSpans(start, end, TypefaceSpan.class);
        boolean exists = false;
        for (TypefaceSpan span : spans) {
            if ("monospace".equals(span.getFamily())) {
                editable.removeSpan(span);
                exists = true;
            }
        }
        
        // Also toggle background color for code block look
        BackgroundColorSpan[] bgSpans = editable.getSpans(start, end, BackgroundColorSpan.class);
        for (BackgroundColorSpan span : bgSpans) {
             if (span.getBackgroundColor() == 0xFFEEEEEE) {
                 editable.removeSpan(span);
             }
        }

        if (!exists) {
            editable.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            editable.setSpan(new BackgroundColorSpan(0xFFEEEEEE), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    
    public static void insertLink(Context context, final EditText editText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Insert Link");

        final EditText input = new EditText(context);
        input.setHint("http://example.com");
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String url = input.getText().toString();
                if (!TextUtils.isEmpty(url)) {
                    int start = editText.getSelectionStart();
                    int end = editText.getSelectionEnd();
                    if (start == end) {
                        // Insert url as text
                        editText.getText().insert(start, url);
                        end = start + url.length();
                    }
                    editText.getText().setSpan(new URLSpan(url), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    public static void applyColor(EditText editText, int color, boolean isBackground) {
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) { int temp = start; start = end; end = temp; }
        
        Editable editable = editText.getText();
        if (isBackground) {
            BackgroundColorSpan[] spans = editable.getSpans(start, end, BackgroundColorSpan.class);
            for (BackgroundColorSpan span : spans) editable.removeSpan(span);
            editable.setSpan(new BackgroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            ForegroundColorSpan[] spans = editable.getSpans(start, end, ForegroundColorSpan.class);
            for (ForegroundColorSpan span : spans) editable.removeSpan(span);
            editable.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
    
    public static void insertDivider(EditText editText) {
         int start = editText.getSelectionStart();
         editText.getText().insert(start, "\n-------------------\n");
    }

    public static void insertImage(EditText editText, String imagePath) {
        // Remove existing fragment if any
        if (imagePath.contains("#")) {
            imagePath = imagePath.substring(0, imagePath.indexOf("#"));
        }
        // Default: no size specified, use intrinsic
        String html = "<img src=\"" + imagePath + "\">";
        int start = editText.getSelectionStart();
        int end = editText.getSelectionEnd();
        if (start > end) { int temp = start; start = end; end = temp; }
        
        Spanned spanned = Html.fromHtml(html, new NoteImageGetter(editText.getContext()), null);
        editText.getText().replace(start, end, spanned);
        // Insert a newline after image for easier typing
        editText.getText().insert(start + spanned.length(), "\n");
    }

    public static void updateImageSpanSize(EditText editText, android.text.style.ImageSpan span, int width, int height) {
        Editable editable = editText.getText();
        int start = editable.getSpanStart(span);
        int end = editable.getSpanEnd(span);
        if (start < 0 || end < 0) return; // Span not found

        String source = span.getSource();
        if (source == null) return;

        // Remove old fragment
        if (source.contains("#")) {
            source = source.substring(0, source.indexOf("#"));
        }

        // Append new dimensions
        String newSource = source + "#w=" + width + "&h=" + height;
        String html = "<img src=\"" + newSource + "\">";

        // Create new span with updated source
        Spanned newSpanned = Html.fromHtml(html, new NoteImageGetter(editText.getContext()), null);
        
        // We only want the ImageSpan, not the whole Spanned (which might contain newline if insertImage added it, but fromHtml for img usually just one char)
        // Actually fromHtml returns a Spanned with ImageSpan on a special character.
        // We can just replace the old span range with new one.
        
        // Careful: replacing text might reset other spans or move cursor. 
        // Better: get the new ImageSpan from newSpanned and set it on the existing range, removing the old one.
        android.text.style.ImageSpan[] newSpans = newSpanned.getSpans(0, newSpanned.length(), android.text.style.ImageSpan.class);
        if (newSpans.length > 0) {
            editable.removeSpan(span);
            editable.setSpan(newSpans[0], start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static String toHtml(Spanned text) {
        return Html.toHtml(text);
    }

    public static Spanned fromHtml(String html, Context context) {
        return Html.fromHtml(html, new NoteImageGetter(context), null);
    }
    
    public static Spanned fromHtml(String html) {
        return Html.fromHtml(html);
    }
}
