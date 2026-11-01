package com.raster.dial;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** 只读把 cache/updates 下的 APK 暴露给系统安装器；不依赖 AndroidX。 */
public class ApkProvider extends ContentProvider {

    public static Uri uriFor(Context c, File f) {
        return Uri.parse("content://" + c.getPackageName() + ".apk/" + Uri.encode(f.getName()));
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("read-only");
        }
        Context c = getContext();
        if (c == null) {
            throw new FileNotFoundException("no context");
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) {
            throw new FileNotFoundException("bad name");
        }
        File dir = new File(c.getCacheDir(), "updates");
        File f = new File(dir, name);
        try {
            String base = dir.getCanonicalPath() + File.separator;
            if (!f.getCanonicalPath().startsWith(base)) {
                throw new FileNotFoundException("escape");
            }
        } catch (Exception e) {
            throw new FileNotFoundException("bad path");
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
