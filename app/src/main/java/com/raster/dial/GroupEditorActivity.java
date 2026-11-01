package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
        Ui.loadTheme(this);
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
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.BG, Ui.BG2}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 24),
                Ui.dp(this, 20), Ui.dp(this, 36));

        // ---------- 头部 ----------
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(22);
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
        TextView title = Ui.largeTitle(this, "编辑光栅组");
        title.setPadding(0, Ui.dp(this, 12), 0, 0);
        head.addView(title);
        TextView sub = Ui.callout(this,
                "槽位按 1→2→3→1 循环；至少 1 张才能使用。");
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        head.addView(sub);
        root.addView(head);

        // ---------- 组名 ----------
        LinearLayout nameCard = Ui.card(this);
        nameCard.addView(Ui.overline(this, "组名"));
        nameInput = new EditText(this);
        nameInput.setText(group.name);
        nameInput.setHint("给这组起个名字");
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextSize(16);
        nameInput.setTextColor(Ui.TEXT);
        nameInput.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        nameInput.setHintTextColor(Ui.TERTIARY);
        nameInput.setBackground(Ui.round(this, Ui.CARD_HI, 12));
        nameInput.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 14), Ui.dp(this, 12));
        Ui.addWithTop(nameCard, nameInput, this, 10);
        TextView saveName = Ui.button(this, "保存名称", false);
        saveName.setOnClickListener(v -> {
            Ui.haptic(v);
            saveName();
        });
        Ui.addWithTop(nameCard, saveName, this, 10);
        root.addView(nameCard);

        // ---------- 槽位 ----------
        TextView slotLabel = Ui.overline(this, "槽位");
        slotLabel.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), 0, 0);
        root.addView(slotLabel);

        slotsContainer = new LinearLayout(this);
        slotsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(slotsContainer);

        addButton = Ui.button(this, "＋ 添加图片", false);
        addButton.setOnClickListener(v -> {
            Ui.haptic(v);
            addSlot();
        });
        Ui.addWithTop(root, addButton, this, 14);

        useButton = Ui.button(this, "使用此组", true);
        useButton.setOnClickListener(v -> {
            Ui.haptic(v);
            useGroup();
        });
        Ui.addWithTop(root, useButton, this, 10);

        // ---------- 分享 / 危险区：分组列表隔离（§16 分组与寻路） ----------
        LinearLayout shareGroup = Ui.group(this);
        LinearLayout exportRow = Ui.listRow(this, "导出此组（分享）", "zip", false);
        exportRow.setOnClickListener(v -> {
            Ui.haptic(v);
            exportGroup();
        });
        Ui.groupRow(shareGroup, exportRow, this);
        Ui.addWithTop(root, shareGroup, this, 24);

        LinearLayout dangerGroup = Ui.group(this);
        LinearLayout delRow = Ui.listRow(this, "删除此组", "不可恢复", true);
        delRow.setOnClickListener(v -> confirmDelete());
        Ui.groupRow(dangerGroup, delRow, this);
        Ui.addWithTop(root, dangerGroup, this, 12);

        Ui.adaptWide(this, scroll, root);
        setContentView(scroll);
        renderSlots();

        Ui.appear(nameCard, 0);
        Ui.appear(shareGroup, 60);
        Ui.appear(dangerGroup, 100);
    }

    /** 导出自定义组为 zip 分享包；完成后弹系统分享面板。 */
    private void exportGroup() {
        if (busy) {
            return;
        }
        if (slots.isEmpty()) {
            Toast.makeText(this, "先添加图片再导出", Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        Toast.makeText(this, "打包中…", Toast.LENGTH_SHORT).show();
        final String name = group.name;
        new Thread(() -> {
            try {
                File exports = new File(getFilesDir(), "exports");
                //noinspection ResultOfMethodCallIgnored
                exports.mkdirs();
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                File out = new File(exports, safe + ".zip");

                org.json.JSONObject meta = new org.json.JSONObject();
                meta.put("format", 1);
                meta.put("name", name);
                org.json.JSONArray arr = new org.json.JSONArray();
                for (CustomStore.Slot s : slots) {
                    org.json.JSONObject so = new org.json.JSONObject();
                    so.put("r", s.rotation);
                    arr.put(so);
                }
                meta.put("slots", arr);

                try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                        new java.io.FileOutputStream(out))) {
                    zos.putNextEntry(new java.util.zip.ZipEntry("raster-group.json"));
                    zos.write(meta.toString().getBytes("UTF-8"));
                    zos.closeEntry();
                    byte[] buf = new byte[8192];
                    for (int i = 0; i < slots.size(); i++) {
                        File src = new File(CustomStore.dir(this), slots.get(i).master);
                        zos.putNextEntry(new java.util.zip.ZipEntry("m_" + (i + 1) + ".webp"));
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(src)) {
                            int r;
                            while ((r = fis.read(buf)) > 0) {
                                zos.write(buf, 0, r);
                            }
                        }
                        zos.closeEntry();
                    }
                }

                runOnUiThread(() -> {
                    busy = false;
                    Uri uri = Uri.parse("content://" + getPackageName() + ".export/"
                            + out.getName());
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/zip");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(send, "分享「" + name + "」");
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chooser.setClipData(ClipData.newRawUri("group", uri));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(chooser);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void renderSlots() {
        slotsContainer.removeAllViews();
        if (slots.isEmpty()) {
            TextView empty = Ui.callout(this,
                    "还没有图片。点下面“＋ 添加图片”选择第一张。");
            empty.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), 0, Ui.dp(this, 4));
            slotsContainer.addView(empty);
        }
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            final CustomStore.Slot s = slots.get(i);

            LinearLayout card = Ui.card(this);
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14),
                    Ui.dp(this, 14), Ui.dp(this, 14));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            ImageView iv = new ImageView(this);
            int sz = Ui.dp(this, 64);
            iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.round(this, Ui.CARD_HI, 12));
            iv.setClipToOutline(true);
            Bitmap thumb = loadThumb(s);
            if (thumb != null) {
                iv.setImageBitmap(thumb);
            }
            top.addView(iv);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(Ui.dp(this, 12), 0, 0, 0);
            info.addView(Ui.text(this, "槽位 " + (i + 1), 15, Ui.TEXT, true));
            info.addView(Ui.text(this, "旋转 " + s.rotation + "°", 12, Ui.SECONDARY, false));
            top.addView(info);

            card.addView(top);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, Ui.dp(this, 12), 0, 0);

            actions.addView(smallChip("选图", v -> pick(idx)), actionLp(0));
            actions.addView(smallChip("旋转", v -> rotate(idx)), actionLp(1));
            TextView up = smallChip("↑", v -> move(idx, idx - 1));
            up.setEnabled(i > 0);
            up.setAlpha(i > 0 ? 1f : 0.3f);
            actions.addView(up, actionLp(2));
            TextView down = smallChip("↓", v -> move(idx, idx + 1));
            down.setEnabled(i < slots.size() - 1);
            down.setAlpha(i < slots.size() - 1 ? 1f : 0.3f);
            actions.addView(down, actionLp(3));
            TextView del = smallChip("删除", v -> deleteSlot(idx));
            del.setEnabled(slots.size() > 1);
            del.setAlpha(slots.size() > 1 ? 1f : 0.3f);
            actions.addView(del, actionLp(4));

            card.addView(actions);
            Ui.addWithTop(slotsContainer, card, this, 10);
            Ui.appear(card, 60 + idx * 50);
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

    private TextView smallChip(String text, View.OnClickListener l) {
        TextView t = Ui.chip(this, text, false);
        t.setTextSize(12);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9));
        t.setOnClickListener(v -> {
            Ui.haptic(v);
            l.onClick(v);
        });
        return t;
    }

    private LinearLayout.LayoutParams actionLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, index == 0 ? 0 : 4);
        lp.rightMargin = Ui.dp(this, index == 4 ? 0 : 4);
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
