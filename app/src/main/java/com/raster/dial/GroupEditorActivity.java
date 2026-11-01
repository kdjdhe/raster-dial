package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 编辑一个自定义光栅组：命名、选图、旋转、上移下移、删除、添加。
 * 每组至少 1 张才能使用，最多 6 个槽位；名称用于选择器。
 */
public class GroupEditorActivity extends Activity {

    public static final String EXTRA_GROUP_ID = "group_id";
    private static final int REQ_PICK = 41;

    private CustomStore.CustomGroup group;
    private List<CustomStore.Slot> slots;
    private EditText nameInput;
    private LinearLayout slotsContainer;
    private TextView addButton;
    private TextView useButton;
    private int pendingIndex = -1;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        String id = getIntent().getStringExtra(EXTRA_GROUP_ID);
        if (id == null || id.isEmpty()) {
            id = GroupCatalog.currentId(this);
        }
        group = CustomStore.find(this, id);
        if (group == null) {
            Toast.makeText(this, "找不到这个自定义组", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        slots = group.slots;

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
        TextView title = Ui.text(this, "编辑光栅组", 20, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 14), 0, 0, 0);
        head.addView(title);
        root.addView(head);

        TextView sub = Ui.text(this,
                "名称会显示在选择器里；槽位按 1→2→3→1 循环。\n"
                        + "至少 1 张才能使用，最多 6 张；删除槽位会自动保存。",
                12, Ui.MUTED, false);
        sub.setLineSpacing(Ui.dp(this, 2), 1f);
        sub.setPadding(0, Ui.dp(this, 8), 0, 0);
        root.addView(sub);

        // ---------- name ----------
        LinearLayout nameCard = Ui.card(this);
        nameCard.addView(Ui.text(this, "组名", 12, Ui.MUTED, true));

        nameInput = new EditText(this);
        nameInput.setText(group.name);
        nameInput.setHint("给这组起个名字");
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextSize(16);
        nameInput.setTextColor(Ui.TEXT);
        nameInput.setHintTextColor(Ui.MUTED);
        nameInput.setBackground(Ui.round(this, Ui.SURFACE2, 12));
        nameInput.setPadding(Ui.dp(this, 14), Ui.dp(this, 10),
                Ui.dp(this, 14), Ui.dp(this, 10));
        Ui.addWithTop(nameCard, nameInput, this, 8);

        TextView saveName = Ui.button(this, "保存名称", false);
        saveName.setOnClickListener(v -> saveName());
        Ui.addWithTop(nameCard, saveName, this, 8);
        Ui.addWithTop(root, nameCard, this, 16);

        slotsContainer = new LinearLayout(this);
        slotsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(slotsContainer);

        addButton = Ui.button(this, "＋ 添加图片", false);
        addButton.setOnClickListener(v -> addSlot());
        Ui.addWithTop(root, addButton, this, 16);

        useButton = Ui.button(this, "使用此组", true);
        useButton.setOnClickListener(v -> useGroup());
        Ui.addWithTop(root, useButton, this, 10);

        TextView deleteGroup = Ui.button(this, "删除此组", false);
        deleteGroup.setTextColor(0xFFFF6B6B);
        deleteGroup.setOnClickListener(v -> confirmDelete());
        Ui.addWithTop(root, deleteGroup, this, 10);

        scroll.addView(root);
        setContentView(scroll);
        renderSlots();
    }

    private void renderSlots() {
        slotsContainer.removeAllViews();
        if (slots.isEmpty()) {
            TextView empty = Ui.text(this,
                    "还没有图片。点下面“＋ 添加图片”选择第一张。",
                    13, Ui.MUTED, false);
            empty.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
            slotsContainer.addView(empty);
        }
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            final CustomStore.Slot s = slots.get(i);

            LinearLayout card = Ui.card(this);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            ImageView iv = new ImageView(this);
            int sz = Ui.dp(this, 68);
            iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.round(this, Ui.SURFACE2, 14));
            iv.setClipToOutline(true);
            Bitmap thumb = loadThumb(s);
            if (thumb != null) {
                iv.setImageBitmap(thumb);
            }
            top.addView(iv);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(Ui.dp(this, 12), 0, 0, 0);
            info.addView(Ui.text(this, "槽位 " + (i + 1), 16, Ui.TEXT, true));
            info.addView(Ui.text(this, "旋转 " + s.rotation + "°", 12, Ui.MUTED, false));
            top.addView(info);

            card.addView(top);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, Ui.dp(this, 10), 0, 0);

            TextView pick = smallChip("选图");
            pick.setOnClickListener(v -> pick(idx));
            actions.addView(pick, actionLp(0));

            TextView rot = smallChip("旋转");
            rot.setOnClickListener(v -> rotate(idx));
            actions.addView(rot, actionLp(1));

            TextView up = smallChip("↑");
            up.setEnabled(i > 0);
            up.setAlpha(i > 0 ? 1f : 0.3f);
            up.setOnClickListener(v -> move(idx, idx - 1));
            actions.addView(up, actionLp(2));

            TextView down = smallChip("↓");
            down.setEnabled(i < slots.size() - 1);
            down.setAlpha(i < slots.size() - 1 ? 1f : 0.3f);
            down.setOnClickListener(v -> move(idx, idx + 1));
            actions.addView(down, actionLp(3));

            TextView del = smallChip("删除");
            del.setEnabled(slots.size() > 1);
            del.setAlpha(slots.size() > 1 ? 1f : 0.3f);
            del.setOnClickListener(v -> deleteSlot(idx));
            actions.addView(del, actionLp(4));

            card.addView(actions);
            Ui.addWithTop(slotsContainer, card, this, 12);
        }

        if (addButton != null) {
            boolean can = slots.size() < CustomStore.MAX_SLOTS;
            addButton.setEnabled(can);
            addButton.setAlpha(can ? 1f : 0.4f);
        }
        if (useButton != null) {
            boolean can = !slots.isEmpty();
            useButton.setEnabled(can);
            useButton.setAlpha(can ? 1f : 0.45f);
        }
    }

    private TextView smallChip(String text) {
        TextView t = Ui.chip(this, text, false);
        t.setTextSize(12);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 8),
                Ui.dp(this, 4), Ui.dp(this, 8));
        return t;
    }

    private LinearLayout.LayoutParams actionLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, index == 0 ? 0 : 3);
        lp.rightMargin = Ui.dp(this, index == 4 ? 0 : 3);
        return lp;
    }

    private Bitmap loadThumb(CustomStore.Slot s) {
        File f = new File(CustomStore.dir(this), s.display);
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), b);
        if (b.outWidth <= 0 || b.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(b.outWidth, b.outHeight) / sample > 400) {
            sample *= 2;
        }
        b.inJustDecodeBounds = false;
        b.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), b);
    }

    private void saveName() {
        if (saveGroup(true)) {
            Toast.makeText(this, "名称已保存", Toast.LENGTH_SHORT).show();
        }
    }

    /** 保存组名和槽位；成功返回 true。 */
    private boolean saveGroup(boolean announce) {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "组名不能为空", Toast.LENGTH_SHORT).show();
            return false;
        }
        group.name = name;
        if (!CustomStore.saveGroup(this, group)) {
            Toast.makeText(this, "名称已存在，换一个", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (announce) {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void useGroup() {
        if (!saveGroup(false)) {
            return;
        }
        if (slots.isEmpty()) {
            Toast.makeText(this, "至少添加一张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        GroupCatalog.setCurrentId(this, group.id);
        Toast.makeText(this, "已切换到「" + group.name + "」", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除此组？")
                .setMessage("会删除组名和所有图片，无法恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    boolean current = group.id.equals(GroupCatalog.currentId(this));
                    if (CustomStore.deleteGroup(this, group.id)) {
                        if (current) {
                            GroupCatalog.setCurrentId(this, GroupCatalog.DEFAULT_ID);
                        }
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pick(int index) {
        if (busy) {
            return;
        }
        pendingIndex = index;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK);
    }

    private void addSlot() {
        if (busy || slots.size() >= CustomStore.MAX_SLOTS) {
            return;
        }
        pendingIndex = -1;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        final int idx = pendingIndex;
        final int rotation = idx >= 0 && idx < slots.size() ? slots.get(idx).rotation : 0;
        busy = true;
        Toast.makeText(this, "处理图片…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                CustomStore.Slot ns = CustomStore.importImage(this, uri, rotation);
                runOnUiThread(() -> {
                    if (idx >= 0 && idx < slots.size()) {
                        CustomStore.Slot old = slots.get(idx);
                        CustomStore.deleteFiles(this, old);
                        slots.set(idx, ns);
                    } else {
                        slots.add(ns);
                    }
                    saveGroup(false);
                    busy = false;
                    pendingIndex = -1;
                    renderSlots();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void rotate(int index) {
        if (busy || index < 0 || index >= slots.size()) {
            return;
        }
        final CustomStore.Slot s = slots.get(index);
        final int old = s.rotation;
        s.rotation = (s.rotation + 90) % 360;
        busy = true;
        new Thread(() -> {
            try {
                CustomStore.renderDisplay(this, s);
                runOnUiThread(() -> {
                    saveGroup(false);
                    busy = false;
                    renderSlots();
                });
            } catch (Exception e) {
                s.rotation = old;
                runOnUiThread(() -> {
                    busy = false;
                    Toast.makeText(this, "旋转失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    renderSlots();
                });
            }
        }).start();
    }

    private void move(int from, int to) {
        if (busy || to < 0 || to >= slots.size()) {
            return;
        }
        Collections.swap(slots, from, to);
        saveGroup(false);
        renderSlots();
    }

    private void deleteSlot(int index) {
        if (busy || slots.size() <= 1) {
            return;
        }
        CustomStore.deleteFiles(this, slots.get(index));
        slots.remove(index);
        saveGroup(false);
        renderSlots();
    }
}
