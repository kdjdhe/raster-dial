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
 * 自定义光栅组：多组，组有名字，每组 0–6 个槽位。
 * 清单在 files/custom/groups.json；每张图有 master（原图）与 display（旋转后）。
 * 旧版单组 files/custom/group.json 会自动迁移成 id=custom 的“自定义”组。
 */
public final class CustomStore {

    private static final String TAG = "CUSTOM";
    private static final String DIR = "custom";
    private static final String MANIFEST = "groups.json";
    private static final String LEGACY_MANIFEST = "group.json";
    private static final String LEGACY_ID = "custom";
    private static final String DEFAULT_NAME = "自定义";
    private static final int MAX_DIM = 3000;

    public static final int MAX_SLOTS = 6;

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

    public static final class CustomGroup {
        public final String id;
        public String name;
        public final List<Slot> slots;

        CustomGroup(String id, String name, List<Slot> slots) {
            this.id = id;
            this.name = name;
            this.slots = slots;
        }

        public int count() {
            return slots.size();
        }
    }

    private static List<CustomGroup> cache;

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

    /** 读取全部自定义组；首次运行 seed 一个“自定义”，旧单组自动迁移。 */
    public static synchronized List<CustomGroup> loadGroups(Context c) {
        if (cache != null) {
            return cache;
        }
        List<CustomGroup> groups = new ArrayList<>();
        File manifest = new File(dir(c), MANIFEST);
        boolean needWrite = false;
        if (manifest.isFile()) {
            groups.addAll(readGroups(manifest));
        } else {
            File legacy = new File(dir(c), LEGACY_MANIFEST);
            if (legacy.isFile()) {
                List<Slot> slots = readSlots(legacy);
                if (!slots.isEmpty()) {
                    groups.add(new CustomGroup(LEGACY_ID, DEFAULT_NAME, slots));
                }
            }
            if (groups.isEmpty()) {
                seed(c, groups);
            }
            needWrite = true;
        }
        cache = groups;
        if (needWrite) {
            writeGroups(c, groups);
        }
        return groups;
    }

    public static synchronized CustomGroup find(Context c, String id) {
        if (id == null) {
            return null;
        }
        for (CustomGroup g : loadGroups(c)) {
            if (g.id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    /** 新建一个空组；名称不能为空或与已有自定义组重名。 */
    public static synchronized CustomGroup create(Context c, String name) {
        List<CustomGroup> groups = loadGroups(c);
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || nameTaken(groups, n, null)) {
            return null;
        }
        CustomGroup g = new CustomGroup(newId(), n, new ArrayList<>());
        groups.add(g);
        saveGroups(c);
        return g;
    }

    /** 保存名称/槽位；名称不能为空或与其它自定义组重名。 */
    public static synchronized boolean saveGroup(Context c, CustomGroup g) {
        List<CustomGroup> groups = loadGroups(c);
        String n = g.name == null ? "" : g.name.trim();
        if (n.isEmpty() || nameTaken(groups, n, g.id)) {
            return false;
        }
        g.name = n;
        if (!groups.contains(g)) {
            groups.add(g);
        }
        saveGroups(c);
        return true;
    }

    public static synchronized boolean deleteGroup(Context c, String id) {
        List<CustomGroup> groups = loadGroups(c);
        for (int i = 0; i < groups.size(); i++) {
            CustomGroup g = groups.get(i);
            if (!g.id.equals(id)) {
                continue;
            }
            for (Slot s : g.slots) {
                deleteFiles(c, s);
            }
            groups.remove(i);
            saveGroups(c);
            return true;
        }
        return false;
    }

    public static synchronized Bitmap[] loadBitmaps(Context c, String groupId) {
        CustomGroup g = find(c, groupId);
        if (g == null) {
            return new Bitmap[0];
        }
        List<Bitmap> out = new ArrayList<>();
        for (Slot s : g.slots) {
            Bitmap bm = BitmapFactory.decodeFile(new File(dir(c), s.display).getAbsolutePath());
            if (bm != null) {
                out.add(bm);
            }
        }
        return out.toArray(new Bitmap[0]);
    }

    public static File firstDisplay(Context c, String groupId) {
        CustomGroup g = find(c, groupId);
        if (g == null || g.slots.isEmpty()) {
            return null;
        }
        return new File(dir(c), g.slots.get(0).display);
    }

    public static synchronized void saveGroupSlots(Context c, CustomGroup g) {
        saveGroups(c);
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

    private static boolean nameTaken(List<CustomGroup> groups, String name, String exceptId) {
        for (CustomGroup g : groups) {
            if (g.id.equals(exceptId)) {
                continue;
            }
            if (g.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String newId() {
        return "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static void saveGroups(Context c) {
        if (cache != null) {
            writeGroups(c, cache);
        }
    }

    private static void writeGroups(Context c, List<CustomGroup> groups) {
        try {
            JSONArray arr = new JSONArray();
            for (CustomGroup g : groups) {
                JSONObject o = new JSONObject();
                o.put("id", g.id);
                o.put("name", g.name);
                JSONArray slots = new JSONArray();
                for (Slot s : g.slots) {
                    JSONObject so = new JSONObject();
                    so.put("m", s.master);
                    so.put("d", s.display);
                    so.put("r", s.rotation);
                    slots.put(so);
                }
                o.put("slots", slots);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("groups", arr);
            writeFile(new File(dir(c), MANIFEST), root.toString());
            bumpVersion(c);
        } catch (Exception e) {
            Log.e(TAG, "save manifest failed", e);
        }
    }

    private static List<CustomGroup> readGroups(File f) {
        List<CustomGroup> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(readFile(f));
            JSONArray arr = root.optJSONArray("groups");
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "");
                String name = o.optString("name", DEFAULT_NAME);
                if (id.isEmpty()) {
                    continue;
                }
                out.add(new CustomGroup(id, name, readSlots(o)));
            }
        } catch (Exception e) {
            Log.e(TAG, "read manifest failed", e);
        }
        return out;
    }

    private static List<Slot> readSlots(File f) {
        try {
            return readSlots(new JSONObject(readFile(f)));
        } catch (Exception e) {
            Log.e(TAG, "read legacy manifest failed", e);
            return new ArrayList<>();
        }
    }

    private static List<Slot> readSlots(JSONObject root) {
        List<Slot> list = new ArrayList<>();
        JSONArray arr = root.optJSONArray("slots");
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                String m = o.optString("m", "");
                String d = o.optString("d", "");
                if (!m.isEmpty() && !d.isEmpty()) {
                    list.add(new Slot(m, d, o.optInt("r", 0)));
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private static void seed(Context c, List<CustomGroup> groups) {
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
            groups.add(new CustomGroup(LEGACY_ID, DEFAULT_NAME, slots));
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
