package com.zxj.screenlive;

public class RTMPPackage {

    public static final int RTMP_PACKET_TYPE_VIDEO = 0;
    public static final int RTMP_PACKET_TYPE_AUDIO_HEAD = 1;
    public static final int RTMP_PACKET_TYPE_AUDIO_DATA = 2;

    private byte[] buffer;
    private int type;
    private long tms;

    public static RTMPPackage EMPTY_PACKAGE = new RTMPPackage();

    public byte[] getBuffer() {
        return buffer;
    }

    public void setBuffer(byte[] buffer) {
        this.buffer = buffer;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTms() {
        return tms;
    }

    public void setTms(long tms) {
        this.tms = tms;
    }
}
