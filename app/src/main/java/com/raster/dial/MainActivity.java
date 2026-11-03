package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private ImageView groupThumb;
    private TextView groupName;
    private TextView groupMeta;
    private TextView groupEdit;
    private TextView sensLabel;

    // 灵敏度映射：0–100% ↔ 半程角 0.60 → 0.15 rad（约 34° → 8.6°），默认 55% ≈ 20°
    private static final float RANGE_MIN = 0.15f;   // 100%：最灵敏
    private static final float RANGE_MAX = 0.60f;   // 0%：最迟钝
    private LinearLayout axisHost;

    // 主屏四张卡片的拖动排序
    private static final long LONG_PRESS_MS = 360;
    private LinearLayout cardRoot;
    private final List<LinearLayout> orderCards = new ArrayList<>();
    private final Map<String, LinearLayout> cardMap = new HashMap<>();
    private final Map<LinearLayout, Integer> cardBaseTop = new HashMap<>();
    private final Map<LinearLayout, Integer> cardBaseH = new HashMap<>();
    private boolean cardDragLifted, cardOrderChanged;
    private int cardDragIndex = -1;
    private int touchSlop;
    private ImageView cardGhost;
    private Bitmap cardGhostBmp;
    private FrameLayout cardGhostOverlay;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.loadTheme(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildContent());
    }

    private boolean rebuilding;
    private ScrollView scrollRef;
    private int builtTheme;
    private int builtRadius;

    /** 原地换色：重建视图但不重播入场动画，并保持滚动位置（切主题用）。 */
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
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
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

        // ---------- 大标题 + 设置入口 ----------
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.largeTitle(this, "Raster Dial");
        titleBlock.addView(t);
        TextView sub = Ui.callout(this, "光栅动态壁纸 · 倾斜切换画面");
        sub.setPadding(0, Ui.dp(this, 4), 0, 0);
        titleBlock.addView(sub);
        titleRow.addView(titleBlock, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("⚙");
        Ui.glyph(settingsBtn, 18);
        settingsBtn.setTextColor(Ui.TEXT);
        settingsBtn.setGravity(Gravity.CENTER);
        int gb = Ui.dp(this, 40);
        settingsBtn.setBackground(Ui.ripple(this, Ui.CARD_HI, 999));
        settingsBtn.setClickable(true);
        settingsBtn.setFocusable(true);
        settingsBtn.setContentDescription("设置");
        Ui.pressable(settingsBtn);
        settingsBtn.setOnClickListener(v -> {
            Ui.haptic(v);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(gb, gb);
        glp.leftMargin = Ui.dp(this, 8);
        titleRow.addView(settingsBtn, glp);
        root.addView(titleRow);

        // ---------- 当前组 hero 卡 ----------
        LinearLayout hero = Ui.card(this);
        hero.setPadding(0, 0, 0, 0);
        hero.setBackground(Ui.surface(this, Ui.CARD, Ui.RADIUS));

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
        heroMediaClip.setBackground(Ui.round(this, Ui.CARD, Ui.RADIUS));
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

        // ---------- 效果 ----------
        LinearLayout effect = Ui.card(this);
        effect.addView(Ui.overline(this, "效果"));
        // 灵敏度滑杆：0–100%，标签实时跟随
        LinearLayout sensBlock = new LinearLayout(this);
        sensBlock.setOrientation(LinearLayout.VERTICAL);
        sensBlock.setPadding(0, Ui.dp(this, 14), 0, 0);
        effect.addView(sensBlock);
        sensLabel = Ui.text(this, "灵敏度 " + sensPercent() + "%", 14, Ui.TEXT, false);
        sensBlock.addView(sensLabel);
        SeekBar sensBar = new SeekBar(this);
        sensBar.setMax(100);
        sensBar.setProgress(sensPercent());
        sensBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        sensBar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        sensBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A2A36));
        sensBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                getSharedPreferences("lock", MODE_PRIVATE).edit()
                        .putFloat("sens", sensForPercent(p)).apply();
                sensLabel.setText("灵敏度 " + p + "%");
                Ui.hapticTick(sb, p / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        LinearLayout.LayoutParams sensLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sensLp.topMargin = Ui.dp(this, 4);
        sensBlock.addView(sensBar, sensLp);

        // 过渡方式：过渡线 / 百叶窗 / 类OriginOS
        LinearLayout styleHost = new LinearLayout(this);
        styleHost.setOrientation(LinearLayout.VERTICAL);
        styleHost.setPadding(0, Ui.dp(this, 10), 0, 0);
        effect.addView(styleHost);
        styleHost.addView(Ui.segmented(this,
                new String[]{"过渡线", "百叶窗", "类 OriginOS"},
                styleIndex(), i -> {
                    getSharedPreferences("lock", MODE_PRIVATE)
                            .edit().putInt("style", i).apply();
                    buildAxis();
                }));

        // 变化轴：过渡线 = 全向/仅X/仅Y；百叶窗与类OriginOS = 横向/竖向
        axisHost = new LinearLayout(this);
        axisHost.setOrientation(LinearLayout.VERTICAL);
        axisHost.setPadding(0, Ui.dp(this, 8), 0, 0);
        effect.addView(axisHost);
        buildAxis();

        switchRow(effect, "反向扫过（切图侧互换）",
                getSharedPreferences("lock", MODE_PRIVATE).getBoolean("invert", false),
                (b, checked) -> getSharedPreferences("lock", MODE_PRIVATE)
                        .edit().putBoolean("invert", checked).apply());
        // 护眼强度：常驻低透光衰减（0% = 关闭），减弱切换闪烁
        LinearLayout eyeRow = new LinearLayout(this);
        eyeRow.setOrientation(LinearLayout.VERTICAL);
        eyeRow.setPadding(0, Ui.dp(this, 14), 0, 0);
        TextView eyeLabel = Ui.text(this, "护眼强度（减弱切换闪烁）", 14, Ui.TEXT, false);
        eyeRow.addView(eyeLabel);
        SeekBar eyeBar = new SeekBar(this);
        eyeBar.setMax(30);   // 0% .. 30%，1% 一档；0 = 关闭
        float eyeSt = getSharedPreferences("lock", MODE_PRIVATE)
                .getFloat("eye_care_strength", 0f);
        eyeBar.setProgress(Math.round(eyeSt * 100f));
        eyeBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        eyeBar.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        eyeBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF2A2A36));
        eyeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    getSharedPreferences("lock", MODE_PRIVATE).edit()
                            .putFloat("eye_care_strength", p / 100f).apply();
                    // 1% 一档：每跨过一档轻震一次
                    Ui.hapticTick(sb);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 4);
        eyeRow.addView(eyeBar, blp);
        effect.addView(eyeRow);

        // ---------- 预览 ----------
        LinearLayout preview = Ui.card(this);
        preview.addView(Ui.overline(this, "预览"));
        TextView full = Ui.button(this, "全屏预览（传感器）", false);
        full.setOnClickListener(v -> {
            Ui.haptic(v);
            startActivity(new Intent(this, LenticularActivity.class));
        });
        Ui.addWithTop(preview, full, this, 12);


        // ---------- 壁纸 ----------
        LinearLayout wall = Ui.card(this);
        wall.addView(Ui.overline(this, "壁纸"));
        TextView set = Ui.button(this, "设为光栅壁纸", true);
        set.setOnClickListener(v -> {
            Ui.haptic(v);
            openWallpaperPicker();
        });
        Ui.addWithTop(wall, set, this, 12);

        TextView wallWarn = Ui.callout(this,
                "设为光栅壁纸之后将会覆盖原有壁纸，在设置里面恢复系统默认壁纸几乎必定会丢失原有壁纸。\n"
                        + "如果你很喜欢现在的壁纸，请做好备份或者放弃使用本App。\n"
                        + "能否同时设置成光栅锁屏取决于机型 详情参见项目README。");
        wallWarn.setLineSpacing(Ui.dp(this, 3), 1f);
        Ui.addWithTop(wall, wallWarn, this, 14);

        // 四张卡片按上次拖拽的顺序排列；长按空白处拖动排序
        cardRoot = root;
        cardMap.put("hero", hero);
        cardMap.put("effect", effect);
        cardMap.put("preview", preview);
        cardMap.put("wall", wall);
        orderCards.clear();
        for (String key : Prefs.cardOrder(this)) {
            LinearLayout card = cardMap.get(key);
            if (card == null) {
                continue;
            }
            orderCards.add(card);
            root.addView(card);
            card.setOnTouchListener(new CardDragGesture(card));
        }


        updateGroupCard();
        Ui.adaptWide(this, scroll, root);

        // ---------- 入场：错开上浮（§4 弹簧，可中断） ----------
        for (int i = 0; i < orderCards.size(); i++) {
            Ui.appear(orderCards.get(i), i * 60);
        }
        return scroll;
    }

    private LinearLayout.LayoutParams heroActionLp(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, index == 0 ? 0 : 8);
        return lp;
    }

    /** 当前灵敏度百分比（0–100）。 */
    private int sensPercent() {
        float r = getSharedPreferences("lock", MODE_PRIVATE).getFloat("sens", 0.35f);
        int p = Math.round((RANGE_MAX - r) / (RANGE_MAX - RANGE_MIN) * 100f);
        return Math.max(0, Math.min(100, p));
    }

    /** 百分比 → 半程角（rad）。 */
    private float sensForPercent(int p) {
        return RANGE_MAX - (RANGE_MAX - RANGE_MIN) * (p / 100f);
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

    private void buildAxis() {
        axisHost.removeAllViews();
        if (styleIndex() == 0) {
            axisHost.addView(Ui.segmented(this,
                    new String[]{"全向", "仅横向", "仅竖向"},
                    axisLineIndex(), i -> getSharedPreferences("lock", MODE_PRIVATE)
                            .edit().putInt("axis_line", i).apply()));
        } else {
            axisHost.addView(Ui.segmented(this,
                    new String[]{"自动", "横向", "竖向"},
                    axisHIndex(), i -> Prefs.setAxisH(this, i)));
        }
    }

    private int axisLineIndex() {
        return Math.max(0, Math.min(2,
                getSharedPreferences("lock", MODE_PRIVATE).getInt("axis_line", 0)));
    }

    private int axisHIndex() {
        return Prefs.axisH(this);
    }

    private int styleIndex() {
        return Math.max(0, Math.min(2,
                getSharedPreferences("lock", MODE_PRIVATE).getInt("style", 0)));
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

    @Override
    protected void onResume() {
        super.onResume();
        UpdateChecker.autoCheck(this);
        UpdateChecker.resumePendingInstall(this);
        if (Ui.themeIndex != builtTheme || Ui.RADIUS != builtRadius) {
            rebuild();
            return;
        }
        updateGroupCard();
    }

    // ---------- 主屏卡片长按拖动排序（高度不等的弹簧让位） ----------
    private float cardBlockTop;
    private float cardSlotGap;

    private String keyOf(LinearLayout card) {
        for (Map.Entry<String, LinearLayout> e : cardMap.entrySet()) {
            if (e.getValue() == card) {
                return e.getKey();
            }
        }
        return "hero";
    }

    private float slotTopFor(List<LinearLayout> order, int index) {
        float y = cardBlockTop;
        for (int i = 0; i < index; i++) {
            y += cardBaseH.get(order.get(i)) + cardSlotGap;
        }
        return y;
    }

    private void liftCard(LinearLayout card) {
        if (cardDragLifted || orderCards.size() < 2) {
            return;
        }
        cardDragIndex = orderCards.indexOf(card);
        if (cardDragIndex < 0) {
            return;
        }
        cardDragLifted = true;
        cardOrderChanged = false;
        for (LinearLayout c : orderCards) {
            Ui.cancel(c);
            c.setTranslationY(0f);
            c.setAlpha(1f);
        }
        cardBaseTop.clear();
        cardBaseH.clear();
        for (LinearLayout c : orderCards) {
            cardBaseTop.put(c, c.getTop());
            cardBaseH.put(c, c.getHeight());
        }
        cardBlockTop = cardBaseTop.get(orderCards.get(0));
        cardSlotGap = orderCards.size() > 1
                ? cardBaseTop.get(orderCards.get(1))
                - cardBaseTop.get(orderCards.get(0))
                - cardBaseH.get(orderCards.get(0))
                : 0f;
        if (cardSlotGap < 0f) {
            cardSlotGap = 0f;
        }
        Ui.hapticLong(card);
        showCardGhost(card);
        card.setAlpha(0f);
        card.getParent().requestDisallowInterceptTouchEvent(true);
    }

    private void showCardGhost(LinearLayout card) {
        int w = card.getWidth();
        int h = card.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        cardGhostBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        card.draw(new Canvas(cardGhostBmp));
        FrameLayout content = findViewById(android.R.id.content);
        cardGhostOverlay = new FrameLayout(this);
        cardGhostOverlay.setClipChildren(false);
        cardGhostOverlay.setClickable(false);
        content.addView(cardGhostOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        int[] cl = new int[2];
        card.getLocationOnScreen(cl);
        int[] fl = new int[2];
        content.getLocationOnScreen(fl);
        cardGhost = new ImageView(this);
        cardGhost.setImageBitmap(cardGhostBmp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = cl[0] - fl[0];
        lp.topMargin = cl[1] - fl[1];
        cardGhost.setElevation(Ui.dp(this, 12));
        cardGhostOverlay.addView(cardGhost, lp);
    }

    private void moveCardTo(int target) {
        LinearLayout dragged = orderCards.get(cardDragIndex);
        List<LinearLayout> others = new ArrayList<>(orderCards);
        others.remove(dragged);
        int tgt = Math.max(0, Math.min(others.size(), target));
        if (tgt == cardDragIndex) {
            return;
        }
        others.add(tgt, dragged);
        orderCards.clear();
        orderCards.addAll(others);
        cardDragIndex = tgt;
        cardOrderChanged = true;
        for (int i = 0; i < orderCards.size(); i++) {
            LinearLayout c = orderCards.get(i);
            if (c == dragged) {
                continue;
            }
            Ui.springY(c, slotTopFor(orderCards, i) - cardBaseTop.get(c));
        }
    }

    private void dropCard(LinearLayout card) {
        cardDragLifted = false;
        float ty = slotTopFor(orderCards, cardDragIndex) - cardBaseTop.get(card);
        if (cardGhost != null && cardGhostOverlay != null) {
            Ui.springY(cardGhost, ty);
            Ui.alpha(cardGhost, 0f);
            final FrameLayout ov = cardGhostOverlay;
            final ImageView ghost = cardGhost;
            final Bitmap bmp = cardGhostBmp;
            ov.postDelayed(() -> {
                ghost.setImageBitmap(null);
                if (bmp != null) {
                    bmp.recycle();
                }
                if (ov.getParent() instanceof ViewGroup) {
                    ((ViewGroup) ov.getParent()).removeView(ov);
                }
            }, 240);
            cardGhost = null;
            cardGhostOverlay = null;
            cardGhostBmp = null;
        }
        Ui.springY(card, ty);
        Ui.alpha(card, 1f);
        Ui.haptic(card);
        if (!cardOrderChanged) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (LinearLayout c : orderCards) {
            keys.add(keyOf(c));
        }
        Prefs.setCardOrder(this, keys);
        cardRoot.postDelayed(this::relayoutCards, 360);
    }

    /** 弹簧落定后按新顺序重排视图（不清空内容，不重播入场）。 */
    private void relayoutCards() {
        for (LinearLayout c : orderCards) {
            Ui.cancel(c);
            c.setTranslationY(0f);
            c.setAlpha(1f);
            cardRoot.removeView(c);
        }
        for (LinearLayout c : orderCards) {
            cardRoot.addView(c);
        }
    }

    private final class CardDragGesture implements View.OnTouchListener {
        private final LinearLayout card;
        private final Runnable liftRun;
        private float downX, downY;
        private boolean lifted;

        CardDragGesture(LinearLayout card) {
            this.card = card;
            this.liftRun = () -> {
                lifted = true;
                liftCard(card);
            };
        }

        private void cancelLift() {
            card.removeCallbacks(liftRun);
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    lifted = false;
                    card.postDelayed(liftRun, LONG_PRESS_MS);
                    Ui.springScale(card, 0.98f);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = ev.getRawX() - downX;
                    float dy = ev.getRawY() - downY;
                    if (!lifted) {
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            cancelLift();
                            Ui.springScale(card, 1f);
                            return false;
                        }
                        return true;
                    }
                    if (!cardDragLifted) {
                        return true;
                    }
                    card.setTranslationY(dy);
                    if (cardGhost != null) {
                        cardGhost.setTranslationY(dy);
                    }
                    float center = cardBaseTop.get(card) + cardBaseH.get(card) / 2f + dy;
                    List<LinearLayout> others = new ArrayList<>(orderCards);
                    others.remove(card);
                    int target = 0;
                    float y = cardBlockTop;
                    for (LinearLayout c : others) {
                        if (center > y + cardBaseH.get(c) / 2f) {
                            target++;
                        }
                        y += cardBaseH.get(c) + cardSlotGap;
                    }
                    if (target != cardDragIndex) {
                        moveCardTo(target);
                        Ui.hapticTick(card);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    cancelLift();
                    if (lifted && cardDragLifted) {
                        lifted = false;
                        dropCard(card);
                    } else {
                        Ui.springScale(card, 1f);
                        card.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelLift();
                    if (lifted && cardDragLifted) {
                        lifted = false;
                        dropCard(card);
                    } else {
                        Ui.springScale(card, 1f);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
