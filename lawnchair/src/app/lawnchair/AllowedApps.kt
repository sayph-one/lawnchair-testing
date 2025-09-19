package app.lawnchair.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

object AllowedApps {
    private val allowedBasePackages = listOf(
        "com.simplemobiletools.dialer",
        "com.simplemobiletools.smsmessenger",
        "com.sec.android.gallery3d",
        "com.sec.android.app.camera",
        "com.sec.factory.camera",
        "com.samsung.android.da.daagent",
        "com.sec.android.app.clockpackage.ClockPackage",
        "com.sec.android.app.popupcalculator",
        "com.sec.android.app.clockpackage",
        "com.sec.android.daemonapp"
    )

    private const val SAYPH_AGENT_PACKAGE = "com.sayph.sayphagent"

    // Registration status change listeners
    private val registrationListeners = mutableSetOf<() -> Unit>()

    // Cache for registration status when context isn't available
    private var lastRegistrationCheck = 0L
    private var cachedRegistrationStatus = false
    private const val CACHE_DURATION_MS = 2000L

    /**
     * Check if an app is allowed to be shown (with context for registration check)
     */
    fun isAllowed(packageName: String, context: Context): Boolean {
        // Always allow Sayph Agent
        if (packageName == SAYPH_AGENT_PACKAGE) return true

        // If device not registered, hide all other apps
        if (!SayphRegistrationChecker.isDeviceRegistered(context)) return false

        // If registered, check if it's in the allowed list
        return isInAllowedList(packageName)
    }

    /**
     * Check if an app is allowed to be shown (backward compatibility method)
     * This version uses a cached registration status to avoid requiring context
     */
    fun isAllowed(packageName: String): Boolean {

        // Check registration status
        val isRegistered = isDeviceRegisteredCached()
        android.util.Log.v("AllowedApps", "Package $packageName - isRegistered: $isRegistered")

        // If device not registered, hide all other apps
        if (!isRegistered) {
            android.util.Log.v("AllowedApps", "Package $packageName - blocked due to no registration")
            return false
        }

        // If registered, check if it's in the allowed list
        val inAllowedList = isInAllowedList(packageName)
        android.util.Log.v("AllowedApps", "Package $packageName - in allowed list: $inAllowedList")
        return inAllowedList
    }

    private fun isInAllowedList(packageName: String): Boolean {
        return allowedBasePackages.any { base ->
            packageName == base ||
                (packageName.startsWith("$base.") &&
                    packageName.substring(base.length + 1).matches(Regex("^[a-zA-Z0-9._-]+$")))
        }
    }

    /**
     * Get cached registration status without requiring context
     */
    private fun isDeviceRegisteredCached(): Boolean {
        val now = System.currentTimeMillis()
        // If cache is stale, return false (assume not registered for safety)
        if (now - lastRegistrationCheck > CACHE_DURATION_MS) {
            return false
        }
        return cachedRegistrationStatus
    }

    /**
     * Update the cached registration status (called from launcher or when status changes)
     */
    fun updateRegistrationCache(context: Context) {
        cachedRegistrationStatus = SayphRegistrationChecker.isDeviceRegistered(context)
        lastRegistrationCheck = System.currentTimeMillis()
    }

    fun getAllowedPackages(): List<String> = allowedBasePackages

    /**
     * Check if device needs registration (for showing overlay)
     */
    fun needsRegistration(context: Context): Boolean {
        val isRegistered = SayphRegistrationChecker.isDeviceRegistered(context)
        val needsReg = !isRegistered
        android.util.Log.d("AllowedApps", "needsRegistration() - isRegistered: $isRegistered, needsRegistration: $needsReg")
        return needsReg
    }

    /**
     * Add listener for registration status changes
     */
    fun addRegistrationListener(listener: () -> Unit) {
        registrationListeners.add(listener)
    }

    /**
     * Remove listener for registration status changes
     */
    fun removeRegistrationListener(listener: () -> Unit) {
        registrationListeners.remove(listener)
    }

    /**
     * Notify all listeners of registration status change
     */
    private fun notifyRegistrationChanged() {
        registrationListeners.forEach { it.invoke() }
    }

    /**
     * Force refresh registration status and notify listeners
     */
    fun refreshRegistrationStatus(context: Context) {
        SayphRegistrationChecker.forceRefresh()
        updateRegistrationCache(context)
        notifyRegistrationChanged()
    }

    /**
     * Broadcast receiver to listen for registration status changes
     */
    class RegistrationStatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.sayph.REGISTRATION_STATUS_CHANGED") {
                // Force refresh and notify listeners
                refreshRegistrationStatus(context)
            }
        }
    }

    /**
     * Register broadcast receiver for registration status changes
     */
    fun registerStatusReceiver(context: Context): RegistrationStatusReceiver {
        val receiver = RegistrationStatusReceiver()
        val filter = IntentFilter("com.sayph.REGISTRATION_STATUS_CHANGED")

        // Use RECEIVER_NOT_EXPORTED for Android 13+ compatibility
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        return receiver
    }
}
