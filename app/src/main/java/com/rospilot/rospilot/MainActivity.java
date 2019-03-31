package com.rospilot.rospilot;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.vuzix.hud.actionmenu.ActionMenuActivity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends ActionMenuActivity {
    public static final String TAG = "MainActivity";
    private H264ViewModel viewModel;
    private MediaCodec codec;
    private SurfaceHolder surfaceHolder;
    private AtomicReference<ByteBuffer> csd = new AtomicReference<>();
    private AtomicReference<ByteBuffer> latestPacket = new AtomicReference<>();
    private AtomicBoolean csdInitialized = new AtomicBoolean();
    private AtomicInteger presentationCounter = new AtomicInteger();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewModel = new H264ViewModel(new H264ViewModel.Callback() {
            @Override
            public void callback(final ByteBuffer packet) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        latestPacket.set(packet);
                    }
                });
            }
        });
        viewModel.requestSPSAndPPS(new H264ViewModel.Callback() {
            @Override
            public void callback(final ByteBuffer packet) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!csd.compareAndSet(null, packet)) {
                            Log.e(TAG, "Failed to store CSD");
                        }
                    }
                });
            }
        });
        viewModel.start();

        SurfaceView surfaceView = findViewById(R.id.surface);
        surfaceView.setZOrderOnTop(true);
     	surfaceHolder = surfaceView.getHolder();
     	surfaceHolder.setFixedSize(128, 128);
     	surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", 320, 240);
                String codecName = new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format);
                try {
                    codec = MediaCodec.createByCodecName(codecName);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
                codec.configure(format, surfaceHolder.getSurface(), null, 0);
                codec.setCallback(new VideoCallback());
                codec.start();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.e(TAG, "DESTROYED!");
                codec.release();
            }
        });
    }

    @Override
    protected boolean onCreateActionMenu(Menu menu) {
        super.onCreateActionMenu(menu);

        getMenuInflater().inflate(R.menu.menu, menu);

        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        viewModel.start();
    }

    @Override
    protected boolean alwaysShowActionMenu() {
        return true;
    }

    private class VideoCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(@androidx.annotation.NonNull MediaCodec codec, int index) {
            ByteBuffer buffer = codec.getInputBuffer(index);
            int flags = 0;
            int sampleLength = 0;
            if (csd.get() != null && csdInitialized.compareAndSet(false, true)) {
                buffer.put(csd.get());
                sampleLength = csd.get().limit();
                flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            } else {
                ByteBuffer packet = latestPacket.getAndSet(null);
                if (packet != null) {
                    buffer.put(packet);
                    sampleLength = packet.limit();
                }
            }
            codec.queueInputBuffer(index, 0, sampleLength, presentationCounter.getAndIncrement(), flags);
        }

        @Override
        public void onOutputBufferAvailable(@androidx.annotation.NonNull MediaCodec codec, int index, @androidx.annotation.NonNull MediaCodec.BufferInfo info) {
            codec.releaseOutputBuffer(index, true);
        }

        @Override
        public void onError(@androidx.annotation.NonNull MediaCodec codec, @androidx.annotation.NonNull MediaCodec.CodecException e) {
            Log.e(TAG, e.toString());
        }

        @Override
        public void onOutputFormatChanged(@androidx.annotation.NonNull MediaCodec codec, @androidx.annotation.NonNull MediaFormat format) {
        }
    }
}
