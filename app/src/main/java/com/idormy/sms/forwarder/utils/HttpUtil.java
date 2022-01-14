package com.idormy.sms.forwarder.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.*;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Objects;

import okhttp3.*;

public class HttpUtil {
    private static final OkHttpClient client = new OkHttpClient();
    private static final String TAG = "HttpUtil";
    private static Boolean hasInit = false;
    @SuppressLint("StaticFieldLeak")
    private static Context context;
    //    @SuppressLint("StaticFieldLeak")
//    private static Handler handError;
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json;charset=utf-8");


    @SuppressLint("HandlerLeak")
    public static void init(Context context) {
        //noinspection SynchronizeOnNonFinalField
        synchronized (hasInit) {
            if (hasInit) return;

            hasInit = true;
            HttpUtil.context = context;
        }
    }

    public static void asyncGet(String tag, String url, Object param, Lamda.Consumer<Response> onResponse, Lamda.Consumer<Exception> onFailure) {
        StringBuilder resUrl = appendQueryStr(tag, url, param);
        Request request = new Request.Builder().url(resUrl.toString()).get().build();
        Lamda.Func<Call, String> func = call -> {
            call.enqueue(new Callback0(tag, onResponse, onFailure));
            return null;
        };
        callAndCatch(tag, request, func);
    }

    public static void asyncPostJson(String tag, String url, Object param, Lamda.Consumer<Response> onResponse, Lamda.Consumer<Exception> onFailure) {
        String jsonString = JSON.toJSONString(param);
        Request request = new Request.Builder().url(url).post(RequestBody.create(jsonString, MEDIA_TYPE_JSON)).build();
        Lamda.Func<Call, String> func = call -> {
            call.enqueue(new Callback0(tag, onResponse, onFailure));
            return null;
        };
        callAndCatch(tag, request, func);
    }

    public static String postJson(String tag, String url, Object param) {
        String jsonString = JSON.toJSONString(param);
        Request request = new Request.Builder().url(url).post(RequestBody.create(jsonString, MEDIA_TYPE_JSON)).build();
        Lamda.Func<Call, String> func = call -> {
            Response response = call.execute();
            if (response.code() == 200) {
                return Objects.requireNonNull(response.body()).toString();
            }
            return null;
        };
        return callAndCatch(tag, request, func);
    }

    public static String get(String tag, String url, Object param) {
        StringBuilder resUrl = appendQueryStr(tag, url, param);
        Request request = new Request.Builder().url(resUrl.toString()).get().build();
        Lamda.Func<Call, String> func = call -> {
            Response response = call.execute();
            if (response.code() == 200) {
                return Objects.requireNonNull(response.body()).toString();
            }
            return null;
        };
        return callAndCatch(tag, request, func);
    }

    public static void Toast(String Tag, String data) {
        Log.i(Tag, data);
        try {
            Toast.makeText(HttpUtil.context, Tag + "-" + data, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    public static StringBuilder appendQueryStr(String tag, String url, Object param) {
        StringBuilder resUrl = new StringBuilder(url);
        if (!url.contains("?")) {
            resUrl.append("?");
        } else {
            resUrl.append("&");
        }
        Map<String, String> paramMap = param instanceof Map ? (Map<String, String>) param
                : JSON.parseObject(JSON.toJSONString(param), new TypeReference<Map<String, String>>() {
        }.getType());
        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            if (entry.getValue() != null) {
                resUrl.append(URLEncoder.encode(entry.getKey())).append("=").append(URLEncoder.encode(entry.getValue()));
            }
        }
        Log.i(tag, "url:" + resUrl);
        return resUrl;
    }

    public static String callAndCatch(String tag, Request request, Lamda.Func<Call, String> func) {
        try {
            Call call = client.newCall(request);
            return func.execute(call);
        } catch (Exception e) {
            Toast(tag, "请求失败：" + e.getMessage());
            Log.e(tag, "请求失败：" + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static class Callback0 implements Callback {
        public Callback0(String tag, Lamda.Consumer<Response> onResponse, Lamda.Consumer<Exception> onFailure) {
            this.tag = tag;
            this.onResponse = onResponse;
            this.onFailure = onFailure;
        }

        public Callback0(String tag, Lamda.Consumer<Response> onResponse) {
            this.tag = tag;
            this.onResponse = onResponse;
        }

        private final String tag;
        private final Lamda.Consumer<Response> onResponse;
        private Lamda.Consumer<Exception> onFailure;

        @Override
        public void onFailure(@NonNull Call call, @NonNull final IOException e) {
            Toast(tag, "onFailure：" + e.getMessage());
            Log.d(tag, "onFailure：" + e.getMessage());
            if (onFailure != null) {
                onFailure.executeThrowRunTimeExcp(e);
            }
        }

        @Override
        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
            Log.d(tag, "onResponse：" + response.code() + ":" + Objects.requireNonNull(response.body()).toString());
            if (onResponse != null)
                onResponse.executeThrowRunTimeExcp(response);
        }

        public String getTag() {
            return tag;
        }
    }

}