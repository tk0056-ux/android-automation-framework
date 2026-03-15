package com.dandantang.autoai.javaluamod;


// 暂时不用了 。
public class luazhfy {
    public static String 翻译代码(String 中文代码) {
        if (中文代码 == null || 中文代码.isEmpty()) return "";

        String 码 = 中文代码;

        // 1. 优先替换长词 (防止拆分错误)
        码 = 码.replaceAll("判断循环", " while ");
        码 = 码.replaceAll("计次循环", " for ");
        码 = 码.replaceAll("跳出循环", " break ");
        码 = 码.replaceAll("本地变量", " local ");
        码 = 码.replaceAll("循环执行", " do ");

        // 2. 逻辑控制
        码 = 码.replaceAll("否则", " else ");
        码 = 码.replaceAll("如果", " if ");
        码 = 码.replaceAll("则", " then ");
        码 = 码.replaceAll("结束", " end ");

        // 3. 基础定义
        码 = 码.replaceAll("函数", " function ");

        // 4. 布尔值适配 (虽然 C++ 有注册，但这里转换后兼容性更强)
        码 = 码.replaceAll("真", " true ");
        码 = 码.replaceAll("假", " false ");

        // 5. 逻辑运算 (建议加上)
        码 = 码.replaceAll(" 并且 ", " and ");
        码 = 码.replaceAll(" 或者 ", " or ");
        码 = 码.replaceAll(" 不是 ", " not ");

        return 码;
    }
}