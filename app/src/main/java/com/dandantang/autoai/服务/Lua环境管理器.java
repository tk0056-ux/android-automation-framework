package com.dandantang.autoai.服务;

import android.content.Context;
import android.util.Log;

import com.dandantang.autoai.MainActivity;
import com.dandantang.autoai.javaluamod.javasystemluamod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Lua环境管理器 {
    private static final String TAG = "Lua环境管理器";

    // 单例模式
    private static Lua环境管理器 instance;
    private Context mContext;

    // 脚本状态常量
    public static final String STATUS_RUNNING = "运行";
    public static final String STATUS_PAUSED = "暂停";
    public static final String STATUS_STOPPED = "停止";

    // 线程管理
    private static class LuaThreadInfo {
        Thread thread;
        String scriptName;
        boolean isRunning = true;

        LuaThreadInfo(Thread t, String name) {
            thread = t;
            scriptName = name;
        }
    }
    private List<LuaThreadInfo> luaThreads = new ArrayList<>();
    private final Object threadLock = new Object();

    // 脚本运行状态（供Lua读取）
    private String currentStatus = STATUS_STOPPED;

    // 私有构造函数
    private Lua环境管理器(Context context) {
        this.mContext = context.getApplicationContext();
    }

    // 获取单例实例
    public static synchronized Lua环境管理器 getInstance(Context context) {
        if (instance == null) {
            instance = new Lua环境管理器(context);
        }
        return instance;
    }

    /**
     * 启动所有Lua脚本（从main.lua入口）
     */
    public void 启动所有脚本() {
        synchronized(threadLock) {
            if (currentStatus.equals(STATUS_RUNNING)) {
                Log.d(TAG, "脚本已在运行中");
                return;
            }

            currentStatus = STATUS_RUNNING;
            javasystemluamod.设置全局变量("脚本状态", STATUS_RUNNING);

            // 创建主Lua线程
            Thread mainThread = new Thread(() -> {
                Log.d(TAG, "启动主Lua脚本...");
                // 通过MainActivity的静态方法运行Lua
                runMainLuaScript();
            });
            mainThread.setName("Lua-Main");
            mainThread.start();

            luaThreads.add(new LuaThreadInfo(mainThread, "main.lua"));
            Log.d(TAG, "所有脚本启动命令已发送");
        }
    }

    /**
     * 暂停所有脚本
     */
    public void 暂停所有脚本() {
        synchronized(threadLock) {
            if (!currentStatus.equals(STATUS_RUNNING)) {
                Log.d(TAG, "脚本不在运行状态");
                return;
            }

            currentStatus = STATUS_PAUSED;
            javasystemluamod.设置全局变量("脚本状态", STATUS_PAUSED);
            Log.d(TAG, "所有脚本已暂停");
        }
    }

    /**
     * 恢复所有脚本
     */
    public void 恢复所有脚本() {
        synchronized(threadLock) {
            if (!currentStatus.equals(STATUS_PAUSED)) {
                Log.d(TAG, "脚本不在暂停状态");
                return;
            }

            currentStatus = STATUS_RUNNING;
            javasystemluamod.设置全局变量("脚本状态", STATUS_RUNNING);
            Log.d(TAG, "所有脚本已恢复");
        }
    }

    /**
     * 停止所有脚本
     */
    public void 停止所有脚本() {
        synchronized(threadLock) {
            currentStatus = STATUS_STOPPED;
            javasystemluamod.设置全局变量("脚本状态", STATUS_STOPPED);

            // 中断所有线程
            for (LuaThreadInfo info : luaThreads) {
                info.isRunning = false;
                info.thread.interrupt();
            }
            luaThreads.clear();

            Log.d(TAG, "所有脚本已停止");
        }
    }

    /**
     * 获取当前脚本状态
     */
    public String 获取当前状态() {
        return currentStatus;
    }

    /**
     * 检查脚本是否应该暂停（供Lua调用）
     */
    public static boolean 应该暂停() {
        if (instance == null) return false;
        return instance.currentStatus.equals(STATUS_PAUSED);
    }

    /**
     * 检查脚本是否应该停止（供Lua调用）
     */
    public static boolean 应该停止() {
        if (instance == null) return false;
        return instance.currentStatus.equals(STATUS_STOPPED);
    }

    /**
     * 注册新启动的Lua线程
     */
    public void 注册Lua线程(Thread thread, String scriptName) {
        synchronized(threadLock) {
            luaThreads.add(new LuaThreadInfo(thread, scriptName));
        }
    }

    /**
     * 移除已结束的Lua线程
     */
    public void 移除Lua线程(Thread thread) {
        synchronized(threadLock) {
            luaThreads.removeIf(info -> info.thread == thread);
        }
    }

    /**
     * 运行主Lua脚本（实际执行）
     */
    private void runMainLuaScript() {
        // 这里需要调用MainActivity的方法来运行Lua
        // 由于MainActivity可能有静态方法，或者通过广播通知

        // 方法1：通过广播
        android.content.Intent intent = new android.content.Intent("com.dandantang.autoai.RUN_LUA");
        intent.putExtra("script", "main.lua");
        mContext.sendBroadcast(intent);

        // 方法2：直接调用（如果MainActivity提供了静态方法）
        // MainActivity.runLuaScript("main.lua");
    }

    /**
     * 供Lua调用的状态检查函数
     */
    public static String getScriptStatus() {
        if (instance == null) return STATUS_STOPPED;
        return instance.currentStatus;
    }
}