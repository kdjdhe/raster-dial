package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页：外观（主题）、版本更新、壁纸与调试工具。
 * 布局参考：返回圆钮 + 大标题，居中“关于”块，分区标签 + 分组卡片
 * （卡内用发丝分隔行：当前版本 / 说明 / 检查更新 / 工具）。
 */
public class SettingsActivity extends Activity {

    private boolean rebuilding;
    private ScrollView scrollRef;
    private int builtTheme;
    private int builtRadius;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.loadTheme(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildContent());
    }

    private void rebuild() {
        int y = scrollRef == null ? 0 : scrollRef.getScrollY();
        rebuilding = true;
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildContent());
        rebuilding = false;
        final ScrollView sc = scrollRef;
        if (sc != null) {
            sc.post(() -> sc.scrollTo(0, y));
        }
    }

    private ScrollView buildContent() {
        builtTheme = Ui.themeIndex;
        builtRadius = Ui.RADIUS;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        Ui.attachEdgeTick(scroll);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.BG, Ui.BG2}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 24),
                Ui.dp(this, 20), Ui.dp(this, 36));

        // ---------- 头部：返回圆钮 + 大标题 ----------
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);

        TextView back = new TextView(this);
        back.setText("‹");
        Ui.glyph(back, 22);
        back.setTextColor(Ui.TEXT);
        back.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        back.setGravity(Gravity.CENTER);
        int bs = Ui.dp(this, 40);
        back.setBackground(Ui.ripple(this, Ui.CARD_HI, 999));
        back.setClickable(true);
        back.setFocusable(true);
        Ui.pressable(back);
        back.setOnClickListener(v -> {
            Ui.haptic(v);
            finish();
        });
        head.addView(back, new LinearLayout.LayoutParams(bs, bs));

        TextView title = Ui.largeTitle(this, "设置");
        title.setPadding(0, Ui.dp(this, 12), 0, 0);
        head.addView(title);

        TextView sub = Ui.callout(this, "外观、版本更新与壁纸");
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        head.addView(sub);
        root.addView(head);

        // ---------- 关于块（居中） ----------
        LinearLayout about = new LinearLayout(this);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setGravity(Gravity.CENTER_HORIZONTAL);
        about.setPadding(0, Ui.dp(this, 26), 0, Ui.dp(this, 4));

        ImageView icon = new ImageView(this);
        int isz = Ui.dp(this, 64);
        icon.setLayoutParams(new LinearLayout.LayoutParams(isz, isz));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setBackground(Ui.round(this, Ui.CARD_HI, 18));
        icon.setClipToOutline(true);
        about.addView(icon);

        TextView name = Ui.largeTitle(this, "Raster Dial");
        name.setPadding(0, Ui.dp(this, 12), 0, 0);
        about.addView(name);

        TextView tag = Ui.callout(this, "光栅动态壁纸 · 倾斜切换画面");
        tag.setPadding(0, Ui.dp(this, 4), 0, 0);
        about.addView(tag);

        TextView ver = Ui.footnote(this, "版本 " + BuildConfig.VERSION_NAME);
        ver.setPadding(0, Ui.dp(this, 2), 0, 0);
        about.addView(ver);
        root.addView(about);

        // ---------- 外观 ----------
        root.addView(sectionLabel("外观"));

        LinearLayout lookCard = Ui.group(this);
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.VERTICAL);
        themeRow.setPadding(Ui.dp(this, 16), Ui.dp(this, 12),
                Ui.dp(this, 16), Ui.dp(this, 14));
        TextView themeLabel = Ui.text(this, "主题", 15, Ui.TEXT, false);
        themeRow.addView(themeLabel);

        android.widget.HorizontalScrollView tHsv = new android.widget.HorizontalScrollView(this);
        tHsv.setHorizontalScrollBarEnabled(false);
        tHsv.setClipToPadding(false);
        LinearLayout tRow = new LinearLayout(this);
        tRow.setOrientation(LinearLayout.HORIZONTAL);
        tRow.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        for (int i = 0; i < Ui.THEME_NAMES.length; i++) {
            final int idx = i;
            FrameLayout sw = new FrameLayout(this);
            GradientDrawable ring = Ui.round(this, Ui.themeBg(idx), 999);
            ring.setStroke(Ui.dp(this, idx == Ui.themeIndex ? 2 : 1),
                    idx == Ui.themeIndex ? Ui.ACCENT : Ui.HAIRLINE);
            sw.setBackground(ring);
            View dot = new View(this);
            int ds = Ui.dp(this, 16);
            dot.setBackground(Ui.round(this, Ui.themeAccent(idx), 999));
            sw.addView(dot, new FrameLayout.LayoutParams(ds, ds, Gravity.CENTER));
            LinearLayout.LayoutParams swlp = new LinearLayout.LayoutParams(
                    Ui.dp(this, 44), Ui.dp(this, 44));
            swlp.rightMargin = Ui.dp(this, 10);
            sw.setClickable(true);
            sw.setFocusable(true);
            sw.setContentDescription(Ui.THEME_NAMES[i]);
            Ui.pressable(sw);
            sw.setOnClickListener(v -> {
                if (idx == Ui.themeIndex) {
                    return;
                }
                Ui.haptic(v);
                Ui.applyTheme(this, idx);
                rebuild();
            });
            tRow.addView(sw, swlp);
        }
        tHsv.addView(tRow);
        themeRow.addView(tHsv);
        Ui.groupRow(lookCard, themeRow, this);

        // 全局卡片圆角：拖动实时显示数值，松手落盘并重建
        LinearLayout radiusRow = new LinearLayout(this);
        radiusRow.setOrientation(LinearLayout.VERTICAL);
        radiusRow.setPadding(Ui.dp(this, 16), Ui.dp(this, 12),
                Ui.dp(this, 16), Ui.dp(this, 14));
        LinearLayout radiusHead = new LinearLayout(this);
        radiusHead.setOrientation(LinearLayout.HORIZONTAL);
        radiusHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView radiusLabel = Ui.text(this, "卡片圆角", 15, Ui.TEXT, false);
        radiusHead.addView(radiusLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView radiusValue = Ui.text(this, Ui.RADIUS + " dp", 13, Ui.TERTIARY, false);
        radiusHead.addView(radiusValue);
        radiusRow.addView(radiusHead);

        SeekBar radiusBar = new SeekBar(this);
        radiusBar.setMax(36);
        radiusBar.setProgress(Ui.RADIUS);
        radiusBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        radiusBar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        radiusBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A2A36));
        radiusBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                Ui.RADIUS = Math.max(0, Math.min(36, p));
                radiusValue.setText(p + " dp");
                Ui.hapticTick(sb);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                Ui.setRadius(SettingsActivity.this, sb.getProgress());
                rebuild();
            }
        });
        LinearLayout.LayoutParams radiusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        radiusLp.topMargin = Ui.dp(this, 4);
        radiusRow.addView(radiusBar, radiusLp);
        Ui.groupRow(lookCard, radiusRow, this);

        Ui.groupRow(lookCard, switchRow("震动反馈",
                "切换、拖拽排序、过档等操作的触觉反馈",
                Ui.haptics, (b, on) -> {
                    getSharedPreferences("lock", MODE_PRIVATE).edit()
                            .putBoolean("haptics", on).apply();
                    Ui.setHaptics(on);
                    if (on) {
                        Ui.haptic(b);   // 开启时给一次确认
                    }
                }), this);
        root.addView(lookCard);

        // ---------- 版本更新 ----------
        root.addView(sectionLabel("版本更新"));

        LinearLayout updateCard = Ui.group(this);
        Ui.groupRow(updateCard, infoRow("当前版本",
                "v" + BuildConfig.VERSION_NAME), this);
        Ui.groupRow(updateCard, descRow(
                "通过成都主源检查更新（GitHub 回退）；下载后校验 SHA-256，再交给系统安装器。"), this);
        Ui.groupRow(updateCard, actionRow("⟳", "检查更新",
                "只访问更新源，不会上传任何数据", v ->
                        UpdateChecker.check(this, true)), this);
        root.addView(updateCard);

        // ---------- 壁纸 ----------
        root.addView(sectionLabel("壁纸"));

        LinearLayout wallCard = Ui.group(this);
        Ui.groupRow(wallCard, switchRow("壁纸调试信息",
                "在壁纸左下角显示状态行",
                getSharedPreferences("lock", MODE_PRIVATE)
                        .getBoolean("wall_debug", true),
                (b, on) -> getSharedPreferences("lock", MODE_PRIVATE).edit()
                        .putBoolean("wall_debug", on).apply()), this);
        Ui.groupRow(wallCard, actionRow("⟲", "清除调试覆盖",
                "回到传感器驱动", v -> {
                    getSharedPreferences("lock", MODE_PRIVATE).edit()
                            .remove("wall_progress")
                            .remove("wall_dir_x")
                            .remove("wall_dir_y")
                            .apply();
                    Toast.makeText(this, "已清除调试覆盖", Toast.LENGTH_SHORT).show();
                }), this);
        Ui.groupRow(wallCard, descRow(
                "壁纸左下角的调试行显示：组名 / 横竖 / 旋转 / phase / 段内进度 / 方向 / 帧耗时。"), this);
        Ui.groupRow(wallCard, actionRow("▤", "恢复系统默认壁纸",
                "清除当前动态壁纸", v -> confirmClearWallpaper()), this);
        root.addView(wallCard);

        // ---------- 开源 ----------
        root.addView(sectionLabel("开源"));

        LinearLayout ossCard = Ui.group(this);
        Ui.groupRow(ossCard, actionRow("↗", "GitHub 项目",
                "kdjdhe/raster-dial · 源码与发布", v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                        "https://github.com/kdjdhe/raster-dial")));
                    } catch (Exception e) {
                        Toast.makeText(this, "没有可打开链接的应用",
                                Toast.LENGTH_SHORT).show();
                    }
                }), this);
        Ui.groupRow(ossCard, infoRow("开源协议", "MIT"), this);
        Ui.groupRow(ossCard, descRow(
                "代码采用 MIT 协议；内置图片为米哈游官方素材，不在 MIT 范围内（详见 NOTICE）。"), this);
        root.addView(ossCard);

        scrollRef = scroll;
        Ui.adaptWide(this, scroll, root);
        if (!rebuilding) {
            Ui.appear(head, 0);
            Ui.appear(about, 60);
            Ui.appear(lookCard, 120);
            Ui.appear(updateCard, 180);
            Ui.appear(wallCard, 240);
            Ui.appear(ossCard, 300);
        }
        return scroll;
    }

    /** 通用开关行：标题 + 副文案 + Switch。 */
    private LinearLayout switchRow(String title, String caption, boolean checked,
                                   android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, title, 15, Ui.TEXT, false));
        TextView cap = Ui.text(this, caption, 12, Ui.TERTIARY, false);
        cap.setPadding(0, Ui.dp(this, 2), 0, 0);
        col.addView(cap);
        row.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.ACCENT, 0xFF5A5A66}));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{(Ui.ACCENT & 0xFFFFFF) | 0x59000000, 0x1FFFFFFF}));
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        return row;
    }

    // ---------- 行构建 ----------
    private TextView sectionLabel(String text) {
        TextView t = Ui.text(this, text, 13, Ui.SECONDARY, true);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 10));
        return t;
    }

    /** 只读信息行：左标题、右值，无箭头。 */
    private LinearLayout infoRow(String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 52));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        row.addView(Ui.text(this, title, 15, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, value, 13, Ui.TERTIARY, false));
        return row;
    }

    /** 说明行：灰色多行文字，不可点。 */
    private TextView descRow(String text) {
        TextView t = Ui.text(this, text, 13, Ui.SECONDARY, false);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        return t;
    }

    /** 动作行：图标 + 标题/副文案 + 箭头。 */
    private LinearLayout actionRow(String glyph, String title, String caption,
                                   View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 56));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        row.setForeground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(Ui.RIPPLE), null, new GradientDrawable()));
        row.setClickable(true);
        row.setFocusable(true);
        Ui.pressable(row);

        TextView ic = Ui.text(this, glyph, 18, Ui.SECONDARY, false);
        Ui.glyph(ic, 18);
        ic.setGravity(Gravity.CENTER);
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 32),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 8), 0, 0, 0);
        col.addView(Ui.text(this, title, 15, Ui.TEXT, false));
        TextView cap = Ui.text(this, caption, 12, Ui.TERTIARY, false);
        cap.setPadding(0, Ui.dp(this, 2), 0, 0);
        col.addView(cap);
        row.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chev = Ui.text(this, "›", 18, Ui.TERTIARY, false);
        Ui.glyph(chev, 18);
        row.addView(chev);
        row.setOnClickListener(v -> {
            Ui.haptic(v);
            l.onClick(v);
        });
        return row;
    }

    private void confirmClearWallpaper() {
        Ui.dialog(this, "恢复系统默认壁纸？", "当前动态壁纸会被清除。",
                null, "清除", true, () -> {
                    try {
                        WallpaperManager.getInstance(this).clear();
                        Toast.makeText(this, "已恢复系统默认壁纸", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "失败：" + e, Toast.LENGTH_LONG).show();
                    }
                }, "取消");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.themeIndex != builtTheme || Ui.RADIUS != builtRadius) {
            rebuild();
        }
    }
}
