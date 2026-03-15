package com.dandantang.autoai.javaluamod; // 包名必须和目录完全一致

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;

public class javasystemluamod {
    public static Context appContext;
    private static ConcurrentHashMap<String, String> 全局变量池 = new ConcurrentHashMap<>();

    // 方法名必须是 run，且参数和返回值必须都是 String
    // 必须是 public static 静态方法
    /**
     * Java 调用：设置变量
     * 比如在网络访问完成后调用：javasystemluamod.设置全局变量("授权码", "123456");
     */
    public static void 设置全局变量(String 键, String 值) {
        if (键 != null && 值 != null) {
            全局变量池.put(键, 值);
        }
    }

    public static String getJavaVar(String 键) {
        // 如果找不到，返回空字符串 "" 而不是 null，防止 C++ 那边崩溃
        return 全局变量池.getOrDefault(键, "");
    }




    public static String readAssetFile(String fileName) {
        try {
            StringBuilder sb = new StringBuilder();
            // 自动处理 a/main.lua 这种路径
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(appContext.getAssets().open(fileName), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }


    public static String run(String data) {
        // 如果输入包含 ".lua"，我们认为它是路径，去读取它
        if (data.endsWith(".lua")) {
            return readAssetFile(data);
        }
        //读取信息的方法
        if (data.startsWith("读取信息|")) {
            String subType = data.replace("读取信息|", "");
            // appContext 是你在 MainActivity 初始化的静态 Context
            return com.dandantang.autoai.服务.信息读取.run(appContext, subType);
        }
        return "Unknown command";
    }

}