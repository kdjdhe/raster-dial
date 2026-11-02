package com.raster.dial;

import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 光栅组分享包（zip）读写：
 *
 * 格式 1（单组）：raster-group.json {"format":1,"name":..,"slots":[{"r":..},..]}
 *                + m_1.webp ... m_N.webp
 * 格式 2（多组）：raster-groups.json {"format":2,"groups":[{"name":..,"slots":[..]},..]}
 *                + <组序号>_m_<槽位序号>.webp（均从 1 起）
 *
 * 读：readAll 统一两种格式成 Parsed 列表；写：writeBundle 输出格式 2。
 * 只做纯解析/打包，落盘交给 CustomStore.importBytes。
 */
final class GroupImporter {

    static final class Parsed {
        final String name;
        final int[] rotations;
        final List<byte[]> images;

        Parsed(String name, int[] rotations, List<byte[]> images) {
            this.name = name;
            this.rotations = rotations;
            this.images = images;
        }
    }

    private static final Pattern MULTI_IMG =
            Pattern.compile("^(\\d+)_m_(\\d+)\\.webp$");
    private static final Pattern SINGLE_IMG =
            Pattern.compile("^m_(\\d+)\\.webp$");

    private GroupImporter() {
    }

    /** 打包多个自定义组为格式 2 分享包。 */
    static void writeBundle(Context c, List<CustomStore.CustomGroup> groups,
                            File out) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", 2);
        JSONArray arr = new JSONArray();
        byte[] buf = new byte[8192];
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            for (int gi = 0; gi < groups.size(); gi++) {
                CustomStore.CustomGroup g = groups.get(gi);
                JSONObject o = new JSONObject();
                o.put("name", g.name);
                JSONArray slots = new JSONArray();
                for (CustomStore.Slot s : g.slots) {
                    JSONObject so = new JSONObject();
                    so.put("r", s.rotation);
                    slots.put(so);
                }
                o.put("slots", slots);
                arr.put(o);
                for (int si = 0; si < g.slots.size(); si++) {
                    zos.putNextEntry(new ZipEntry((gi + 1) + "_m_" + (si + 1) + ".webp"));
                    try (InputStream is = new FileInputStream(
                            new File(CustomStore.dir(c), g.slots.get(si).master))) {
                        int r;
                        while ((r = is.read(buf)) > 0) {
                            zos.write(buf, 0, r);
                        }
                    }
                    zos.closeEntry();
                }
            }
            root.put("groups", arr);
            zos.putNextEntry(new ZipEntry("raster-groups.json"));
            zos.write(root.toString().getBytes("UTF-8"));
            zos.closeEntry();
        }
    }

    /** 读取分享包（单组 / 多组皆可），返回 1..N 个 Parsed。 */
    static List<Parsed> readAll(ContentResolver cr, Uri uri) throws Exception {
        String multi = null;
        String single = null;
        Map<Integer, byte[]> multiImgs = new HashMap<>();
        Map<Integer, byte[]> singleImgs = new HashMap<>();
        try (InputStream is = cr.openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {
            if (is == null) {
                throw new Exception("打不开这个文件");
            }
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName();
                if (n.endsWith("/")) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int r;
                while ((r = zis.read(buf)) > 0) {
                    bos.write(buf, 0, r);
                }
                if (n.equals("raster-groups.json")) {
                    multi = bos.toString("UTF-8");
                } else if (n.equals("raster-group.json")) {
                    single = bos.toString("UTF-8");
                } else {
                    Matcher m = MULTI_IMG.matcher(n);
                    if (m.matches()) {
                        multiImgs.put(Integer.parseInt(m.group(1)) * 10000
                                + Integer.parseInt(m.group(2)), bos.toByteArray());
                        continue;
                    }
                    m = SINGLE_IMG.matcher(n);
                    if (m.matches()) {
                        singleImgs.put(Integer.parseInt(m.group(1)), bos.toByteArray());
                    }
                }
                zis.closeEntry();
            }
        }

        List<Parsed> out = new ArrayList<>();
        if (multi != null) {
            JSONObject root = new JSONObject(multi);
            if (root.optInt("format", 0) != 2) {
                throw new Exception("分享包版本不支持");
            }
            JSONArray groups = root.optJSONArray("groups");
            if (groups == null || groups.length() == 0) {
                throw new Exception("分享包里没有光栅组");
            }
            for (int gi = 0; gi < groups.length(); gi++) {
                JSONObject o = groups.getJSONObject(gi);
                String name = o.optString("name", "").trim();
                if (name.isEmpty()) {
                    throw new Exception("分享包里有组名为空");
                }
                JSONArray slots = o.optJSONArray("slots");
                if (slots == null || slots.length() == 0) {
                    throw new Exception("「" + name + "」里没有槽位");
                }
                if (slots.length() > CustomStore.MAX_SLOTS) {
                    throw new Exception("「" + name + "」超过 "
                            + CustomStore.MAX_SLOTS + " 张，不支持");
                }
                int[] rotations = new int[slots.length()];
                List<byte[]> imgs = new ArrayList<>();
                for (int si = 0; si < slots.length(); si++) {
                    rotations[si] = (slots.optJSONObject(si) == null)
                            ? 0 : slots.optJSONObject(si).optInt("r", 0) % 360;
                    byte[] img = multiImgs.get((gi + 1) * 10000 + (si + 1));
                    if (img == null) {
                        throw new Exception("「" + name + "」缺 "
                                + (gi + 1) + "_m_" + (si + 1) + ".webp");
                    }
                    imgs.add(img);
                }
                out.add(new Parsed(name, rotations, imgs));
            }
            return out;
        }

        if (single != null) {
            JSONObject root = new JSONObject(single);
            if (root.optInt("format", 0) != 1) {
                throw new Exception("分享包版本不支持");
            }
            String name = root.optString("name", "").trim();
            if (name.isEmpty()) {
                throw new Exception("分享包里的组名为空");
            }
            JSONArray slots = root.optJSONArray("slots");
            if (slots == null || slots.length() == 0) {
                throw new Exception("分享包里没有槽位");
            }
            if (slots.length() > CustomStore.MAX_SLOTS) {
                throw new Exception("超过 " + CustomStore.MAX_SLOTS + " 张，不支持");
            }
            int[] rotations = new int[slots.length()];
            List<byte[]> imgs = new ArrayList<>();
            for (int si = 0; si < slots.length(); si++) {
                rotations[si] = (slots.optJSONObject(si) == null)
                        ? 0 : slots.optJSONObject(si).optInt("r", 0) % 360;
                byte[] img = singleImgs.get(si + 1);
                if (img == null) {
                    throw new Exception("缺 m_" + (si + 1) + ".webp");
                }
                imgs.add(img);
            }
            out.add(new Parsed(name, rotations, imgs));
            return out;
        }

        throw new Exception("不是光栅组分享包");
    }
}
