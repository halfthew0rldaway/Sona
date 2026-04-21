#include "usb_audio_engine.h"

#include "logging.h"

#include <jni.h>
#include <unistd.h>

#include <memory>
#include <vector>

namespace {
UsbAudioEngine* FromHandle(jlong handle) {
    return reinterpret_cast<UsbAudioEngine*>(handle);
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_bleu_usbaudiopoc_usb_UsbNativeBridge_nativeInitUsb(
    JNIEnv* env,
    jobject /* thiz */,
    jint device_fd,
    jbyteArray raw_descriptors,
    jint vendor_id,
    jint product_id) {
    const auto length = static_cast<std::size_t>(env->GetArrayLength(raw_descriptors));
    std::vector<uint8_t> descriptors(length, 0U);
    env->GetByteArrayRegion(
        raw_descriptors,
        0,
        static_cast<jsize>(length),
        reinterpret_cast<jbyte*>(descriptors.data()));

    const int duplicated_fd = dup(device_fd);
    if (duplicated_fd < 0) {
        USB_POC_LOGE("dup(device_fd) failed");
        return 0;
    }

    auto engine = std::make_unique<UsbAudioEngine>(
        duplicated_fd,
        std::move(descriptors),
        vendor_id,
        product_id);
    if (!engine->Initialize()) {
        engine->Release();
        return 0;
    }
    return reinterpret_cast<jlong>(engine.release());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_bleu_usbaudiopoc_usb_UsbNativeBridge_nativeStartStream(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong native_handle,
    jint sample_rate,
    jint bit_depth,
    jint channels) {
    auto* engine = FromHandle(native_handle);
    if (engine == nullptr) {
        return JNI_FALSE;
    }
    return engine->StartStream(sample_rate, bit_depth, channels) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_bleu_usbaudiopoc_usb_UsbNativeBridge_nativeWritePcm(
    JNIEnv* env,
    jobject /* thiz */,
    jlong native_handle,
    jobject buffer,
    jint size) {
    auto* engine = FromHandle(native_handle);
    if (engine == nullptr) {
        return -1;
    }
    auto* bytes = reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (bytes == nullptr) {
        USB_POC_LOGE("GetDirectBufferAddress returned null");
        return -1;
    }
    return engine->WritePcm(bytes, static_cast<std::size_t>(size));
}

extern "C" JNIEXPORT void JNICALL
Java_dev_bleu_usbaudiopoc_usb_UsbNativeBridge_nativeStopStream(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong native_handle) {
    auto* engine = FromHandle(native_handle);
    if (engine != nullptr) {
        engine->StopStream();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_bleu_usbaudiopoc_usb_UsbNativeBridge_nativeRelease(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong native_handle) {
    std::unique_ptr<UsbAudioEngine> engine(FromHandle(native_handle));
    if (engine != nullptr) {
        engine->Release();
    }
}
