package com.raster.dial;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.InputStream;

/**
 * 按当前 surface 方向加载组图片：
 * - 内置组：竖屏用 groups/，横屏用 groups_land/
 * - 自定义组：files/custom/
 */
public final class GroupImages {

    private static final String TAG = "GIMG";

    private GroupImages() {
    }

    public static Bitmap[] load(Context c, GroupCatalog.Group group, boolean landscape) {
        if (group.custom) {
            return CustomStore.loadBitmaps(c);
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

    private static Bitmap loadAsset(Context c, String path) {
        try (InputStream is = c.getAssets().open(path)) {
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            Log.e(TAG, "load " + path, e);
            return null;
        }
    }
}
