package com.raster.dial;

import android.content.ContentResolver;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 光栅组分享包（zip）读取：
 *   raster-group.json  {"format":1,"name":"...","slots":[{"r":0},...]}
 *   m_1.webp ... m_N.webp（master 图，按槽位顺序）
 * 只做纯解析，不落盘；入组交给 CustomStore.importBytes。
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

    private GroupImporter() {
    }

    static Parsed read(ContentResolver cr, Uri uri) throws Exception {
        String json = null;
        Map<Integer, byte[]> images = new HashMap<>();
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
                if (n.equals("raster-group.json")) {
                    json = bos.toString("UTF-8");
                } else if (n.startsWith("m_") && n.endsWith(".webp")) {
                    String mid = n.substring(2, n.length() - 5);
                    try {
                        images.put(Integer.parseInt(mid), bos.toByteArray());
                    } catch (NumberFormatException ignored) {
                    }
                }
                zis.closeEntry();
            }
        }
        if (json == null) {
            throw new Exception("不是光栅组分享包（缺 raster-group.json）");
        }
        JSONObject root = new JSONObject(json);
        if (root.optInt("format", 0) != 1) {
            throw new Exception("分享包版本不支持");
        }
        String name = root.optString("name", "").trim();
        if (name.isEmpty()) {
            throw new Exception("分享包里的组名为空");
        }
        org.json.JSONArray slots = root.optJSONArray("slots");
        if (slots == null || slots.length() == 0) {
            throw new Exception("分享包里没有槽位");
        }
        if (slots.length() > CustomStore.MAX_SLOTS) {
            throw new Exception("超过 " + CustomStore.MAX_SLOTS + " 张，不支持");
        }
        int[] rotations = new int[slots.length()];
        List<byte[]> imgs = new ArrayList<>();
        for (int i = 0; i < slots.length(); i++) {
            rotations[i] = ((slots.optJSONObject(i) == null)
                    ? 0 : slots.optJSONObject(i).optInt("r", 0)) % 360;
            byte[] img = images.get(i + 1);
            if (img == null) {
                throw new Exception("缺 m_" + (i + 1) + ".webp");
            }
            imgs.add(img);
        }
        return new Parsed(name, rotations, imgs);
    }
}
