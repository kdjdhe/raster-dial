package com.raster.dial;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自定义光栅组：槽位图片存在 files/custom/，每个槽位有 master（原图）与 display（旋转后）。
 * 支持选图、旋转、上移/下移、删除、添加。
 */
public final class CustomStore {

    private static final String TAG = "CUSTOM";
    private static final String DIR = "custom";
    private static final String MANIFEST = "group.json";
    private static final int MAX_DIM = 3000;

    public static final class Slot {
        public String master;
        public String display;
        public int rotation;

        Slot(String master, String display, int rotation) {
            this.master = master;
            this.display = display;
            this.rotation = rotation;
        }
    }

    private CustomStore() {
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    public static synchronized List<Slot> load(Context c) {
        File f = new File(dir(c), MANIFEST);
        if (!f.exists()) {
            seed(c);
        }
        List<Slot> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readFile(f));
            JSONArray arr = root.getJSONArray("slots");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Slot(o.getString("m"), o.getString("d"),
                        o.optInt("r", 0)));
            }
        } catch (Exception e) {
            Log.e(TAG, "load manifest failed", e);
        }
        return list;
    }

    public static synchronized void save(Context c, List<Slot> slots) {
        try {
            JSONArray arr = new JSONArray();
            for (Slot s : slots) {
                JSONObject o = new JSONObject();
                o.put("m", s.master);
                o.put("d", s.display);
                o.put("r", s.rotation);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("slots", arr);
            writeFile(new File(dir(c), MANIFEST), root.toString());
            bumpVersion(c);
        } catch (Exception e) {
            Log.e(TAG, "save manifest failed", e);
        }
    }

    public static int count(Context c) {
        return load(c).size();
    }

    public static synchronized Bitmap[] loadBitmaps(Context c) {
        List<Slot> slots = load(c);
        List<Bitmap> out = new ArrayList<>();
        for (Slot s : slots) {
            Bitmap bm = BitmapFactory.decodeFile(new File(dir(c), s.display).getAbsolutePath());
            if (bm != null) {
                out.add(bm);
            }
        }
        return out.toArray(new Bitmap[0]);
    }

    /** 选图：解码 -> 按 EXIF 摆正 -> 存 master -> 按 rotation 生成 display。 */
    public static Slot importImage(Context c, Uri uri, int rotation) throws Exception {
        ContentResolver cr = c.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new Exception("无法读取图片尺寸");
        }
        int sample = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxDim / sample > MAX_DIM) {
            sample *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm;
        try (InputStream is = cr.openInputStream(uri)) {
            bm = BitmapFactory.decodeStream(is, null, o);
        }
        if (bm == null) {
            throw new Exception("图片解码失败");
        }
        Bitmap corrected = rotate(bm, exifRotation(c, uri));
        if (corrected == null) {
            throw new Exception("图片旋转失败");
        }
        if (corrected != bm) {
            bm.recycle();
        }
        bm = corrected;

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        File master = new File(dir(c), "m_" + id + ".webp");
        try (FileOutputStream fos = new FileOutputStream(master)) {
            bm.compress(Bitmap.CompressFormat.WEBP, 92, fos);
        }
        File display = new File(dir(c), "s_" + id + ".webp");
        writeDisplay(bm, rotation, display);
        return new Slot(master.getName(), display.getName(), rotation);
    }

    /** 基于 master 重新生成 display（不做累积旋转，限制显示分辨率以控制内存）。 */
    public static void renderDisplay(Context c, Slot slot) throws Exception {
        Bitmap master = BitmapFactory.decodeFile(new File(dir(c), slot.master).getAbsolutePath());
        if (master == null) {
            throw new Exception("master 丢失");
        }
        writeDisplay(master, slot.rotation, new File(dir(c), slot.display));
    }

    private static void writeDisplay(Bitmap master, int rotation, File out) throws Exception {
        Bitmap rotated = rotate(master, rotation);
        Bitmap scaled = scaleDown(rotated, 1600);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            scaled.compress(Bitmap.CompressFormat.WEBP, 90, fos);
        }
        if (scaled != rotated) {
            scaled.recycle();
        }
        if (rotated != master) {
            rotated.recycle();
        }
        master.recycle();
    }

    private static Bitmap scaleDown(Bitmap bm, int maxDim) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        float s = Math.min(1f, (float) maxDim / Math.max(w, h));
        if (s >= 1f) {
            return bm;
        }
        return Bitmap.createScaledBitmap(bm, Math.round(w * s), Math.round(h * s), true);
    }

    public static void deleteFiles(Context c, Slot slot) {
        new File(dir(c), slot.master).delete();
        new File(dir(c), slot.display).delete();
    }

    public static void bumpVersion(Context c) {
        SharedPreferences sp = c.getSharedPreferences("lock", Context.MODE_PRIVATE);
        sp.edit().putLong("custom_version", sp.getLong("custom_version", 0) + 1).apply();
    }

    private static void seed(Context c) {
        try {
            File dir = dir(c);
            List<Slot> slots = new ArrayList<>();
            String[] src = {"groups/Pyrois/1.webp", "groups/Pyrois/2.webp"};
            for (int i = 0; i < src.length; i++) {
                String id = "seed" + i;
                File master = new File(dir, "m_" + id + ".webp");
                File display = new File(dir, "s_" + id + ".webp");
                copyAsset(c, src[i], master);
                copyFile(master, display);
                slots.add(new Slot(master.getName(), display.getName(), 0));
            }
            save(c, slots);
            Log.i(TAG, "seeded " + slots.size() + " slots");
        } catch (Exception e) {
            Log.e(TAG, "seed failed", e);
        }
    }

    private static int exifRotation(Context c, Uri uri) {
        try (InputStream is = c.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(is);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static Bitmap rotate(Bitmap bm, int degrees) {
        int d = ((degrees % 360) + 360) % 360;
        if (d == 0) {
            return bm;
        }
        Matrix m = new Matrix();
        m.postRotate(d);
        return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
    }

    private static void copyAsset(Context c, String asset, File dst) throws Exception {
        try (InputStream is = c.getAssets().open(asset);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream is = new java.io.FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        }
    }

    private static String readFile(File f) throws Exception {
        try (InputStream is = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        }
    }

    private static void writeFile(File f, String text) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(text.getBytes("UTF-8"));
        }
    }
}
