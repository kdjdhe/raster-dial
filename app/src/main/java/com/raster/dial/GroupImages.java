package com.raster.dial;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.InputStream;

/**
 * 按当前 surface 方向加载组图片：
 * - 内置组：竖屏用 groups/，横屏用 groups_land/
 * - 自定义组：files/custom/，按自定义组 id 读取
 */
public final class GroupImages {

    private static final String TAG = "GIMG";

    private GroupImages() {
    }

    public static Bitmap[] load(Context c, GroupCatalog.Group group, boolean landscape) {
        if (group == null) {
            return new Bitmap[0];
        }
        if (group.custom) {
            return CustomStore.loadBitmaps(c, group.id);
        }
        String dir = landscape ? "groups_land/" : "groups/";
        Bitmap[] out = new Bitmap[group.count];
        for (int i = 0; i < group.count; i++) {
            out[i] = loadAsset(c, dir + group.id + "/" + (i + 1) + ".webp");
            if (out[i] == null && landscape) {
                out[i] = loadAsset(c, "groups/" + group.id + "/" + (i + 1) + ".webp");
            }
        }
        return out;
    }

    /** 选择器/主界面缩略图：第一张图；自定义空组返回 null。 */
    public static Bitmap loadThumb(Context c, GroupCatalog.Group group, int maxPx) {
        if (group == null) {
            return null;
        }
        if (group.custom) {
            File f = CustomStore.firstDisplay(c, group.id);
            return f == null ? null : decodeFileThumb(f, maxPx);
        }
        return decodeAssetThumb(c, "groups/" + group.id + "/1.webp", maxPx);
    }

    private static Bitmap loadAsset(Context c, String path) {
        try (InputStream is = c.getAssets().open(path)) {
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            Log.e(TAG, "load " + path, e);
            return null;
        }
    }

    private static Bitmap decodeAssetThumb(Context c, String path, int maxPx) {
        try (InputStream is = c.getAssets().open(path)) {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, b);
            if (b.outWidth <= 0 || b.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample(b.outWidth, b.outHeight, maxPx);
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream is2 = c.getAssets().open(path)) {
                return BitmapFactory.decodeStream(is2, null, o);
            }
        } catch (Exception e) {
            Log.e(TAG, "thumb " + path, e);
            return null;
        }
    }

    private static Bitmap decodeFileThumb(File f, int maxPx) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), b);
        if (b.outWidth <= 0 || b.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample(b.outWidth, b.outHeight, maxPx);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private static int sample(int w, int h, int maxPx) {
        int s = 1;
        while (Math.max(w, h) / s > maxPx) {
            s *= 2;
        }
        return s;
    }
}
