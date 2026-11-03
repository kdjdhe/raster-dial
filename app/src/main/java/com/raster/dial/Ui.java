package com.raster.dial;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Apple-HIG 风格设计系统（无 AndroidX，纯平台 View）。
 *
 * 排版：字号-字重-字距-行距成套（大字负字距、小字正字距）；
 * 材质：分层表面 + 发丝线描边，不叠半透明；
 * 动效：弹簧（damping/response），总是从当前值起跳，可中断；
 * 反馈：按压即缩放 + ripple，提交动作触发触觉。
 */
public final class Ui {

    // ---------- 主题色板：全部色值经 applyTheme 写入，各处只读字段 ----------
    public static final String[] THEME_NAMES = {
            "曜石", "冰川", "森绿", "霞紫", "绯红", "鎏金",
    };

    /** 一套完整配色：分层表面 + 强调色 + 文字层级。 */
    private static final class Palette {
        final int bg, bg2, card, cardHi, accent, onAccent, text, secondary, tertiary, danger;

        Palette(int bg, int bg2, int card, int cardHi, int accent, int onAccent,
                int text, int secondary, int tertiary, int danger) {
            this.bg = bg;
            this.bg2 = bg2;
            this.card = card;
            this.cardHi = cardHi;
            this.accent = accent;
            this.onAccent = onAccent;
            this.text = text;
            this.secondary = secondary;
            this.tertiary = tertiary;
            this.danger = danger;
        }
    }

    private static final Palette[] THEMES = {
            // 曜石（默认）：近黑 + 橙
            new Palette(0xFF0A0A0F, 0xFF101018, 0xFF16161F, 0xFF20202C,
                    0xFFFF7A3D, 0xFF1A0D05, 0xFFF5F5F7, 0xFFA2A2AE, 0xFF6F6F7C, 0xFFFF6B6B),
            // 冰川：冷蓝黑 + 青
            new Palette(0xFF0A0F14, 0xFF0F151C, 0xFF141B24, 0xFF1D2733,
                    0xFF4FC3F7, 0xFF06212E, 0xFFF0F5F8, 0xFF9FB4C2, 0xFF66798A, 0xFFFF6B7A),
            // 森绿：墨绿 + 荧绿
            new Palette(0xFF0A120D, 0xFF0F1A13, 0xFF132119, 0xFF1B2C22,
                    0xFF3DDC97, 0xFF06231A, 0xFFEFF7F2, 0xFFA2B8AC, 0xFF6C8177, 0xFFFF6B6B),
            // 霞紫：暗紫 + 雾紫
            new Palette(0xFF0F0A14, 0xFF150F1C, 0xFF1A1424, 0xFF251D33,
                    0xFFB08CFF, 0xFF1D1040, 0xFFF5F2F8, 0xFFACA2BE, 0xFF746C87, 0xFFFF6B8B),
            // 绯红：暗酒红 + 玫红
            new Palette(0xFF140A0C, 0xFF1C0F12, 0xFF231418, 0xFF301C21,
                    0xFFFF5C7A, 0xFF2E0810, 0xFFF8F2F3, 0xFFBEA2A8, 0xFF876C72, 0xFFFFA94D),
            // 鎏金：暖黑 + 金
            new Palette(0xFF121008, 0xFF181509, 0xFF1E1B10, 0xFF2A2517,
                    0xFFF2C14E, 0xFF241A03, 0xFFF7F4EA, 0xFFBAB2A0, 0xFF857D6B, 0xFFFF6B6B),
    };

    public static int BG, BG2, CARD, CARD_HI, ACCENT, ACCENT_DIM,
            ON_ACCENT, TEXT, SECONDARY, TERTIARY, DANGER;
    public static final int HAIRLINE = 0x14FFFFFF;
    public static final int RIPPLE = 0x2AFFFFFF;
    public static int themeIndex = 0;
    /** 全局卡片圆角（dp，0–36），设置页滑杆可调。 */
    public static int RADIUS = 22;
    /** 全局触觉开关（设置页“震动反馈”）。 */
    public static boolean haptics = true;

    public static void setHaptics(boolean v) {
        haptics = v;
    }

    /** 设置并持久化卡片圆角（dp）；改完需重建界面以生效。 */
    public static void setRadius(Context c, int dpValue) {
        RADIUS = Math.max(0, Math.min(36, dpValue));
        c.getSharedPreferences("lock", Context.MODE_PRIVATE)
                .edit().putInt("radius", RADIUS).apply();
    }

    /** 应用并持久化主题；切换后需重建界面（或重进）以刷新所有已建视图。 */
    public static void applyTheme(Context c, int idx) {
        themeIndex = Math.max(0, Math.min(THEMES.length - 1, idx));
        Palette p = THEMES[themeIndex];
        BG = p.bg;
        BG2 = p.bg2;
        CARD = p.card;
        CARD_HI = p.cardHi;
        ACCENT = p.accent;
        ACCENT_DIM = (0x24 << 24) | (p.accent & 0xFFFFFF);
        ON_ACCENT = p.onAccent;
        TEXT = p.text;
        SECONDARY = p.secondary;
        TERTIARY = p.tertiary;
        DANGER = p.danger;
        c.getSharedPreferences("lock", Context.MODE_PRIVATE)
                .edit().putInt("theme", themeIndex).apply();
    }

    /** 从偏好恢复主题与触觉开关（各 Activity onCreate 时调用）。 */
    public static void loadTheme(Context c) {
        android.content.SharedPreferences sp =
                c.getSharedPreferences("lock", Context.MODE_PRIVATE);
        applyTheme(c, sp.getInt("theme", 0));
        haptics = sp.getBoolean("haptics", true);
        RADIUS = Math.max(0, Math.min(36, sp.getInt("radius", 22)));
    }

    public static int themeAccent(int idx) {
        return THEMES[Math.max(0, Math.min(THEMES.length - 1, idx))].accent;
    }

    public static int themeBg(int idx) {
        return THEMES[Math.max(0, Math.min(THEMES.length - 1, idx))].bg;
    }

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- 形状 ----------
    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** 卡片表面：圆角 + 发丝线描边。 */
    public static GradientDrawable surface(Context c, int color, float radiusDp) {
        GradientDrawable d = round(c, color, radiusDp);
        d.setStroke(dp(c, 1), HAIRLINE);
        return d;
    }

    public static RippleDrawable ripple(Context c, int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE),
                round(c, color, radiusDp), null);
    }

    // ---------- 排版刻度：weight + size + tracking 成套 ----------
    private static TextView type(Context c, String s, float sp, int color,
                                 int weight, float trackingEm, float lineSpacingDp) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", weight));
        if (trackingEm != 0f) {
            t.setLetterSpacing(trackingEm);
        }
        if (lineSpacingDp > 0f) {
            t.setLineSpacing(dp(c, lineSpacingDp), 1f);
        }
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView largeTitle(Context c, String s) {
        return type(c, s, 28, TEXT, Typeface.BOLD, -0.02f, 0);
    }

    public static TextView title(Context c, String s) {
        return type(c, s, 20, TEXT, Typeface.BOLD, -0.01f, 0);
    }

    public static TextView heading(Context c, String s) {
        return type(c, s, 16, TEXT, Typeface.BOLD, 0f, 0);
    }

    public static TextView body(Context c, String s) {
        return type(c, s, 15, TEXT, Typeface.NORMAL, 0f, 0);
    }

    public static TextView callout(Context c, String s) {
        return type(c, s, 13, SECONDARY, Typeface.NORMAL, 0.01f, 0);
    }

    public static TextView footnote(Context c, String s) {
        return type(c, s, 11, TERTIARY, Typeface.NORMAL, 0.02f, 2);
    }

    public static TextView overline(Context c, String s) {
        return type(c, s, 11, SECONDARY, Typeface.BOLD, 0.06f, 0);
    }

    /** 兼容旧调用的快捷文本。 */
    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        return type(c, s, sp, color, bold ? Typeface.BOLD : Typeface.NORMAL, 0f, 0);
    }

    // ---------- 卡片 ----------
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18));
        l.setBackground(surface(c, CARD, RADIUS));
        return l;
    }

    // ---------- 按钮 / chip ----------
    public static TextView button(Context c, String s, boolean primary) {
        TextView t = type(c, s, 15, primary ? ON_ACCENT : TEXT,
                Typeface.BOLD, 0f, 0);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        t.setBackground(ripple(c, primary ? ACCENT : CARD_HI, 15));
        t.setClickable(true);
        t.setFocusable(true);
        return pressable(t);
    }

    public static TextView chip(Context c, String s, boolean selected) {
        TextView t = type(c, s, 13, selected ? ON_ACCENT : TEXT,
                selected ? Typeface.BOLD : Typeface.NORMAL, 0f, 0);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 9), dp(c, 14), dp(c, 9));
        t.setBackground(ripple(c, selected ? ACCENT : CARD_HI, 999));
        t.setClickable(true);
        t.setFocusable(true);
        return pressable(t);
    }

    /** 装饰字形（‹ × ⚙ ✓ › ＋）：固定 dp 字号，不随系统字号放大而被小容器裁切。 */
    public static void glyph(TextView t, float dpSize) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, dpSize);
    }

    /** 设置式列表行：标题 + 可选值 + ›；分组容器负责圆角裁切。 */
    public static LinearLayout listRow(Context c, String title, String tail, boolean danger) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 52));
        row.setPadding(dp(c, 16), dp(c, 8), dp(c, 14), dp(c, 8));
        row.setForeground(new RippleDrawable(ColorStateList.valueOf(RIPPLE),
                null, new GradientDrawable()));
        row.setClickable(true);
        row.setFocusable(true);
        pressable(row);

        TextView tv = type(c, title, 15, danger ? DANGER : TEXT,
                danger ? Typeface.BOLD : Typeface.NORMAL, 0f, 0);
        row.addView(tv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (tail != null && !tail.isEmpty()) {
            TextView value = type(c, tail, 13, TERTIARY, Typeface.NORMAL, 0f, 0);
            value.setPadding(dp(c, 8), 0, 0, 0);
            row.addView(value, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        TextView chevron = type(c, "›", 18, danger ? DANGER : TERTIARY,
                Typeface.NORMAL, 0f, 0);
        glyph(chevron, 18);
        row.addView(chevron, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** 分组容器：包住若干 listRow。 */
    public static LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(surface(c, CARD, RADIUS));
        g.setClipToOutline(true);
        return g;
    }

    /** 给分组容器加行 + 行间发丝分隔线（首行无）。 */
    public static void groupRow(LinearLayout group, View row, Context c) {
        if (group.getChildCount() > 0) {
            View line = new View(c);
            line.setBackgroundColor(HAIRLINE);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 0.67f));
            llp.leftMargin = dp(c, 16);
            group.addView(line, llp);
        }
        group.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ---------- 按压反馈：down 即缩放，up 弹簧回位 ----------
    public static <V extends View> V pressable(V v) {
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scale(view, 0.97f, 0, 1f, 0.12f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scale(view, 1f, 0, 0.8f, 0.4f);
                    break;
                default:
                    break;
            }
            return false; // 不消费，click / ripple 照常
        });
        return v;
    }

    // ---------- 触觉：提交类动作调用 ----------
    public static void haptic(View v) {
        if (!haptics) {
            return;
        }
        int id = Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY;
        v.performHapticFeedback(id);
    }

    /** 轻 tick：拖动跨越卡位等细粒度反馈。 */
    public static void hapticTick(View v) {
        if (!haptics) {
            return;
        }
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    /** 力度渐变的 tick：strength 0..1 -> 振幅约 20..220、时长 6..12ms（灵敏度滑杆用）。 */
    public static void hapticTick(View v, float strength) {
        if (!haptics) {
            return;
        }
        float sat = Math.max(0f, Math.min(1f, strength));
        Vibrator vib = vibrator(v.getContext());
        if (vib == null || !vib.hasVibrator() || !vib.hasAmplitudeControl()) {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            return;
        }
        int amp = Math.max(1, Math.round(20 + 200 * sat));
        int ms = 6 + Math.round(6 * sat);
        vib.vibrate(VibrationEffect.createOneShot(ms, amp));
    }

    private static Vibrator vibrator(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm =
                    (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** 长震：长按进入某状态（如排序）。 */
    public static void hapticLong(View v) {
        if (!haptics) {
            return;
        }
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    // ---------- 弹簧：damping(ζ) / response(s)，从当前值起跳、可中断 ----------
    private static final class Spring implements TimeInterpolator {
        private final float zeta, omega, durationMs;

        Spring(float damping, float response, float durationMs) {
            this.zeta = damping;
            this.omega = (float) (2 * Math.PI / response);
            this.durationMs = durationMs;
        }

        @Override
        public float getInterpolation(float t) {
            float sec = t * durationMs / 1000f;
            if (zeta < 1f) {
                float wd = omega * (float) Math.sqrt(1 - zeta * zeta);
                return (float) (1 - Math.exp(-zeta * omega * sec)
                        * (Math.cos(wd * sec)
                        + zeta / Math.sqrt(1 - zeta * zeta) * Math.sin(wd * sec)));
            }
            return (float) (1 - (1 + omega * sec) * Math.exp(-omega * sec));
        }
    }

    private static final Map<View, Map<String, ValueAnimator>> RUNNING = new HashMap<>();

    /** 按（View, 属性）登记：同一 View 的不同属性动画互不取消。 */
    private static void track(View v, String prop, ValueAnimator a) {
        Map<String, ValueAnimator> m = RUNNING.get(v);
        if (m == null) {
            m = new HashMap<>();
            RUNNING.put(v, m);
        }
        ValueAnimator old = m.get(prop);
        if (old != null) {
            old.cancel();
        }
        m.put(prop, a);
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator anim) {
                Map<String, ValueAnimator> mm = RUNNING.get(v);
                if (mm != null) {
                    mm.remove(prop);
                }
            }
        });
    }

    /** 取消该 View 上所有进行中的弹簧（拖拽接管前用）。 */
    public static void cancel(View v) {
        Map<String, ValueAnimator> m = RUNNING.get(v);
        if (m == null) {
            return;
        }
        for (ValueAnimator a : new java.util.ArrayList<>(m.values())) {
            a.cancel();
        }
        m.clear();
    }

    /** 弹簧动画到目标 alpha。 */
    public static void alpha(View v, float to) {
        float from = v.getAlpha();
        if (Math.abs(to - from) < 0.01f) {
            v.setAlpha(to);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setInterpolator(new Spring(1f, 0.3f, 260));
        a.addUpdateListener(anim ->
                v.setAlpha(from + (to - from) * (float) anim.getAnimatedValue()));
        track(v, "alpha", a);
        a.start();
    }

    private static void scale(View v, float to, int delayMs, float damping, float response) {
        float from = v.getScaleX();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setStartDelay(delayMs);
        a.setInterpolator(new Spring(damping, response,
                Math.max(120, response * 5000)));
        a.addUpdateListener(anim -> {
            float s = from + (to - from) * (float) anim.getAnimatedValue();
            v.setScaleX(s);
            v.setScaleY(s);
        });
        track(v, "scale", a);
        a.start();
    }

    /** 弹簧动画到目标 translationY。 */
    public static void springY(View v, float to) {
        float from = v.getTranslationY();
        if (Math.abs(to - from) < 0.5f) {
            v.setTranslationY(to);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setInterpolator(new Spring(1f, 0.4f, 420));
        a.addUpdateListener(anim ->
                v.setTranslationY(from + (to - from) * (float) anim.getAnimatedValue()));
        track(v, "y", a);
        a.start();
    }

    /** 弹簧动画到目标 (translationX, translationY)：排序换位用。 */
    public static void springXY(View v, float x, float y) {
        float fx = v.getTranslationX(), fy = v.getTranslationY();
        if (Math.abs(x - fx) < 0.5f && Math.abs(y - fy) < 0.5f) {
            v.setTranslationX(x);
            v.setTranslationY(y);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setInterpolator(new Spring(0.85f, 0.3f, 320));
        a.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            v.setTranslationX(fx + (x - fx) * t);
            v.setTranslationY(fy + (y - fy) * t);
        });
        track(v, "xy", a);
        a.start();
    }

    /** 弹簧缩放（拿起/放下卡片）。 */
    public static void springScale(View v, float to) {
        float from = v.getScaleX();
        if (Math.abs(to - from) < 0.01f) {
            v.setScaleX(to);
            v.setScaleY(to);
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setInterpolator(new Spring(0.8f, 0.3f, 280));
        a.addUpdateListener(anim -> {
            float s = from + (to - from) * (float) anim.getAnimatedValue();
            v.setScaleX(s);
            v.setScaleY(s);
        });
        track(v, "scale", a);
        a.start();
    }

    /** 入场：淡入 + 上浮（弹簧），配合系统 animator scale 即 reduced-motion 降级。 */
    public static void appear(View v, int delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 14));
        v.postDelayed(() -> {
            alpha(v, 1f);
            springY(v, 0f);
        }, delayMs);
    }

    // ---------- 分段控件：滑动指示器 + 弹簧 ----------
    public interface OnSegment {
        void onSegment(int index);
    }

    public static LinearLayout segmented(Context c, String[] items, int initial,
                                         OnSegment listener) {
        // 外层胶囊；内层 FrameLayout 叠层：thumb 在下、等宽标签行在上。
        // 标签全部 weight 1f，段宽 = 行宽 / n，thumb 平移 idx * 段宽，几何确定。
        final LinearLayout seg = new LinearLayout(c);
        seg.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 3);
        seg.setPadding(pad, pad, pad, pad);
        seg.setBackground(round(c, 0xFF0D0D13, 999));
        seg.setClipToOutline(true);

        final int[] sel = {initial};
        final TextView[] labels = new TextView[items.length];

        FrameLayout stage = new FrameLayout(c);
        seg.addView(stage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final View thumb = new View(c);
        thumb.setBackground(round(c, 0x30FFFFFF, 999));
        stage.addView(thumb, new FrameLayout.LayoutParams(
                0, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView label = type(c, items[i], 13,
                    idx == initial ? TEXT : SECONDARY,
                    idx == initial ? Typeface.BOLD : Typeface.NORMAL, 0f, 0);
            label.setGravity(Gravity.CENTER);
            label.setClickable(true);
            label.setMinHeight(dp(c, 34));
            labels[i] = label;
            row.addView(label, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        stage.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        final float[] segW = {0f};
        seg.post(() -> {
            segW[0] = row.getWidth() / (float) items.length;
            FrameLayout.LayoutParams tlp =
                    (FrameLayout.LayoutParams) thumb.getLayoutParams();
            tlp.width = Math.round(segW[0]);
            thumb.setLayoutParams(tlp);
            thumb.setTranslationX(sel[0] * segW[0]);
        });

        // 点击与左右滑动共用同一个选择入口（滑动切换也带弹簧和触觉）
        final class Selector {
            void go(int idx, View from) {
                if (idx < 0 || idx >= items.length || idx == sel[0] || segW[0] <= 0f) {
                    return;
                }
                haptic(from);
                labels[sel[0]].setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                labels[sel[0]].setTextColor(SECONDARY);
                labels[idx].setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                labels[idx].setTextColor(TEXT);
                // 弹簧滑到新段（从当前值起跳）
                float fromX = thumb.getTranslationX();
                float toX = idx * segW[0];
                ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
                a.setInterpolator(new Spring(0.8f, 0.3f, 300));
                a.addUpdateListener(anim -> thumb.setTranslationX(
                        fromX + (toX - fromX) * (float) anim.getAnimatedValue()));
                track(thumb, "x", a);
                a.start();
                sel[0] = idx;
                listener.onSegment(idx);
            }
        }
        final Selector selector = new Selector();

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            final TextView label = labels[i];
            label.setOnClickListener(v -> selector.go(idx, v));
            final float[] downX = {0f};
            final float[] downY = {0f};
            final boolean[] swiped = {false};
            label.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX[0] = ev.getX();
                        downY[0] = ev.getY();
                        swiped[0] = false;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dx = ev.getX() - downX[0];
                        float dy = ev.getY() - downY[0];
                        if (!swiped[0] && Math.abs(dx) > dp(c, 20)
                                && Math.abs(dx) > Math.abs(dy)) {
                            swiped[0] = true;
                            selector.go(sel[0] + (dx < 0 ? 1 : -1), v);
                        }
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                        if (!swiped[0]) {
                            v.performClick();
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return true;
                }
            });
        }
        return seg;
    }

    /**
     * ScrollView 滑到顶/底时轻震一次（快速滑到头“停住”的反馈）。
     * 只在从非边缘进入边缘时触发，带 350ms 冷却，避免连续事件重复震。
     */
    public static void attachEdgeTick(ScrollView sv) {
        final boolean[] atTop = {true};
        final boolean[] atBottom = {false};
        final long[] lastTs = {0};
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            android.view.View child = sv.getChildAt(0);
            if (child == null) {
                return;
            }
            int max = Math.max(0, child.getHeight() - sv.getHeight());
            boolean top = y <= 0;
            boolean bottom = y >= max - 2;
            long now = SystemClock.uptimeMillis();
            if ((bottom && !atBottom[0]) || (top && !atTop[0])) {
                if (now - lastTs[0] > 350) {
                    lastTs[0] = now;
                    hapticTick(sv);
                }
            }
            atTop[0] = top;
            atBottom[0] = bottom;
        });
    }

    // ---------- 布局小工具 ----------
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

    /** 平板/宽屏（sw≥640dp）时把内容列限宽 620dp 居中，避免卡片被拉得过宽。 */
    public static void adaptWide(Activity a, ScrollView scroll, LinearLayout content) {
        int sw = a.getResources().getConfiguration().screenWidthDp;
        if (sw < 640) {
            scroll.addView(content, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT));
            return;
        }
        FrameLayout wrap = new FrameLayout(a);
        scroll.addView(wrap, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                dp(a, 620), FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        wrap.addView(content, clp);
    }

    // ---------- 对话框 / 输入框：全应用统一材质 ----------

    /** 统一对话框：分层卡片 + 标题/说明 + 可选内容 + 取消/确认（可标红）。 */
    public static Dialog dialog(Activity a, String title, String message, View content,
                                String positive, boolean danger, Runnable onPositive,
                                String negative) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(a, 22);
        wrap.setPadding(pad, pad, pad, pad);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 16));
        card.setBackground(surface(a, CARD, RADIUS));

        card.addView(type(a, title, 20, TEXT, Typeface.BOLD, -0.01f, 0));
        if (message != null && !message.isEmpty()) {
            TextView m = type(a, message, 14, SECONDARY, Typeface.NORMAL, 0f, 0);
            m.setPadding(0, dp(a, 8), 0, 0);
            m.setLineSpacing(dp(a, 3), 1f);
            card.addView(m);
        }
        if (content != null) {
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.topMargin = dp(a, 16);
            card.addView(content, clp);
        }

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 20);
        card.addView(row, rlp);

        boolean hasCancel = negative != null && !negative.isEmpty();
        if (hasCancel) {
            TextView cancel = button(a, negative, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(a, 8);
            row.addView(cancel, lp);
            cancel.setOnClickListener(v -> {
                haptic(v);
                d.dismiss();
            });
        }
        TextView ok = button(a, positive, true);
        if (danger) {
            ok.setBackground(ripple(a, DANGER, 15));
        }
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        okLp.leftMargin = hasCancel ? dp(a, 8) : 0;
        row.addView(ok, okLp);
        ok.setOnClickListener(v -> {
            haptic(v);
            d.dismiss();
            onPositive.run();
        });

        wrap.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrap);

        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0));
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams wlp = win.getAttributes();
            wlp.dimAmount = 0.62f;
            int max = Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(a, 44),
                    dp(a, 420));
            wlp.width = Math.max(dp(a, 240), max);
            wlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            win.setAttributes(wlp);
        }
        d.show();
        if (content instanceof EditText) {
            content.requestFocus();
            if (win != null) {
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            }
        }
        return d;
    }

    /** 统一文本输入框（命名等）。 */
    public static EditText textField(Context c) {
        EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        e.setHintTextColor(TERTIARY);
        e.setBackground(round(c, CARD_HI, 12));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return e;
    }
}
