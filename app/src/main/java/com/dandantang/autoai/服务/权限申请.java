package com.dandantang.autoai.服务;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import com.dandantang.autoai.服务.ce;
import java.io.DataOutputStream;
import java.io.IOException;
import android.content.Context;
import android.widget.Toast;
import com.dandantang.autoai.globalvariable;

public class 权限申请 {

    public static final int REQUEST_CODE_NORMAL = 101; // 基础权限请求码
    public static final int REQUEST_CODE_OVERLAY = 102; // 悬浮窗请求码.
    public static final int REQUEST_CODE_SCREEN_CAPTURE = 2026; // 截图权限请求码

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


    public static void 控制模式权限申请(Context context){
        if ("hid".equals(globalvariable.open模式)){
            Log.d("控制模式", "当前模式：HID");
            if (globalvariable.截图数据令牌 == null) {
                if (context instanceof Activity) {
                    Activity activity = (Activity) context;
                    MediaProjectionManager mpm = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    if (mpm != null) {
                        // 弹出系统投屏授权框
                        activity.startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
                    }
                }
            } else {
                Log.d("控制模式", "截图令牌已存在，无需重复申请");
            }



        } else if ("root".equals(globalvariable.open模式)) {
            ce.saveSettings(context,"/com.systeam.dandantang");  // 启动ce
            Log.d("控制模式", "当前模式：root ");
        } else if ("acc".equals(globalvariable.open模式)) {
            Log.d("控制模式", "当前模式：无障碍 ");
        } else if ("adb".equals(globalvariable.open模式)) {
            Log.d("控制模式", "当前模式：adb ");
        }


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


    // 建议移除 static，或者保留 static 但必须传入 context
    // 移除 static，增加 context 和 port 参数
    public void suPermissions(Context context, String port) {
        ce myCe = new ce();

        // 逻辑判断：真
        if (myCe.判断是否开启SU() == true) {

            // 1. 执行保存：存入 SharedPreferences
            // 这里的 port 是你调用时指定的“随机端口”
            SharedPreferences.Editor editor = context.getSharedPreferences("config", Context.MODE_PRIVATE).edit();
            editor.putString("port", port); // 已经过“到文本”处理
            editor.putBoolean("auto_start", true); // 自动加载设为：真
            editor.apply();

            Log.d("ce", "执行结果: 权限开启成功，端口已设为 " + port);

            // 2. 启动进程并传递端口参数
            // 假设你的 C 代码支持 -p 参数来指定端口
            try {
                // 将端口动态拼接到命令中
                String command = "su -c /data/local/tmp/com.systeam.dandantang -p " + port;
                Runtime.getRuntime().exec(command);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            Log.d("ce", "执行结果: 权限开启失败 (假)");
        }
    }
}