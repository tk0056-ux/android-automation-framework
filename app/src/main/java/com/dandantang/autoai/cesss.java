package com.dandantang.autoai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import com.dandantang.autoai.服务.截图服务;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;


// 一个简单的网络请求服务器
public class cesss extends NanoHTTPD {


    private static final String TAG = "http服务";

    // 构造函数
    public cesss(int port) {
        super(port);
        Log.d(TAG, "HTTP服务已启动，端口: " + port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        // 获取请求方法 (GET, POST等)
        Method method = session.getMethod();

        // 获取请求地址
        String 请求地址 = session.getUri();

        // 获取请求参数
        Map<String, String> 参数 = session.getParms();

        // 如果是POST请求，还需要获取POST body中的数据
        String postData = "";
        if (method == Method.POST) {
            try {
                // 获取POST请求的内容长度
                int contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
                byte[] buffer = new byte[contentLength];

                // 读取POST数据
                session.getInputStream().read(buffer, 0, contentLength);
                postData = new String(buffer, "UTF-8");

                Log.d(TAG, "POST数据: " + postData);

                // 解析POST数据（如果是application/x-www-form-urlencoded格式）
                if (session.getHeaders().get("content-type") != null &&
                        session.getHeaders().get("content-type").contains("application/x-www-form-urlencoded")) {
                    解析POST表单数据(postData, 参数);
                }

            } catch (Exception e) {
                Log.e(TAG, "读取POST数据失败: " + e.getMessage());
            }
        }

        Log.d(TAG, "请求方法: " + method);
        Log.d(TAG, "收到请求路径: " + 请求地址);
        Log.d(TAG, "请求参数: " + 参数.toString());

        // 根据请求路径处理不同的请求
        if ("/gettest".equals(请求地址)) {
            return 处理GetTest请求(method, 参数, postData);
        } else if ("/posttest".equals(请求地址)) {
            return 处理PostTest请求(method, 参数, postData);
        } else if ("/screenshot".equals(请求地址)) {
            return 处理截图请求(参数);
        } else if ("/json".equals(请求地址)) {
            return 返回JSON数据(参数, postData);
        } else {
            // 404 Not Found
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
        }
    }

    // 处理GET测试请求
    private Response 处理GetTest请求(Method method, Map<String, String> 参数, String postData) {
        String responseText = "这是GET测试响应\n";
        responseText += "请求方法: " + method + "\n";
        responseText += "参数列表:\n";

        for (Map.Entry<String, String> entry : 参数.entrySet()) {
            responseText += "  " + entry.getKey() + " = " + entry.getValue() + "\n";
        }

        return newFixedLengthResponse(responseText);
    }

    // 处理POST测试请求
    private Response 处理PostTest请求(Method method, Map<String, String> 参数, String postData) {
        StringBuilder responseText = new StringBuilder();
        responseText.append("POST请求处理成功\n");
        responseText.append("请求方法: ").append(method).append("\n");

        if (!postData.isEmpty()) {
            responseText.append("原始POST数据: ").append(postData).append("\n");
        }

        responseText.append("解析后的参数:\n");
        for (Map.Entry<String, String> entry : 参数.entrySet()) {
            responseText.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
        }

        return newFixedLengthResponse(String.valueOf(responseText));
    }

    // 处理截图请求
    private Response 处理截图请求(Map<String, String> 参数) {
        try {
            // 获取截图
            Bitmap screenshot = 截图服务.获取当前全屏截图();
            Log.d(TAG, "处理截图请求: " + screenshot);

            if (screenshot == null) {
                String errorResponse = "{\"status\":\"error\",\"message\":\"获取截图失败，截图为空\"}";
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", errorResponse);
            }

            // 从参数中获取图片格式和质量
            String formatType = 参数.getOrDefault("format", "jpg").toLowerCase();
            String qualityStr = 参数.getOrDefault("quality", "80");
            boolean 是否需要预览 = Boolean.parseBoolean(参数.getOrDefault("preview", "false"));

            int quality = 80;
            try {
                quality = Integer.parseInt(qualityStr);
                quality = Math.max(1, Math.min(100, quality)); // 限制在1-100之间
            } catch (NumberFormatException e) {
                // 使用默认值
            }

            Bitmap.CompressFormat format = formatType.equals("png") ?
                    Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;

            // 将Bitmap转换为Base64字符串
            String base64Image = 将Bitmap转换为Base64(screenshot, format, quality);

            // 获取图片信息
            int width = screenshot.getWidth();
            int height = screenshot.getHeight();
            long fileSize = 获取图片大小(screenshot, format, quality);

            // 回收Bitmap
            if (!screenshot.isRecycled()) {
                screenshot.recycle();
            }

            // 构建详细的JSON响应
            StringBuilder jsonResponse = new StringBuilder();
            jsonResponse.append("{");
            jsonResponse.append("\"status\":\"success\",");
            jsonResponse.append("\"message\":\"截图成功\",");
            jsonResponse.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
            jsonResponse.append("\"image\":\"").append(base64Image).append("\",");
            jsonResponse.append("\"info\":{");
            jsonResponse.append("\"format\":\"").append(formatType).append("\",");
            jsonResponse.append("\"quality\":").append(quality).append(",");
            jsonResponse.append("\"width\":").append(width).append(",");
            jsonResponse.append("\"height\":").append(height).append(",");
            jsonResponse.append("\"size\":" ).append(fileSize);
            jsonResponse.append("}");

            // 如果需要预览（返回小尺寸的预览图）
            if (是否需要预览) {
                Bitmap previewBitmap = 生成预览图(screenshot, 200); // 生成200px宽的预览图
                String base64Preview = 将Bitmap转换为Base64(previewBitmap, format, quality);
                jsonResponse.append(",\"preview\":\"").append(base64Preview).append("\"");

                if (!previewBitmap.isRecycled()) {
                    previewBitmap.recycle();
                }
            }

            jsonResponse.append("}");

            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse.toString());

        } catch (Exception e) {
            Log.e(TAG, "处理截图请求失败: " + e.getMessage());
            String errorResponse = String.format(
                    "{\"status\":\"error\",\"message\":\"%s\"}",
                    e.getMessage().replace("\"", "\\\"")
            );
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", errorResponse);
        }
    }

    // 重载方法：带格式和质量的Base64转换
    private String 将Bitmap转换为Base64(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        if (bitmap == null) return "";

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(format, quality, outputStream);
        byte[] imageBytes = outputStream.toByteArray();

        try {
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "关闭输出流失败: " + e.getMessage());
        }

        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    // 获取图片大小（字节）
    private long 获取图片大小(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(format, quality, outputStream);
        long size = outputStream.size();
        try {
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "关闭输出流失败: " + e.getMessage());
        }
        return size;
    }

    // 生成预览图
    private Bitmap 生成预览图(Bitmap original, int maxWidth) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxWidth) {
            return original; // 如果原图已经小于最大宽度，直接返回
        }

        float scale = (float) maxWidth / width;
        int newWidth = maxWidth;
        int newHeight = Math.round(height * scale);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    // 返回JSON格式数据
    private Response 返回JSON数据(Map<String, String> 参数, String postData) {
        // 构建JSON响应
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"status\": \"success\",\n");
        json.append("  \"message\": \"数据处理成功\",\n");
        json.append("  \"data\": {\n");
        json.append("    \"method\": \"").append(参数.get("method")).append("\",\n");
        json.append("    \"timestamp\": ").append(System.currentTimeMillis()).append(",\n");
        json.append("    \"params\": {\n");

        // 添加所有参数
        int i = 0;
        for (Map.Entry<String, String> entry : 参数.entrySet()) {
            json.append("      \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            if (i < 参数.size() - 1) {
                json.append(",");
            }
            json.append("\n");
            i++;
        }

        json.append("    }\n");
        json.append("  }\n");
        json.append("}");

        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString());
    }

    // 解析POST表单数据
    private void 解析POST表单数据(String postData, Map<String, String> 参数) {
        try {
            String[] pairs = postData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    String value = URLDecoder.decode(keyValue[1], "UTF-8");
                    参数.put(key, value);
                }
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "解析POST数据失败: " + e.getMessage());
        }
    }

    // 便捷方法：返回纯文本响应
    private Response 返回文本(String text) {
        return newFixedLengthResponse(text);
    }

    // 便捷方法：返回JSON响应
    private Response 返回JSON(String json) {
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    // 便捷方法：返回HTML响应
    private Response 返回HTML(String html) {
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }
}