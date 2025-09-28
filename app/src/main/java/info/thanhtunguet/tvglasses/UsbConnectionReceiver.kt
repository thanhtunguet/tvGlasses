package info.thanhtunguet.tvglasses

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.storage.StorageManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class UsbConnectionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "UsbConnectionReceiver"
        private val _usbConnectionState = MutableLiveData<UsbConnectionState>()
        val usbConnectionState: LiveData<UsbConnectionState> = _usbConnectionState
        
        fun register(context: Context): UsbConnectionReceiver {
            val receiver = UsbConnectionReceiver()
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addDataScheme("file")
            }
            context.registerReceiver(receiver, filter)
            
            // Check initial state
            receiver.checkInitialUsbState(context)
            
            return receiver
        }
        
        fun unregister(context: Context, receiver: UsbConnectionReceiver) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Log.d(TAG, "USB device attached")
                checkUsbStorageState(context)
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.d(TAG, "USB device detached")
                checkUsbStorageState(context)
            }
            Intent.ACTION_MEDIA_MOUNTED -> {
                val path = intent.data?.path
                Log.d(TAG, "Media mounted at: $path")
                if (path != null && isUsbPath(path)) {
                    checkUsbStorageState(context)
                }
            }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT -> {
                val path = intent.data?.path
                Log.d(TAG, "Media unmounted/ejected at: $path")
                if (path != null && isUsbPath(path)) {
                    checkUsbStorageState(context)
                }
            }
        }
    }
    
    private fun checkInitialUsbState(context: Context) {
        checkUsbStorageState(context)
    }
    
    private fun checkUsbStorageState(context: Context) {
        try {
            val usbHelper = UsbStorageHelper(context)
            
            // Use a separate thread to avoid blocking the main thread
            Thread {
                try {
                    val hasUsb = kotlinx.coroutines.runBlocking {
                        usbHelper.hasUsbStorageConnected()
                    }
                    
                    val previousState = _usbConnectionState.value
                    val newState = if (hasUsb) {
                        UsbConnectionState.Connected(System.currentTimeMillis())
                    } else {
                        UsbConnectionState.Disconnected(System.currentTimeMillis())
                    }
                    
                    // Only update if state actually changed
                    if (previousState?.isConnected != newState.isConnected) {
                        Log.d(TAG, "USB state changed: ${if (hasUsb) "Connected" else "Disconnected"}")
                        _usbConnectionState.postValue(newState)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking USB storage state", e)
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkUsbStorageState", e)
        }
    }
    
    private fun isUsbPath(path: String): Boolean {
        val lowerPath = path.lowercase()
        val usbKeywords = listOf("/usb", "/mnt/usb", "/storage/usb", "/mnt/media_rw/usb", "/storage/usbotg")
        return usbKeywords.any { lowerPath.contains(it) }
    }
}

sealed class UsbConnectionState(val timestamp: Long) {
    abstract val isConnected: Boolean
    
    data class Connected(private val time: Long) : UsbConnectionState(time) {
        override val isConnected = true
    }
    
    data class Disconnected(private val time: Long) : UsbConnectionState(time) {
        override val isConnected = false
    }
}