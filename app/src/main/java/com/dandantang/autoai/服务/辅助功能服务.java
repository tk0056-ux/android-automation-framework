package com.dandantang.autoai.服务;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;

import com.dandantang.autoai.globalvariable;

/**
 * 辅助功能服务 - 用于自动化操作
 */
public class 辅助功能服务 extends AccessibilityService {

    private static final String TAG = "辅助功能服务";
    private static 辅助功能服务 sInstance;  // 修正：应该是自己的类名

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "辅助功能服务创建");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "辅助功能服务已连接");

        // 配置服务
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.packageNames = null;

        setServiceInfo(info);

        // 通知全局变量服务已连接
        globalvariable.辅助功能服务是否开启 = true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();

        switch (eventType) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "未知";
                Log.d(TAG, "窗口切换: " + packageName);
                break;

            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                Log.d(TAG, "点击事件");
                break;

            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                Log.d(TAG, "文本变化");
                break;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "辅助功能服务中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        globalvariable.辅助功能服务是否开启 = false;
        Log.d(TAG, "辅助功能服务销毁");
    }

    /**
     * 获取服务实例
     */
    public static 辅助功能服务 getInstance() {  // 修正：返回类型
        return sInstance;
    }

    /**
     * 检查服务是否已开启
     */
    public static boolean isEnabled() {
        return sInstance != null;
    }
}