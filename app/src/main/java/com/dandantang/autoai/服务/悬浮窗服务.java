package com.dandantang.autoai.服务;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.dandantang.autoai.R;
import com.dandantang.autoai.服务.Lua环境管理器;

public class 悬浮窗服务 extends Service {
    private Lua环境管理器 luaManager;
    private boolean isRunning = false;  // ✅ 只保留这一个定义，删除下面重复的那个

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private View floatView;

    private LinearLayout menuContainer;
    private ImageButton 运行按钮, 停止按钮;

    private int screenWidth;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // 5秒无交互自动隐藏逻辑
    private Runnable hideRunnable = () -> {
        // 1. 移除 initFloatingWindow(); <-- 必须删掉，否则会重复创建窗口

        // 2. 实时获取当前的屏幕宽度（考虑旋转）
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;

        if (menuContainer != null) {
            menuContainer.setVisibility(View.GONE);
        }
        floatView.setAlpha(0.5f);

        floatView.postDelayed(() -> {
            int viewWidth = floatView.getWidth();
            // 兜底宽度计算
            if (viewWidth <= 0) viewWidth = (int) (50 * getResources().getDisplayMetrics().density);

            int stayVisibleWidth = (int) (10 * getResources().getDisplayMetrics().density);
            int hideDistance = viewWidth - stayVisibleWidth;

            // 3. 计算隐藏方向
            // 注意：这里判断的是 layoutParams.x，即窗口左上角位置
            if (layoutParams.x <= screenWidth / 2) {
                floatView.animate().translationX(-hideDistance).setDuration(300).start(); // 增加动画更平滑
            } else {
                floatView.animate().translationX(hideDistance).setDuration(300).start();
            }

            // 4. 不要在这里 updateViewLayout，除非你修改了 layoutParams 的属性
            Log.d("悬浮窗服务", "已自动收缩，隐藏距离: " + hideDistance);
        }, 100);
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        展示前台服务通知();

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        更新屏幕宽度();
        初始化悬浮窗();
        //初始化lua环境
        luaManager = Lua环境管理器.getInstance(this);

    }

    private void 更新屏幕宽度() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            screenWidth = windowManager.getCurrentWindowMetrics().getBounds().width();
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(dm);
            screenWidth = dm.widthPixels;
        }
    }

    private void 初始化悬浮窗() {
        // 1. 设置窗口参数 (解决找不到 TYPE_PHONE 的问题)
        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 统一使用这个，不再分 PHONE 或 PRIORITY_PHONE
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // 极老版本的兼容写法
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.x = 0;
        layoutParams.y = 400;

        // 2. 加载你的布局文件 activity_floating_window.xml
        floatView = LayoutInflater.from(this).inflate(R.layout.activity_floating_window, null);

        // 3. 绑定控件 (根据你 XML 里的 ID 修改)
        menuContainer = floatView.findViewById(R.id.menu_container);
        运行按钮 = floatView.findViewById(R.id.btn_run);
        停止按钮 = floatView.findViewById(R.id.btn_stop);

        // 4. 触摸与拖动逻辑
        floatView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 触摸时恢复显示
                mHandler.removeCallbacks(hideRunnable);
                floatView.setAlpha(1.0f);
                floatView.setTranslationX(0);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatView, layoutParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // 靠边吸附
                        if (layoutParams.x + v.getWidth() / 2 <= screenWidth / 2) {
                            layoutParams.x = 0;
                        } else {
                            layoutParams.x = screenWidth - v.getWidth();
                        }
                        windowManager.updateViewLayout(floatView, layoutParams);

                        // 点击判断
                        if (Math.abs(event.getRawX() - initialTouchX) < 10) {
                            if (menuContainer != null) {
                                int vis = menuContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
                                menuContainer.setVisibility(vis);
                            }
                        }
                        重置隐藏计时器();
                        return true;
                }
                return false;
            }
        });

        // 5. 按钮功能
        运行按钮.setOnClickListener(v -> {
            if (!isRunning) {
                // 启动脚本
                luaManager.启动所有脚本();
                isRunning = true;
                运行按钮.setImageResource(android.R.drawable.ic_media_pause);
                Toast.makeText(this, "脚本已启动", Toast.LENGTH_SHORT).show();
            } else {
                String status = luaManager.获取当前状态();
                if (status.equals(Lua环境管理器.STATUS_RUNNING)) {
                    // 运行中 -> 暂停
                    luaManager.暂停所有脚本();
                    运行按钮.setImageResource(android.R.drawable.ic_media_play);
                    Toast.makeText(this, "脚本已暂停", Toast.LENGTH_SHORT).show();
                } else if (status.equals(Lua环境管理器.STATUS_PAUSED)) {
                    // 暂停中 -> 恢复
                    luaManager.恢复所有脚本();
                    运行按钮.setImageResource(android.R.drawable.ic_media_pause);
                    Toast.makeText(this, "脚本已恢复", Toast.LENGTH_SHORT).show();
                }
            }
        });

        停止按钮.setOnClickListener(v -> {
            luaManager.停止所有脚本();
            isRunning = false;
            stopSelf();
        });

        windowManager.addView(floatView, layoutParams);
        重置隐藏计时器();
    }

    private void 重置隐藏计时器() {
        mHandler.removeCallbacks(hideRunnable);
        mHandler.postDelayed(hideRunnable, 5000);
    }

    private void 展示前台服务通知() {
        String id = "float_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(id, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, id)
                .setContentTitle("助手运行中")
                .setSmallIcon(android.R.drawable.btn_star)
                .build();
        startForeground(1001, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatView != null) windowManager.removeView(floatView);
        mHandler.removeCallbacks(hideRunnable);
    }
}