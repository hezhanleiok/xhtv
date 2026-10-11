package com.github.catvod.crawler;

import android.text.TextUtils;
import android.util.Log;

public class SpiderDebug {

    private static final String TAG = "Spider";

    public static void log(Throwable th) {
        if (th == null) return;
        th.printStackTrace();
        // 同时打到 logcat，便于过滤 TAG=Spider 定位 Guard 加载失败原因
        Log.e(TAG, "spider", th);
    }

    public static void log(String msg) {
        if (!TextUtils.isEmpty(msg)) Log.d(TAG, msg);
    }

    public static void log(String tag, String msg, Object... args) {
        if (TextUtils.isEmpty(msg)) return;
        try {
            Log.d(tag, String.format(msg, args));
        } catch (Exception e) {
            Log.d(tag, msg);
        }
    }
}
