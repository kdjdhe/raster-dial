package com.raster.dial;

import android.content.Context;
import android.content.SharedPreferences;

/** 跨 Activity / 壁纸服务共享的偏好（轴、主屏卡片顺序等）。 */
public final class Prefs {

    public static final String[] CARD_KEYS = {"hero", "effect", "preview", "wall"};
    private static final String DEFAULT_ORDER = "hero,effect,preview,wall";

    private Prefs() {
    }

    public static SharedPreferences lock(Context c) {
        return c.getSharedPreferences("lock", Context.MODE_PRIVATE);
    }

    /**
     * 百叶窗 / 类OriginOS 的变化轴：0 自动 / 1 横向 / 2 竖向。
     * 旧版本 axis_h 只有 0 横向 / 1 竖向，首次读取时做迁移。
     */
    public static int axisH(Context c) {
        SharedPreferences sp = lock(c);
        if (sp.contains("axis_h2")) {
            return Math.max(0, Math.min(2, sp.getInt("axis_h2", 0)));
        }
        return sp.getInt("axis_h", 0) == 1 ? 2 : 1;
    }

    public static void setAxisH(Context c, int v) {
        lock(c).edit().putInt("axis_h2", Math.max(0, Math.min(2, v))).apply();
    }

    /** 主屏四张卡片的顺序（hero/effect/preview/wall）。 */
    public static String[] cardOrder(Context c) {
        String raw = lock(c).getString("card_order", DEFAULT_ORDER);
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw != null) {
            for (String s : raw.split(",")) {
                if (validKey(s) && !out.contains(s)) {
                    out.add(s);
                }
            }
        }
        for (String k : CARD_KEYS) {
            if (!out.contains(k)) {
                out.add(k);
            }
        }
        return out.toArray(new String[0]);
    }

    public static void setCardOrder(Context c, java.util.List<String> order) {
        StringBuilder b = new StringBuilder();
        for (String s : order) {
            if (b.length() > 0) {
                b.append(',');
            }
            b.append(s);
        }
        lock(c).edit().putString("card_order", b.toString()).apply();
    }

    private static boolean validKey(String s) {
        for (String k : CARD_KEYS) {
            if (k.equals(s)) {
                return true;
            }
        }
        return false;
    }
}
