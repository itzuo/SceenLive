package com.zxj.screenlive;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoCodec extends Thread{

    private final ScreenLive screenLive;
    private MediaCodec mediaCodec;
    private boolean isLiving;
    private long timeStamp;
    private long startTime;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    public VideoCodec(ScreenLive screenLive) {
        this.screenLive = screenLive;
    }

    public void startLive(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
        // 配置编码参数
//        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 360, 640);
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 720, 1280);
        //编码数据源的格式
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //码率
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 400_000);
        //帧率
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        //关键帧间隔，2秒
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        try {
            // 创建编码器
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(videoFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            // 从编码器创建一个画布, 画布上的图像会被编码器自动编码
            Surface surface = mediaCodec.createInputSurface();
            virtualDisplay = mediaProjection.createVirtualDisplay("screen-codec",
                    720, 1280, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        start();
    }

    @Override
    public void run() {
        super.run();
        isLiving = true;
        mediaCodec.start();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        //TODO mediaCodec有个关键帧问题，需要手动触发输出关键帧
        while (isLiving) {
            if (timeStamp != 0) {
                //2000毫秒 手动触发输出关键帧
                if (System.currentTimeMillis() - timeStamp >= 2_000) {
                    Bundle params = new Bundle();
                    //立即刷新 让下一帧是关键帧
                    params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                    mediaCodec.setParameters(params);
                    timeStamp = System.currentTimeMillis();
                }
            } else {
                timeStamp = System.currentTimeMillis();
            }


            //获得编码之后的数据

            //从输出队列获取到输出到数据
            int index = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);//超时时间：10微秒
            Log.e("zxj","2222222=="+index);
            if (index >= 0) {
                //成功取出的编码数据
                ByteBuffer buffer = mediaCodec.getOutputBuffer(index);
                byte[] outData = new byte[bufferInfo.size];
                buffer.get(outData);
                FileUtils.writeBytes(outData);
                //这样也能拿到 sps pps
//                ByteBuffer sps = mediaCodec.getOutputFormat().getByteBuffer
//                        ("csd-0");
//                ByteBuffer pps = mediaCodec.getOutputFormat().getByteBuffer
//                        ("csd-1");
                if (startTime == 0) {
                    // 微妙转为毫秒
                    startTime = bufferInfo.presentationTimeUs / 1000;
                }
                RTMPPackage rtmpPackage = new RTMPPackage();
                rtmpPackage.setBuffer(outData);
                rtmpPackage.setType(RTMPPackage.RTMP_PACKET_TYPE_VIDEO);
                long tms = (bufferInfo.presentationTimeUs / 1000) - startTime;
                rtmpPackage.setTms(tms);
                screenLive.addPackage(rtmpPackage);

                //释放，让队列中index位置能放新数据
                mediaCodec.releaseOutputBuffer(index, false);
            }
        }

        isLiving = false;
        startTime = 0;
        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        virtualDisplay.release();
        virtualDisplay = null;
        mediaProjection.stop();
        mediaProjection = null;
    }

    public void stopLive(){
        isLiving = false;
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
