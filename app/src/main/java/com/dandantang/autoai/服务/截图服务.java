package com.dandantang.autoai.服务;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;

import com.dandantang.autoai.globalvariable;

import java.nio.ByteBuffer;

public class 截图服务 extends Service {

    private static final String TAG = "截图服务";

    // 静态变量，确保全局唯一且方便工具类调用
    private static MediaProjection mMediaProjection;
    private static ImageReader mImageReader;
    private static VirtualDisplay mVirtualDisplay;
    private static WindowManager mWindowManager;

    private static int mScreenWidth;
    private static int mScreenHeight;
    private static int mScreenDensity;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        创建通知渠道();

        // 1. 初始化屏幕参数
        更新显示指标();

        // 2. 初始化 MediaProjection 引擎
        if (globalvariable.截图数据令牌 != null) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mMediaProjection = projectionManager.getMediaProjection(globalvariable.截图数据令牌结果码 , globalvariable.截图数据令牌 );

            if (mMediaProjection != null) {
                Log.d(TAG, "MediaProjection 引擎启动成功");
                准备图像读取器();
            } else {
                Log.e(TAG, "引擎启动失败：权限令牌无效");
            }
        }
        return START_STICKY;
    }

    private void 创建通知渠道() {
        String channelId = "screen_capture";
        NotificationChannel channel = new NotificationChannel(channelId, "屏幕截图服务", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("截图引擎运行中")
                .setContentText("正在实时监听屏幕数据")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
        startForeground(1, notification);
    }

    // 核心：实时获取当前屏幕的物理宽高
    private static void 更新显示指标() {
        if (mWindowManager == null) return;
        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        mScreenDensity = metrics.densityDpi;
    }

    // 核心：创建或重建虚拟显示器
    private static void 准备图像读取器() {
        try {
            // 如果已经存在，先安全释放
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }

            // 创建新的 ImageReader，注意宽高必须是当前的物理宽高
            mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 2);
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    mScreenWidth, mScreenHeight, mScreenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mImageReader.getSurface(), null, null);

            Log.d(TAG, "ImageReader 已重构: " + mScreenWidth + "x" + mScreenHeight);
        } catch (Exception e) {
            Log.e(TAG, "准备图像读取器失败: " + e.getMessage());
        }
    }

    /**
     * 供外部调用的静态截图方法
     */
    public static Bitmap 获取当前全屏截图() {
        if (mImageReader == null || mMediaProjection == null) {
            Log.e(TAG, "截图失败：引擎未初始化");
            return null;
        }

        // --- 旋转检测逻辑 ---
        int preW = mScreenWidth;
        int preH = mScreenHeight;
        更新显示指标();

        // 如果宽高发生了变化（说明旋转了），立刻重建引擎
        if (preW != mScreenWidth || preH != mScreenHeight) {
            Log.w(TAG, "检测到屏幕旋转，正在重构缓冲区...");
            准备图像读取器();
            // 等待硬件缓冲区就绪
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        }

        Image image = null;
        try {
            // 尝试获取最新帧
            image = mImageReader.acquireLatestImage();
            if (image == null) {
                image = mImageReader.acquireNextImage();
            }
            if (image == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();

            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            // 创建位图并填充数据
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            // 必须先关闭 Image 释放缓冲区
            image.close();
            image = null;

            // 裁剪掉 Padding 部分
            if (rowPadding != 0) {
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                bitmap.recycle();
                return croppedBitmap;
            }

            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "获取图像异常: " + e.getMessage());
            if (image != null) image.close();
            return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mVirtualDisplay != null) mVirtualDisplay.release();
        if (mImageReader != null) mImageReader.close();
        if (mMediaProjection != null) mMediaProjection.stop();
        Log.d(TAG, "截图服务已停止，资源已释放");
    }

}