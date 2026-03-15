package com.dandantang.autoai.服务;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.dandantang.autoai.R;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import com.dandantang.autoai.globalvariable;
public class UI处理 {
    private Activity 当前界面;
    private SharedPreferences 本地配置;
    private final OkHttpClient 互联网客户端 = new OkHttpClient();
    private final Handler 主线程工具 = new Handler(Looper.getMainLooper());
    private static TextView 静态_到期时间文本框;

    public EditText UI界面_文本框_卡密, UI界面_文本框_用户名, UI界面_文本框_IP, UI界面_文本框_设备编号;
    public RadioGroup UI界面_单选框组_模式;
    public Button UI界面_运行按钮;
    public TextView UI界面_文本显示_到期时间, UI界面_文本显示_显示IP, UI界面_文本显示_外网IP;
    public Switch UI界面_调试模式;


    public void 绑定UI控件(Activity 界面) {
        // 初始化 SharedPreferences (文件名: AppConfig)
        本地配置 = 界面.getSharedPreferences("AppConfig", Context.MODE_PRIVATE);
        this.当前界面 = 界面; // 保存 Activity 引用




        UI界面_文本框_卡密 = 界面.findViewById(R.id.text_CDK);
        UI界面_文本框_设备编号 = 界面.findViewById(R.id.text_udni);
        UI界面_文本框_用户名 = 界面.findViewById(R.id.text_user);
        UI界面_文本框_IP = 界面.findViewById(R.id.text_IP);
        UI界面_单选框组_模式 = 界面.findViewById(R.id.mode_group);
        UI界面_运行按钮 = 界面.findViewById(R.id.button_run);
        UI界面_文本显示_到期时间 = 界面.findViewById(R.id.textdaoqishijian);
        UI界面_文本显示_显示IP = 界面.findViewById(R.id.lock_本机ip);
        UI界面_文本显示_外网IP = 界面.findViewById(R.id.text_外网ip);
        UI界面_调试模式 = 界面.findViewById(R.id.tiaoshimob);


        静态_到期时间文本框 = UI界面_文本显示_到期时间; // 保存到静态变量

        // 1. 加载上次保存的内容
        加载所有配置();


        UI界面_文本显示_显示IP.setText("当前设备IP： " + 获取局域网IP());




        // 2. 获取并显示外网 IP
        获取外网IP(界面, new 验证回调() {
            @Override
            public void 成功(String 结果内容) {
                // 直接更新 UI，因为方法内部已经处理了 runOnUiThread
                UI界面_文本显示_外网IP.setText("外网IP: " + 结果内容);
                globalvariable.Internetip =  结果内容;

                // 顺便保存到本地，下次启动可以先显示旧 IP
                //本地配置.edit().putString("最后外网IP", 结果内容).apply();
            }

            @Override
            public void 失败(String 错误信息) {
                UI界面_文本显示_外网IP.setText("IP获取失败: 网络超时");
                Log.e("IP_ERROR", 错误信息);
            }
        });

        // 3. 监听变化并保存 (示例：当点击运行按钮时保存所有输入)
        UI界面_运行按钮.setOnClickListener(v -> 保存当前所有配置());
    }

    // 保存配置到本地
    public void 保存当前所有配置() {
        SharedPreferences.Editor 编辑器 = 本地配置.edit();
        编辑器.putString("卡密", UI界面_文本框_卡密.getText().toString());
        编辑器.putString("用户名", UI界面_文本框_用户名.getText().toString());
        编辑器.putString("IP", UI界面_文本框_IP.getText().toString());
        编辑器.putString("SN", UI界面_文本框_设备编号.getText().toString());
        编辑器.putBoolean("调试模式", UI界面_调试模式.isChecked());
        编辑器.putInt("选中的模式ID", UI界面_单选框组_模式.getCheckedRadioButtonId());
        编辑器.apply(); // 异步提交保存
        globalvariable.cdk = UI界面_文本框_卡密.getText().toString();
        int checkedId = UI界面_单选框组_模式.getCheckedRadioButtonId();

        if (checkedId == R.id.mode_root) {
            globalvariable.open模式 = "root";
            //Log.d("控制模式111", "当前模式: root模式");
        } else if (checkedId == R.id.Mode_HID) {
            globalvariable.open模式 = "hid";
            //Log.d("控制模式111", "当前模式: HID模式");
        } else if (checkedId == R.id.acc) {
            globalvariable.open模式 = "acc";
            //Log.d("控制模式111", "当前模式: 无障碍模式");
        } else if (checkedId == R.id.mode_adb) {
            globalvariable.open模式 = "adb";
            //Log.d("控制模式111", "当前模式: ADB模式");
        } else {
            globalvariable.open模式 = "hid";
            //Log.d("控制模式111", "当前模式: 未选择 (假)");
        }
    }

    private void 加载所有配置() {
        UI界面_文本框_卡密.setText(本地配置.getString("卡密", ""));
        UI界面_文本框_用户名.setText(本地配置.getString("用户名", ""));
        UI界面_文本框_IP.setText(本地配置.getString("IP", ""));
        UI界面_文本框_设备编号.setText(本地配置.getString("SN", ""));
        UI界面_调试模式.setChecked(本地配置.getBoolean("调试模式", false));
        int 历史ID = 本地配置.getInt("选中的模式ID", -1);
        if (历史ID != -1) {
            UI界面_单选框组_模式.check(历史ID);
        }
    }

    public void 获取外网IP(Activity 界面上下文, 验证回调 回调) {
        // 建议使用 https 地址，更安全且无需配置额外的明文通行权
        Request 请求 = new Request.Builder()
                .url("https://checkip.amazonaws.com/") // 改为 https
                .get()
                .build();

        // 发起异步请求
        互联网客户端.newCall(请求).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 回到 UI 线程执行失败回调
                界面上下文.runOnUiThread(() -> 回调.失败("连接失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response 返回对象) throws IOException {
                try {
                    if (返回对象.isSuccessful() && 返回对象.body() != null) {
                        // 读取返回的 IP 并去除换行符
                        final String ip = 返回对象.body().string().trim();
                        // 回到 UI 线程执行成功回调
                        界面上下文.runOnUiThread(() -> 回调.成功(ip));
                    } else {
                        界面上下文.runOnUiThread(() -> 回调.失败("服务器错误: " + 返回对象.code()));
                    }
                } finally {
                    // 记得关闭 body 防止内存泄漏
                    if (返回对象.body() != null) 返回对象.body().close();
                }
            }
        });
    }

    public static void 界面修改_验证结果(String text) {
        //
        // Log.d("验证结果", "界面修改_验证结果: " + text);

        if (静态_到期时间文本框 != null) {
            // 确保在主线程更新UI
            静态_到期时间文本框.post(new Runnable() {
                @Override
                public void run() {
                    静态_到期时间文本框.setText("卡密到期时间： " + text);
                    //Log.d("验证结果", "界面修改成功: " + text);
                }
            });
        }
    }
    public interface 验证回调 {
        void 成功(String 结果内容);
        void 失败(String 错误信息);
    }
    public static String 获取局域网IP() {
        try {
            // 获取手机所有网络接口（如 wifi, 移动数据, 蓝牙等）
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    // 排除回环地址 (127.0.0.1) 并且必须是 IPv4
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String sAddr = addr.getHostAddress();
                        // 过滤掉虚拟网卡或特定的 IP 范围（可选）
                        return sAddr;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1";
    }




    public void 弹窗提示(String title, String message) {
        if (当前界面 != null && !当前界面.isFinishing()) { // 检查界面是否还在
            new AlertDialog.Builder(当前界面)  // 使用保存的 Activity
                    .setTitle(title)
                    .setMessage(message)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .show();
        }
    }
}