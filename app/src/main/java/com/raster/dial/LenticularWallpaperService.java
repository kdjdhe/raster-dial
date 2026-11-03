package com.raster.dial;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.util.Locale;

/**
 * 多组光栅壁纸。运行时按 surface 宽高比选择竖屏/横屏素材，
 * 绘制时 aspect-fill 裁切，不拉伸；传感器方向按屏幕旋转补偿。
 */
public class LenticularWallpaperService extends WallpaperService {

    private static final String TAG = "WALL";

    @Override
    public Engine onCreateEngine() {
        return new LentiEngine();
    }

    private final class LentiEngine extends Engine {

        private final HandlerThread thread = new HandlerThread("lenti-wall");
        private Handler h;
        private GroupCatalog.Group group;
        private Bitmap[] images;
        private String loadedId;
        private boolean landscape;

        private TiltTracker tracker;
        private volatile float phase = 0f;
        private volatile float hx = 0f, hy = 0f;    // 瞬时单轴倾角（归一化）
        private volatile float phaseX = 0f, phaseY = 0f;  // 有符号单轴跟随相位
        private volatile float dirX = 1f, dirY = 0f;
        private volatile boolean visible;

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean debug = true;
        private int styleMode;              // 0 过渡线 / 1 百叶窗 / 2 类OriginOS
        private int axisLine;               // 0 全向 / 1 仅横向 / 2 仅竖向
        private float eyeStrength;          // 护眼强度（常驻低透光衰减，0=关闭）
        private int axisH;                  // 0 自动 / 1 横向 / 2 竖向（百叶窗/类OriginOS）
        private boolean autoVertical;       // 自动轴：带迟滞，避免对角线来回抖
        private int lastStyle = -1;
        private final Paint dividerPaint = new Paint();
        private final HStepper hStep = new HStepper();
        private static final int BLINDS_N = 6;
        private static final float SCENE_HINT = 0.7f;  // 调试覆盖 phase→归一化倾角的换算
        private float avgMs;
        private int frames;
        private String debugText = "";
        private long lastPrefRead;
        private float overridePhase = -1f;
        private float overrideX = 1f, overrideY = 0f;
        private float lastSens = -1f;
        private float baseSens = 0.35f;
        private long lastCustomVersion = -1;
        private int lastRotation = -1;

        private final Runnable draw = new Runnable() {
            @Override
            public void run() {
                drawFrame();
                if (visible) {
                    h.postDelayed(this, avgMs > 14f ? 33 : 16);
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            thread.start();
            h = new Handler(thread.getLooper());

            GroupCatalog.Group g = GroupCatalog.find(
                    LenticularWallpaperService.this,
                    GroupCatalog.currentId(LenticularWallpaperService.this));
            loadGroup(g);

            tracker = new TiltTracker(LenticularWallpaperService.this,
                    t -> {
                        phase = t.phase;
                        phaseX = t.phaseX;
                        phaseY = t.phaseY;
                        hx = t.hx;
                        hy = t.hy;
                        dirX = t.dirX;
                        dirY = t.dirY;
                    });

            debugPaint.setColor(0x99FFFFFF);
            debugPaint.setTextSize(26);
            debugPaint.setTypeface(Typeface.MONOSPACE);
            debugPaint.setShadowLayer(6, 0, 2, 0xAA000000);

            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

            Log.i(TAG, "engine created group=" + (group == null ? "?" : group.id)
                    + " landscape=" + landscape);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            boolean land = width > height;
            if (land != landscape || images == null) {
                landscape = land;
                if (group != null) {
                    loadGroup(group);
                }
                applyRange();
            }
            super.onSurfaceChanged(holder, format, width, height);
            Log.i(TAG, "surface " + width + "x" + height + " landscape=" + landscape);
        }

        private void loadGroup(GroupCatalog.Group g) {
            if (g == null) {
                return;
            }
            Bitmap[] imgs = GroupImages.load(LenticularWallpaperService.this, g, landscape);
            Bitmap[] old = images;
            images = imgs;
            group = g;
            loadedId = g.id;
            if (old != null && old != imgs) {
                for (Bitmap b : old) {
                    if (b != null) {
                        b.recycle();
                    }
                }
            }
            Log.i(TAG, "group=" + g.id + " count=" + imgs.length
                    + " landscape=" + landscape);
        }

        /** 平板/横屏时半程角度放大 1.2 倍，降低大屏上的视觉灵敏度。 */
        private void applyRange() {
            if (tracker != null) {
                tracker.setRange(baseSens * (landscape ? 1.2f : 1f));
            }
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            Log.i(TAG, "visible=" + v);
            if (v) {
                SharedPreferences sp = getSharedPreferences("lock", MODE_PRIVATE);
                tracker.setInvert(sp.getBoolean("invert", false));
                baseSens = sp.getFloat("sens", 0.35f);
                lastSens = baseSens;
                applyRange();
                debug = sp.getBoolean("wall_debug", true);
                tracker.start();
                tracker.calibrate();
                h.post(draw);
            } else {
                tracker.stop();
                h.removeCallbacks(draw);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            if (h != null) {
                h.removeCallbacks(draw);
            }
            if (tracker != null) {
                tracker.stop();
            }
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            visible = false;
            if (h != null) {
                h.removeCallbacks(draw);
            }
            if (tracker != null) {
                tracker.stop();
            }
            thread.quitSafely();
            if (images != null) {
                for (Bitmap b : images) {
                    if (b != null) {
                        b.recycle();
                    }
                }
                images = null;
            }
            super.onDestroy();
        }

        private void readDebugPrefs() {
            long now = SystemClock.uptimeMillis();
            if (now - lastPrefRead < 500) {
                return;
            }
            lastPrefRead = now;
            SharedPreferences sp = getSharedPreferences("lock", MODE_PRIVATE);
            overridePhase = sp.getFloat("wall_progress", -1f);
            overrideX = sp.getFloat("wall_dir_x", 1f);
            overrideY = sp.getFloat("wall_dir_y", 0f);
            debug = sp.getBoolean("wall_debug", true);
            styleMode = Math.max(0, Math.min(2, sp.getInt("style", 0)));
            axisLine = Math.max(0, Math.min(2, sp.getInt("axis_line", 0)));
            axisH = Prefs.axisH(LenticularWallpaperService.this);
            eyeStrength = Math.max(0f, Math.min(0.30f,
                    sp.getFloat("eye_care_strength", 0f)));
            if (styleMode != lastStyle) {
                lastStyle = styleMode;
                hStep.reset();
            }
            float sens = sp.getFloat("sens", 0.35f);
            if (Math.abs(sens - lastSens) > 0.001f) {
                lastSens = sens;
                baseSens = sens;
                applyRange();
            }

            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            int rot = d.getRotation();
            if (rot != lastRotation) {
                lastRotation = rot;
                tracker.setDisplayRotation(rot, TiltTracker.naturalLandscape(d));
            }

            String gid = GroupCatalog.currentId(LenticularWallpaperService.this);
            if (loadedId == null || !gid.equals(loadedId)) {
                loadGroup(GroupCatalog.find(LenticularWallpaperService.this, gid));
            }

            long cv = sp.getLong("custom_version", 0);
            if (cv != lastCustomVersion) {
                lastCustomVersion = cv;
                if (group != null && group.custom) {
                    GroupCatalog.Group g = GroupCatalog.find(
                            LenticularWallpaperService.this, group.id);
                    if (g != null) {
                        loadGroup(g);
                    }
                }
            }
        }

        private void drawFrame() {
            readDebugPrefs();
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                try {
                    c = holder.lockHardwareCanvas();
                } catch (Exception e) {
                    c = null;
                }
                if (c == null) {
                    c = holder.lockCanvas();
                }
                if (c == null) {
                    return;
                }

                int w = c.getWidth();
                int ht = c.getHeight();
                long t0 = SystemClock.uptimeMillis();

                int n = images == null ? 0 : images.length;
                if (n == 0) {
                    c.drawColor(0xFF000000);
                    return;
                }

                float ph = overridePhase >= 0f ? overridePhase : phase;
                float dx = overridePhase >= 0f ? overrideX : dirX;
                float dy = overridePhase >= 0f ? overrideY : dirY;

                float len = (float) Math.hypot(dx, dy);
                if (len < 0.001f) {
                    dx = 1f;
                    dy = 0f;
                    len = 1f;
                }
                dx /= len;
                dy /= len;

                // 传感器输出 y 向上；Canvas y 向下
                float dyCanvas = -dy;

                int dbgSeg;
                float dbgFrac;
                if (styleMode >= 1) {
                    // 横向风格：瞬时倾角映射场景，触发后 100ms 一锤子走完
                    long now = SystemClock.uptimeMillis();
                    boolean vertical;
                    if (axisH == 1) {
                        vertical = false;
                    } else if (axisH == 2) {
                        vertical = true;
                    } else {
                        float ax = Math.abs(hx);
                        float ay = Math.abs(hy);
                        if (autoVertical) {
                            if (ax > ay * 1.15f) {
                                autoVertical = false;
                            }
                        } else if (ay > ax * 1.15f) {
                            autoVertical = true;
                        }
                        vertical = autoVertical;
                    }
                    float hIn = overridePhase >= 0f ? overridePhase * SCENE_HINT
                            : (vertical ? hy : hx);
                    float soft = styleMode == 2 ? 0.32f : 0.09f;
                    if (hStep.update(hIn, now)) {
                        int from = hStep.fromMod(n);
                        int to = hStep.segMod(n);
                        float t = hStep.progress(now);
                        float dirXW = vertical ? 0f : hStep.dir;
                        float dirYW = vertical ? hStep.dir : 0f;
                        drawAspect(c, images[from], w, ht);
                        Bitmap reveal = images[to];
                        if (reveal != null && t >= 0.001f) {
                            if (t >= 0.999f) {
                                drawAspect(c, reveal, w, ht);
                            } else if (styleMode == 1) {
                                drawBlinds(c, w, ht, t, dirXW, dirYW, reveal, vertical);
                            } else {
                                drawGradientWipe(c, w, ht, t, dirXW, dirYW, reveal, soft);
                            }
                        }
                        dbgSeg = to;
                        dbgFrac = t;
                    } else {
                        dbgSeg = hStep.segMod(n);
                        dbgFrac = 0f;
                        drawAspect(c, images[dbgSeg], w, ht);
                    }
                } else {
                    float phUse;
                    float wdX;
                    float wdY;
                    if (overridePhase >= 0f) {
                        phUse = overridePhase;
                        wdX = dx;
                        wdY = dyCanvas;
                    } else if (axisLine == 1) {
                        phUse = phaseX;
                        wdX = 1f;
                        wdY = 0f;
                    } else if (axisLine == 2) {
                        phUse = phaseY;
                        wdX = 0f;
                        wdY = 1f;
                    } else {
                        phUse = ph;
                        wdX = dx;
                        wdY = dyCanvas;
                    }
                    int seg = ((int) Math.floor(phUse)) % n;
                    if (seg < 0) {
                        seg += n;
                    }
                    float frac = phUse - (float) Math.floor(phUse);
                    Bitmap base = images[seg];
                    Bitmap reveal = images[(seg + 1) % n];

                    drawAspect(c, base, w, ht);
                    if (reveal != null && frac > 0.001f) {
                        if (frac >= 0.999f) {
                            drawAspect(c, reveal, w, ht);
                        } else {
                            drawGradientWipe(c, w, ht, frac, wdX, wdY, reveal, 0.09f);
                        }
                    }
                    dbgSeg = seg;
                    dbgFrac = frac;
                }

                if (eyeStrength > 0.001f) {
                    c.drawColor(((int) (eyeStrength * 255)) << 24);
                }

                long dt = SystemClock.uptimeMillis() - t0;
                frames++;
                avgMs = avgMs * 0.95f + dt * 0.05f;
                if (frames % 10 == 0) {
                    debugText = String.format(Locale.US,
                            "%s %s rot=%d ph=%.2f s=%d f=%.2f (%.2f,%.2f)%s %.1fms",
                            group == null ? "?" : group.nameZh,
                            landscape ? "L" : "P",
                            lastRotation * 90,
                            ph, dbgSeg, dbgFrac, dx, dyCanvas,
                            overridePhase >= 0f ? " DBG" : "", avgMs);
                }
                if (debug && debugText.length() > 0) {
                    c.drawText(debugText, 28, ht - 36, debugPaint);
                }
            } catch (Exception e) {
                Log.e(TAG, "draw", e);
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        /** aspect-fill：按目标宽高比中心裁切，不拉伸。 */
        private void drawAspect(Canvas c, Bitmap bm, int w, int ht) {
            if (bm == null) {
                return;
            }
            Rect src = srcRect(bm, w, ht);
            c.drawBitmap(bm, src, new Rect(0, 0, w, ht), bitmapPaint);
        }

        private Rect srcRect(Bitmap bm, int w, int ht) {
            float target = (float) w / ht;
            float sa = (float) bm.getWidth() / bm.getHeight();
            if (sa > target) {
                int cw = (int) (bm.getHeight() * target);
                int l = (bm.getWidth() - cw) / 2;
                return new Rect(l, 0, l + cw, bm.getHeight());
            } else {
                int ch = (int) (bm.getWidth() / target);
                int t = (bm.getHeight() - ch) / 2;
                return new Rect(0, t, bm.getWidth(), t + ch);
            }
        }

        private void drawGradientWipe(Canvas c, int w, int ht, float frac,
                                      float dx, float dy, Bitmap reveal, float soft) {
            float cx = w * 0.5f;
            float cy = ht * 0.5f;
            float extent = 0.5f * (Math.abs(dx) * w + Math.abs(dy) * ht);
            float edge = frac * (1f + 2f * soft) - soft;
            setWipeShader(cx - extent * dx, cy - extent * dy,
                    cx + extent * dx, cy + extent * dy,
                    Math.max(0f, Math.min(1f, edge - soft)),
                    Math.max(0f, Math.min(1f, edge + soft)));

            int layer = c.saveLayer(0f, 0f, w, ht, null);
            drawAspect(c, reveal, w, ht);
            c.drawRect(0f, 0f, w, ht, maskPaint);
            c.restoreToCount(layer);
        }

        /** 软渐变遮罩：s0..s1 为白色（显露）区间，两侧透明。 */
        private void setWipeShader(float x0, float y0, float x1, float y1,
                                   float s0, float s1) {
            int[] colors;
            float[] pos;
            if (s1 <= 0f) {
                colors = new int[]{0x00000000, 0x00000000};
                pos = new float[]{0f, 1f};
            } else if (s0 >= 1f) {
                colors = new int[]{0xFFFFFFFF, 0xFFFFFFFF};
                pos = new float[]{0f, 1f};
            } else if (s0 <= 0f) {
                colors = new int[]{0xFFFFFFFF, 0x00000000, 0x00000000};
                pos = new float[]{0f, s1, 1f};
            } else if (s1 >= 1f) {
                colors = new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0x00000000};
                pos = new float[]{0f, s0, 1f};
            } else {
                colors = new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0x00000000, 0x00000000};
                pos = new float[]{0f, s0, s1, 1f};
            }
            maskPaint.setShader(new LinearGradient(x0, y0, x1, y1,
                    colors, pos, Shader.TileMode.CLAMP));
        }

        /** 百叶窗：横向变化=竖分条（竖边界）；竖向变化=横分条（横边界），每条同步推进。 */
        private void drawBlinds(Canvas c, int w, int ht, float frac,
                                float dx, float dy, Bitmap reveal, boolean verticalAxis) {
            float soft = 0.09f;
            float edge = frac * (1f + 2f * soft) - soft;
            float s0 = Math.max(0f, Math.min(1f, edge - soft));
            float s1 = Math.max(0f, Math.min(1f, edge + soft));
            dividerPaint.setColor(0x2E000000);
            if (!verticalAxis) {
                float stripW = w / (float) BLINDS_N;
                for (int i = 0; i < BLINDS_N; i++) {
                    float l = i * stripW;
                    float cx = l + stripW * 0.5f;
                    float cy = ht * 0.5f;
                    float extent = 0.5f * (Math.abs(dx) * stripW + Math.abs(dy) * ht);
                    setWipeShader(cx - extent * dx, cy - extent * dy,
                            cx + extent * dx, cy + extent * dy, s0, s1);
                    int layer = c.saveLayer(l, 0f, l + stripW, ht, null);
                    c.clipRect(l, 0f, l + stripW, ht);
                    drawAspect(c, reveal, w, ht);
                    c.drawRect(l, 0f, l + stripW, ht, maskPaint);
                    c.restoreToCount(layer);
                }
                for (int i = 1; i < BLINDS_N; i++) {
                    float x = i * stripW;
                    c.drawRect(x - 1.5f, 0f, x + 1.5f, ht, dividerPaint);
                }
            } else {
                float stripH = ht / (float) BLINDS_N;
                for (int i = 0; i < BLINDS_N; i++) {
                    float t = i * stripH;
                    float cx = w * 0.5f;
                    float cy = t + stripH * 0.5f;
                    float extent = 0.5f * (Math.abs(dx) * w + Math.abs(dy) * stripH);
                    setWipeShader(cx - extent * dx, cy - extent * dy,
                            cx + extent * dx, cy + extent * dy, s0, s1);
                    int layer = c.saveLayer(0f, t, w, t + stripH, null);
                    c.clipRect(0f, t, w, t + stripH);
                    drawAspect(c, reveal, w, ht);
                    c.drawRect(0f, t, w, t + stripH, maskPaint);
                    c.restoreToCount(layer);
                }
                for (int i = 1; i < BLINDS_N; i++) {
                    float y = i * stripH;
                    c.drawRect(0f, y - 1.5f, w, y + 1.5f, dividerPaint);
                }
            }
        }


    }
}
