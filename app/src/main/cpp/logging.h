#pragma once

#include <android/log.h>

#define USB_POC_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "UsbAudioPoc", __VA_ARGS__)
#define USB_POC_LOGW(...) __android_log_print(ANDROID_LOG_WARN, "UsbAudioPoc", __VA_ARGS__)
#define USB_POC_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "UsbAudioPoc", __VA_ARGS__)
