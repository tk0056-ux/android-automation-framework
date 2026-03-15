package com.dandantang.autoai.服务;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import com.dandantang.autoai.globalvariable;

public class 网络访问 {

    private static final String TAG = "网络访问";

    // 静态单例客户端，全局共用一个连接池
    private static final OkHttpClient 客户端 = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    // 内部私有接口：仅供类内部实现异步到同步的转换
    private interface 结果接口 {
        void 完成(String 结果);
    }

    /**
     * 定义结果承载类（模拟精易模块的参考参数）
     */
    public static class 结果对象 {
        public int 状态代码 = 0;
        public String 返回Cookies = "";
        public String 返回协议头 = "";
    }

    // --- 极简调用区：供 MainActivity 调用 ---

    /**
     * 异步 POST 访问（一行代码调用，自动回主线程）
     * 适合点击“运行”按钮时进行授权检查
     */
    public static void 访问POST(String 网址, String 参数 , int 访问类型) {
        // 在内部调用私有底层逻辑
        Log.d(TAG, "访问POST: "+网址+ " | "+参数);
        执行底层访问post(网址, 参数, new 结果接口() {
            @Override
            public void 完成(String 结果) {
                // 1. 存入全局变量供 Lua 读取
                com.dandantang.autoai.javaluamod.javasystemluamod.设置全局变量("最后网络返回", 结果);
                // Log.d(TAG, "访问完成返回数据: " + 结果);

                // 2. 补全启动逻辑
                if (globalvariable.上下文 != null) {
                    try {
                        // 卡密验证逻辑
                        if (访问类型 == 1){
                            卡密验证.卡密验证结果处理(结果);
                        }


                        //Log.d(TAG, "✅ 悬浮窗服务启动指令已发送");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ 启动悬浮窗服务失败: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "❌ 启动失败：globalvariable.上下文 为空，请检查程序初始化顺序");
                }
            }
        });
    }

    // --- 底层封装区：不对外公开的黑盒逻辑 ---

    /**
     * 真正的异步干活方法，处理线程切换
     */
    private static void 执行底层访问post(String 网址, String 参数, 结果接口 接口) {
        new Thread(() -> {
            try {
                String postData = (参数 == null) ? "" : 参数;
                RequestBody body = RequestBody.create(postData, MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"));
                Request request = new Request.Builder().url(网址).post(body).build();

                try (Response response = 客户端.newCall(request).execute()) {
                    // 1. 先拿到原始字节
                    byte[] b = (response.body() != null) ? response.body().bytes() : null;
                    String 返回数据 = "";

                    if (b != null) {
                        // 2. 尝试用 GBK 解码（易语言/精易服务器常用编码）
                        // 如果你的服务器是标准的 UTF-8，这里可以加逻辑判断
                        返回数据 = new String(b, "GBK");

                        // 如果 GBK 还是乱码，可以根据协议头 content-type 动态判断，k
                        // 但通常这种情况改成 GBK 就能解决。
                    }

                    String 最终结果 = 返回数据;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (接口 != null) 接口.完成(最终结果);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "请求出错: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (接口 != null) 接口.完成("请求失败: " + e.getMessage());
                });
            }
        }).start();
    }

    // --- 功能扩展区：网页_访问_对象 (同步方法，模拟精易模块) ---

    /**
     * 网页_访问_对象
     * @param 访问方式 0=GET, 1=POST, 2=HEAD ...
     */
    public byte[] 网页_访问_对象(
            String 网址,
            int 访问方式,
            String 提交信息,
            String 附加协议头,
            final 结果对象 结果
    ) {
        try {
            String[] methods = {"GET", "POST", "HEAD", "PUT", "OPTIONS", "DELETE", "TRACE", "CONNECT", "PATCH"};
            String method = methods[访问方式];

            RequestBody body = null;
            // 只有特定方法需要 Body
            if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
                String postData = (提交信息 == null) ? "" : 提交信息;
                body = RequestBody.create(postData, MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"));
            }

            Request.Builder requestBuilder = new Request.Builder().url(网址);

            // 解析协议头
            if (附加协议头 != null && !附加协议头.isEmpty()) {
                String[] lines = 附加协议头.split("\n");
                for (String line : lines) {
                    if (line.contains(":")) {
                        String[] parts = line.split(":", 2);
                        requestBuilder.addHeader(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            requestBuilder.method(method, body);

            // 使用 try-with-resources 自动释放 response 资源
            try (Response response = 客户端.newCall(requestBuilder.build()).execute()) {
                if (结果 != null) {
                    结果.状态代码 = response.code();
                    结果.返回协议头 = response.headers().toString();
                    结果.返回Cookies = response.header("Set-Cookie", "");
                }

                return (response.body() != null) ? response.body().bytes() : null;
            }

        } catch (Exception e) {
            Log.e(TAG, "网页访问异常: " + e.getMessage());
        }
        return null;
    }
}