package com.zxj.screenlive;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioCodec extends Thread{

    private final ScreenLive screenLive;
    private AudioRecord audioRecord;
    private int sampleRate = 44100;
    private MediaCodec mediaCodec;
    private boolean isRecoding;
    private int minBufferSize;
    private long startTime;

    public AudioCodec(ScreenLive screenLive) {
        this.screenLive =screenLive;
    }

    public void startLive() {
        //2:采样率，3：声道数
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1);
        //编码规格，可以看成质量
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        //码率
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 64_000);

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaCodec.configure(audioFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /**
         * 获得创建AudioRecord所需的最小缓冲区
         * 采样+单声道+16位pcm
         */
        minBufferSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        /**
         * 创建录音对象
         * 麦克风+采样+单声道+16位pcm+缓冲区大小
         */
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC, //采集源，麦克风
                sampleRate,//采样率,44.1kHz，所有设备都支持
                AudioFormat.CHANNEL_IN_MONO,//声道数,CHANNEL_IN_MONO:单声道，CHANNEL_IN_STEREO ：双声道
                AudioFormat.ENCODING_PCM_16BIT,//采样位,16 位，所有设备都支持
                minBufferSize);//最小缓冲区大小

        start();
    }

    public void stopLive(){
        isRecoding = false;
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        isRecoding = true;

        //在获取播放的音频数据之前，先发送 audio special config
        RTMPPackage rtmpPackage = new RTMPPackage();
        byte[] audioDecoderSpecificInfo = {0x12, 0x08};//发送音频之前需要先发送0x12, 0x08
        rtmpPackage.setBuffer(audioDecoderSpecificInfo);
        rtmpPackage.setType(RTMPPackage.RTMP_PACKET_TYPE_AUDIO_HEAD);
        rtmpPackage.setTms(0);
        screenLive.addPackage(rtmpPackage);
        audioRecord.startRecording();

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        byte[] buffer = new byte[minBufferSize];
        while (isRecoding){
            int len = audioRecord.read(buffer, 0, buffer.length);
            if(len <=0){
                continue;
            }

            //立即得到有效输入缓冲区
            //获取输入队列中能够使用的容器的下标
            int index = mediaCodec.dequeueInputBuffer(0);
            if(index >=0){
                ByteBuffer byteBuffer = mediaCodec.getInputBuffer(index);
                byteBuffer.clear();
                //把数据塞入容器
                byteBuffer.put(buffer,0,len);
                //填充数据后再加入队列
                //通知容器我们使用完了，你可以拿去编码了
                mediaCodec.queueInputBuffer(index, 0, len,
                        System.nanoTime() / 1000, 0);

            }

            //获取编码之后的数据
            index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            //每次从编码器取完，再往编码器塞数据
            while (index >=0 && isRecoding){
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if(startTime ==0){
                    startTime = bufferInfo.presentationTimeUs / 1000;
                }

                //送去推流
                rtmpPackage = new RTMPPackage();
                rtmpPackage.setBuffer(outData);
                rtmpPackage.setType(RTMPPackage.RTMP_PACKET_TYPE_AUDIO_DATA);
                long tms = (bufferInfo.presentationTimeUs / 1000) - startTime;
                rtmpPackage.setTms(tms);
                screenLive.addPackage(rtmpPackage);

                //释放输出队列，让其能能存放新数据
                mediaCodec.releaseOutputBuffer(index,false);
                index = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
            }
        }

        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;

        mediaCodec.stop();
        mediaCodec.release();
        mediaCodec = null;
        startTime = 0;
        isRecoding = false;
    }
}
