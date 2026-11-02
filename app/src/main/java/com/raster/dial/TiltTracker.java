package com.raster.dial;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

/**
 * 倾角输入 + 消抖：
 * 1) 相对四元数轴角 (x,y)，不用 Euler 角，避免屏幕朝下/水平时的万向节奇异；
 * 2) One Euro Filter 压手抖：静止时强平滑，运动时自动跟手；
 * 3) 软死区：小幅抖动直接归零，且不产生硬边跳变；
 * 4) 方向死区 + 慢平滑：倾斜量很小时锁住上一次的光栅方向；
 * 5) phase 限速 3 圈/秒，任何残余跳变都不会变成频闪；
 * 6) 平板/横屏时由上层把 range 放大 1.2 倍，降低大屏上的视觉灵敏度。
 */
public class TiltTracker implements SensorEventListener {

    public interface Listener {
        void onTilt(Tilt t);
    }

    private static final String TAG = "TILT";
    private static final float DEAD = 0.015f;      // ~0.86°，软死区
    private static final float DIR_MIN = 0.02f;    // ~1.15°，低于此保持方向
    private static final float MAX_RATE = 3.0f;    // 圈/秒

    private final SensorManager sm;
    private final Sensor sensor;
    private final Listener listener;
    private final float[] q0 = new float[4];
    private final float[] q = new float[4];
    private final float[] prevQ = new float[4];
    private final float[] g0 = new float[3];

    /** 倾角输出：phase 全向累计；phaseX/phaseY 有符号单轴跟随；hx/hy 瞬时单轴（归一化）。 */
    public static final class Tilt {
        public float phase;
        public float phaseX;
        public float phaseY;
        public float hx;
        public float hy;
        public float dirX = 1f, dirY = 0f;
    }

    private final Tilt out = new Tilt();

    private final OneEuroFilter filterX = new OneEuroFilter(0.5f, 0.03f, 1.0f);
    private final OneEuroFilter filterY = new OneEuroFilter(0.5f, 0.03f, 1.0f);
    /** 单轴专用低延迟通道：高截止频率 + 速度自适应，保证跟手。 */
    private final OneEuroFilter filterHX = new OneEuroFilter(2.5f, 0.5f, 1.0f);
    private final OneEuroFilter filterHY = new OneEuroFilter(2.5f, 0.5f, 1.0f);

    private boolean hasQ0;
    private boolean hasPrevQ;
    private boolean invert;
    private boolean accelOnly;
    private int displayRotation = Surface.ROTATION_0;
    private boolean naturalLandscape;

    private float smoothX = 1f, smoothY = 0f;
    private float phase = 0f;
    private long lastNs;
    private float range = 0.35f;

    public TiltTracker(Context c, Listener l) {
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (s == null) {
            s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        if (s == null) {
            s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            accelOnly = true;
        }
        sensor = s;
        listener = l;
        Log.i(TAG, "sensor=" + (s == null ? "none" : s.getName()) + " accelOnly=" + accelOnly);
    }

    public void setInvert(boolean v) {
        invert = v;
    }

    /**
     * 屏幕旋转与其自然方向。getRotation() 是相对自然方向的：手机自然竖屏，
     * 多数平板自然横屏（rotation 0 即横屏），传感器轴须按“相对竖屏的角度”补偿。
     */
    public void setDisplayRotation(int rotation, boolean naturalLandscape) {
        displayRotation = rotation;
        this.naturalLandscape = naturalLandscape;
    }

    /** 由当前旋转与 realSize 反推自然方向是否横屏（平板）；rot 0/180 不改变横竖，90/270 交换。 */
    public static boolean naturalLandscape(Display d) {
        int rot = d.getRotation();
        Point real = new Point();
        d.getRealSize(real);
        return (rot == Surface.ROTATION_0 || rot == Surface.ROTATION_180)
                ? real.x > real.y : real.x < real.y;
    }

    /** 半程角度（弧度）：越大越不灵敏。 */
    public void setRange(float r) {
        if (r > 0.05f && r < 2.0f) {
            range = r;
        }
    }

    public void start() {
        if (sensor != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        }
        hasQ0 = false;
        hasPrevQ = false;
        resetState();
    }

    public void stop() {
        sm.unregisterListener(this);
    }

    public void calibrate() {
        hasQ0 = false;
        hasPrevQ = false;
        resetState();
        Log.i(TAG, "calibrate requested");
    }

    private void resetState() {
        phase = 0f;
        smoothX = 1f;
        smoothY = 0f;
        filterX.reset();
        filterY.reset();
        filterHX.reset();
        filterHY.reset();
        out.phaseX = 0f;
        out.phaseY = 0f;
        lastNs = 0;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accel(e.values[0], e.values[1], e.values[2], e.timestamp);
            return;
        }

        q[0] = e.values[0];
        q[1] = e.values[1];
        q[2] = e.values[2];
        q[3] = e.values[3];

        if (hasPrevQ && dot4(q, prevQ) < 0f) {
            negate4(q);
        }
        System.arraycopy(q, 0, prevQ, 0, 4);
        hasPrevQ = true;

        if (!hasQ0) {
            System.arraycopy(q, 0, q0, 0, 4);
            hasQ0 = true;
            lastNs = e.timestamp;
            return;
        }

        // qRel = conj(q0) * q
        float x0 = -q0[0], y0 = -q0[1], z0 = -q0[2], w0 = q0[3];
        float x = w0 * q[0] + x0 * q[3] + y0 * q[2] - z0 * q[1];
        float y = w0 * q[1] - x0 * q[2] + y0 * q[3] + z0 * q[0];
        float z = w0 * q[2] + x0 * q[1] - y0 * q[0] + z0 * q[3];
        float w = w0 * q[3] - x0 * q[0] - y0 * q[1] - z0 * q[2];

        if (w < 0f) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }

        float sinHalf = (float) Math.hypot(x, y);
        float tx = 0f, ty = 0f;
        if (sinHalf > 1e-6f) {
            float angle = 2f * (float) Math.atan2(sinHalf, w);
            tx = x / sinHalf * angle;
            ty = y / sinHalf * angle;
        }
        update(tx, ty, e.timestamp);
    }

    private void accel(float ax, float ay, float az, long now) {
        float n = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (n < 1e-3f) {
            return;
        }
        ax /= n;
        ay /= n;
        az /= n;

        if (!hasQ0) {
            g0[0] = ax;
            g0[1] = ay;
            g0[2] = az;
            hasQ0 = true;
            lastNs = now;
            return;
        }

        float cx = g0[1] * az - g0[2] * ay;
        float cy = g0[2] * ax - g0[0] * az;
        float cz = g0[0] * ay - g0[1] * ax;
        float s = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        float d = g0[0] * ax + g0[1] * ay + g0[2] * az;
        float tx = 0f, ty = 0f;
        if (s > 1e-6f) {
            float angle = (float) Math.atan2(s, d);
            tx = cx / s * angle;
            ty = cy / s * angle;
        }
        update(tx, ty, now);
    }

    private void update(float tx, float ty, long now) {
        // 传感器自然坐标 -> 当前屏幕坐标（y 向上）。
        // 先把 getRotation() 换算成“相对竖屏的顺时针档位”：自然横屏的平板
        // 整体偏移一档（rot0 即横屏，等效竖屏坐标系已转 90°）；自然竖屏的
        // 手机逐位保持原有映射。
        int idx = naturalLandscape ? (displayRotation + 1) & 3 : displayRotation;
        float sx, sy;
        switch (idx) {
            case 1: // ROTATION_90
                sx = ty;
                sy = -tx;
                break;
            case 2: // ROTATION_180
                sx = -tx;
                sy = -ty;
                break;
            case 3: // ROTATION_270
                sx = -ty;
                sy = tx;
                break;
            default: // ROTATION_0
                sx = tx;
                sy = ty;
                break;
        }

        float dt = lastNs == 0 ? 0.02f : (now - lastNs) / 1e9f;
        lastNs = now;
        if (dt <= 0f || dt > 0.2f) {
            dt = 0.02f;
        }

        // 0) 单轴跟手通道：轻滤波（低延迟），只保留很小的死区。
        //    实测：左右倾斜对应屏幕 y 分量，上下倾斜对应 x 分量
        float hx = filterHX.filter(sy, dt);
        float hy = filterHY.filter(sx, dt);
        if (Math.abs(hx) < DEAD * 0.6f) {
            hx = 0f;
        }
        if (Math.abs(hy) < DEAD * 0.6f) {
            hy = 0f;
        }

        // 1) One Euro 压手抖（过渡线式使用）
        float fx = filterX.filter(sx, dt);
        float fy = filterY.filter(sy, dt);
        float rawMag = (float) Math.hypot(fx, fy);

        // 2) 软死区：小幅抖动归零，方向保持
        float mag;
        if (rawMag <= DEAD) {
            fx = 0f;
            fy = 0f;
            mag = 0f;
        } else {
            float k = (rawMag - DEAD) / rawMag;
            fx *= k;
            fy *= k;
            mag = rawMag - DEAD;
        }

        float target = mag / range;
        float maxDelta = MAX_RATE * dt;
        float delta = target - phase;
        if (delta > maxDelta) {
            delta = maxDelta;
        } else if (delta < -maxDelta) {
            delta = -maxDelta;
        }
        phase += delta;



        // 3) 方向死区 + 慢平滑
        if (rawMag > DIR_MIN && mag > 1e-5f) {
            float nx = fx / mag;
            float ny = fy / mag;
            if (invert) {
                nx = -nx;
                ny = -ny;
            }
            smoothX = smoothX * 0.92f + nx * 0.08f;
            smoothY = smoothY * 0.92f + ny * 0.08f;
            float sl = (float) Math.hypot(smoothX, smoothY);
            if (sl > 0.001f) {
                smoothX /= sl;
                smoothY /= sl;
            }
        }

        // 有符号单轴跟随相位（供“仅 X / 仅 Y”的连续过渡线）
        float maxDeltaH = MAX_RATE * dt;
        float targetX = hx / range;
        float dxX = targetX - out.phaseX;
        out.phaseX += Math.max(-maxDeltaH, Math.min(maxDeltaH, dxX));
        float targetY = hy / range;
        float dxY = targetY - out.phaseY;
        out.phaseY += Math.max(-maxDeltaH, Math.min(maxDeltaH, dxY));

        out.phase = phase;
        out.hx = hx / range;
        out.hy = hy / range;
        out.dirX = smoothX;
        out.dirY = smoothY;
        listener.onTilt(out);
    }

    private static float dot4(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
    }

    private static void negate4(float[] a) {
        a[0] = -a[0];
        a[1] = -a[1];
        a[2] = -a[2];
        a[3] = -a[3];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
