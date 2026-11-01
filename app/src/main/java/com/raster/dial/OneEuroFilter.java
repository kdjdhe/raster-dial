package com.raster.dial;

/**
 * One Euro Filter：静止时强平滑（压手抖），运动时自动提高截止频率（跟手）。
 * 用于倾角 x/y 两轴。
 */
final class OneEuroFilter {

    private final float minCutoff;
    private final float beta;
    private final float dCutoff;

    private boolean init;
    private float xPrev;
    private float dxPrev;

    OneEuroFilter(float minCutoff, float beta, float dCutoff) {
        this.minCutoff = minCutoff;
        this.beta = beta;
        this.dCutoff = dCutoff;
    }

    void reset() {
        init = false;
        xPrev = 0f;
        dxPrev = 0f;
    }

    float filter(float x, float dt) {
        if (dt <= 0f) {
            dt = 0.02f;
        }
        if (!init) {
            xPrev = x;
            dxPrev = 0f;
            init = true;
            return x;
        }
        float dx = (x - xPrev) / dt;
        float edx = lowpass(dx, dxPrev, alpha(dCutoff, dt));
        float cutoff = minCutoff + beta * Math.abs(edx);
        float xf = lowpass(x, xPrev, alpha(cutoff, dt));
        xPrev = xf;
        dxPrev = edx;
        return xf;
    }

    private static float alpha(float cutoff, float dt) {
        float tau = 1f / (2f * (float) Math.PI * cutoff);
        return 1f / (1f + tau / dt);
    }

    private static float lowpass(float x, float prev, float a) {
        return prev + a * (x - prev);
    }
}
