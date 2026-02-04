package net.micode.notes.tool;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import net.micode.notes.R;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义的 URLSpan，用于处理智能识别出的时间、地点点击事件。
 */
public class SmartURLSpan extends URLSpan {
    private static final String TAG = "SmartURLSpan";

    public SmartURLSpan(String url) {
        super(url);
    }

    @Override
    public void onClick(View widget) {
        String url = getURL();
        Context context = widget.getContext();

        if (url.startsWith(SmartParser.SCHEME_TIME)) {
            handleTimeClick(context, url.substring(SmartParser.SCHEME_TIME.length()));
        } else if (url.startsWith(SmartParser.SCHEME_GEO)) {
            handleGeoClick(context, url.substring(SmartParser.SCHEME_GEO.length()));
        } else {
            super.onClick(widget);
        }
    }

    /**
     * 处理时间点击：跳转到系统闹钟设置页面
     */
    private void handleTimeClick(Context context, String timeStr) {
        try {
            int hour = -1;
            int minute = 0;

            // 尝试解析小时和分钟
            // 支持格式如：14:30, 10点30分, 9点
            Pattern p = Pattern.compile("(\\d{1,2})[:点](\\d{0,2})");
            Matcher m = p.matcher(timeStr);
            if (m.find()) {
                hour = Integer.parseInt(m.group(1));
                String minStr = m.group(2);
                if (minStr != null && !minStr.isEmpty()) {
                    minute = Integer.parseInt(minStr);
                }
            }

            // 处理上下午
            if (timeStr.contains("下午") && hour < 12) {
                hour += 12;
            } else if (timeStr.contains("上午") && hour == 12) {
                hour = 0;
            }

            if (hour == -1) {
                // 如果没解析出来，默认打开闹钟主界面
                Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
                context.startActivity(intent);
                return;
            }

            // 设置闹钟意图
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Log.w(TAG, "No activity found to handle set alarm, trying without resolveActivity");
                try {
                    context.startActivity(intent);
                } catch (Exception e2) {
                    Toast.makeText(context, "无法打开闹钟应用", Toast.LENGTH_SHORT).show();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to set alarm", e);
            Toast.makeText(context, "解析时间失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理地点点击：跳转到地图应用
     */
    private void handleGeoClick(Context context, String location) {
        try {
            // 使用 geo:0,0?q=location 格式打开地图
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(location));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            
            if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(mapIntent);
            } else {
                Log.w(TAG, "No activity found to handle geo intent, trying web fallback");
                // 如果没有地图应用支持 geo 协议，尝试搜索
                Uri webUri = Uri.parse("https://www.google.com/maps/search/" + Uri.encode(location));
                Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
                context.startActivity(webIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open map", e);
            Toast.makeText(context, "无法打开地图应用", Toast.LENGTH_SHORT).show();
        }
    }
}
