package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private Spinner spinner;
    private List<GroupCatalog.Group> groups;
    private final TextView[] sensChips = new TextView[3];

    private static final float[] SENS = {0.24f, 0.35f, 0.52f};
    private static final String[] SENS_LABEL = {"高 14°", "中 20°", "低 30°"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.BG, Ui.BG2}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 28),
                Ui.dp(this, 20), Ui.dp(this, 36));

        // ---------- header ----------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        int is = Ui.dp(this, 58);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        icon.setLayoutParams(ilp);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setBackground(Ui.round(this, Ui.SURFACE2, 16));
        icon.setClipToOutline(true);
        header.addView(icon);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 14), 0, 0, 0);
        titles.addView(Ui.text(this, "Raster Dial", 22, Ui.TEXT, true));
        TextView sub = Ui.text(this, "光栅壁纸 · Android 9+ · 5 组 + 自定义", 12, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        titles.addView(sub);
        header.addView(titles);
        root.addView(header);

        // ---------- group card ----------
        LinearLayout groupCard = Ui.card(this);
        groupCard.addView(Ui.text(this, "光栅组", 12, Ui.MUTED, true));

        groups = GroupCatalog.load(this);
        ArrayAdapter<GroupCatalog.Group> adapter =
                new ArrayAdapter<GroupCatalog.Group>(this,
                        android.R.layout.simple_spinner_item, groups) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        TextView tv = (TextView) super.getView(position, convertView, parent);
                        tv.setTextColor(Ui.TEXT);
                        tv.setTextSize(16);
                        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                        tv.setPadding(Ui.dp(MainActivity.this, 4), 0,
                                Ui.dp(MainActivity.this, 4), 0);
                        return tv;
                    }

                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                        tv.setTextColor(Ui.TEXT);
                        tv.setTextSize(15);
                        tv.setPadding(Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 12),
                                Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 12));
                        return tv;
                    }
                };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner = new Spinner(this);
        spinner.setAdapter(adapter);
        spinner.setBackground(Ui.round(this, Ui.SURFACE2, 12));
        spinner.setPadding(Ui.dp(this, 12), Ui.dp(this, 4),
                Ui.dp(this, 12), Ui.dp(this, 4));
        spinner.setPopupBackgroundDrawable(Ui.round(this, Ui.SURFACE2, 12));
        selectCurrentGroup();
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                GroupCatalog.setCurrentId(MainActivity.this, groups.get(position).id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        LinearLayout spinnerRow = new LinearLayout(this);
        spinnerRow.setOrientation(LinearLayout.HORIZONTAL);
        spinnerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        spinnerRow.addView(spinner, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = Ui.text(this, "▾", 16, Ui.MUTED, false);
        arrow.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        spinnerRow.addView(arrow);
        Ui.addWithTop(groupCard, spinnerRow, this, 8);

        TextView edit = Ui.button(this, "编辑自定义光栅组", false);
        edit.setOnClickListener(v -> startActivity(new Intent(this, GroupEditorActivity.class)));
        Ui.addWithTop(groupCard, edit, this, 10);
        Ui.addWithTop(root, groupCard, this, 18);

        // ---------- effect card ----------
        LinearLayout effect = Ui.card(this);
        effect.addView(Ui.text(this, "效果", 12, Ui.MUTED, true));

        LinearLayout sensRow = new LinearLayout(this);
        sensRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < SENS.length; i++) {
            final int idx = i;
            sensChips[i] = Ui.chip(this, SENS_LABEL[i], i == sensIndex());
            sensChips[i].setOnClickListener(v -> pickSens(idx));
            LinearLayout.LayoutParams lp = Ui.weight(this, 8);
            lp.leftMargin = Ui.dp(this, i == 0 ? 0 : 3);
            lp.rightMargin = Ui.dp(this, i == SENS.length - 1 ? 0 : 3);
            sensRow.addView(sensChips[i], lp);
        }
        effect.addView(sensRow);

        Switch debugSwitch = switchRow(effect, "壁纸调试信息",
                getSharedPreferences("lock", MODE_PRIVATE).getBoolean("wall_debug", true),
                (b, checked) -> getSharedPreferences("lock", MODE_PRIVATE)
                        .edit().putBoolean("wall_debug", checked).apply());

        Switch invertSwitch = switchRow(effect, "反向扫过（切图侧互换）",
                getSharedPreferences("lock", MODE_PRIVATE).getBoolean("invert", false),
                (b, checked) -> getSharedPreferences("lock", MODE_PRIVATE)
                        .edit().putBoolean("invert", checked).apply());
        Ui.addWithTop(root, effect, this, 16);

        // ---------- preview card ----------
        LinearLayout previewCard = Ui.card(this);
        previewCard.addView(Ui.text(this, "预览", 12, Ui.MUTED, true));
        TextView full = Ui.button(this, "全屏预览（传感器）", false);
        full.setOnClickListener(v -> startActivity(new Intent(this, LenticularActivity.class)));
        Ui.addWithTop(previewCard, full, this, 10);

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        addChip(chips, "第1张", 0f, 0f);
        addChip(chips, "1→2 50%", 0.5f, 0f);
        addChip(chips, "第2张", 1f, 0f);
        addChip(chips, "2→3 50%", 1.5f, 0f);
        addChip(chips, "第3张", 2f, 0f);
        addChip(chips, "斜向 45°", 0.5f, 45f);
        hsv.addView(chips);
        Ui.addWithTop(previewCard, hsv, this, 10);
        Ui.addWithTop(root, previewCard, this, 16);

        // ---------- wallpaper card ----------
        LinearLayout wall = Ui.card(this);
        wall.addView(Ui.text(this, "壁纸", 12, Ui.MUTED, true));
        TextView set = Ui.button(this, "设为光栅壁纸", true);
        set.setOnClickListener(v -> openWallpaperPicker());
        Ui.addWithTop(wall, set, this, 10);
        TextView clear = Ui.button(this, "清除壁纸（恢复系统默认）", false);
        clear.setOnClickListener(v -> confirmClearWallpaper());
        Ui.addWithTop(wall, clear, this, 8);
        Ui.addWithTop(root, wall, this, 16);

        // ---------- tools ----------
        LinearLayout tools = Ui.card(this);
        tools.addView(Ui.text(this, "工具", 12, Ui.MUTED, true));
        TextView checkUpdate = Ui.button(this, "检查更新", false);
        checkUpdate.setOnClickListener(v -> UpdateChecker.check(this, true));
        Ui.addWithTop(tools, checkUpdate, this, 10);
        TextView clearDebug = Ui.button(this, "清除调试覆盖（回到传感器）", false);
        clearDebug.setOnClickListener(v -> clearDebugOverride());
        Ui.addWithTop(tools, clearDebug, this, 10);
        Ui.addWithTop(root, tools, this, 16);

        TextView note = Ui.text(this,
                "只改壁纸，不接管锁屏；壁纸是系统管理的 WallpaperService，不会拦截触摸。\n"
                        + "调试行显示组名 / 横竖 / 旋转 / phase / 段号 / 段内进度 / 方向 / 帧耗时。\n"
                        + "更新检查优先走成都源，失败自动回退 GitHub。",
                11, 0xFF777782, false);
        note.setLineSpacing(Ui.dp(this, 2), 1f);
        note.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(note);

        scroll.addView(root);
        setContentView(scroll);
    }

    private Switch switchRow(LinearLayout parent, String text, boolean checked,
                             android.widget.CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextColor(Ui.TEXT);
        sw.setTextSize(14);
        sw.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        sw.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.ACCENT, 0xFF6E6E7A}));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0x66FF7A3D, 0x33FFFFFF}));
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
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

    private void selectCurrentGroup() {
        String cur = GroupCatalog.currentId(this);
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).id.equals(cur)) {
                spinner.setSelection(i);
                break;
            }
        }
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

    private void pickSens(int i) {
        getSharedPreferences("lock", MODE_PRIVATE)
                .edit().putFloat("sens", SENS[i]).apply();
        for (int k = 0; k < SENS.length; k++) {
            boolean sel = k == i;
            sensChips[k].setTextColor(sel ? Ui.ON_ACCENT : Ui.TEXT);
            sensChips[k].setBackground(Ui.ripple(this,
                    sel ? Ui.ACCENT : Ui.SURFACE2, 18));
            sensChips[k].setTypeface(Typeface.create("sans-serif",
                    sel ? Typeface.BOLD : Typeface.NORMAL));
        }
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
        if (spinner != null && groups != null) {
            selectCurrentGroup();
        }
        for (int k = 0; k < SENS.length; k++) {
            boolean sel = k == sensIndex();
            sensChips[k].setTextColor(sel ? Ui.ON_ACCENT : Ui.TEXT);
            sensChips[k].setBackground(Ui.ripple(this,
                    sel ? Ui.ACCENT : Ui.SURFACE2, 18));
            sensChips[k].setTypeface(Typeface.create("sans-serif",
                    sel ? Typeface.BOLD : Typeface.NORMAL));
        }
    }
}
