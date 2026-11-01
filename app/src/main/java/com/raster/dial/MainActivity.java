package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {

    private ImageView groupThumb;
    private TextView groupName;
    private TextView groupMeta;
    private TextView groupEdit;
    private LinearLayout segmentedHost;

    private static final float[] SENS = {0.24f, 0.35f, 0.52f};
    private static final String[] SENS_LABEL = {"高 14°", "中 20°", "低 30°"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.loadTheme(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildContent());
    }

    private void rebuild() {
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildContent());
    }

    private ScrollView buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.BG, Ui.BG2}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 24),
                Ui.dp(this, 20), Ui.dp(this, 36));

        // ---------- 大标题 ----------
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.largeTitle(this, "Raster Dial");
        titleBlock.addView(t);
        TextView sub = Ui.callout(this, "光栅动态壁纸 · 倾斜切换画面");
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        titleBlock.addView(sub);
        root.addView(titleBlock);

        // ---------- 当前组 hero 卡 ----------
        LinearLayout hero = Ui.card(this);
        hero.setPadding(0, 0, 0, 0);
        hero.setBackground(Ui.surface(this, Ui.CARD, 22));

        FrameLayout heroMedia = new FrameLayout(this);
        groupThumb = new ImageView(this);
        groupThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        groupThumb.setBackground(Ui.round(this, Ui.CARD_HI, 0));
        heroMedia.addView(groupThumb, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 190)));

        // 底部渐变叠层：让下方文字区过渡自然（材质分层，不叠亮色半透明）
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x66000000}));
        heroMedia.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 64), Gravity.BOTTOM));

        TextView heroTag = Ui.overline(this, "当前光栅组");
        heroTag.setTextColor(0xFFFFFFFF);
        heroTag.setShadowLayer(6, 0, 2, 0x99000000);
        heroMedia.addView(heroTag, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        FrameLayout.LayoutParams tlp = (FrameLayout.LayoutParams) heroTag.getLayoutParams();
        tlp.leftMargin = Ui.dp(this, 16);
        tlp.bottomMargin = Ui.dp(this, 12);
        heroTag.setLayoutParams(tlp);

        LinearLayout heroMediaClip = new LinearLayout(this);
        heroMediaClip.setOrientation(LinearLayout.VERTICAL);
        heroMediaClip.setBackground(Ui.round(this, Ui.CARD, 22));
        heroMediaClip.setClipToOutline(true);
        heroMediaClip.addView(heroMedia);
        hero.addView(heroMediaClip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.VERTICAL);
        heroInfo.setPadding(Ui.dp(this, 18), Ui.dp(this, 14),
                Ui.dp(this, 18), Ui.dp(this, 18));
        groupName = Ui.heading(this, "");
        heroInfo.addView(groupName);
        groupMeta = Ui.callout(this, "");
        groupMeta.setPadding(0, Ui.dp(this, 3), 0, 0);
        heroInfo.addView(groupMeta);

        LinearLayout heroActions = new LinearLayout(this);
        heroActions.setOrientation(LinearLayout.HORIZONTAL);
        heroActions.setGravity(Gravity.CENTER_VERTICAL);
        heroActions.setPadding(0, Ui.dp(this, 12), 0, 0);

        TextView pick = Ui.button(this, "选择光栅组", false);
        pick.setOnClickListener(v -> openPicker());
        heroActions.addView(pick, heroActionLp(0));

        groupEdit = Ui.button(this, "编辑", false);
        groupEdit.setVisibility(View.GONE);
        groupEdit.setOnClickListener(v -> openEditor(GroupCatalog.currentId(this)));
        heroActions.addView(groupEdit, heroActionLp(1));
        heroInfo.addView(heroActions);
        hero.addView(heroInfo);

        // 整卡可点：进入选择器（§10 反馈在 down，haptic 在提交）
        hero.setClickable(true);
        hero.setFocusable(true);
        Ui.pressable(hero);
        hero.setOnClickListener(v -> {
            Ui.haptic(v);
            openPicker();
        });
        root.addView(hero);

        // ---------- 效果 ----------
        LinearLayout effect = Ui.card(this);
        effect.addView(Ui.overline(this, "效果"));
        segmentedHost = new LinearLayout(this);
        segmentedHost.setOrientation(LinearLayout.VERTICAL);
        segmentedHost.setPadding(0, Ui.dp(this, 10), 0, 0);
        effect.addView(segmentedHost);
        buildSegmented();

        switchRow(effect, "壁纸调试信息",
                getSharedPreferences("lock", MODE_PRIVATE).getBoolean("wall_debug", true),
                (b, checked) -> getSharedPreferences("lock", MODE_PRIVATE)
                        .edit().putBoolean("wall_debug", checked).apply());
        switchRow(effect, "反向扫过（切图侧互换）",
                getSharedPreferences("lock", MODE_PRIVATE).getBoolean("invert", false),
                (b, checked) -> getSharedPreferences("lock", MODE_PRIVATE)
                        .edit().putBoolean("invert", checked).apply());
        root.addView(effect);

        // ---------- 预览 ----------
        LinearLayout preview = Ui.card(this);
        preview.addView(Ui.overline(this, "预览"));
        TextView full = Ui.button(this, "全屏预览（传感器）", false);
        full.setOnClickListener(v -> {
            Ui.haptic(v);
            startActivity(new Intent(this, LenticularActivity.class));
        });
        Ui.addWithTop(preview, full, this, 12);

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        addChip(chips, "第1张", 0f, 0f);
        addChip(chips, "1→2 50%", 0.5f, 0f);
        addChip(chips, "第2张", 1f, 0f);
        addChip(chips, "2→3 50%", 1.5f, 0f);
        addChip(chips, "第3张", 2f, 0f);
        addChip(chips, "斜向 45°", 0.5f, 45f);
        hsv.addView(chips);
        Ui.addWithTop(preview, hsv, this, 12);
        root.addView(preview);

        // ---------- 壁纸 ----------
        LinearLayout wall = Ui.card(this);
        wall.addView(Ui.overline(this, "壁纸"));
        TextView set = Ui.button(this, "设为光栅壁纸", true);
        set.setOnClickListener(v -> {
            Ui.haptic(v);
            openWallpaperPicker();
        });
        Ui.addWithTop(wall, set, this, 12);
        root.addView(wall);

        // ---------- 主题 ----------
        LinearLayout themeCard = Ui.card(this);
        themeCard.addView(Ui.overline(this, "主题"));
        android.widget.HorizontalScrollView tHsv = new android.widget.HorizontalScrollView(this);
        tHsv.setHorizontalScrollBarEnabled(false);
        tHsv.setClipToPadding(false);
        LinearLayout tRow = new LinearLayout(this);
        tRow.setOrientation(LinearLayout.HORIZONTAL);
        tRow.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
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
        Ui.addWithTop(themeCard, tHsv, this, 12);
        root.addView(themeCard);

        // ---------- 设置式分组列表 ----------
        LinearLayout tools = Ui.group(this);
        Ui.groupRow(tools, row(tools, "检查更新", null, false, v ->
                UpdateChecker.check(this, true)), this);
        Ui.groupRow(tools, row(tools, "清除调试覆盖", "回到传感器", false, v ->
                clearDebugOverride()), this);
        Ui.groupRow(tools, row(tools, "恢复系统默认壁纸", null, false, v ->
                confirmClearWallpaper()), this);
        root.addView(tools);

        TextView note = Ui.footnote(this,
                "只改桌面壁纸，不接管锁屏；不会拦截触摸。\n"
                        + "调试行显示组名 / 横竖 / 旋转 / phase / 段内进度 / 方向 / 帧耗时。");
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(note);

        updateGroupCard();
        Ui.adaptWide(this, scroll, root);

        // ---------- 入场：错开上浮（§4 弹簧，可中断） ----------
        Ui.appear(hero, 0);
        Ui.appear(effect, 60);
        Ui.appear(preview, 120);
        Ui.appear(wall, 180);
        Ui.appear(themeCard, 240);
        Ui.appear(tools, 300);
        Ui.appear(note, 340);
        return scroll;
    }

    private LinearLayout.LayoutParams heroActionLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, index == 0 ? 0 : 8);
        return lp;
    }

    private LinearLayout row(LinearLayout group, String title, String tail,
                             boolean danger, View.OnClickListener l) {
        LinearLayout r = Ui.listRow(this, title, tail, danger);
        r.setOnClickListener(v -> {
            Ui.haptic(v);
            l.onClick(v);
        });
        return r;
    }

    private void buildSegmented() {
        segmentedHost.removeAllViews();
        int idx = sensIndex();
        segmentedHost.addView(Ui.segmented(this, SENS_LABEL, idx, i ->
                getSharedPreferences("lock", MODE_PRIVATE)
                        .edit().putFloat("sens", SENS[i]).apply()));
    }

    private Switch switchRow(LinearLayout parent, String text, boolean checked,
                             android.widget.CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextColor(Ui.TEXT);
        sw.setTextSize(14);
        sw.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        sw.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 2));
        sw.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.ACCENT, 0xFF5A5A66}));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{(Ui.ACCENT & 0xFFFFFF) | 0x59000000, 0x1FFFFFFF}));
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> {
            Ui.haptic(b);
            l.onCheckedChanged(b, on);
        });
        parent.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return sw;
    }

    private void addChip(LinearLayout row, String label, float phase, float angleDeg) {
        TextView chip = Ui.chip(this, label, false);
        chip.setOnClickListener(v -> preview(phase, angleDeg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        row.addView(chip, lp);
    }

    private void updateGroupCard() {
        GroupCatalog.Group g = GroupCatalog.find(this, GroupCatalog.currentId(this));
        if (g == null) {
            return;
        }
        groupName.setText(g.displayName());
        groupMeta.setText((g.custom ? "自定义" : "内置") + " · " + g.count + " 张");
        Bitmap thumb = GroupImages.loadThumb(this, g, 720);
        groupThumb.setImageBitmap(thumb);
        groupEdit.setVisibility(g.custom ? View.VISIBLE : View.GONE);
        // 缩略图换图淡入（从当前值起跳）
        Ui.alpha(groupThumb, 0.4f);
        Ui.alpha(groupThumb, 1f);
    }

    private void openPicker() {
        startActivity(new Intent(this, GroupPickerActivity.class));
    }

    private void openEditor(String id) {
        Intent i = new Intent(this, GroupEditorActivity.class);
        i.putExtra(GroupEditorActivity.EXTRA_GROUP_ID, id);
        startActivity(i);
    }

    private int sensIndex() {
        float v = getSharedPreferences("lock", MODE_PRIVATE).getFloat("sens", 0.35f);
        int best = 1;
        float bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < SENS.length; i++) {
            float d = Math.abs(SENS[i] - v);
            if (d < bestDiff) {
                bestDiff = d;
                best = i;
            }
        }
        return best;
    }

    private void preview(float phase, float angleDeg) {
        Intent i = new Intent(this, LenticularActivity.class);
        i.putExtra("progress", phase);
        i.putExtra("angle", (float) Math.toRadians(angleDeg));
        startActivity(i);
    }

    private void openWallpaperPicker() {
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, LenticularWallpaperService.class));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "打开壁纸选择器失败：" + e, Toast.LENGTH_LONG).show();
        }
    }

    private void clearDebugOverride() {
        getSharedPreferences("lock", MODE_PRIVATE).edit()
                .remove("wall_progress")
                .remove("wall_dir_x")
                .remove("wall_dir_y")
                .apply();
        Toast.makeText(this, "已清除调试覆盖", Toast.LENGTH_SHORT).show();
    }

    private void confirmClearWallpaper() {
        new AlertDialog.Builder(this)
                .setMessage("清除当前动态壁纸，恢复系统默认静态壁纸？")
                .setPositiveButton("清除", (d, w) -> {
                    try {
                        WallpaperManager.getInstance(this).clear();
                        Toast.makeText(this, "已恢复系统默认壁纸", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "失败：" + e, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateChecker.autoCheck(this);
        UpdateChecker.resumePendingInstall(this);
        updateGroupCard();
    }
}
