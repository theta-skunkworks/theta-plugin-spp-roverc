LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=SHARED
include C:\opencv\opencv-3.4.11-android-sdk\sdk\native\jni\OpenCV.mk

LOCAL_CFLAGS := -fopenmp -static-openmp
LOCAL_LDFLAGS := -fopenmp -static-openmp
LOCAL_MODULE := rotation_equi
LOCAL_SRC_FILES := rotation_equi.cpp
include $(BUILD_SHARED_LIBRARY)
