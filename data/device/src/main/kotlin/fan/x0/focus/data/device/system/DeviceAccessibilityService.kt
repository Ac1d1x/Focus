package fan.x0.focus.data.device.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.InputMethod
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import fan.x0.focus.data.device.DeviceEvents

class DeviceAccessibilityService : AccessibilityService() {
    val indicator: AutomationIndicator by lazy { AutomationIndicator(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        var flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            flags = flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
        }
        info.flags = flags
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 80
        runCatching { setServiceInfo(info) }
        current = this
    }

    override fun onCreateInputMethod(): InputMethod {
        val method = InputMethod(this)
        inputMethodInstance = method
        return method
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString().orEmpty()
        if (eventPackage == packageName) return
        lastEventAt = System.currentTimeMillis()
        lastPackage = eventPackage
        lastClass = event.className?.toString().orEmpty()
        val text = event.text.joinToString(" ")
        DeviceEvents.record(
            type = AccessibilityEvent.eventTypeToString(event.eventType),
            packageName = eventPackage,
            className = event.className?.toString().orEmpty(),
            text = text.ifBlank { event.contentDescription?.toString().orEmpty() },
        )
    }

    override fun onInterrupt() {
        indicator.dismiss()
    }

    override fun onDestroy() {
        indicator.dismiss()
        if (current === this) current = null
        inputMethodInstance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var current: DeviceAccessibilityService? = null
            private set

        @Volatile
        var inputMethodInstance: InputMethod? = null
            private set

        @Volatile
        var lastEventAt: Long = 0
            private set

        @Volatile
        var lastPackage: String = ""
            private set

        @Volatile
        var lastClass: String = ""
            private set

        fun ready(): Boolean = current != null
    }
}
