package kr.ac.snu.nxc.cloudcamera.util;

import android.util.Log;

public class CCLog {
    private static final String TAG = "CloudCamera";
    public static boolean sLogOn = true;
    private static final int REAL_METHOD_POS = 2;

    private static String prefix() {
        StackTraceElement[] ste = new Throwable().getStackTrace();
        StackTraceElement realMethod = ste[REAL_METHOD_POS];

        String threadName = Thread.currentThread().getName();
        return "[" + realMethod.getFileName() + ":"
                + realMethod.getLineNumber() + ":"
                + realMethod.getMethodName() + "()-" + "[Thread:" + threadName + "] ";
    }

    public static void d(String tag, String msg) {
        if (sLogOn) {
            Log.d(TAG + "[" + tag + "]", prefix() + msg);
        }
    }

    public static void i(String tag, String msg) {
        if (sLogOn) {
            Log.i(TAG + "[" + tag + "]", prefix() + msg);
        }
    }

    public static void e(String tag, String msg) {
        // if (LOG_ON) {
        Log.e(TAG + "[" + tag + "]", prefix() + msg);
        // }
    }

    public static void v(String tag, String msg) {
        if (sLogOn) {
            Log.v(TAG + "[" + tag + "]", prefix() + msg);
        }
    }

    public static void w(String tag, String msg) {
        if (sLogOn) {
            Log.w(TAG + "[" + tag + "]", prefix() + msg);
        }
    }

    public static void d(String tag, String msg, Throwable tr) {
        if (sLogOn) {
            Log.d(TAG + "[" + tag + "]", prefix() + msg, tr);
        }
    }

    public static void i(String tag, String msg, Throwable tr) {
        if (sLogOn) {
            Log.i(TAG + "[" + tag + "]", prefix() + msg, tr);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (sLogOn) {
            Log.e(TAG + "[" + tag + "]", prefix() + msg, tr);
        }
    }

    public static void v(String tag, String msg, Throwable tr) {
        if (sLogOn) {
            Log.v(TAG + "[" + tag + "]", prefix() + msg, tr);
        }
    }

    public static void w(String tag, String msg, Throwable tr) {
        if (sLogOn) {
            Log.w(TAG + "[" + tag + "]", prefix() + msg, tr);
        }
    }
}
