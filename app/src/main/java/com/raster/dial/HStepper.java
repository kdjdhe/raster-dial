package com.raster.dial;

/**
 * 横向过渡风格的“瞬态映射”状态机（壁纸引擎与全屏预览共用）。
 *
 * 场景索引 = 当前横向倾角 / 步长（瞬时值，不累计）：
 * 摆到某个角度就稳定在对应场景，持续倾斜也不会连环翻页。
 * 跨档触发一次过渡，100ms 内一锤子跑完，不停留中间态。
 */
final class HStepper {

    static final long DURATION_MS = 100;
    private static final float SCENE_PER_RANGE = 0.7f;  // 每 0.7×半程角一档
    private static final float HYST = 0.55f;            // 滞回，防边界抖动
    private static final int UNSET = Integer.MIN_VALUE;

    private int seg = UNSET;
    private long startMs;
    /** 当前过渡方向：+1 前进 / -1 后退 / 0 稳定。 */
    int dir;

    void reset() {
        seg = UNSET;
        dir = 0;
        startMs = 0;
    }

    /** 每帧调用（hz 为归一化横向倾角，1.0 = 半程角）；返回是否正在过渡。 */
    boolean update(float hz, long now) {
        float steps = hz / SCENE_PER_RANGE;
        if (seg == UNSET) {
            seg = Math.round(steps);
            dir = 0;
            startMs = 0;
        }
        if (dir != 0 && now - startMs < DURATION_MS) {
            return true;
        }
        dir = 0;
        float dist = steps - seg;
        if (dist > HYST) {
            dir = 1;
        } else if (dist < -HYST) {
            dir = -1;
        }
        if (dir == 0) {
            return false;
        }
        seg += dir;
        startMs = now;
        return true;
    }

    /** 过渡进度 0..1（固定时长）。 */
    float progress(long now) {
        return Math.min(1f, (now - startMs) / (float) DURATION_MS);
    }

    int segMod(int n) {
        return ((seg % n) + n) % n;
    }

    int fromMod(int n) {
        return (((seg - dir) % n) + n) % n;
    }
}
