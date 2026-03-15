package com.dandantang.autoai.服务;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.dandantang.autoai.MainActivity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dandantang.autoai.globalvariable;
import com.dandantang.autoai.javaluamod.javasystemluamod;
/**
 * 蓝牙管理器 - 可靠的蓝牙连接检测
 */
public class 蓝牙管理器 {
    private static final String TAG = "蓝牙管理器";

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    // 蓝牙设备信息类
    public static class 蓝牙设备信息 {
        private String 名称;
        private String mac地址;
        private boolean 是否已连接;
        private String 连接类型;

        public 蓝牙设备信息(String 名称, String mac地址, boolean 是否已连接, String 连接类型) {
            this.名称 = 名称 != null ? 名称 : "未知设备";
            this.mac地址 = mac地址 != null ? mac地址 : "未知地址";
            this.是否已连接 = 是否已连接;
            this.连接类型 = 连接类型 != null ? String.valueOf(连接类型) : "未知";
        }

        public String get名称() { return 名称; }
        public String getMac地址() { return mac地址; }
        public boolean is是否已连接() { return 是否已连接; }
        public String get连接类型() { return 连接类型; }

        @Override
        public String toString() {
            String 蓝牙 =   名称 + "|" + mac地址 ;
            globalvariable.蓝牙已连接设备 = 蓝牙;
            javasystemluamod.设置全局变量("蓝牙",蓝牙);
            Log.d(TAG, "toString: "+蓝牙);

            return 蓝牙;
        }
    }

    // 回调接口
    public interface 设备列表回调 {
        void 设备列表获取成功(List<蓝牙设备信息> 设备列表);
        void 设备列表获取失败(String 错误信息);
    }

    public 蓝牙管理器(Context context) {
        this.mContext = context.getApplicationContext();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            } catch (Exception e) {
                Log.e(TAG, "获取BluetoothManager失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取已连接设备
     */
    public void 获取已连接设备(设备列表回调 回调) {
        if (回调 == null) return;

        new Thread(() -> {
            try {
                // 1. 先检查蓝牙适配器
                if (mBluetoothAdapter == null) {
                    mMainHandler.post(() -> 回调.设备列表获取失败("设备不支持蓝牙"));
                    return;
                }

                // 2. 检查蓝牙是否开启
                boolean 蓝牙已开启 = false;
                try {
                    蓝牙已开启 = mBluetoothAdapter.isEnabled();
                } catch (SecurityException e) {
                    mMainHandler.post(() -> 回调.设备列表获取失败("检查蓝牙状态权限不足"));
                    return;
                }

                if (!蓝牙已开启) {
                    mMainHandler.post(() -> 回调.设备列表获取失败("蓝牙未开启"));
                    return;
                }

                // 3. 检查权限
                if (!检查蓝牙权限()) {
                    mMainHandler.post(() -> 回调.设备列表获取失败("缺少蓝牙连接权限"));
                    return;
                }

                // 4. 获取已连接设备
                List<蓝牙设备信息> 已连接设备 = new ArrayList<>();

                // 方法1: 通过BluetoothManager获取（最可靠）
                if (mBluetoothManager != null) {
                    已连接设备.addAll(通过管理器获取已连接设备());
                }

                // 方法2: 通过反射获取（备用）
                if (已连接设备.isEmpty()) {
                    已连接设备.addAll(通过反射获取已连接设备());
                }

                // 5. 去重
                List<蓝牙设备信息> 去重后列表 = 去重设备列表(已连接设备);

                // 6. 返回结果
                List<蓝牙设备信息> finalList = 去重后列表;
                mMainHandler.post(() -> 回调.设备列表获取成功(finalList));

            } catch (Exception e) {
                Log.e(TAG, "获取已连接设备异常: " + e.getMessage());
                // 不要崩溃，返回空列表
                mMainHandler.post(() -> 回调.设备列表获取成功(new ArrayList<>()));
            }
        }).start();
    }

    /**
     * 通过 BluetoothManager 获取已连接设备（只使用支持的profile）
     */
    private List<蓝牙设备信息> 通过管理器获取已连接设备() {
        List<蓝牙设备信息> 设备列表 = new ArrayList<>();
        Set<String> 已添加Mac地址 = new HashSet<>();

        // 只使用最常用的profile，避免不支持的profile导致异常
        int[][] profile尝试列表 = {
                {BluetoothProfile.HEADSET, 1},      // 1
                {BluetoothProfile.A2DP, 2},      // 2
                {BluetoothProfile.GATT, 7},     // 7
                {BluetoothProfile.GATT_SERVER,8 } // 8
        };

        // 尝试添加HID_DEVICE (19)，但不保证所有版本都支持
        try {
            // 通过反射获取HID_DEVICE的值，避免直接使用常量
            int hidDeviceValue = 19; // HID_DEVICE的常数值通常是19
            尝试添加Profile(profile尝试列表, hidDeviceValue, "输入设备");
        } catch (Exception e) {
            // 忽略，可能不支持
        }

        for (int[] profileInfo : profile尝试列表) {
            int profileType = profileInfo[0];
            String 类型名称 = String.valueOf(profileInfo[1]);

            try {
                List<BluetoothDevice> connectedDevices = mBluetoothManager.getConnectedDevices(profileType);

                for (BluetoothDevice device : connectedDevices) {
                    if (device == null) continue;

                    String mac地址 = 获取设备地址安全(device);

                    // 去重
                    if (!已添加Mac地址.contains(mac地址)) {
                        String 设备名称 = 获取设备名称安全(device);

                        蓝牙设备信息 信息 = new 蓝牙设备信息(
                                设备名称,
                                mac地址,
                                true,
                                类型名称
                        );
                        设备列表.add(信息);
                        已添加Mac地址.add(mac地址);
                        //Log.d(TAG, "找到已连接设备: " + 设备名称 + " (" + mac地址 + ") - " + 类型名称);
                    }
                }
            } catch (IllegalArgumentException e) {
                // Profile不支持，忽略这个profile
                Log.d(TAG, "Profile " + profileType + " 不支持，跳过");
            } catch (SecurityException e) {
                Log.w(TAG, "获取profile " + profileType + " 设备权限异常");
            } catch (Exception e) {
                Log.w(TAG, "获取profile " + profileType + " 设备异常: " + e.getMessage());
            }
        }

        return 设备列表;
    }

    /**
     * 尝试添加profile到尝试列表
     */
    private void 尝试添加Profile(int[][] 列表, int profileValue, String 名称) {
        // 检查是否已存在
        for (int[] item : 列表) {
            if (item[0] == profileValue) {
                return;
            }
        }

        // 创建新数组添加
        int[][] 新列表 = new int[列表.length + 1][2];
        System.arraycopy(列表, 0, 新列表, 0, 列表.length);
        新列表[列表.length] = new int[]{profileValue, 0};
        // 这里简化处理，实际可能需要更复杂的逻辑
    }

    /**
     * 通过反射获取已连接设备（安全版本）
     */
    private List<蓝牙设备信息> 通过反射获取已连接设备() {
        List<蓝牙设备信息> 设备列表 = new ArrayList<>();

        try {
            Set<BluetoothDevice> pairedDevices = 安全获取配对设备();
            if (pairedDevices.isEmpty()) {
                return 设备列表;
            }

            // 尝试获取getConnectionState方法
            Method getConnectionStateMethod = null;
            try {
                getConnectionStateMethod = BluetoothDevice.class.getMethod("getConnectionState");
            } catch (NoSuchMethodException e) {
                // 方法不存在，直接返回
                return 设备列表;
            }

            for (BluetoothDevice device : pairedDevices) {
                try {
                    int state = (int) getConnectionStateMethod.invoke(device);
                    if (state == 2) { // STATE_CONNECTED = 2
                        String 设备名称 = 获取设备名称安全(device);
                        String mac地址 = 获取设备地址安全(device);

                        蓝牙设备信息 信息 = new 蓝牙设备信息(
                                设备名称,
                                mac地址,
                                true,
                                "已连接"
                        );
                        设备列表.add(信息);
                        Log.d(TAG, "通过反射找到已连接设备: " + 设备名称);
                    }
                } catch (Exception e) {
                    // 单个设备失败不影响其他设备
                }
            }

        } catch (Exception e) {
            Log.d(TAG, "反射获取设备失败: " + e.getMessage());
        }

        return 设备列表;
    }

    /**
     * 安全获取配对设备
     */
    private Set<BluetoothDevice> 安全获取配对设备() {
        Set<BluetoothDevice> devices = new HashSet<>();

        if (!检查蓝牙权限()) {
            return devices;
        }

        try {
            Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
            if (bondedDevices != null) {
                devices.addAll(bondedDevices);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "获取配对设备权限异常");
        } catch (Exception e) {
            Log.w(TAG, "获取配对设备异常: " + e.getMessage());
        }

        return devices;
    }

    /**
     * 检查蓝牙权限
     */
    private boolean 检查蓝牙权限() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return ActivityCompat.checkSelfPermission(mContext,
                        android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 安全获取设备名称
     */
    private String 获取设备名称安全(BluetoothDevice 设备) {
        if (设备 == null) return "未知设备";

        if (!检查蓝牙权限()) {
            return "未知设备";
        }

        try {
            String 名称 = 设备.getName();
            return 名称 != null ? 名称 : "未知设备";
        } catch (SecurityException e) {
            return "未知设备";
        } catch (Exception e) {
            return "未知设备";
        }
    }

    /**
     * 安全获取设备地址
     */
    private String 获取设备地址安全(BluetoothDevice 设备) {
        if (设备 == null) return "未知地址";

        if (!检查蓝牙权限()) {
            return "未知地址";
        }

        try {
            return 设备.getAddress();
        } catch (SecurityException e) {
            return "未知地址";
        } catch (Exception e) {
            return "未知地址";
        }
    }

    /**
     * 去重设备列表
     */
    private List<蓝牙设备信息> 去重设备列表(List<蓝牙设备信息> 设备列表) {
        List<蓝牙设备信息> 去重后列表 = new ArrayList<>();
        Set<String> existingMacs = new HashSet<>();

        for (蓝牙设备信息 设备 : 设备列表) {
            if (设备 == null) continue;

            String mac = 设备.getMac地址();
            if (mac != null && !mac.isEmpty() && !existingMacs.contains(mac)) {
                去重后列表.add(设备);
                existingMacs.add(mac);
            }
        }

        return 去重后列表;
    }















    // 检查蓝牙设备
    public static void BLUETOOTH(){
        蓝牙管理器 蓝牙 = new 蓝牙管理器(globalvariable.上下文);

        蓝牙.获取已连接设备(new 蓝牙管理器.设备列表回调() {
            @Override
            public void 设备列表获取成功(List<蓝牙管理器.蓝牙设备信息> 设备列表) {
                // 回调已经在主线程，可以直接更新UI
                for (蓝牙管理器.蓝牙设备信息 设备 : 设备列表) {
                    Log.d("蓝牙", "设备: " + 设备.toString());

                }

                if (设备列表.isEmpty()) {
                    Toast.makeText(globalvariable.上下文, "没有找到已连接的蓝牙设备", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void 设备列表获取失败(String 错误信息) {
                // 已经在主线程
                // Toast.makeText(MainActivity.this, "获取失败: " + 错误信息, Toast.LENGTH_SHORT).show();
                Log.e("蓝牙", "获取失败: " + 错误信息);
            }
        });

    }

}