package com.raster.dial;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
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
        void onTilt(float phase, float dirX, float dirY);
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

    private final OneEuroFilter filterX = new OneEuroFilter(0.5f, 0.03f, 1.0f);
    private final OneEuroFilter filterY = new OneEuroFilter(0.5f, 0.03f, 1.0f);

    private boolean hasQ0;
    private boolean hasPrevQ;
    private boolean invert;
    private boolean accelOnly;
    private int displayRotation = Surface.ROTATION_0;

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

    public void setDisplayRotation(int rotation) {
        displayRotation = rotation;
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
        // 传感器自然坐标 -> 当前屏幕坐标（y 向上）
        float sx, sy;
        switch (displayRotation) {
            case Surface.ROTATION_90:
                sx = ty;
                sy = -tx;
                break;
            case Surface.ROTATION_180:
                sx = -tx;
                sy = -ty;
                break;
            case Surface.ROTATION_270:
                sx = -ty;
                sy = tx;
                break;
            default:
                sx = tx;
                sy = ty;
                break;
        }

        float dt = lastNs == 0 ? 0.02f : (now - lastNs) / 1e9f;
        lastNs = now;
        if (dt <= 0f || dt > 0.2f) {
            dt = 0.02f;
        }

        // 1) One Euro 压手抖
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

        listener.onTilt(phase, smoothX, smoothY);
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
