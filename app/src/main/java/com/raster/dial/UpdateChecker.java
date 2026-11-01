package com.raster.dial;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用内更新检查：成都主源 + GitHub 回退。
 * manifest 见仓库根目录 update.json；APK 下载后校验 SHA-256，再交给系统安装器。
 */
public final class UpdateChecker {

    private static final String PREFS = "lock";
    private static final String K_LAST_CHECK = "update_last_check";
    private static final String K_PENDING = "update_pending_apk";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static volatile boolean checking;

    private UpdateChecker() {
    }

    public static void autoCheck(Activity a) {
        long last = prefs(a).getLong(K_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - last >= DAY_MS) {
            check(a, false);
        }
    }

    public static void check(Activity a, boolean manual) {
        if (checking) {
            if (manual) {
                Toast.makeText(a, "正在检查更新…", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        checking = true;
        prefs(a).edit().putLong(K_LAST_CHECK, System.currentTimeMillis()).apply();
        new Thread(() -> {
            UpdateInfo info;
            try {
                info = fetchBest();
            } catch (Exception e) {
                info = null;
            }
            final UpdateInfo result = info;
            a.runOnUiThread(() -> {
                checking = false;
                if (a.isFinishing()) {
                    return;
                }
                if (result == null) {
                    if (manual) {
                        Toast.makeText(a, "检查更新失败，请稍后再试", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                long installed = installedCode(a);
                if (result.versionCode <= installed) {
                    if (manual) {
                        Toast.makeText(a, "已是最新版本 " + result.versionName, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (result.minSdk > Build.VERSION.SDK_INT) {
                    if (manual) {
                        Toast.makeText(a, "新版本需要 Android API " + result.minSdk, Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                showUpdate(a, result);
            });
        }, "update-check").start();
    }

    private static UpdateInfo fetchBest() {
        UpdateInfo primary = fetchManifest(BuildConfig.UPDATE_MANIFEST_PRIMARY);
        UpdateInfo fallback = fetchManifest(BuildConfig.UPDATE_MANIFEST_FALLBACK);
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }
        return fallback.versionCode > primary.versionCode ? fallback : primary;
    }

    private static UpdateInfo fetchManifest(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url.trim()).openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("User-Agent", "RasterDial/" + BuildConfig.VERSION_NAME);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            String body = readText(c.getInputStream(), 256 * 1024);
            JSONObject o = new JSONObject(body);
            UpdateInfo info = new UpdateInfo();
            info.versionCode = o.getInt("versionCode");
            info.versionName = o.optString("versionName", "?");
            info.minSdk = o.optInt("minSdk", 0);
            info.sha256 = o.optString("sha256", "").toLowerCase(Locale.US);
            info.size = o.optLong("size", 0L);
            info.notes = o.optString("notes", "");
            JSONArray arr = o.optJSONArray("apkUrls");
            if (arr == null && o.has("apkUrl")) {
                arr = new JSONArray().put(o.getString("apkUrl"));
            }
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String u = arr.optString(i, "").trim();
                    if (!u.isEmpty()) {
                        info.apkUrls.add(u);
                    }
                }
            }
            if (info.versionCode <= 0 || info.apkUrls.isEmpty() || info.sha256.isEmpty()) {
                return null;
            }
            return info;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String readText(InputStream in, int limit) throws Exception {
        try (BufferedInputStream is = new BufferedInputStream(in)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > limit) {
                    throw new IllegalStateException("manifest too large");
                }
            }
            return out.toString("UTF-8");
        }
    }

    private static void showUpdate(Activity a, UpdateInfo info) {
        String size = info.size > 0
                ? String.format(Locale.US, "%.1f MB", info.size / 1024.0 / 1024.0)
                : "未知大小";
        String msg = (info.notes.isEmpty() ? "" : info.notes + "\n\n")
                + "大小：" + size
                + "\n下载后校验 SHA-256，然后打开系统安装器。"
                + "\n当前版本：" + BuildConfig.VERSION_NAME + " → " + info.versionName;
        new AlertDialog.Builder(a)
                .setTitle("发现新版本 " + info.versionName)
                .setMessage(msg)
                .setPositiveButton("下载并安装", (d, w) -> downloadAndInstall(a, info))
                .setNegativeButton("稍后", null)
                .show();
    }

    private static void downloadAndInstall(Activity a, UpdateInfo info) {
        ProgressDialog pd = new ProgressDialog(a);
        pd.setTitle("下载 " + info.versionName);
        pd.setMessage("准备下载…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(true);
        final AtomicBoolean cancel = new AtomicBoolean(false);
        pd.setOnCancelListener(d -> cancel.set(true));
        pd.show();

        new Thread(() -> {
            File apk = null;
            for (String url : info.apkUrls) {
                if (cancel.get()) {
                    break;
                }
                apk = downloadApk(a, url, info, cancel, pd);
                if (apk == null) {
                    continue;
                }
                String got = sha256(apk);
                if (got.equalsIgnoreCase(info.sha256)) {
                    break;
                }
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                apk = null;
            }
            final File file = apk;
            a.runOnUiThread(() -> {
                if (pd.isShowing()) {
                    pd.dismiss();
                }
                if (cancel.get()) {
                    return;
                }
                if (file == null) {
                    Toast.makeText(a, "下载失败或校验失败，请稍后再试", Toast.LENGTH_LONG).show();
                    return;
                }
                installApk(a, file);
            });
        }, "update-download").start();
    }

    private static File downloadApk(Activity a, String url, UpdateInfo info,
                                    AtomicBoolean cancel, ProgressDialog pd) {
        HttpURLConnection c = null;
        File tmp = null;
        try {
            File dir = new File(a.getCacheDir(), "updates");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            String name = "raster-dial-" + safe(info.versionName) + ".apk";
            tmp = new File(dir, name + ".part");
            File outFile = new File(dir, name);
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "RasterDial/" + BuildConfig.VERSION_NAME);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            long total = c.getContentLengthLong();
            if (total <= 0) {
                total = info.size;
            }
            final long max = total;
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel.get()) {
                        return null;
                    }
                    out.write(buf, 0, n);
                    done += n;
                    if (max > 0) {
                        final int pct = (int) Math.min(100L, done * 100L / max);
                        a.runOnUiThread(() -> {
                            if (pd.isShowing()) {
                                pd.setProgress(pct);
                            }
                        });
                    }
                }
            }
            if (cancel.get()) {
                return null;
            }
            if (!tmp.renameTo(outFile)) {
                return null;
            }
            return outFile;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
            if (tmp != null && tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private static void installApk(Activity a, File apk) {
        if (apk == null || !apk.isFile()) {
            return;
        }
        PackageManager pm = a.getPackageManager();
        if (!pm.canRequestPackageInstalls()) {
            prefs(a).edit().putString(K_PENDING, apk.getAbsolutePath()).apply();
            new AlertDialog.Builder(a)
                    .setTitle("允许安装未知应用")
                    .setMessage("下载完成。请在系统设置中允许 Raster Dial 安装应用，返回后会自动打开安装器。")
                    .setPositiveButton("打开设置", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            Toast.makeText(a, "打开设置失败：" + e, Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("稍后", null)
                    .show();
            return;
        }
        Uri uri = ApkProvider.uriFor(a, apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "打开安装器失败：" + e, Toast.LENGTH_LONG).show();
        }
    }

    public static void resumePendingInstall(Activity a) {
        String path = prefs(a).getString(K_PENDING, null);
        if (path == null) {
            return;
        }
        File f = new File(path);
        if (!f.isFile()) {
            prefs(a).edit().remove(K_PENDING).apply();
            return;
        }
        if (a.getPackageManager().canRequestPackageInstalls()) {
            prefs(a).edit().remove(K_PENDING).apply();
            installApk(a, f);
        }
    }

    private static long installedCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.getLongVersionCode();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String s) {
        return s == null ? "apk" : s.replaceAll("[^0-9A-Za-z._-]", "_");
    }

    private static String sha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static final class UpdateInfo {
        int versionCode;
        String versionName = "?";
        int minSdk;
        String sha256 = "";
        long size;
        String notes = "";
        final List<String> apkUrls = new ArrayList<>();
    }
}
