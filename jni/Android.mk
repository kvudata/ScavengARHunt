LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_CAMERA_MODULES := off
OPENCV_PACKAGE_DIR := /home/kvudata/OpenCV_Android/OpenCV-2.3.1
include $(OPENCV_PACKAGE_DIR)/share/OpenCV/OpenCV.mk

LOCAL_MODULE    := mixed_sample
LOCAL_SRC_FILES := jni_part.cpp
LOCAL_LDLIBS += -llog
#LOCAL_CFLAGS=-ffast-math -O3 -funroll-loops
include $(BUILD_SHARED_LIBRARY)
