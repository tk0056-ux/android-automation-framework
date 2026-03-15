package com.dandantang.autoai;

import android.content.Context;
import android.widget.EditText;

import com.dandantang.autoai.服务.信息读取;
import android.content.Intent;

public class globalvariable {
    public static String Internetip = "0.0.0.0";
    public static String cdk = "";
    public static String 原始机器码 = "";
    public static Context 上下文 = null;
    public static String keyhttp = "";
    public static String keyParameter = "";
    public static String open模式 = ""; // 操作模式  hid  adb  root 无障碍？？？
    public static Intent 截图数据令牌 = null;
    public static int 截图数据令牌结果码 = 0;
    public static String 蓝牙已连接设备 = "";

    public static void 初始化keyhttp参数(Context context){
        // 卫士盾验证的api 卡密验证地址  不同的软件 需要使用不同的地址
         keyhttp = "http://47.97.191.182:45561/0q6d1h0w0f4l5o7w";
         keyParameter = "CardPwd="+cdk+"&Mac=" + 信息读取.机器码(context)+"&LgCity="+Internetip;
    }


}
