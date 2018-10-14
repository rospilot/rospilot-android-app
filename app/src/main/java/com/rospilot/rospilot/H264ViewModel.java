package com.rospilot.rospilot;


import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

class H264ViewModel {
    interface Callback {
        void callback(ByteBuffer packet);
    }

    private final AtomicBoolean done = new AtomicBoolean(false);
    private final Callback callback;
    private final List<Callback> spsAndPPSRequests = new LinkedList<>();

    H264ViewModel(Callback callback) {
        this.callback = callback;
    }

    void requestSPSAndPPS(Callback callback) {
        synchronized (spsAndPPSRequests) {
            spsAndPPSRequests.add(callback);
        }
    }

    void start() {
        done.set(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!done.get()) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .readTimeout(500, TimeUnit.MILLISECONDS)
                            .connectTimeout(500, TimeUnit.MILLISECONDS)
                            .writeTimeout(500, TimeUnit.MILLISECONDS)
                            .build();

                    List<Callback> callbacks;
                    synchronized (spsAndPPSRequests) {
                        callbacks = new ArrayList<>(spsAndPPSRequests);
                        spsAndPPSRequests.clear();
                    }

                    if (callbacks.size() > 0) {
                        Request request = new Request.Builder()
                                .url("http://192.168.122.1:8666/h264_sps_pps")
                                .build();

                        Log.e("MainActivity", "Fetching metadata");
                        Response response;
                        try {
                            response = client.newCall(request).execute();
                            ByteBuffer buffer = ByteBuffer.wrap(response.body().bytes());
                            for (Callback callback: callbacks) {
                                callback.callback(buffer);
                            }
                        } catch (IOException e) {
                            Log.e("HI", e.toString());
                        }
                    }

                    Request request = new Request.Builder()
                        .url("http://192.168.122.1:8666/h264/1236")
                        .build();

                    Response response;
                    try {
                        response = client.newCall(request).execute();
                        ByteBuffer buffer = ByteBuffer.wrap(response.body().bytes());
                        callback.callback(buffer);
                    } catch (IOException e) {
                        Log.e(MainActivity.TAG, e.toString());
                    }

                }
            }
        }).start();
    }

    void stop() {
        done.set(true);
    }
}


