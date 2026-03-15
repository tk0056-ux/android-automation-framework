package com.dandantang.autoai;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager; // 新增：资源管理器引用
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dandantang.autoai.服务.UI处理;
import com.dandantang.autoai.服务.信息读取;
import com.dandantang.autoai.服务.悬浮窗服务;
import com.dandantang.autoai.服务.截图服务;
import com.dandantang.autoai.服务.权限申请;
import com.dandantang.autoai.服务.网络访问;
import com.dandantang.autoai.javaluamod.javasystemluamod;
import com.dandantang.autoai.globalvariable;
import com.dandantang.autoai.服务.卡密验证;
import com.dandantang.autoai.服务.Lua环境管理器;
import com.dandantang.autoai.服务.ce;
import com.dandantang.autoai.服务.蓝牙管理器;

// 新增：缺少的导入
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LuaTest";
    private UI处理 UI;

    private static boolean isLibLoaded = false;

    // 1. 定义 Native 方法
    public native String runLuaTest(String code);

    // 新增：初始化 C++ 层的 AssetManager 引用，用于内存读取脚本
    public native void nativeInit(AssetManager assetManager);

    private Lua环境管理器 luaManager;

    // 新增：将 BroadcastReceiver 定义为成员变量
    private BroadcastReceiver luaRunReceiver;

    // 2. 加载库 (名字必须与 CMakeLists 中的 add_library 一致)
    static {
        try {
            System.loadLibrary("native-lua");
            isLibLoaded = true;
            Log.d(TAG, "✅ libnative-lua.so 加载成功！");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "❌ 找不到 so 库，请检查 CMake 中的项目名: " + e.getMessage());
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        globalvariable.上下文 = getApplicationContext();
        信息读取.初始化上下文(getApplicationContext());
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        javasystemluamod.appContext = this;
        // 新增：在 Lua 引擎运行前，先初始化 C++ 层的资源管理器
        if (isLibLoaded) {
            try {
                nativeInit(getAssets());
                Log.d(TAG, "✅ Native 环境初始化完成");
            } catch (Exception e) {
                Log.e(TAG, "❌ nativeInit 失败: " + e.getMessage());
            }
        }
        // 初始化Lua管理器
        luaManager = Lua环境管理器.getInstance(this);
        //  初始化 CE
        ce.init(this);

        // 新增：初始化 BroadcastReceiver
        luaRunReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String script = intent.getStringExtra("script");
                if (script != null) {
                    runLuaScript(script);
                }
            }
        };

        // Android 14+ 需要使用这个新方法
        registerReceiver(luaRunReceiver, new IntentFilter("com.dandantang.autoai.RUN_LUA"),
                Context.RECEIVER_NOT_EXPORTED);  //



        // 初始化 UI 和权限
        UI = new UI处理();
        UI.绑定UI控件(this);

        权限申请.申请悬浮窗权限(this);
        权限申请.申请基础权限(this);


        // 处理状态栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 3. 运行按钮逻辑
        UI.UI界面_运行按钮.setOnClickListener(v -> {
            // 输入框 空 返回 并弹窗
            String A = UI.UI界面_文本框_卡密.getText().toString().trim();
            String B = UI.UI界面_文本框_用户名.getText().toString().trim();
            String C = UI.UI界面_文本框_设备编号.getText().toString().trim();
            int checkedId = UI.UI界面_单选框组_模式.getCheckedRadioButtonId();
            if (A.isEmpty() || B.isEmpty() || C.isEmpty() ) {
                UI.弹窗提示("提示", "所有字段都必须填写，不能为空");
                return;
            }
            globalvariable.初始化keyhttp参数(MainActivity.this);


            if(checkedId == R.id.Mode_HID){
                if (权限申请.是否有基础权限(this)) {
                    UI.保存当前所有配置();
                    //检查蓝牙设备
                    if (globalvariable.蓝牙已连接设备 == ""){
                        蓝牙管理器.BLUETOOTH();
                    }


                    // 检查令牌
                    if (globalvariable.截图数据令牌 == null) {
                        // 申请截图权限
                        申请截图权限();
                        // 使用方式完全一样



                    } else {
                        // 令牌已存在，直接启动截图服务
                        startScreenshotService();
                        // 继续卡密验证
                        卡密验证.卡密验证();
                    }
                } else {
                    Toast.makeText(this, "请先授予必要权限", Toast.LENGTH_SHORT).show();
                }
            } else {
                卡密验证.卡密验证();
            }





        });
    }

    private void startScreenshotService() {
        Intent 意图 = new Intent(this, 截图服务.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(意图);
        } else {
            startService(意图);
        }
        Log.d(TAG, "截图服务启动命令已发送");
    }

    public void 申请截图权限() {
        MediaProjectionManager 管理器 = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        截图授权启动器.launch(管理器.createScreenCaptureIntent());
    }
    private ActivityResultLauncher<Intent> 截图授权启动器 = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            结果 -> {
                if (结果.getResultCode() == RESULT_OK) {
                    globalvariable.截图数据令牌结果码 = 结果.getResultCode();
                    globalvariable.截图数据令牌 = 结果.getData();
                    Log.d("截图服务", "✓ 数据令牌保存成功");

                    // 启动截图服务
                    startScreenshotService();

                    // 等待服务初始化完成
                    new Thread(() -> {
                        try {
                            Thread.sleep(15); // 等待1.5秒让服务初始化
                            runOnUiThread(() -> {
                                // 继续卡密验证
                                卡密验证.卡密验证();
                            });
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            }
    );



    private void executeLuaScript() {
        if (!isLibLoaded) {
            Toast.makeText(this, "Native 库未加载，无法运行 Lua", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            // 获取脚本内容
            String luaCode = getAssetScript("main.lua");


            if (luaCode.isEmpty()) {
                // 如果文件为空，给个默认测试脚本，防止底层崩溃
                luaCode = "return 'Hello Lua! Script was empty but I am running.'";
            }
            try {
                // 调用 Native
                final String result = runLuaTest(luaCode);

                // 回到主线程更新 UI
                runOnUiThread(() -> {
                    Log.i(TAG, "Lua 执行结果: " + result);
                    Toast.makeText(MainActivity.this, "结果: " + result, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "调用 runLuaTest 崩溃: " + e.getMessage());
            }
        }).start();
    }

    // 读取 Assets 脚本
    private String getAssetScript(String fileName) {
        try (InputStream is = getAssets().open(fileName)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            // 显式指定 UTF-8 编码，防止中文乱码传入 C++
            return new String(buffer, 0, size, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e("LuaTest", "Read Assets Failed: " + fileName);
            return "";
        }
    }

    // 新增：运行Lua脚本的方法
    private void runLuaScript(String scriptName) {
        new Thread(() -> {
            String luaCode = getAssetScript(scriptName);
            if (!luaCode.isEmpty()) {
                runLuaTest(luaCode);
            }
        }).start();
    }




    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 新增：取消注册广播接收器
        if (luaRunReceiver != null) {
            unregisterReceiver(luaRunReceiver);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 权限申请.REQUEST_CODE_NORMAL) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                UI.绑定UI控件(this);
            } else {
                Toast.makeText(this, "拒绝权限将无法使用核心功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

}