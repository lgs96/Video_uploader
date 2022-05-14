package kr.ac.snu.nxc.cloudcamera.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public class CCPreferences {
    private static SharedPreferences preferences = null;
    private static SharedPreferences.Editor editor = null;

    public static boolean checkInitialized() {
        return preferences != null;
    }

    public static void initPreferences(Context context) {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
            editor = preferences.edit();
        }
    }

    public static SharedPreferences getPreferences() {
        return preferences;
    }

    public static void setString(String key, String value) {
        editor.putString(key, value);
        editor.commit();
    }

    public static void setStringList(String key, ArrayList<String> list) {
        Set<String> set = new TreeSet<String>();
        set.addAll(list);

        editor.putStringSet(key, set);
        editor.apply();
    }

    public static void setInt(String key, int value) {
        editor.putInt(key, value);
        editor.commit();
    }

    public static void setLong(String key, long value) {
        editor.putLong(key, value);
        editor.commit();
    }

    public static void setFloat(String key, float value) {
        editor.putFloat(key, value);
        editor.commit();
    }

    public static void setBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.commit();
    }

    public static String getString(String key) {
        return preferences.getString(key, null);
    }

    public static ArrayList<String> getStringList(String key) {
        Set<String> set = preferences.getStringSet(key, null);
        return (set != null) ? new ArrayList<String>(set) : new ArrayList<String>();
    }

    public static int getInt(String key) {
        return preferences.getInt(key, -1);
    }

    public static float getFloat(String key) {
        return preferences.getFloat(key, 0.0f);

    }

    public static long getLong(String key) {
        return preferences.getLong(key, 0);
    }

    public static boolean getBoolean(String key) {
        return preferences.getBoolean(key, false);
    }
}
