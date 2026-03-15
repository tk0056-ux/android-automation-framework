package com.dandantang.autoai.服务;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class 权限申请 {

    public static final int REQUEST_CODE_NORMAL = 101; // 基础权限请求码
    public static final int REQUEST_CODE_OVERLAY = 102; // 悬浮窗请求码

    // 检查是否有基础权限 (存储 + 电话)
    public static boolean 是否有基础权限(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

        boolean phoneOk = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        // Android 13+ 存储权限变了，这里做个简单兼容
        boolean storageOk = true;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            storageOk = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return phoneOk && storageOk;
    }




    // 申请基础权限
    public static void 申请基础权限(Activity activity) {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // 1. 设备识别码相关 (读取手机信息)
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                list.add(android.Manifest.permission.READ_PHONE_STATE);
            }

            // 2. 存储权限 (适配 Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13 及以上版本不再申请 READ_EXTERNAL_STORAGE，改用细分权限
                if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    list.add(android.Manifest.permission.READ_MEDIA_IMAGES);
                }
            } else {
                // Android 12 及以下版本
                if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    list.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }

            // 3. 蓝牙与定位 (读取蓝牙设备名称必须有定位权限)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 专有的蓝牙权限
                if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    list.add(android.Manifest.permission.BLUETOOTH_CONNECT);
                }
            } else {
                // Android 11 及以下蓝牙权限
                if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    list.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
                }
            }

            // 执行申请
            if (!list.isEmpty()) {
                ActivityCompat.requestPermissions(activity, list.toArray(new String[0]), REQUEST_CODE_NORMAL);
            }
        }
    }

    // 申请悬浮窗权限
    public static void 申请悬浮窗权限(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            Toast.makeText(activity, "请找到 [弹弹堂助手] 并开启悬浮窗权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY);
        }
    }
}