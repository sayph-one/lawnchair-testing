package app.lawnchair.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.Collections

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

    // Registration status change listeners - now thread-safe
    private val registrationListeners = Collections.synchronizedSet(mutableSetOf<() -> Unit>())

    // Enhanced cache with thread safety
    private val cacheLock = ReentrantReadWriteLock()
    @Volatile
    private var lastRegistrationCheck = 0L
    @Volatile
    private var cachedRegistrationStatus = false
    private const val CACHE_DURATION_MS = 5000L // Increased from 2000L for more stability

    /**
     * Check if an app is allowed to be shown (with context for registration check)
     * This is the primary method - use this when context is available
     */
    fun isAllowed(packageName: String, context: Context): Boolean {
        // Never show Sayph Agent in app list
        if (packageName == SAYPH_AGENT_PACKAGE) return false

        // Get fresh registration status and update cache
        val isRegistered = SayphRegistrationChecker.isDeviceRegistered(context)
        updateRegistrationCacheInternal(isRegistered)

        // If device not registered, hide all other apps
        if (!isRegistered) return false

        // If registered, check if it's in the allowed list
        return isInAllowedList(packageName)
    }

    /**
     * Check if an app is allowed to be shown (backward compatibility method)
     * This version uses a cached registration status to avoid requiring context
     */
    fun isAllowed(packageName: String): Boolean {
        // Never show Sayph Agent in app list
        if (packageName == SAYPH_AGENT_PACKAGE) return false

        // Check registration status from cache
        val isRegistered = getRegistrationStatusFromCache()
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

    /**
     * Thread-safe method to get registration status from cache
     */
    private fun getRegistrationStatusFromCache(): Boolean {
        cacheLock.readLock().lock()
        try {
            val now = System.currentTimeMillis()
            return if (now - lastRegistrationCheck <= CACHE_DURATION_MS) {
                cachedRegistrationStatus
            } else {
                // Cache is stale - assume not registered for safety
                android.util.Log.d("AllowedApps", "Registration cache is stale, returning false")
                false
            }
        } finally {
            cacheLock.readLock().unlock()
        }
    }

    /**
     * Thread-safe method to update registration cache
     */
    private fun updateRegistrationCacheInternal(isRegistered: Boolean) {
        cacheLock.writeLock().lock()
        try {
            val previousStatus = cachedRegistrationStatus
            cachedRegistrationStatus = isRegistered
            lastRegistrationCheck = System.currentTimeMillis()

            if (previousStatus != isRegistered) {
                android.util.Log.d("AllowedApps", "Registration status changed: $previousStatus -> $isRegistered")
            }
        } finally {
            cacheLock.writeLock().unlock()
        }
    }

    /**
     * Check if package is in the allowed list (ignoring registration status)
     */
    fun isInAllowedList(packageName: String): Boolean {
        // Sayph Agent should never be visible in app lists
        if (packageName == SAYPH_AGENT_PACKAGE) return false

        return allowedBasePackages.any { base ->
            packageName == base ||
                (packageName.startsWith("$base.") &&
                    packageName.substring(base.length + 1).matches(Regex("^[a-zA-Z0-9._-]+$")))
        }
    }

    /**
     * Update the cached registration status (called from launcher or when status changes)
     */
    fun updateRegistrationCache(context: Context) {
        val isRegistered = SayphRegistrationChecker.isDeviceRegistered(context)
        updateRegistrationCacheInternal(isRegistered)
        android.util.Log.d("AllowedApps", "Registration cache updated: isRegistered=$isRegistered")
    }

    /**
     * Get all allowed package names for bulk operations
     */
    fun getAllowedPackages(): List<String> = allowedBasePackages

    /**
     * Get all allowed package names including Sayph Agent
     */
    fun getAllowedPackageNames(): Set<String> {
        return allowedBasePackages.toSet() + SAYPH_AGENT_PACKAGE
    }

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
        android.util.Log.d("AllowedApps", "Registration listener added. Total listeners: ${registrationListeners.size}")
    }

    /**
     * Remove listener for registration status changes
     */
    fun removeRegistrationListener(listener: () -> Unit) {
        registrationListeners.remove(listener)
        android.util.Log.d("AllowedApps", "Registration listener removed. Total listeners: ${registrationListeners.size}")
    }

    /**
     * Notify all listeners of registration status change
     */
    private fun notifyRegistrationChanged() {
        android.util.Log.d("AllowedApps", "Notifying ${registrationListeners.size} registration listeners")
        registrationListeners.forEach {
            try {
                it.invoke()
            } catch (e: Exception) {
                android.util.Log.w("AllowedApps", "Error invoking registration listener", e)
            }
        }
    }

    /**
     * Force refresh registration status and notify listeners
     */
    fun refreshRegistrationStatus(context: Context) {
        android.util.Log.d("AllowedApps", "Force refreshing registration status")
        SayphRegistrationChecker.forceRefresh()
        updateRegistrationCache(context)
        notifyRegistrationChanged()
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStatus(): String {
        cacheLock.readLock().lock()
        try {
            val now = System.currentTimeMillis()
            val ageMs = now - lastRegistrationCheck
            val isValid = ageMs <= CACHE_DURATION_MS
            return "Cache: valid=$isValid, age=${ageMs}ms, status=$cachedRegistrationStatus"
        } finally {
            cacheLock.readLock().unlock()
        }
    }

    /**
     * Broadcast receiver to listen for registration status changes
     */
    class RegistrationStatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.sayph.REGISTRATION_STATUS_CHANGED") {
                android.util.Log.d("AllowedApps", "Received registration status change broadcast")
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

        android.util.Log.d("AllowedApps", "Registration status receiver registered")
        return receiver
    }
}
