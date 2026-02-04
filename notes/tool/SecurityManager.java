package net.micode.notes.tool;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 安全管理器
 * <p>
 * 负责管理应用的隐私锁功能，包括密码的设置、验证、清除以及密码类型的管理。
 * 使用SHA-256对密码进行哈希存储，保障安全性。
 * </p>
 */
public class SecurityManager {
    private static SecurityManager sInstance;
    private Context mContext;
    
    private static final String PREFERENCE_NAME = "notes_preferences";
    private static final String PREF_PASSWORD_TYPE = "security_password_type";
    private static final String PREF_PASSWORD_HASH = "security_password_hash";
    
    /** 无密码 */
    public static final int TYPE_NONE = 0;
    /** 数字密码 (PIN) */
    public static final int TYPE_PIN = 1;
    /** 手势密码 (Pattern) */
    public static final int TYPE_PATTERN = 2;

    private SecurityManager(Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * 获取单例实例
     * @param context 上下文
     * @return SecurityManager实例
     */
    public static synchronized SecurityManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SecurityManager(context);
        }
        return sInstance;
    }

    /**
     * 检查是否已设置密码
     * @return true 如果已设置密码
     */
    public boolean isPasswordSet() {
        return getPasswordType() != TYPE_NONE;
    }

    /**
     * 获取当前密码类型
     * @return 密码类型 (TYPE_NONE, TYPE_PIN, TYPE_PATTERN)
     */
    public int getPasswordType() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREF_PASSWORD_TYPE, TYPE_NONE);
    }

    /**
     * 验证密码
     * @param input 用户输入的密码（明文）
     * @return true 如果密码正确
     */
    public boolean checkPassword(String input) {
        if (!isPasswordSet()) return true;
        if (input == null) return false;
        
        String hash = getHash(input);
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        String savedHash = prefs.getString(PREF_PASSWORD_HASH, "");
        return TextUtils.equals(hash, savedHash);
    }

    /**
     * 设置密码
     * @param input 密码明文
     * @param type 密码类型
     */
    public void setPassword(String input, int type) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_PASSWORD_TYPE, type);
        editor.putString(PREF_PASSWORD_HASH, getHash(input));
        editor.commit();
    }
    
    /**
     * 移除密码
     */
    public void removePassword() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_PASSWORD_TYPE, TYPE_NONE);
        editor.remove(PREF_PASSWORD_HASH);
        editor.commit();
    }

    /**
     * 计算SHA-256哈希值
     * @param input 输入字符串
     * @return Base64编码的哈希值
     */
    private String getHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return input; // 理论上不会发生
        }
    }
}
