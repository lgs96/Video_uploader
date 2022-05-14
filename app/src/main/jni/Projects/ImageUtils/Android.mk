LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/../../include         \
    $(ANDROID_CL_INCLUDE_DIR)

LOCAL_MODULE    := ImageUtils
LOCAL_SRC_FILES +=   \
    ImageUtils.cpp   \

LOCAL_LDLIBS := -llog -ljnigraphics -lz -lm

include $(BUILD_SHARED_LIBRARY)