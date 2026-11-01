package com.raster.dial;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * 只读导出文件提供器：把 files/exports/ 下的分享包以 content:// 暴露给
 * 系统分享面板（无 AndroidX，替代 FileProvider）。
 * Uri 形如 content://<pkg>.export/<文件名>；文件名严格白名单校验。
 */
public class ExportProvider extends ContentProvider {

    private static final String MIME = "application/zip";

    private File exportsDir() {
        return new File(getContext().getFilesDir(), "exports");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileFor(String name) throws FileNotFoundException {
        if (name == null || !name.matches("[A-Za-z0-9._\\-]+")) {
            throw new FileNotFoundException("bad name");
        }
        File f = new File(exportsDir(), name);
        try {
            if (!f.getCanonicalFile().getParentFile().equals(
                    exportsDir().getCanonicalFile()) || !f.isFile()) {
                throw new FileNotFoundException(name);
            }
        } catch (IOException e) {
            throw new FileNotFoundException(name);
        }
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File f = fileFor(uri.getLastPathSegment());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = fileFor(uri.getLastPathSegment());
        } catch (FileNotFoundException e) {
            return null;
        }
        if (projection == null || projection.length == 0) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor c = new MatrixCursor(projection);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                row[i] = f.getName();
            } else if (OpenableColumns.SIZE.equals(projection[i])) {
                row[i] = f.length();
            } else {
                row[i] = null;
            }
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return MIME;
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
