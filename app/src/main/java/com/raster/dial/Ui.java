package com.raster.dial;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 轻量 UI 工具：深色卡片、圆角、ripple、chip。不依赖 AndroidX。 */
public final class Ui {

    public static final int BG = 0xFF0B0B10;
    public static final int BG2 = 0xFF12121A;
    public static final int SURFACE = 0xFF17171F;
    public static final int SURFACE2 = 0xFF23232E;
    public static final int ACCENT = 0xFFFF7A3D;
    public static final int ON_ACCENT = 0xFF1A0D05;
    public static final int TEXT = 0xFFF5F5F7;
    public static final int MUTED = 0xFF9A9AA5;
    public static final int STROKE = 0x22FFFFFF;

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(Context c, int color, float radiusDp) {
        GradientDrawable d = round(c, color, radiusDp);
        d.setStroke(dp(c, 1), STROKE);
        return d;
    }

    public static RippleDrawable ripple(Context c, int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                round(c, color, radiusDp), null);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        l.setBackground(roundStroke(c, SURFACE, 20));
        return l;
    }

    public static TextView button(Context c, String s, boolean primary) {
        TextView t = text(c, s, 15, primary ? ON_ACCENT : TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        t.setBackground(ripple(c, primary ? ACCENT : SURFACE2, 14));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static TextView chip(Context c, String s, boolean selected) {
        TextView t = text(c, s, 13, selected ? ON_ACCENT : TEXT, selected);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        t.setBackground(ripple(c, selected ? ACCENT : SURFACE2, 18));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static void addWithTop(LinearLayout parent, View v, Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        parent.addView(v, lp);
    }

    public static LinearLayout.LayoutParams weight(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.topMargin = dp(c, topDp);
        return lp;
    }
}
