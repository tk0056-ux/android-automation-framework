package com.dandantang.autoai.服务;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import com.dandantang.autoai.服务.UI处理;

public class ce {
    private static final String TAG = "CE_Service";

    // Assets 里的目录和文件名
    private static final String ASSET_DIR = "dandantang111/";
    private static final String MAIN_BIN = "dandantangserver";

    // 预设端口变量
    public static int DEFAULT_PORT = 19527;

    /**
     * 启动 CE 服务并开放网络 IP 访问
     */
    public static void startService(Context context, int port) {
        File internalDir = context.getFilesDir();
        File targetFile = new File(internalDir, MAIN_BIN);

        // 1. 强力清场：杀掉所有名为 dandantangserver 的旧进程
        runAsRoot("pkill -9 " + MAIN_BIN);
        Log.d(TAG, "已清理旧进程，准备重新启动...");

        // 2. 物理删除旧文件，确保物理层面的覆盖
        if (targetFile.exists()) {
            targetFile.delete();
        }

        // 3. 释放最新的服务端文件
        String mainPath = copyAsset(context, ASSET_DIR + MAIN_BIN, MAIN_BIN);

        if (mainPath != null) {
            // 4. 构建启动指令
            // -p 指定端口
            // 为了防止手机防火墙拦截，我们在启动命令中加入一条允许该端口通行的指令（可选但推荐）
            String startCmd = "chmod 777 " + mainPath + " && " +
                    "iptables -I INPUT -p tcp --dport " + port + " -j ACCEPT && " +
                    mainPath + " -p " + port + " > /dev/null 2>&1 &";

            runAsRoot(startCmd);
            Log.d(TAG, "CE 服务端已启动。端口: " + port + "，请使用手机 IP 直接连接"+UI处理.获取局域网IP());
        }
    }

    private static String copyAsset(Context context, String assetPath, String outName) {
        File outFile = new File(context.getFilesDir(), outName);
        try (InputStream is = context.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();

            // 物理同步到磁盘
            if (os instanceof FileOutputStream) {
                ((FileOutputStream) os).getFD().sync();
            }

            return outFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "文件释放失败: " + assetPath + " -> " + e.getMessage());
            return null;
        }
    }

    private static void runAsRoot(String command) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            try (OutputStream os = p.getOutputStream()) {
                os.write((command + "\n").getBytes());
                os.write("exit\n".getBytes());
                os.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Root 命令执行失败: " + e.getMessage());
        }
    }


    //  关闭ce进程
    public static void stopService() {
        // 直接通過 Root 命令殺掉進程
        runAsRoot("pkill -9 " + MAIN_BIN);
        Log.d(TAG, "CE 服務端已停止運行");
    }
}