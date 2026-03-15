package com.dandantang.autoai.服务;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.File;
import java.io.OutputStream;

public class ce {
    // 引擎运行状态，严格使用 true/false
    public static boolean isRunning = false;
    private static final String CONFIG_NAME = "CE_SETTINGS";

    /**
     * 初始化并自动加载设置
     * 对应你点击“保存设置”后的自动恢复逻辑
     */
    public static void init(Context context) {
        SharedPreferences sp = context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE);

        // 自动加载：获取之前保存的服务路径（到文本）
        String savedPath = sp.getString("server_path", "");
        boolean autoStart = sp.getBoolean("auto_start", false);

        if (autoStart == true && !savedPath.isEmpty()) {
            startEngine(savedPath);
        }
    }

    /**
     * 启动引擎逻辑
     */
    public static void startEngine(String path) {
        File file = new File(path);
        if (file.exists() == true) {
            try {
                // 使用 Root 权限运行二进制文件
                Runtime.getRuntime().exec("su -c " + path);
                isRunning = true;
            } catch (Exception e) {
                isRunning = false;
            }
        } else {
            isRunning = false;
        }
    }

    /**
     * 保存设置功能
     * 点击“保存设置”时调用此方法
     */
    public static void saveSettings(Context context,String 要启动的CE包名) {
        // 到文本：获取编译出的二进制文件绝对路径
        String nativePath = context.getApplicationInfo().nativeLibraryDir + 要启动的CE包名;

        SharedPreferences sp = context.getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        editor.putString("server_path", nativePath); // 到文本存储
        editor.putBoolean("auto_start", true);      // 设为 true
        editor.apply();
    }

    public static boolean 检查SU文件是否存在() {
        String[] locations = {"/system/bin/", "/system/xbin/", "/sbin/", "/system/sd/xbin/",
                "/system/bin/failsafe/", "/data/local/xbin/", "/data/local/bin/", "/data/local/"};
        for (String location : locations) {
            if (new File(location + "su").exists()) {
                Log.d("ce", "检查SU文件是否存在:   存在");
                return true; // 发现 SU 路径
            }
        }
        Log.d("ce", "检查SU文件是否存在:   不存在");
        return false;
    }

    public boolean 判断是否开启SU() {
        Process process = null;
        DataOutputStream os = null;
        try {
            // 尝试申请 su 权限
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();

            int exitValue = process.waitFor();
            // 返回码 0 代表成功获取权限
            if (exitValue == 0) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            // 发生异常通常是因为没有 Root 环境或被拒绝
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }


}