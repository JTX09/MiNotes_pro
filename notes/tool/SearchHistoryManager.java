package net.micode.notes.tool;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class SearchHistoryManager {
    private static final String PREF_NAME = "search_history";
    private static final String KEY_HISTORY = "history_list";
    private static final int MAX_HISTORY_SIZE = 10;

    private final SharedPreferences mPrefs;

    public SearchHistoryManager(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<String> getHistory() {
        String json = mPrefs.getString(KEY_HISTORY, "");
        List<String> list = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return list;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addHistory(String keyword) {
        if (TextUtils.isEmpty(keyword)) return;
        List<String> history = getHistory();
        // Remove existing to move to top
        history.remove(keyword);
        history.add(0, keyword);
        // Limit size
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(0, MAX_HISTORY_SIZE);
        }
        saveHistory(history);
    }

    public void removeHistory(String keyword) {
        List<String> history = getHistory();
        if (history.remove(keyword)) {
            saveHistory(history);
        }
    }

    public void clearHistory() {
        mPrefs.edit().remove(KEY_HISTORY).apply();
    }

    private void saveHistory(List<String> history) {
        JSONArray array = new JSONArray(history);
        mPrefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }
}
