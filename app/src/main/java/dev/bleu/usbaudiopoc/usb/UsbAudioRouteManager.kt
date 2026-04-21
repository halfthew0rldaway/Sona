package dev.bleu.usbaudiopoc.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

class UsbAudioRouteManager(context: Context) : Closeable {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val permissionMutex = Mutex()
    private var pendingPermissionRequest: CompletableDeferred<Boolean>? = null

    private val permissionIntent: PendingIntent = PendingIntent.getBroadcast(
        appContext,
        1001,
        Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private val _usbState = MutableStateFlow(UsbRouteState())
    val usbState: StateFlow<UsbRouteState> = _usbState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "USB permission result granted=$granted")
                    pendingPermissionRequest?.complete(granted)
                    refreshDevices()
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED
                -> refreshDevices()
            }
        }
    }

    init {
        registerReceiver()
        refreshDevices()
    }

    fun refreshDevices() {
        val compatible = usbManager.deviceList.values.firstOrNull(::isCompatibleUsbAudioDevice)
        val state = if (compatible == null) {
            UsbRouteState()
        } else {
            val permission = usbManager.hasPermission(compatible)
            val productName = compatible.productName ?: "${compatible.vendorId}:${compatible.productId}"
            UsbRouteState(
                compatibleDevice = compatible,
                hasPermission = permission,
                description = "USB route: $productName (${if (permission) "permission granted" else "permission required"})",
            )
        }
        _usbState.value = state
    }

    suspend fun openPlaybackSessionOrNull(): UsbPlaybackSession? {
        val device = usbState.value.compatibleDevice ?: return null
        if (!usbManager.hasPermission(device)) {
            val granted = requestPermission(device)
            if (!granted) {
                Log.w(TAG, "USB permission denied for ${device.deviceName}")
                return null
            }
        }
        val connection = usbManager.openDevice(device) ?: return null
        Log.i(TAG, "Opened UsbDeviceConnection for ${device.deviceName}")
        val rawDescriptors = connection.rawDescriptors ?: ByteArray(0)
        return UsbPlaybackSession(
            device = device,
            connection = connection,
            fileDescriptor = connection.fileDescriptor,
            rawDescriptors = rawDescriptors,
        )
    }

    override fun close() {
        runCatching { appContext.unregisterReceiver(receiver) }
        pendingPermissionRequest?.cancel()
        pendingPermissionRequest = null
    }

    private suspend fun requestPermission(device: UsbDevice): Boolean = permissionMutex.withLock {
        if (usbManager.hasPermission(device)) {
            return@withLock true
        }
        val deferred = CompletableDeferred<Boolean>()
        pendingPermissionRequest = deferred
        Log.i(TAG, "Requesting USB permission for ${device.deviceName}")
        usbManager.requestPermission(device, permissionIntent)
        try {
            deferred.await()
        } finally {
            pendingPermissionRequest = null
        }
    }

    private fun isCompatibleUsbAudioDevice(device: UsbDevice): Boolean {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            val isAudioStreaming = usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                usbInterface.interfaceSubclass == AUDIO_SUBCLASS_STREAMING
            if (!isAudioStreaming) {
                continue
            }
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.direction == UsbConstants.USB_DIR_OUT &&
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }

    private companion object {
        const val TAG = "UsbAudioRouteManager"
        const val AUDIO_SUBCLASS_STREAMING = 0x02
        const val ACTION_USB_PERMISSION = "dev.bleu.usbaudiopoc.USB_PERMISSION"
    }
}
