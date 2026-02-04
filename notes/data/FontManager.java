package net.micode.notes.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

public class FontManager {
    public static final String PREF_FONT_FAMILY = "pref_font_family";

    private final SharedPreferences mPrefs;
    private static FontManager sInstance;

    private FontManager(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static synchronized FontManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FontManager(context.getApplicationContext());
        }
        return sInstance;
    }

    public void applyFont(TextView textView) {
        String fontValue = mPrefs.getString(PREF_FONT_FAMILY, "default");
        Typeface typeface = getTypeface(fontValue);
        if (typeface != null) {
            textView.setTypeface(typeface);
        } else {
            textView.setTypeface(Typeface.DEFAULT);
        }
    }

    private Typeface getTypeface(String fontValue) {
        switch (fontValue) {
            case "serif":
                return Typeface.SERIF;
            case "sans-serif":
                return Typeface.SANS_SERIF;
            case "monospace":
                return Typeface.MONOSPACE;
            case "cursive":
                // Android doesn't have a built-in cursive typeface constant, 
                // but we can try to load sans-serif-light or similar as a placeholder,
                // or load from assets if we had custom fonts.
                // For now, let's map it to serif-italic style if possible or just serif.
                return Typeface.create(Typeface.SERIF, Typeface.ITALIC); 
            case "default":
            default:
                return Typeface.DEFAULT;
        }
    }
}
