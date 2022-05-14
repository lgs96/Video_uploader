//
// Created by User on 2021-05-24.
//

#ifndef CLOUDCAMERA_LOGUTIL_H
#define CLOUDCAMERA_LOGUTIL_H

#include <android/log.h>
#include <time.h>

#define LOG_LEVEL 5         //Default Log level 5
#define LOG_DEBUG_PRINT 7   //Print all parameter
#define LOG_DEBUG 5
#define LOG_RELEASE 1

#define LOGV(level, fmt, args...) if (level <= LOG_LEVEL) {__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, "[%s][%s:%d] " fmt, "CloudCamera_Natvie", __func__, __LINE__, ##args);}
#define LOGI(level, fmt, args...) if (level <= LOG_LEVEL) {__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[%s][%s:%d] " fmt, "CloudCamera_Natvie", __func__, __LINE__, ##args);}
#define LOGD(level, fmt, args...) if (level <= LOG_LEVEL) {__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,"[%s][%s:%d] " fmt, "CloudCamera_Natvie", __func__, __LINE__, ##args);}
#define LOGW(level, fmt, args...) if (level <= LOG_LEVEL) {__android_log_print(ANDROID_LOG_WARN, LOG_TAG, "[%s][%s:%d] " fmt, "CloudCamera_Natvie", __func__, __LINE__, ##args);}
#define LOGE(level, fmt, args...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[%s][%s:%d] " fmt, "CloudCamera_Natvie", __func__, __LINE__, ##args);


static void setTimespec(struct timespec *time) {
    clock_gettime(CLOCK_REALTIME, time);
}

static float calcInterval(struct timespec *post) {
    struct timespec curr;
    setTimespec(&curr);

    long interval = (curr.tv_sec - post->tv_sec) * 1000000000L;
    interval += curr.tv_nsec - post->tv_nsec;

    return (float)interval / 1000000;
}

#endif //CLOUDCAMERA_LOGUTIL_H
