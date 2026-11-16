package com.farewell.dynbarland;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * v0.1 (eksploratif): mencoba mempertahankan Dynamic Bar statis (view "island_resident_bar" milik
 * SystemUI) saat layar landscape. Hook bekerja di level View/Window sehingga tidak bergantung pada
 * nama kelas yang diobfuskasi. Semua kejadian penting dicatat ke log LSPosed supaya hook bisa
 * dipertajam di versi berikutnya.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[DynBarLand] ";
    public static final String KEY_ENABLE = "dynbar_land_static";      // 1 = aktif (default)
    public static final String KEY_AGGRESSIVE = "dynbar_land_aggressive"; // 1 = blokir pelepasan window (default 0)
    private static final String ISLAND_ID_NAME = "island_resident_bar";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final int MAX_LOGS = 300;

    private static final AtomicInteger LOG_COUNT = new AtomicInteger();
    private static volatile int islandId = 0;  // >0 ditemukan, 0 belum diketahui
    private static volatile int idAttempts = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI.equals(lpparam.packageName)) {
            return;
        }
        log("memasang hook di SystemUI (proses " + lpparam.processName + ")");
        try {
            hookViewVisibility();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
        try {
            hookWindowGlobal();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    // ------------------------------------------------------------------ hook

    private static void hookViewVisibility() {
        XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int visibility = (Integer) param.args[0];
                if (visibility == View.VISIBLE) {
                    return;
                }
                View v = (View) param.thisObject;
                if (!isIslandBar(v)) {
                    return;
                }
                boolean land = isLandscape(v);
                boolean enabled = readFlag(v, KEY_ENABLE, 1);
                log("setVisibility(" + visibility + ") land=" + land + " aktif=" + enabled + shortStack());
                if (land && enabled) {
                    param.args[0] = View.VISIBLE;
                }
            }
        });
    }

    private static void hookWindowGlobal() throws Throwable {
        Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");

        XposedBridge.hookAllMethods(wmg, "removeView", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof View)) {
                    return;
                }
                View v = (View) param.args[0];
                if (!containsIsland(v)) {
                    return;
                }
                boolean land = isLandscape(v);
                boolean enabled = readFlag(v, KEY_ENABLE, 1);
                boolean aggressive = readFlag(v, KEY_AGGRESSIVE, 0);
                log("removeView window island land=" + land + " aktif=" + enabled
                        + " agresif=" + aggressive + shortStack());
                if (land && enabled && aggressive) {
                    log("removeView diblokir (mode agresif)");
                    param.setResult(null);
                }
            }
        });

        XposedBridge.hookAllMethods(wmg, "addView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args == null || param.args.length < 2 || !(param.args[0] instanceof View)) {
                    return;
                }
                View v = (View) param.args[0];
                if (!containsIsland(v)) {
                    return;
                }
                StringBuilder sb = new StringBuilder("addView window island land=" + isLandscape(v));
                if (param.args[1] instanceof WindowManager.LayoutParams) {
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.args[1];
                    sb.append(" gravity=").append(lp.gravity)
                            .append(" type=").append(lp.type)
                            .append(" flags=0x").append(Integer.toHexString(lp.flags))
                            .append(" x=").append(lp.x).append(" y=").append(lp.y)
                            .append(" w=").append(lp.width).append(" h=").append(lp.height);
                } else if (param.args[1] instanceof ViewGroup.LayoutParams) {
                    ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) param.args[1];
                    sb.append(" w=").append(lp.width).append(" h=").append(lp.height);
                }
                log(sb.toString());
            }
        });
    }

    // ----------------------------------------------------------------- util

    private static int resolveIslandId(View v) {
        int id = islandId;
        if (id > 0) {
            return id;
        }
        if (idAttempts++ > 40) {
            return 0;
        }
        try {
            Resources r = v.getResources();
            int found = r.getIdentifier(ISLAND_ID_NAME, "id", SYSTEMUI);
            if (found != 0) {
                islandId = found;
                log("id " + ISLAND_ID_NAME + " ditemukan: 0x" + Integer.toHexString(found));
                return found;
            }
        } catch (Throwable ignored) {
            // coba lagi pada pemanggilan berikutnya
        }
        return 0;
    }

    private static boolean isIslandBar(View v) {
        int id = v.getId();
        if (id == View.NO_ID) {
            return false;
        }
        int target = resolveIslandId(v);
        return target > 0 && id == target;
    }

    private static boolean containsIsland(View root) {
        if (isIslandBar(root)) {
            return true;
        }
        int target = islandId;
        if (target > 0) {
            try {
                return root.findViewById(target) != null;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isLandscape(View v) {
        try {
            return v.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean readFlag(View v, String key, int def) {
        try {
            return Settings.Global.getInt(v.getContext().getContentResolver(), key, def) == 1;
        } catch (Throwable t) {
            return def == 1;
        }
    }

    private static String shortStack() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StackTraceElement e : st) {
            String c = e.getClassName();
            if (c.startsWith("de.robv") || c.startsWith("LSPHooker") || c.startsWith("com.farewell")
                    || c.startsWith("android.view.View")) {
                continue;
            }
            sb.append("\n   at ").append(c).append('.').append(e.getMethodName())
                    .append(':').append(e.getLineNumber());
            if (++n >= 10) {
                break;
            }
        }
        return sb.toString();
    }

    private static void log(String s) {
        if (LOG_COUNT.incrementAndGet() <= MAX_LOGS) {
            XposedBridge.log(TAG + s);
        }
    }
}
