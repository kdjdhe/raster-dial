package com.raster.dial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 壁纸调试注入：
 * adb shell am broadcast -a com.raster.dial.WALL_DEBUG --ef progress 0.5 --ef dirX 1 --ef dirY 0
 * adb shell am broadcast -a com.raster.dial.WALL_DEBUG --ez clear true
 */
public class WallDebugReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String group = intent.getStringExtra("group");
        if (group != null && !group.isEmpty()) {
            GroupCatalog.setCurrentId(context, group);
        }

        android.content.SharedPreferences.Editor e =
                context.getSharedPreferences("lock", Context.MODE_PRIVATE).edit();
        if (intent.getBooleanExtra("clear", false)) {
            e.remove("wall_progress");
            e.remove("wall_dir_x");
            e.remove("wall_dir_y");
        } else {
            if (intent.hasExtra("progress")) {
                e.putFloat("wall_progress", intent.getFloatExtra("progress", -1f));
            }
            if (intent.hasExtra("dirX")) {
                e.putFloat("wall_dir_x", intent.getFloatExtra("dirX", 1f));
            }
            if (intent.hasExtra("dirY")) {
                e.putFloat("wall_dir_y", intent.getFloatExtra("dirY", 0f));
            }
        }
        e.apply();
    }
}
