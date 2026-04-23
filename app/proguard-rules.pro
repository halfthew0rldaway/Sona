# Keep native methods and their enclosing classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the UsbNativeBridge explicitly just in case for JNI resolution
-keep class dev.bleu.usbaudiopoc.usb.UsbNativeBridge { *; }

# General safety rules for Android
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
