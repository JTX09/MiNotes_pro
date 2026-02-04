package net.micode.notes.tool;

import android.content.Context;
import android.os.Build;
import android.text.Spannable;
import android.text.style.URLSpan;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextLinks;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能解析工具类
 * <p>
 * 整合了 Android 系统级 AI (TextClassifier) 和自定义正则表达式。
 * 能够识别文本中的时间、地点、电话、URL 等信息。
 * </p>
 */
public class SmartParser {
    // 时间 Scheme 前缀
    public static final String SCHEME_TIME = "smarttime:";
    // 地点 Scheme 前缀
    public static final String SCHEME_GEO = "smartgeo:";

    // 优化的时间正则表达式（作为补充）
    private static final String TIME_REGEX = 
        "((今天|明天|后天|下周[一二三四五六日])?\\s*([上下]午)?\\s*(\\d{1,2})[:点](\\d{0,2})分?)" +
        "|(\\b\\d{1,2}:\\d{2}\\b)";

    // 优化的地点正则表达式：匹配 1-10 个中文字符/数字 + 常见的地点后缀
    private static final String GEO_REGEX = 
        "([一-龥0-9]{1,10}(?:省|市|区|县|街道|路|弄|巷|楼|院|场|店|里|广场|大厦|中心|医院|学校|大学|公园|车站|机场|酒店|宾馆|超市|商场))";

    // 扩展噪音词列表：包含动词、代词、时间单位和方位词
    private static final String NOISE_PREFIXES = "我在去到从的地了你他们这那点分时上下午";

    /**
     * 解析文本并应用智能链接
     * 
     * @param context 上下文，用于获取系统服务
     * @param text 要解析的文本内容
     */
    public static void parse(Context context, Spannable text) {
        if (text == null || text.length() == 0) return;

        // 1. 清除旧的智能链接
        URLSpan[] allSpans = text.getSpans(0, text.length(), URLSpan.class);
        for (URLSpan span : allSpans) {
            String url = span.getURL();
            if (url != null && (url.startsWith(SCHEME_TIME) || url.startsWith(SCHEME_GEO))) {
                text.removeSpan(span);
            }
        }

        // 2. 识别逻辑
        // 步骤 A: 优先识别时间（因为时间格式相对固定，误报率低）
        applyRegexLinks(text, Pattern.compile(TIME_REGEX, Pattern.CASE_INSENSITIVE), SCHEME_TIME);
        
        // 步骤 B: 识别地点（地点正则较宽松，需要避开已识别的时间）
        applyRegexLinks(text, Pattern.compile(GEO_REGEX), SCHEME_GEO);

        // 3. 使用系统级 AI (TextClassifier) 作为增强
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                TextClassificationManager tcm = context.getSystemService(TextClassificationManager.class);
                if (tcm != null) {
                    TextClassifier classifier = tcm.getTextClassifier();
                    TextLinks.Request request = new TextLinks.Request.Builder(text).build();
                    TextLinks links = classifier.generateLinks(request);
                    
                    for (TextLinks.TextLink link : links.getLinks()) {
                        String entityType = getTopEntity(link);
                        if ("address".equals(entityType) || "location".equals(entityType) || "place".equals(entityType)) {
                            applySmartSpan(text, link.getStart(), link.getEnd(), SCHEME_GEO);
                        } else if ("date".equals(entityType) || "datetime".equals(entityType)) {
                            applySmartSpan(text, link.getStart(), link.getEnd(), SCHEME_TIME);
                        }
                    }
                }
            } catch (Exception e) {
                // 静默回退
            }
        }
    }

    /**
     * 获取置信度最高的实体类型
     */
    private static String getTopEntity(TextLinks.TextLink link) {
        float maxConfidence = -1;
        String topType = null;
        for (int i = 0; i < link.getEntityCount(); i++) {
            String type = link.getEntity(i);
            float confidence = link.getConfidenceScore(type);
            if (confidence > maxConfidence) {
                maxConfidence = confidence;
                topType = type;
            }
        }
        return topType;
    }

    /**
     * 应用智能 Span 并处理重叠冲突
     */
    private static void applySmartSpan(Spannable text, int start, int end, String scheme) {
        // 1. 噪音修剪（特别是地点识别）
        if (SCHEME_GEO.equals(scheme)) {
            while (start < end && NOISE_PREFIXES.indexOf(text.charAt(start)) != -1) {
                start++;
            }
        }

        if (start >= end) return;

        // 2. 处理重叠冲突
        URLSpan[] existing = text.getSpans(start, end, URLSpan.class);
        if (existing.length > 0) {
            for (URLSpan span : existing) {
                int spanStart = text.getSpanStart(span);
                int spanEnd = text.getSpanEnd(span);
                
                // 如果当前识别结果完全落在已有 span 内部，则跳过
                if (start >= spanStart && end <= spanEnd) {
                    return;
                }
                
                // 如果当前识别结果包含了已有 span，尝试修剪当前结果的起始位置
                if (start < spanEnd && end > spanStart) {
                    // 如果重叠发生在开头，将起始位置移动到已有 span 之后
                    if (start < spanEnd) {
                        start = spanEnd;
                    }
                }
            }
        }

        // 再次检查修剪后的合法性
        if (start >= end) return;
        
        // 针对地点识别，修剪后可能剩下的是噪音或过短
        if (SCHEME_GEO.equals(scheme)) {
            // 再次修剪新起点处的噪音
            while (start < end && NOISE_PREFIXES.indexOf(text.charAt(start)) != -1) {
                start++;
            }
            // 如果剩下的文本太短（如只有 1 个字且不是后缀），则放弃
            if (end - start < 2) return;
        }

        text.setSpan(new SmartURLSpan(scheme + text.subSequence(start, end)), 
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 正则应用链接
     */
    private static void applyRegexLinks(Spannable text, Pattern pattern, String scheme) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            applySmartSpan(text, m.start(), m.end(), scheme);
        }
    }
}
