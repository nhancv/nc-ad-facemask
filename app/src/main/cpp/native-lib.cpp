//
// Created by iNhan Cao on 2019-06-18.
//

#include <jni.h>
#include <string>
#include <stddef.h>

#include <opencv2/opencv.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <android/bitmap.h>
#include <android/log.h>

#define  LOG_TAG    "native-lib"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define log_print __android_log_print

using namespace cv;

extern "C" JNIEXPORT void JNICALL
Java_com_nhancv_facemask_tracking_PointState_init(JNIEnv *env, jobject /* this */) {
}
