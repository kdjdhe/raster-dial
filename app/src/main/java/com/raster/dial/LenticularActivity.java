package com.raster.dial;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全屏预览（调试用）：光栅渲染器 + 时钟 + 上滑退出。
 * 不接管锁屏、不常驻；真实效果在 LenticularWallpaperService。
 */
public class LenticularActivity extends Activity {

    private LenticularView view;
    private TiltTracker tracker;
    private TextView dateView;

    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean fixed;
    private float downX, downY;
    private long downT;

    private final Runnable autoFinish = new Runnable() {
        @Override
        public void run() {
            finish();
        }
    };

    private final Runnable calibrate = new Runnable() {
        @Override
        public void run() {
            if (tracker != null) {
                tracker.calibrate();
            }
        }
    };

    private final Runnable dateTick = new Runnable() {
        @Override
        public void run() {
            updateDate();
            h.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersive();

        FrameLayout root = new FrameLayout(this);

        String groupId = GroupCatalog.currentId(this);
        GroupCatalog.Group group = GroupCatalog.find(this, groupId);
        view = new LenticularView(this, group.id);
        view.setStyle(getSharedPreferences("lock", MODE_PRIVATE).getInt("style", 0));
        view.setAxis(getSharedPreferences("lock", MODE_PRIVATE).getInt("axis_line", 0),
                getSharedPreferences("lock", MODE_PRIVATE).getInt("axis_h", 0));
        view.setEyeStrength(getSharedPreferences("lock", MODE_PRIVATE)
                .getFloat("eye_care_strength", 0f));
        root.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB4000000, 0x00000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(460), Gravity.TOP));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(0, dp(118), 0, 0);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(76);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setLetterSpacing(-0.02f);
        clock.setTextColor(Color.WHITE);
        clock.setShadowLayer(14, 0, 4, 0xCC000000);
        clock.setGravity(Gravity.CENTER);
        clock.setOnLongClickListener(v -> {
            if (tracker != null) {
                tracker.calibrate();
                Toast.makeText(this, "已校准：当前姿态设为零点", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        top.addView(clock);

        dateView = new TextView(this);
        dateView.setTextSize(14);
        dateView.setLetterSpacing(0.04f);
        dateView.setTextColor(0xCCFFFFFF);
        dateView.setShadowLayer(10, 0, 2, 0xCC000000);
        dateView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(6);
        top.addView(dateView, dlp);

        root.addView(top, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        TextView hint = new TextView(this);
        int styleNow = Math.max(0, Math.min(2,
                getSharedPreferences("lock", MODE_PRIVATE).getInt("style", 0)));
        int axisLineNow = Math.max(0, Math.min(2,
                getSharedPreferences("lock", MODE_PRIVATE).getInt("axis_line", 0)));
        int axisHNow = Math.max(0, Math.min(1,
                getSharedPreferences("lock", MODE_PRIVATE).getInt("axis_h", 0)));
        String[] styleNames = {"过渡线", "百叶窗", "类OriginOS"};
        String axisDesc = styleNow == 0
                ? new String[]{"全向", "仅横向", "仅竖向"}[axisLineNow]
                : new String[]{"横向", "竖向"}[axisHNow];
        hint.setText(group.nameZh + " · " + styleNames[styleNow]
                + "（" + axisDesc + "） · 上滑退出");
        hint.setTextSize(12);
        hint.setLetterSpacing(0.03f);
        hint.setTextColor(0xD9FFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(16), dp(9), dp(16), dp(9));
        // 半透明胶囊提示条（材质浮层，压暗保证可读）
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x66000000);
        pill.setCornerRadius(dp(999));
        hint.setBackground(pill);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = dp(86);
        root.addView(hint, hlp);

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(22);
        close.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        close.setTextColor(0xE6FFFFFF);
        close.setGravity(Gravity.CENTER);
        int cs = dp(38);
        GradientDrawable circle = new GradientDrawable();
        circle.setColor(0x66000000);
        circle.setCornerRadius(dp(999));
        close.setBackground(circle);
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                cs, cs, Gravity.TOP | Gravity.END);
        clp.topMargin = dp(46);
        clp.rightMargin = dp(16);
        root.addView(close, clp);

        setContentView(root);
        root.setClickable(true);
        root.setOnTouchListener(this::onScreenTouch);

        SharedPreferences sp = getSharedPreferences("lock", MODE_PRIVATE);
        boolean invert = sp.getBoolean("invert", false);
        float sens = sp.getFloat("sens", 0.35f);

        Intent intent = getIntent();
        fixed = intent.hasExtra("progress");
        if (fixed) {
            float p = intent.getFloatExtra("progress", 0f);
            float a = intent.getFloatExtra("angle", 0f);
            view.setState(p, p, p, p * 0.7f, p * 0.7f,
                    (float) Math.cos(a), (float) Math.sin(a));
            hint.setText(String.format(Locale.US,
                    "固定预览 phase=%.2f  ·  %s（%s）  ·  点 × 退出",
                    p, styleNames[styleNow], axisDesc));
        } else {
            tracker = new TiltTracker(this, t -> view.setState(t.phase, t.phaseX,
                    t.phaseY, t.hx, t.hy, t.dirX, t.dirY));
            tracker.setInvert(invert);
            boolean land = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            tracker.setRange(sens * (land ? 1.2f : 1f));
            Display d = getWindowManager().getDefaultDisplay();
            tracker.setDisplayRotation(d.getRotation(), TiltTracker.naturalLandscape(d));
        }

        updateDate();
        h.postDelayed(dateTick, 60_000);
        h.postDelayed(autoFinish, 5 * 60 * 1000L);
    }

    private boolean onScreenTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downT = SystemClock.uptimeMillis();
                return true;
            case MotionEvent.ACTION_UP:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                long dt = SystemClock.uptimeMillis() - downT;
                if (dy < -dp(110) && Math.abs(dx) < dp(130) && dt < 900) {
                    finish();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) {
            view.onResume();
        }
        if (!fixed && tracker != null) {
            Display d = getWindowManager().getDefaultDisplay();
            tracker.setDisplayRotation(d.getRotation(), TiltTracker.naturalLandscape(d));
            boolean land = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            float sens = getSharedPreferences("lock", MODE_PRIVATE).getFloat("sens", 0.35f);
            tracker.setRange(sens * (land ? 1.2f : 1f));
            tracker.start();
            h.postDelayed(calibrate, 500);
        }
        applyImmersive();
    }

    @Override
    protected void onPause() {
        h.removeCallbacks(calibrate);
        if (tracker != null) {
            tracker.stop();
        }
        if (view != null) {
            view.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (tracker != null) {
            tracker.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void updateDate() {
        dateView.setText(new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date()));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
