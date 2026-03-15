package com.dandantang.autoai.服务;

import android.content.Intent;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.dandantang.autoai.globalvariable;
import com.dandantang.autoai.javaluamod.javasystemluamod;

public class 卡密验证 {


    public static void 卡密验证() {
        // 在内部调用私有底层逻辑+
        Log.d("验证结果", "卡密验证: "+ globalvariable.keyhttp +"参数"+  globalvariable.keyParameter);
        网络访问.访问POST(globalvariable.keyhttp, globalvariable.keyParameter,1);
    };
    public static void 卡密验证结果处理(String 访问结果){
        // 卡密不存在！
        // 2026-3-16 23:25:24|||26084892|||空uictjaxoerprhjukhtmtsda|||1771082724|||7feaa47cba90abec1c0d55fead17561f
        // 帐户已被封停，请联系管理处理！
        // 账号已到期
        // 2026-2-14 23:25:24|||会变|||会变|||会变|||会变

        Log.d("验证结果", "卡密验证结果处理: " + 访问结果);
        if (访问结果.contains("|||")) {
            String[] 分割 = 访问结果.split("\\|\\|\\|");
            if (分割.length > 0) {
                int 成员数 = 分割.length;
                for (int i = 0; i < 分割.length; i++) {
                    Log.d("验证结果", "卡密验证结果处理: " + 分割[i]);
                    switch (i) {
                        case 0:
                            UI处理.界面修改_验证结果(分割[i]);//在UI上显示 剩余时间
                            Log.d("验证结果", "卡密验证结果处理: "+ 分割[i]);
                            验证结果成功();


                            break;
                        case 1:
                            javasystemluamod.设置全局变量("Token", 分割[i]); //  更新全局变量后 让lua可以读取
                            break;
                        case 2:
                            // 可能保存用户名
                            //远程信息
                            break;
                        case 3:
                            // 可能保存设备号
                            //md5
                            break;
                        default:
                            //Log.d("验证结果", "额外字段: " + 分割[i]);
                            break;
                    }

                }
            }
        }else {
            if (访问结果.contains("未到自动解绑时间，请先解绑或关闭其他机器正在运行的程序稍后再登陆！")){
                UI处理.界面修改_验证结果("禁止多台设备登录，请先解绑");
            }

            UI处理.界面修改_验证结果(访问结果);
            //Log.d("卡密验证", "卡密验证结果处理: " + 访问结果);
        }




    }

    public static void 验证结果成功(){
        // 验证通过后  打开悬浮窗
        Intent serviceIntent = new Intent(globalvariable.上下文, 悬浮窗服务.class); // 启动悬浮窗
        // 必须执行启动命令，服务才会运行
        globalvariable.上下文.startForegroundService(serviceIntent);   // 启动悬浮窗

    }













}
