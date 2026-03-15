package com.dandantang.autoai.服务;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import com.dandantang.autoai.globalvariable;



public class 信息读取 {
    private static Context mContext;
    public static void 初始化上下文(Context context) {
        mContext = context.getApplicationContext();
    }

    // 静态 run 方法供 Lua 调用 (遵循你之前的格式)
    public static String run(Context context, String type) {
        switch (type) {
            case "设备名称": return Build.PRODUCT;
            case "型号": return Build.MODEL;
            case "制造商": return Build.MANUFACTURER;
            case "平台": return "Android " + Build.VERSION.RELEASE;
            case "设备ID": return getAndroidID(context);
            case "CPU型号": return Build.HARDWARE;
            case "当前时间": return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            case "内存信息": return getRamInfo(context);
            case "存储信息": return getRomInfo();
            case "MAC地址": return getMacAddress(context);
            case "蓝牙名称": return getBluetoothName();
            case "全部": return getAllInfo(context);
            default: return "未知参数";
        }
    }
    public static String 机器码(Context context){


        String 原始机器码1 = run(context,"型号");
        String 原始机器码2 = run(context,"制造商");
        String 原始机器码3 = run(context,"设备ID");
        String 原始机器码4 = run(context,"CPU型号");
        String 原始机器码5 = run(context,"存储信息");
        // 先转换成 base64格式  然后删除空格 和 符号 数字
        String avccc1 = Base64.getEncoder()
                .encodeToString((原始机器码1 +  原始机器码2 + 原始机器码3 +  原始机器码4 +  原始机器码5)
                        .getBytes(StandardCharsets.UTF_8))
                .replaceAll("[\\d=/\\\\+ ]", "")
                .toUpperCase();

        // 每隔一个抽出一个字母 保留6位
        while (avccc1.length() > 6) {
            StringBuilder temp = new StringBuilder();
            for (int i = 1; i < avccc1.length(); i += 2) {
                temp.append(avccc1.charAt(i));
            }
            avccc1 = temp.toString();
            if (avccc1.length() <= 12 && avccc1.length() >= 6) break;
        }

        if (avccc1.length() > 6) avccc1 = avccc1.substring(0, 6);
        while (avccc1.length() < 6) avccc1 += "0";

        Log.d("机器码", "机器码: " + avccc1);

        return avccc1;
    }

    private static String getAndroidID(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private static String getRamInfo(Context context) {
        ActivityManager hm = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        hm.getMemoryInfo(mi);
        return (mi.totalMem / 1024 / 1024) + "MB";
    }

    private static String getRomInfo() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return (totalBlocks * blockSize / 1024 / 1024 / 1024) + "GB";
    }

    @SuppressLint("HardwareIds")
    private static String getMacAddress(Context context) {
        // 注意：Android 6.0+ 默认返回 02:00:00:00:00:00 以保护隐私
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifi.getConnectionInfo();
            return info.getMacAddress();
        } catch (Exception e) {
            return "Unavailable";
        }
    }

    @SuppressLint("MissingPermission")
    private static String getBluetoothName() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                // 返回已连接设备或本地名称
                return adapter.getName();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        return "未开启/无连接";
    }

    private static String getAllInfo(Context context) {
        return "型号:" + Build.MODEL + "|ID:" + getAndroidID(context) + "|RAM:" + getRamInfo(context);
    }
}