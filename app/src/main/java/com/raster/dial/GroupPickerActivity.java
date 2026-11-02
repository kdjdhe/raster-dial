package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 光栅组选择器：搜索、缩略图卡片、当前标记、长按拖拽排序、新建/导入/编辑/选择。
 *
 * 排序是单一连续手势：按住卡片 380ms 拿起（长震），同一根手指 1:1 拖动，
 * 每跨越一个卡位轻 tick，松手弹簧归位并保存；按住期间移动先超阈值则交给滚动，
 * 轻点（不足时长）仍是切换组。卡片间让位用弹簧，从当前值起跳可中断。
 * 顺序存 SharedPreferences（内置组 + 自定义组统一排序）。
 */
public class GroupPickerActivity extends Activity {

    private static final int REQ_IMPORT = 71;
    private static final int CARD_H = 296;   // 卡片统一高度（dp），排序几何依赖它
    private static final int ROW_GAP = 14;   // 行距（dp）
    private static final int COL_GAP = 12;   // 列距（dp，左右 margin 各 6）
    private static final int LONG_PRESS_MS = 380;
    private static final String ORDER_KEY = "group_order";

    private LinearLayout cardsContainer;
    private EditText searchInput;
    private TextView importBtn;
    private TextView shareBtn;
    private TextView caption;
    private TextView tip;
    private LinearLayout shareActions;
    private TextView shareGo;

    private List<GroupCatalog.Group> groups = new ArrayList<>();  // 全量（排序后）
    private final List<LinearLayout> gridCards = new ArrayList<>(); // 与 groups 平行
    private final Map<String, Bitmap> thumbCache = new HashMap<>();
    private String query = "";
    private int cardIndex;
    private int gridCols = 2;   // 平板宽屏 3 列，其余 2 列

    // ---------- 多选分享状态 ----------
    private boolean shareMode;
    private final java.util.Set<String> shareSel = new java.util.LinkedHashSet<>();

    // ---------- 拖拽状态（单卡持有，gesture 内闭环） ----------
    private boolean dragLifted;
    private boolean orderChanged;
    private int dragIndex = -1;
    private float cardW, cardH, pitchX, pitchY, baseX, baseY;
    private int touchSlop;
    private long lastBlockedToast;
    private FrameLayout dragOverlay;  // 拖拽影子浮层：永远盖在网格之上
    private ImageView dragGhost;
    private Bitmap dragGhostBmp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.loadTheme(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        Ui.attachEdgeTick(scroll);
        scroll.setClipChildren(false);
        scroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.BG, Ui.BG2}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
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

        TextView title = Ui.largeTitle(this, "光栅组");
        title.setPadding(0, Ui.dp(this, 12), 0, 0);
        head.addView(title);

        caption = Ui.callout(this,
                "点卡片切换；长按拖动排序；自定义组可编辑、删除。");
        caption.setPadding(0, Ui.dp(this, 4), 0, 0);
        head.addView(caption);
        root.addView(head);

        // ---------- 搜索 + 导入 ----------
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = Ui.dp(this, 16);
        root.addView(searchRow, srlp);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setTextSize(15);
        searchInput.setTextColor(Ui.TEXT);
        searchInput.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        searchInput.setHintTextColor(Ui.TERTIARY);
        searchInput.setHint("🔍  搜索光栅组");
        searchInput.setBackground(Ui.surface(this, Ui.CARD, 999));
        searchInput.setPadding(Ui.dp(this, 16), Ui.dp(this, 10),
                Ui.dp(this, 16), Ui.dp(this, 10));
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                query = s.toString().trim();
                renderGroups();
            }
        });
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        importBtn = Ui.chip(this, "导入", false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = Ui.dp(this, 8);
        importBtn.setOnClickListener(v -> {
            Ui.haptic(v);
            startImport();
        });
        searchRow.addView(importBtn, ilp);

        shareBtn = Ui.chip(this, "分享", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 8);
        shareBtn.setOnClickListener(v -> {
            Ui.haptic(v);
            enterShare();
        });
        searchRow.addView(shareBtn, slp);

        // 多选分享的操作行（默认隐藏）
        shareActions = new LinearLayout(this);
        shareActions.setOrientation(LinearLayout.HORIZONTAL);
        shareActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams salp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        salp.topMargin = Ui.dp(this, 10);
        shareActions.setVisibility(View.GONE);
        root.addView(shareActions, salp);
        buildShareActions();

        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        cardsContainer.setClipChildren(false);
        root.addView(cardsContainer);

        tip = Ui.footnote(this,
                "自定义组最多 6 张；至少 1 张才能设为当前组。\n按住卡片约半秒拿起后拖动排序。");
        tip.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(tip);

        Ui.adaptWide(this, scroll, root);
        setContentView(scroll);

        Ui.appear(head, 0);
        Ui.appear(searchRow, 40);
    }

    @Override
    protected void onResume() {
        super.onResume();
        thumbCache.clear();   // 编辑器可能改过图
        groups = ordered(GroupCatalog.load(this));
        renderGroups();
    }

    private Bitmap thumbFor(GroupCatalog.Group g) {
        String key = g.id + "#" + g.count;
        Bitmap b = thumbCache.get(key);
        if (b == null) {
            b = GroupImages.loadThumb(this, g, 480);
            thumbCache.put(key, b);
        }
        return b;
    }

    // ---------- 数据与排序 ----------
    /** 按 SharedPreferences 里的顺序排列；缺失的按原顺序排后面。 */
    private List<GroupCatalog.Group> ordered(List<GroupCatalog.Group> src) {
        List<GroupCatalog.Group> out = new ArrayList<>();
        String s = getSharedPreferences("lock", MODE_PRIVATE).getString(ORDER_KEY, null);
        if (s != null) {
            try {
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i);
                    for (GroupCatalog.Group g : src) {
                        if (g.id.equals(id)) {
                            out.add(g);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        for (GroupCatalog.Group g : src) {
            if (!out.contains(g)) {
                out.add(g);
            }
        }
        return out;
    }

    private void persistOrder() {
        try {
            JSONArray arr = new JSONArray();
            for (GroupCatalog.Group g : groups) {
                arr.put(g.id);
            }
            getSharedPreferences("lock", MODE_PRIVATE).edit()
                    .putString(ORDER_KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private boolean matches(GroupCatalog.Group g) {
        if (query.isEmpty()) {
            return true;
        }
        String q = query.toLowerCase();
        return g.displayName().toLowerCase().contains(q)
                || g.name.toLowerCase().contains(q)
                || g.nameZh.toLowerCase().contains(q);
    }

    // ---------- 渲染 ----------
    private void renderGroups() {
        renderGroups(true);
    }

    /** animate=false：跳过入场动画（排序落定后的无感重建）。 */
    private void renderGroups(boolean animate) {
        orderChanged = false;
        gridCols = getResources().getConfiguration().screenWidthDp >= 640 ? 3 : 2;
        gridCards.clear();
        String current = GroupCatalog.currentId(this);
        cardsContainer.removeAllViews();
        cardIndex = 0;

        List<GroupCatalog.Group> shown = new ArrayList<>();
        for (GroupCatalog.Group g : groups) {
            if (matches(g)) {
                shown.add(g);
            }
        }
        boolean filtered = !query.isEmpty();

        if (shown.isEmpty()) {
            TextView empty = Ui.callout(this,
                    filtered ? "没有匹配「" + query + "」的光栅组" : "还没有光栅组");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 40), 0, 0);
            cardsContainer.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            return;
        }

        int totalTiles = shown.size() + (filtered ? 0 : 1);
        for (int i = 0; i < totalTiles; i += gridCols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            row.setClipChildren(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = Ui.dp(this, ROW_GAP);
            cardsContainer.addView(row, rowLp);

            for (int j = i; j < i + gridCols; j++) {
                if (j < shown.size()) {
                    GroupCatalog.Group g = shown.get(j);
                    LinearLayout card = groupCard(g, current);
                    if (!filtered && !shareMode) {
                        // tag = 卡片在 groups 里的槽位；排序几何依赖它
                        card.setTag(gridCards.size());
                        card.setOnTouchListener(new CardGesture(card, g));
                        gridCards.add(card);
                    }
                    LinearLayout.LayoutParams lp = cellLp(j % gridCols);
                    lp.height = Ui.dp(this, CARD_H);
                    row.addView(card, lp);
                    if (animate) {
                        Ui.appear(card, 40 + cardIndex * 50);
                    }
                    cardIndex++;
                } else if (j < totalTiles) {
                    LinearLayout tile = newTileCard();
                    LinearLayout.LayoutParams lp = cellLp(j % gridCols);
                    lp.height = Ui.dp(this, CARD_H);
                    row.addView(tile, lp);
                    if (animate) {
                        Ui.appear(tile, 40 + cardIndex * 50);
                    }
                    cardIndex++;
                } else {
                    row.addView(new View(this), cellLp(j % gridCols));
                }
            }
        }
    }

    /** 当前选中的判定：普通模式 = 当前组；分享模式 = 已勾选。 */
    private boolean isSelected(GroupCatalog.Group g) {
        return shareMode ? shareSel.contains(g.id)
                : g.id.equals(GroupCatalog.currentId(this));
    }

    private void applyCardBg(LinearLayout card, boolean selected, boolean lifted) {
        GradientDrawable bg;
        if (lifted) {
            bg = Ui.surface(this, Ui.CARD_HI, 20);
            bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        } else if (selected) {
            bg = Ui.round(this, Ui.ACCENT_DIM, 20);
            bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
        } else {
            bg = Ui.round(this, Ui.CARD, 20);
            bg.setStroke(Ui.dp(this, 1), Ui.HAIRLINE);
        }
        card.setBackground(bg);
    }

    private LinearLayout groupCard(GroupCatalog.Group g, String current) {
        boolean selected;
        boolean showCurrent;
        if (shareMode) {
            selected = shareSel.contains(g.id);
            showCurrent = false;
        } else {
            selected = g.id.equals(current);
            showCurrent = true;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 8), Ui.dp(this, 8),
                Ui.dp(this, 8), Ui.dp(this, 12));
        applyCardBg(card, selected, false);
        card.setClickable(true);
        card.setFocusable(true);
        card.setClipChildren(true);

        FrameLayout media = new FrameLayout(this);
        ImageView iv = new RoundedImageView(this, 14);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(Ui.round(this, Ui.CARD_HI, 14));
        iv.setClipToOutline(true);
        Bitmap thumb = thumbFor(g);
        if (thumb != null) {
            iv.setImageBitmap(thumb);
        }
        media.addView(iv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (g.count == 0) {
            TextView empty = Ui.callout(this, "空组 · 点卡片添加");
            empty.setGravity(Gravity.CENTER);
            media.addView(empty, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        if (selected) {
            FrameLayout badge = new FrameLayout(this);
            TextView check = Ui.text(this, "✓", 12, Ui.ON_ACCENT, true);
            check.setGravity(Gravity.CENTER);
            badge.addView(check, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            int bsz = Ui.dp(this, 22);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(bsz, bsz,
                    Gravity.TOP | Gravity.END);
            blp.rightMargin = Ui.dp(this, 8);
            blp.topMargin = Ui.dp(this, 8);
            badge.setBackground(Ui.round(this, Ui.ACCENT, 999));
            media.addView(badge, blp);
        }
        card.addView(media, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 168)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 2), 0);
        TextView name = Ui.text(this, g.displayName(), 14,
                selected ? Ui.TEXT : Ui.SECONDARY, selected);
        name.setMaxLines(2);
        info.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(info);

        TextView meta = Ui.text(this,
                (g.custom ? "自定义" : "内置") + " · "
                        + (g.count == 0 ? "空" : g.count + " 张")
                        + (selected ? (shareMode ? " · 已选" : " · 当前") : ""),
                11, selected ? Ui.ACCENT : Ui.TERTIARY, false);
        meta.setPadding(Ui.dp(this, 6), Ui.dp(this, 3), 0, 0);
        card.addView(meta);

        if (g.custom) {
            TextView edit = Ui.chip(this, "编辑", false);
            edit.setTextSize(11);
            edit.setPadding(Ui.dp(this, 12), Ui.dp(this, 6),
                    Ui.dp(this, 12), Ui.dp(this, 6));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.topMargin = Ui.dp(this, 10);
            edit.setOnClickListener(v -> openEditor(g.id));
            card.addView(edit, elp);
        }

        card.setOnClickListener(v -> {
            Ui.haptic(v);
            if (shareMode) {
                if (!g.custom || g.count == 0) {
                    return;   // 内置组/空组不可导出
                }
                if (shareSel.contains(g.id)) {
                    shareSel.remove(g.id);
                } else {
                    shareSel.add(g.id);
                }
                updateShareGo();
                renderGroups(false);
                return;
            }
            onPick(g);
        });
        return card;
    }

    // ---------- 多选分享 ----------
    private void buildShareActions() {
        shareGo = Ui.button(this, "打包分享", true);
        shareGo.setOnClickListener(v -> {
            Ui.haptic(v);
            packSelected();
        });
        shareActions.addView(shareGo, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView all = Ui.button(this, "全选", false);
        all.setOnClickListener(v -> {
            Ui.haptic(v);
            int selectable = 0;
            for (GroupCatalog.Group g : groups) {
                if (g.custom && g.count > 0) {
                    selectable++;
                }
            }
            if (shareSel.size() >= selectable) {
                shareSel.clear();
            } else {
                for (GroupCatalog.Group g : groups) {
                    if (g.custom && g.count > 0) {
                        shareSel.add(g.id);
                    }
                }
            }
            updateShareGo();
            renderGroups(false);
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        alp.leftMargin = Ui.dp(this, 8);
        shareActions.addView(all, alp);

        TextView cancel = Ui.button(this, "取消", false);
        cancel.setOnClickListener(v -> {
            Ui.haptic(v);
            exitShare();
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = Ui.dp(this, 8);
        shareActions.addView(cancel, clp);
        updateShareGo();
    }

    private void updateShareGo() {
        if (shareGo == null) {
            return;
        }
        shareGo.setText(shareSel.isEmpty() ? "打包分享" : "打包分享(" + shareSel.size() + ")");
    }

    private void enterShare() {
        if (shareMode) {
            return;
        }
        shareMode = true;
        shareSel.clear();
        caption.setText("点选要分享的自定义组（内置组不支持导出）。");
        importBtn.setEnabled(false);
        importBtn.setAlpha(0.35f);
        shareBtn.setEnabled(false);
        shareBtn.setAlpha(0.35f);
        shareActions.setVisibility(View.VISIBLE);
        tip.setText("打包成一个 zip 发给对方；对方在「导入」里选择即可。");
        renderGroups(false);
    }

    private void exitShare() {
        if (!shareMode) {
            return;
        }
        shareMode = false;
        shareSel.clear();
        caption.setText("点卡片切换；长按拖动排序；自定义组可编辑、删除。");
        importBtn.setEnabled(true);
        importBtn.setAlpha(1f);
        shareBtn.setEnabled(true);
        shareBtn.setAlpha(1f);
        shareActions.setVisibility(View.GONE);
        tip.setText("自定义组最多 6 张；至少 1 张才能设为当前组。\n按住卡片约半秒拿起后拖动排序。");
        renderGroups(false);
    }

    private void packSelected() {
        if (shareSel.isEmpty()) {
            Toast.makeText(this, "先点选至少一个自定义组", Toast.LENGTH_SHORT).show();
            return;
        }
        List<CustomStore.CustomGroup> gs = new ArrayList<>();
        for (GroupCatalog.Group g : groups) {
            if (shareSel.contains(g.id)) {
                CustomStore.CustomGroup cg = CustomStore.find(this, g.id);
                if (cg != null && cg.count() > 0) {
                    gs.add(cg);
                }
            }
        }
        if (gs.isEmpty()) {
            Toast.makeText(this, "选中的组都还没有图片", Toast.LENGTH_SHORT).show();
            return;
        }
        shareGo.setEnabled(false);
        shareGo.setText("打包中…");
        final int n = gs.size();
        new Thread(() -> {
            try {
                File exports = new File(getFilesDir(), "exports");
                //noinspection ResultOfMethodCallIgnored
                exports.mkdirs();
                File out = new File(exports, "raster-groups.zip");
                GroupImporter.writeBundle(this, gs, out);
                runOnUiThread(() -> {
                    shareGo.setEnabled(true);
                    updateShareGo();
                    Uri uri = Uri.parse("content://" + getPackageName() + ".export/"
                            + out.getName());
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/zip");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(send, "分享 " + n + " 个光栅组");
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    chooser.setClipData(ClipData.newRawUri("groups", uri));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(chooser);
                    exitShare();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    shareGo.setEnabled(true);
                    updateShareGo();
                    Toast.makeText(this, "打包失败：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** 虚线边框“＋ 新建组”卡片。 */
    private LinearLayout newTileCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dp(this, 8), Ui.dp(this, 8),
                Ui.dp(this, 8), Ui.dp(this, 12));
        GradientDrawable dash = new GradientDrawable();
        dash.setColor(0x0FFFFFFF);
        dash.setCornerRadius(Ui.dp(this, 20));
        dash.setStroke(Ui.dp(this, 1), 0x30FFFFFF, Ui.dp(this, 5), Ui.dp(this, 5));
        card.setBackground(dash);
        card.setClickable(true);
        card.setFocusable(true);
        Ui.pressable(card);

        TextView plus = Ui.text(this, "＋", 26, Ui.SECONDARY, false);
        plus.setGravity(Gravity.CENTER);
        card.addView(plus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        TextView label = Ui.callout(this, "新建光栅组");
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(this, 6);
        card.addView(label, llp);

        card.setOnClickListener(v -> {
            Ui.haptic(v);
            showCreateDialog();
        });
        return card;
    }

    private LinearLayout.LayoutParams cellLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, index == 0 ? 0 : 6);
        lp.rightMargin = Ui.dp(this, index == gridCols - 1 ? 0 : 6);
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

    // ---------- 长按拖拽排序（单一连续手势） ----------
    private void measureGrid() {
        if (gridCards.isEmpty()) {
            return;
        }
        LinearLayout c0 = gridCards.get(0);
        LinearLayout r0 = (LinearLayout) c0.getParent();
        cardW = c0.getWidth();
        cardH = c0.getHeight();
        baseX = c0.getLeft();                      // 行是满宽的，X 即容器坐标
        baseY = r0.getTop() + c0.getTop();          // Y 要叠加行的位置
        if (gridCards.size() > 1) {
            pitchX = gridCards.get(1).getLeft() - c0.getLeft();
        } else {
            pitchX = cardW + Ui.dp(this, COL_GAP);
        }
        pitchY = cardH + Ui.dp(this, ROW_GAP);      // 卡高统一，节距确定
    }

    /** 把卡片快照挂到 Activity 顶层浮层：影子跟手，绘制顺序上永远盖住网格。 */
    /** 软件画布也会生效的圆角图：clipToOutline 只在硬件渲染下裁剪，
     *  拖拽影子（Bitmap 快照）走软件画布，需要 clipPath 兜底。 */
    private static final class RoundedImageView extends ImageView {
        private final Path path = new Path();
        private final float radiusPx;

        RoundedImageView(Context c, float radiusDp) {
            super(c);
            radiusPx = Ui.dp(c, radiusDp);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            path.reset();
            path.addRoundRect(new RectF(0, 0, w, h), radiusPx, radiusPx,
                    Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.clipPath(path);
            super.onDraw(canvas);
            canvas.restore();
        }
    }

    private void showGhost(LinearLayout card) {
        int w = card.getWidth();
        int h = card.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        dragGhostBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        card.draw(new Canvas(dragGhostBmp));
        FrameLayout content = findViewById(android.R.id.content);
        dragOverlay = new FrameLayout(this);
        dragOverlay.setClipChildren(false);
        dragOverlay.setClickable(false);
        content.addView(dragOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        dragGhost = new ImageView(this);
        dragGhost.setImageBitmap(dragGhostBmp);
        int[] cl = new int[2];
        card.getLocationOnScreen(cl);
        int[] fl = new int[2];
        content.getLocationOnScreen(fl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = cl[0] - fl[0];
        lp.topMargin = cl[1] - fl[1];
        dragGhost.setElevation(Ui.dp(this, 12));
        dragOverlay.addView(dragGhost, lp);
    }

    private void moveSlot(int from, int to) {
        GroupCatalog.Group g = groups.remove(from);
        groups.add(to, g);
        LinearLayout cv = gridCards.remove(from);
        gridCards.add(to, cv);
        dragIndex = to;
        orderChanged = true;
        for (int i = 0; i < gridCards.size(); i++) {
            LinearLayout c = gridCards.get(i);
            if (c == cv) {
                continue;
            }
            int ri = (Integer) c.getTag();
            // 目标平移 = 当前槽位 - 渲染槽位（相对各自布局位置）
            float tx = ((i % gridCols) - (ri % gridCols)) * pitchX;
            float ty = ((i / gridCols) - (ri / gridCols)) * pitchY;
            Ui.springXY(c, tx, ty);
        }
    }

    private final class CardGesture implements View.OnTouchListener {
        private final LinearLayout card;
        private final GroupCatalog.Group group;
        private final Runnable liftRun;
        private float downX, downY;
        private boolean liftPending;
        private boolean lifted;

        CardGesture(LinearLayout card, GroupCatalog.Group g) {
            this.card = card;
            this.group = g;
            this.liftRun = this::lift;
        }

        private void lift() {
            if (lifted || dragLifted || shareMode) {
                return;
            }
            if (!query.isEmpty()) {
                long now = System.currentTimeMillis();
                if (now - lastBlockedToast > 1200) {
                    lastBlockedToast = now;
                    Toast.makeText(GroupPickerActivity.this,
                            "清空搜索后再排序", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            liftPending = false;
            lifted = true;
            dragLifted = true;
            dragIndex = (Integer) card.getTag();
            orderChanged = false;
            Ui.hapticLong(card);
            measureGrid();
            applyCardBg(card, false, true);
            // 拖拽影子：快照挂到 Activity 顶层浮层，保证任何情况下都在最上层。
            // 原卡整卡透明（alpha 不影响触摸分发，手势不断）——画面上只有影子
            // 这一个视觉体，避免双层描边；空出的槽位即落点提示
            card.setScaleX(1f);
            card.setScaleY(1f);
            showGhost(card);
            card.setAlpha(0f);
            card.getParent().requestDisallowInterceptTouchEvent(true);
        }

        private void drop() {
            dragLifted = false;
            lifted = false;
            applyCardBg(card, isSelected(group), false);
            Ui.alpha(card, 1f);   // 原卡淡回（影子淡出、网格重排的衔接）
            // 影子弹簧到目标槽位并淡出
            if (dragGhost != null && dragOverlay != null) {
                int ri = (Integer) card.getTag();
                float tx = ((dragIndex % gridCols) - (ri % gridCols)) * pitchX;
                float ty = ((dragIndex / gridCols) - (ri / gridCols)) * pitchY;
                Ui.springXY(dragGhost, tx, ty);
                Ui.alpha(dragGhost, 0f);
                final FrameLayout ov = dragOverlay;
                final ImageView ghost = dragGhost;
                final Bitmap bmp = dragGhostBmp;
                ov.postDelayed(() -> {
                    ghost.setImageBitmap(null);
                    if (bmp != null) {
                        bmp.recycle();
                    }
                    if (ov.getParent() instanceof ViewGroup) {
                        ((ViewGroup) ov.getParent()).removeView(ov);
                    }
                }, 240);
                dragGhost = null;
                dragOverlay = null;
                dragGhostBmp = null;
            }
            LinearLayout cv = gridCards.get(dragIndex);
            int ri = (Integer) cv.getTag();
            float tx = ((dragIndex % gridCols) - (ri % gridCols)) * pitchX;
            float ty = ((dragIndex / gridCols) - (ri / gridCols)) * pitchY;
            Ui.springXY(cv, tx, ty);
            Ui.haptic(cv);
            if (orderChanged) {
                persistOrder();
                // 等让位弹簧落定后无感重建（跳过入场动画，像素一致无闪烁）
                cardsContainer.postDelayed(() -> {
                    groups = ordered(GroupCatalog.load(GroupPickerActivity.this));
                    renderGroups(false);
                }, 360);
            }
        }

        private void cancelLift() {
            liftPending = false;
            card.removeCallbacks(liftRun);
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    liftPending = true;
                    lifted = false;
                    card.postDelayed(liftRun, LONG_PRESS_MS);
                    Ui.springScale(card, 0.98f);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getRawX() - downX;
                    float dy = ev.getRawY() - downY;
                    if (!lifted) {
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            // 先动了：这是滚动，取消长按，交给 ScrollView
                            cancelLift();
                            Ui.springScale(card, 1f);
                            return false;
                        }
                        return true; // 仍在等待长按
                    }
                    // 已拿起：1:1 跟手（原卡占位 + 影子同步）
                    card.setTranslationX(dx);
                    card.setTranslationY(dy);
                    if (dragGhost != null) {
                        dragGhost.setTranslationX(dx);
                        dragGhost.setTranslationY(dy);
                    }
                    LinearLayout row = (LinearLayout) card.getParent();
                    int col = Math.max(0, Math.min(gridCols - 1,
                            Math.round((card.getLeft() + dx - baseX) / pitchX)));
                    int rowIdx = Math.max(0,
                            Math.round((row.getTop() + card.getTop() + dy - baseY) / pitchY));
                    int h = Math.max(0, Math.min(groups.size() - 1, rowIdx * gridCols + col));
                    if (h != dragIndex && pitchX > 0) {
                        moveSlot(dragIndex, h);
                        Ui.hapticTick(card);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    cancelLift();
                    if (lifted) {
                        drop();
                        return true;
                    }
                    Ui.springScale(card, 1f);
                    // 手势消费了 DOWN，View 没进 pressed 态，框架不会自动分发 click；
                    // 轻点在此手动补发（仍是切换组 / 分享多选）
                    card.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelLift();
                    if (lifted) {
                        drop();
                    } else {
                        Ui.springScale(card, 1f);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---------- 导入 ----------
    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) {
            return;
        }
        final Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        Toast.makeText(this, "导入中…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<GroupImporter.Parsed> parsed =
                        GroupImporter.readAll(getContentResolver(), uri);
                int imported = 0;
                for (GroupImporter.Parsed p : parsed) {
                    // 名称冲突：追加序号重试
                    CustomStore.CustomGroup g = null;
                    String chosen = p.name;
                    for (int k = 1; g == null && k <= 50; k++) {
                        final String candidate = k == 1 ? p.name : p.name + " " + k;
                        g = CustomStore.create(this, candidate);
                        if (g != null) {
                            chosen = candidate;
                        }
                    }
                    if (g == null) {
                        throw new Exception("无法创建「" + p.name + "」（名称冲突）");
                    }
                    for (int idx = 0; idx < p.rotations.length; idx++) {
                        CustomStore.importBytes(this, g, p.images.get(idx), p.rotations[idx]);
                    }
                    CustomStore.saveGroupSlots(this, g);
                    imported++;
                }
                final int n = imported;
                runOnUiThread(() -> {
                    Toast.makeText(this, "已导入 " + n + " 个光栅组",
                            Toast.LENGTH_LONG).show();
                    groups = ordered(GroupCatalog.load(this));
                    renderGroups();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showCreateDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextSize(16);
        input.setTextColor(Ui.TEXT);
        input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        input.setHintTextColor(Ui.TERTIARY);
        input.setHint("给这组起个名字");
        input.setBackground(Ui.round(this, Ui.CARD_HI, 12));
        input.setPadding(Ui.dp(this, 14), Ui.dp(this, 12),
                Ui.dp(this, 14), Ui.dp(this, 12));
        int customCount = 0;
        for (GroupCatalog.Group g : groups) {
            if (g.custom) {
                customCount++;
            }
        }
        input.setText("自定义组 " + (customCount + 1));
        input.setSelectAllOnFocus(true);

        FrameLayout wrap = new FrameLayout(this);
        int pad = Ui.dp(this, 20);
        wrap.setPadding(pad, Ui.dp(this, 8), pad, 0);
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
