package com.raster.dial;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置组来自 assets/groups，自定义组来自 files/custom。
 * 组内图片：1 = 色彩最丰富；组名带绝区零中文名。
 */
public final class GroupCatalog {

    private static final String TAG = "GROUPS";
    public static final String DEFAULT_ID = "Pyrois";
    public static final String CUSTOM_ID = "custom";
    public static final String PREF_GROUP = "group";

    public static final class Group {
        public final String id;
        public final String name;
        public final String nameZh;
        public final int count;
        public final boolean custom;

        Group(String id, String name, String nameZh, int count, boolean custom) {
            this.id = id;
            this.name = name;
            this.nameZh = nameZh == null || nameZh.isEmpty() ? name : nameZh;
            this.count = count;
            this.custom = custom;
        }

        /** 例如：星见雅 · Miyabi；自定义组只显示“自定义”。 */
        public String displayName() {
            return nameZh.equals(name) ? name : nameZh + " · " + name;
        }

        public String asset(int index1) {
            return "groups/" + id + "/" + index1 + ".webp";
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    private static List<Group> builtins;

    private GroupCatalog() {
    }

    private static synchronized List<Group> builtins(Context c) {
        if (builtins != null) {
            return builtins;
        }
        List<Group> list = new ArrayList<>();
        try (InputStream is = c.getAssets().open("groups/groups.json")) {
            JSONObject root = new JSONObject(read(is));
            JSONArray arr = root.getJSONArray("groups");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.getString("name");
                list.add(new Group(o.getString("id"), name,
                        o.optString("name_zh", name), o.getInt("count"), false));
            }
        } catch (Exception e) {
            Log.e(TAG, "load groups.json failed", e);
        }
        if (list.isEmpty()) {
            list.add(new Group(DEFAULT_ID, DEFAULT_ID, DEFAULT_ID, 2, false));
        }
        builtins = list;
        return list;
    }

    /** 内置组 + 自定义组（自定义组数量动态读取）。 */
    public static List<Group> load(Context c) {
        List<Group> list = new ArrayList<>(builtins(c));
        list.add(new Group(CUSTOM_ID, "Custom", "自定义", CustomStore.count(c), true));
        return list;
    }

    public static Group find(Context c, String id) {
        if (CUSTOM_ID.equals(id)) {
            return new Group(CUSTOM_ID, "Custom", "自定义", CustomStore.count(c), true);
        }
        List<Group> all = builtins(c);
        for (Group g : all) {
            if (g.id.equals(id)) {
                return g;
            }
        }
        for (Group g : all) {
            if (g.id.equals(DEFAULT_ID)) {
                return g;
            }
        }
        return all.get(0);
    }

    public static String currentId(Context c) {
        SharedPreferences sp = c.getSharedPreferences("lock", Context.MODE_PRIVATE);
        String id = sp.getString(PREF_GROUP, DEFAULT_ID);
        if (CUSTOM_ID.equals(id)) {
            return id;
        }
        for (Group g : builtins(c)) {
            if (g.id.equals(id)) {
                return id;
            }
        }
        sp.edit().putString(PREF_GROUP, DEFAULT_ID).apply();
        return DEFAULT_ID;
    }

    public static void setCurrentId(Context c, String id) {
        c.getSharedPreferences("lock", Context.MODE_PRIVATE)
                .edit().putString(PREF_GROUP, id).apply();
    }

    private static String read(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
