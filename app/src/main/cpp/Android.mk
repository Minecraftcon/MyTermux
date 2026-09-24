LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := termux-socket-bridge
LOCAL_MODULE_FILENAME := libtermux-socket-bridge
LOCAL_MODULE_SUFFIX := .so
LOCAL_SRC_FILES := bridge/termux-socket-bridge.c
LOCAL_LDFLAGS := -pie
include $(BUILD_EXECUTABLE)

