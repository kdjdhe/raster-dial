package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 光栅组选择器：缩略图卡片、当前标记、新建/编辑/选择。
 * 自定义组为空时点卡片直接进编辑器；内置组点卡片直接切换。
 */
public class GroupPickerActivity extends Activity {

    private LinearLayout cardsContainer;
    private List<GroupCatalog.Group> groups;

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

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.chip(this, "‹ 返回", false);
        back.setOnClickListener(v -> finish());
        head.addView(back);

        TextView title = Ui.text(this, "光栅组", 20, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 14), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView newGroup = Ui.chip(this, "＋ 新建", false);
        newGroup.setOnClickListener(v -> showCreateDialog());
        head.addView(newGroup);
        root.addView(head);

        TextView sub = Ui.text(this,
                "点卡片切换光栅组；自定义组可编辑、命名、删除。",
                12, Ui.MUTED, false);
        sub.setLineSpacing(Ui.dp(this, 2), 1f);
        sub.setPadding(0, Ui.dp(this, 8), 0, 0);
        root.addView(sub);

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(cardsContainer);

        TextView tip = Ui.text(this,
                "自定义组最多 6 张；至少 1 张才能设为当前组。",
                11, 0xFF777782, false);
        tip.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(tip);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderGroups();
    }

    private void renderGroups() {
        groups = GroupCatalog.load(this);
        String current = GroupCatalog.currentId(this);
        cardsContainer.removeAllViews();

        for (int i = 0; i < groups.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = Ui.dp(this, 12);
            cardsContainer.addView(row, rowLp);

            addCard(row, groups.get(i), current, 0);
            if (i + 1 < groups.size()) {
                addCard(row, groups.get(i + 1), current, 1);
            } else {
                View spacer = new View(this);
                row.addView(spacer, cellLp(1));
            }
        }
    }

    private void addCard(LinearLayout row, GroupCatalog.Group g, String current, int index) {
        boolean selected = g.id.equals(current);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 10),
                Ui.dp(this, 10), Ui.dp(this, 10));
        GradientDrawable bg = Ui.round(this,
                selected ? 0x2AFF7A3D : Ui.SURFACE, 20);
        bg.setStroke(Ui.dp(this, selected ? 2 : 1),
                selected ? Ui.ACCENT : Ui.STROKE);
        card.setBackground(bg);
        card.setClickable(true);
        card.setFocusable(true);

        FrameLayout frame = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(Ui.round(this, Ui.SURFACE2, 14));
        iv.setClipToOutline(true);
        Bitmap thumb = GroupImages.loadThumb(this, g, 480);
        if (thumb != null) {
            iv.setImageBitmap(thumb);
        }
        frame.addView(iv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (thumb == null) {
            TextView empty = Ui.text(this, "空组", 14, Ui.MUTED, true);
            empty.setGravity(Gravity.CENTER);
            frame.addView(empty, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        card.addView(frame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 176)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(0, Ui.dp(this, 8), 0, 0);
        TextView name = Ui.text(this, g.displayName(), 14, Ui.TEXT, true);
        name.setMaxLines(2);
        info.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = Ui.text(this, g.custom ? "自定义" : "内置", 10, Ui.MUTED, false);
        badge.setPadding(Ui.dp(this, 6), 0, 0, 0);
        info.addView(badge);
        card.addView(info);

        String meta = (g.count == 0 ? "空组" : g.count + " 张")
                + (selected ? " · 当前" : "");
        TextView metaTv = Ui.text(this, meta, 11, selected ? Ui.ACCENT : Ui.MUTED, false);
        metaTv.setPadding(0, Ui.dp(this, 3), 0, 0);
        card.addView(metaTv);

        if (g.custom) {
            TextView edit = Ui.chip(this, "编辑", false);
            edit.setTextSize(11);
            edit.setPadding(Ui.dp(this, 4), Ui.dp(this, 6),
                    Ui.dp(this, 4), Ui.dp(this, 6));
            edit.setOnClickListener(v -> openEditor(g.id));
            Ui.addWithTop(card, edit, this, 8);
        }

        card.setOnClickListener(v -> onPick(g));
        row.addView(card, cellLp(index));
    }

    private LinearLayout.LayoutParams cellLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, index == 0 ? 0 : 6);
        lp.rightMargin = Ui.dp(this, index == 0 ? 6 : 0);
        return lp;
    }

    private void onPick(GroupCatalog.Group g) {
        if (g.custom && g.count == 0) {
            openEditor(g.id);
            return;
        }
        GroupCatalog.setCurrentId(this, g.id);
        setResult(RESULT_OK);
        finish();
    }

    private void openEditor(String id) {
        Intent i = new Intent(this, GroupEditorActivity.class);
        i.putExtra(GroupEditorActivity.EXTRA_GROUP_ID, id);
        startActivity(i);
    }

    private void showCreateDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextSize(16);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setHint("给这组起个名字");
        input.setBackground(Ui.round(this, Ui.SURFACE2, 10));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8),
                Ui.dp(this, 12), Ui.dp(this, 8));
        int customCount = 0;
        if (groups != null) {
            for (GroupCatalog.Group g : groups) {
                if (g.custom) {
                    customCount++;
                }
            }
        }
        input.setText("自定义组 " + (customCount + 1));
        input.setSelectAllOnFocus(true);

        FrameLayout wrap = new FrameLayout(this);
        int pad = Ui.dp(this, 20);
        wrap.setPadding(pad, Ui.dp(this, 4), pad, 0);
        wrap.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("新建光栅组")
                .setView(wrap)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    CustomStore.CustomGroup g = CustomStore.create(this, name);
                    if (g == null) {
                        Toast.makeText(this, "名称不能为空或已存在", Toast.LENGTH_LONG).show();
                        return;
                    }
                    openEditor(g.id);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
