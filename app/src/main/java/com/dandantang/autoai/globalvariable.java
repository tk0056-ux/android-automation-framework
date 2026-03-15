package com.dandantang.autoai;

import android.content.Context;
import android.widget.EditText;

import com.dandantang.autoai.服务.信息读取;

public class globalvariable {
    public static String Internetip = "0.0.0.0";
    public static String cdk = "";
    public static String 原始机器码 = "";
    public static Context 上下文 = null;
    public static String keyhttp = "";
    public static String keyParameter = "";

    public static void 初始化keyhttp参数(Context context){
         keyhttp = "http://47.97.191.182:18990/8p4l5g1o3p0f1p5t";
         keyParameter = "CardPwd="+cdk+"&Mac=" + 信息读取.机器码(context)+"&LgCity="+Internetip;
    }


}
