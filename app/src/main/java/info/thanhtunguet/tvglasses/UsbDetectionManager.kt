package info.thanhtunguet.tvglasses

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer

/**
 * Manager for USB detection that can be easily integrated into any activity.
 * Automatically handles registration/unregistration of USB connection listeners
 * and provides callbacks for USB connection state changes.
 */
class UsbDetectionManager(
    private val context: Context,
    private val onUsbStateChanged: (isConnected: Boolean) -> Unit = {},
    private val onUsbConnected: () -> Unit = {},
    private val onUsbDisconnected: () -> Unit = {},
    private val autoNavigateToVideo: Boolean = false
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "UsbDetectionManager"
        private var globalReceiver: UsbConnectionReceiver? = null
        private var referenceCount = 0
        
        /**
         * Create a USB detection manager for an activity that automatically
         * navigates to VideoActivity when USB is connected.
         */
        fun createWithAutoNavigation(
            context: Context,
            onUsbStateChanged: (isConnected: Boolean) -> Unit = {}
        ): UsbDetectionManager {
            return UsbDetectionManager(
                context = context,
                onUsbStateChanged = onUsbStateChanged,
                onUsbConnected = {
                    onUsbStateChanged(true)
                    navigateToVideoActivity(context)
                },
                autoNavigateToVideo = true
            )
        }
        
        /**
         * Create a USB detection manager for VideoActivity that doesn't navigate
         * but can trigger video list updates.
         */
        fun createForVideoActivity(
            context: Context,
            onUsbConnected: () -> Unit = {},
            onUsbDisconnected: () -> Unit = {},
            onUsbStateChanged: (isConnected: Boolean) -> Unit = {}
        ): UsbDetectionManager {
            return UsbDetectionManager(
                context = context,
                onUsbStateChanged = onUsbStateChanged,
                onUsbConnected = onUsbConnected,
                onUsbDisconnected = onUsbDisconnected,
                autoNavigateToVideo = false
            )
        }
        
        private fun navigateToVideoActivity(context: Context) {
            try {
                val intent = Intent(context, VideoActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                Log.d(TAG, "Navigated to VideoActivity due to USB connection")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to navigate to VideoActivity", e)
            }
        }
    }
    
    private var isObserving = false
    private val usbStateObserver = Observer<UsbConnectionState> { state ->
        Log.d(TAG, "USB state changed: ${if (state.isConnected) "Connected" else "Disconnected"}")
        
        onUsbStateChanged(state.isConnected)
        
        if (state.isConnected) {
            onUsbConnected()
        } else {
            onUsbDisconnected()
        }
    }
    
    override fun onCreate(owner: LifecycleOwner) {
        startObserving()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        stopObserving()
    }
    
    private fun startObserving() {
        if (isObserving) return
        
        synchronized(UsbDetectionManager::class.java) {
            if (globalReceiver == null) {
                Log.d(TAG, "Registering global USB receiver")
                globalReceiver = UsbConnectionReceiver.register(context.applicationContext)
            }
            referenceCount++
        }
        
        UsbConnectionReceiver.usbConnectionState.observeForever(usbStateObserver)
        isObserving = true
        
        Log.d(TAG, "Started USB detection (ref count: $referenceCount)")
    }
    
    private fun stopObserving() {
        if (!isObserving) return
        
        UsbConnectionReceiver.usbConnectionState.removeObserver(usbStateObserver)
        isObserving = false
        
        synchronized(UsbDetectionManager::class.java) {
            referenceCount--
            if (referenceCount <= 0) {
                globalReceiver?.let { receiver ->
                    Log.d(TAG, "Unregistering global USB receiver")
                    UsbConnectionReceiver.unregister(context.applicationContext, receiver)
                    globalReceiver = null
                }
                referenceCount = 0
            }
        }
        
        Log.d(TAG, "Stopped USB detection (ref count: $referenceCount)")
    }
    
    /**
     * Manually start observing USB connections (if not using lifecycle)
     */
    fun start() {
        startObserving()
    }
    
    /**
     * Manually stop observing USB connections (if not using lifecycle)
     */
    fun stop() {
        stopObserving()
    }
    
    /**
     * Get current USB connection state
     */
    fun getCurrentUsbState(): Boolean {
        return UsbConnectionReceiver.usbConnectionState.value?.isConnected ?: false
    }
}