package com.github.catvod;

import android.content.Context;

import java.lang.ref.WeakReference;

public class Init {

    private WeakReference<Context> context;

    private static Init instance() {
        return Loader.INSTANCE;
    }

    public static void set(Context context) {
        instance().context = new WeakReference<>(context);
    }

    /** 兼容旧垫片：thunder AAR 调用的是 init(Context)。 */
    public static void init(Context context) {
        set(context);
    }

    public static Context context() {
        return instance().context.get();
    }

    /** 兼容旧垫片：thunder AAR 调用的是 get()。 */
    public static Context get() {
        return context();
    }

    private static class Loader {
        static volatile Init INSTANCE = new Init();
    }
}
