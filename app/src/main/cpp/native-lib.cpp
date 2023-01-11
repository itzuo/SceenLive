#include <jni.h>
#include <string>
#include "packt.h"
#include "librtmp/rtmp.h"

Live *live = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_zxj_screenlive_ScreenLive_connect(JNIEnv *env, jobject thiz, jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    int ret;
    do{
        live = (Live*)malloc(sizeof(Live));
        memset(live,0, sizeof(Live));
        live->rtmp = RTMP_Alloc();
        RTMP_Init(live->rtmp);
        live->rtmp->Link.timeout = 10;
        LOGI("connect %s", url);
        if (!(ret = RTMP_SetupURL(live->rtmp, (char*)url))) break;
        RTMP_EnableWrite(live->rtmp);
        LOGI("RTMP_Connect");
        if (!(ret = RTMP_Connect(live->rtmp, 0))) break;
        LOGI("RTMP_ConnectStream ");
        if (!(ret = RTMP_ConnectStream(live->rtmp, 0))) break;
        LOGI("connect success");
    }while (0);
    
    if(!ret && live){
        free(live);
        live = nullptr;
    }
    env->ReleaseStringUTFChars(url_,url);
    return ret;
}

int sendPacket(RTMPPacket *packet) {
    int ret = RTMP_SendPacket(live->rtmp, packet, 1);
    RTMPPacket_Free(packet);
    free(packet);
    return ret;
}


int sendVideo(int8_t *buf, int len, long tms) {
    int ret;
    do {
        if (buf[4] == 0x67) {//sps pps
            if (live && (!live->pps || !live->sps)) {
                prepareVideo(buf, len, live);
            }
        } else {
            if (buf[4] == 0x65) {//关键帧
                RTMPPacket *packet = createVideoPackage(live);
                if (!(ret = sendPacket(packet))) {
                    break;
                }
            }

            //将编码之后的数据 按照 flv、rtmp的格式 拼好之后
            RTMPPacket *packet = createVideoPackage(buf, len, tms, live);
            ret = sendPacket(packet);
        }
    }while (0);
    return ret;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_zxj_screenlive_ScreenLive_disConnect(JNIEnv *env, jobject thiz) {
    if(live){
        if(live->sps){
            free(live->sps);
        }
        if(live->pps){
            free(live->pps);
        }
        if(live->rtmp){
            RTMP_Close(live->rtmp);
            RTMP_Free(live->rtmp);
        }
        free(live);
        live = nullptr;
    }
    
}

int sendAudio(int8_t *buf, int len, int type, long tms) {
    int ret;
    RTMPPacket *packet = createAudioPacket(buf, len, type ,tms,  live);
    ret = sendPacket(packet);
    return ret;
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_zxj_screenlive_ScreenLive_sendData(JNIEnv *env, jobject instance, jbyteArray data_, jint len,
                                            jint type, jlong tms) {
    jbyte *data = env->GetByteArrayElements(data_, 0);
    
    int ret;
    switch (type){
        case 0: //video
            ret = sendVideo(data, len, tms);
            LOGI("send Video......");
            break;
        default: //audio
            ret = sendAudio(data, len, type, tms);
            LOGI("send Audio......");
            break;
            
    }
    
    env->ReleaseByteArrayElements(data_,data,0);
    return ret;
}