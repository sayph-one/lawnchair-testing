package app.lawnchair.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.Collections
import app.lawnchair.util.SayphDowntimeChecker

object AllowedApps {
    private val allowedBasePackages = listOf(
        "com.simplemobiletools.dialer",
        "com.simplemobiletools.dialer.debug",
        "com.simplemobiletools.smsmessenger",
        "com.simplemobiletools.smsmessenger.debug",
        "com.sayph.messenger",
        "com.sayph.messenger.debug",
        "com.sayph.phone",
        "com.sayph.phone.debug",
        "com.sayph.notes",
        "com.sayph.notes.debug",
        "app.organicmaps",
        "app.organicmaps.debug",
        "one.sayph.music",
        "one.sayph.music.debug",
        "one.sayph.calculator",
        "one.sayph.calculator.debug",
        "one.sayph.clock",
        "one.sayph.clock.debug",
        "one.sayph.weather",
        "one.sayph.weather.debug",
        "one.sayph.gallery",
        "one.sayph.gallery.debug",
        "com.sayph.cam",
        "com.sayph.cam.debug",
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

    // Downtime status change listeners
    private val downtimeListeners = Collections.synchronizedSet(mutableSetOf<() -> Unit>())

    // Permissions status change listeners
    private val permissionsListeners = Collections.synchronizedSet(mutableSetOf<() -> Unit>())

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

        // If device is in downtime, hide all apps
        if (SayphDowntimeChecker.isInDowntime(context)) return false

        // If registered, check if it's in the allowed list
        return isInAllowedList(packageName)
    }

    /**
     * Check if an app is allowed to be shown (backward compatibility method)
     * This version uses a cached registration status to avoid requiring context
     */
    fun isAllowed(packageName: String): Boolean {
        // Debug for messaging app (will catch .debug variant too)
        if (packageName.contains("smsmessenger")) {
            android.util.Log.e("AllowedApps", "=== MESSAGING APP DEBUG ===")
            android.util.Log.e("AllowedApps", "Package: $packageName")
            android.util.Log.e("AllowedApps", "Is Sayph Agent? ${packageName == SAYPH_AGENT_PACKAGE}")

            val isRegistered = getRegistrationStatusFromCache()
            android.util.Log.e("AllowedApps", "Is Registered (cached): $isRegistered")

            val inList = isInAllowedList(packageName)
            android.util.Log.e("AllowedApps", "Is in allowed list: $inList")

            android.util.Log.e("AllowedApps", "Checking against base packages:")
            allowedBasePackages.forEachIndexed { index, base ->
                val exactMatch = packageName == base
                val subMatch = packageName.startsWith("$base.") &&
                    packageName.substring(base.length + 1).matches(Regex("^[a-zA-Z0-9._-]+$"))
                android.util.Log.e("AllowedApps", "  [$index] $base - exact: $exactMatch, sub: $subMatch")
            }

            android.util.Log.e("AllowedApps", "Final result: ${isRegistered && inList}")
            android.util.Log.e("AllowedApps", "=========================")
        }

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
     * KEY FIX: Added parameter to control whether to notify listeners
     */
    private fun updateRegistrationCacheInternal(
        isRegistered: Boolean,
        notifyListeners: Boolean = false
    ) {
        cacheLock.writeLock().lock()
        val previousStatus: Boolean
        val statusChanged: Boolean
        try {
            previousStatus = cachedRegistrationStatus
            cachedRegistrationStatus = isRegistered
            lastRegistrationCheck = System.currentTimeMillis()
            statusChanged = previousStatus != isRegistered

            if (statusChanged) {
                android.util.Log.d(
                    "AllowedApps",
                    "Registration status changed: $previousStatus -> $isRegistered"
                )
            }
        } finally {
            cacheLock.writeLock().unlock()
        }

        // Notify listeners AFTER releasing the lock and only if requested
        if (notifyListeners && statusChanged) {
            notifyRegistrationChanged()
        }
    }

    /**
     * Check if package is in the allowed list (ignoring registration status)
     */
    fun isInAllowedList(packageName: String): Boolean {
        // Sayph Agent should never be visible in app lists
        if (packageName == SAYPH_AGENT_PACKAGE) return false

        // Hide apps the parent has turned off via the agent (e.g. music, camera).
        if (SayphAppPolicy.isDisabled(packageName)) return false

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
        updateRegistrationCacheInternal(isRegistered, notifyListeners = false)
        android.util.Log.d("AllowedApps", "Registration cache updated: isRegistered=$isRegistered")
    }

    /**
     * Get all allowed package names for bulk operations
     */
    fun getAllowedPackages(): List<String> = allowedBasePackages

    /** Set cached registration status for unit tests */
    @androidx.annotation.VisibleForTesting
    fun setCachedRegistrationStatus(registered: Boolean) {
        cachedRegistrationStatus = registered
        lastRegistrationCheck = System.currentTimeMillis()
    }

    /** Check if allowed using cached status, without Android context. For unit tests. */
    @androidx.annotation.VisibleForTesting
    fun isAllowedCached(packageName: String): Boolean {
        if (packageName == SAYPH_AGENT_PACKAGE) return false
        if (!cachedRegistrationStatus) return false
        return isInAllowedList(packageName)
    }

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
        android.util.Log.d(
            "AllowedApps",
            "needsRegistration() - isRegistered: $isRegistered, needsRegistration: $needsReg"
        )
        return needsReg
    }

    /**
     * Add listener for registration status changes
     */
    fun addRegistrationListener(listener: () -> Unit) {
        registrationListeners.add(listener)
        android.util.Log.d(
            "AllowedApps",
            "Registration listener added. Total listeners: ${registrationListeners.size}"
        )
    }

    /**
     * Remove listener for registration status changes
     */
    fun removeRegistrationListener(listener: () -> Unit) {
        registrationListeners.remove(listener)
        android.util.Log.d(
            "AllowedApps",
            "Registration listener removed. Total listeners: ${registrationListeners.size}"
        )
    }

    /**
     * Notify all listeners of registration status change
     */
    private fun notifyRegistrationChanged() {
        android.util.Log.d(
            "AllowedApps",
            "Notifying ${registrationListeners.size} registration listeners"
        )
        registrationListeners.forEach {
            try {
                it.invoke()
            } catch (e: Exception) {
                android.util.Log.w("AllowedApps", "Error invoking registration listener", e)
            }
        }
    }

    /**
     * Force refresh registration status and notify listeners ONLY if status changed
     */
    fun refreshRegistrationStatus(context: Context) {
        android.util.Log.d("AllowedApps", "=== Checking registration status ===")
        SayphRegistrationChecker.forceRefresh()

        val isRegistered = SayphRegistrationChecker.isDeviceRegistered(context)
        val previousStatus: Boolean?

        // Get previous status before updating
        cacheLock.readLock().lock()
        try {
            previousStatus = if (lastRegistrationCheck > 0) cachedRegistrationStatus else null
        } finally {
            cacheLock.readLock().unlock()
        }

        android.util.Log.d("AllowedApps", "Previous: $previousStatus, Current: $isRegistered")

        // Update cache WITHOUT notifying
        updateRegistrationCacheInternal(isRegistered, notifyListeners = false)

        // Only notify if status actually changed
        if (previousStatus != null && previousStatus != isRegistered) {
            android.util.Log.e("AllowedApps", "!!! REGISTRATION STATUS CHANGED: $previousStatus -> $isRegistered !!!")
            notifyRegistrationChanged()
        } else if (previousStatus == null) {
            android.util.Log.d("AllowedApps", "Initial registration status set: $isRegistered (no notification)")
        } else {
            android.util.Log.d("AllowedApps", "Registration status unchanged: $isRegistered (no notification)")
        }
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

    // --- Downtime methods ---

    fun isInDowntime(context: Context): Boolean {
        return SayphDowntimeChecker.isInDowntime(context)
    }

    fun needsDowntimeOverlay(context: Context): Boolean {
        return SayphDowntimeChecker.isInDowntime(context)
    }

    fun addDowntimeListener(listener: () -> Unit) {
        downtimeListeners.add(listener)
    }

    fun removeDowntimeListener(listener: () -> Unit) {
        downtimeListeners.remove(listener)
    }

    private fun notifyDowntimeChanged() {
        downtimeListeners.forEach {
            try { it.invoke() } catch (e: Exception) {
                android.util.Log.w("AllowedApps", "Error invoking downtime listener", e)
            }
        }
    }

    fun refreshDowntimeStatus(context: Context) {
        SayphDowntimeChecker.forceRefresh()
        notifyDowntimeChanged()
    }

    /**
     * Broadcast receiver to listen for downtime status changes
     */
    class DowntimeStatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.sayph.DOWNTIME_STATE_CHANGED") {
                android.util.Log.d("AllowedApps", "Received downtime status change broadcast")
                refreshDowntimeStatus(context)
            }
        }
    }

    fun registerDowntimeReceiver(context: Context): DowntimeStatusReceiver {
        val receiver = DowntimeStatusReceiver()
        val filter = IntentFilter("com.sayph.DOWNTIME_STATE_CHANGED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        android.util.Log.d("AllowedApps", "Downtime status receiver registered")
        return receiver
    }

    // ===== Permissions status =====

    fun addPermissionsListener(listener: () -> Unit) {
        permissionsListeners.add(listener)
        android.util.Log.d("AllowedApps", "Permissions listener added. Total: ${permissionsListeners.size}")
    }

    fun removePermissionsListener(listener: () -> Unit) {
        permissionsListeners.remove(listener)
    }

    private fun notifyPermissionsChanged() {
        permissionsListeners.forEach {
            try { it.invoke() } catch (e: Exception) {
                android.util.Log.w("AllowedApps", "Error invoking permissions listener", e)
            }
        }
    }

    fun refreshPermissionsStatus(context: Context) {
        SayphPermissionsChecker.forceRefresh()
        notifyPermissionsChanged()
    }

    /**
     * Broadcast receiver for `com.sayph.PERMISSIONS_STATE_CHANGED` emitted by the Agent
     * whenever the set of missing required permissions changes. Must be registered with
     * RECEIVER_EXPORTED on Android 13+ because the broadcast comes from a different package.
     */
    class PermissionsStatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.sayph.PERMISSIONS_STATE_CHANGED") {
                android.util.Log.d("AllowedApps", "Received permissions status change broadcast")
                refreshPermissionsStatus(context)
            }
        }
    }

    fun registerPermissionsReceiver(context: Context): PermissionsStatusReceiver {
        val receiver = PermissionsStatusReceiver()
        val filter = IntentFilter("com.sayph.PERMISSIONS_STATE_CHANGED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // The Agent sends an explicit per-package broadcast — the receiver must be
            // exported to accept broadcasts from another package on API 33+.
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        android.util.Log.d("AllowedApps", "Permissions status receiver registered")
        return receiver
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

    // Add these methods to AllowedApps object for better debugging

    /**
     * Enhanced isAllowed with detailed logging for debugging
     */
    fun isAllowedDebug(packageName: String, context: Context? = null): Boolean {
        val timestamp = System.currentTimeMillis()
        val threadName = Thread.currentThread().name

        android.util.Log.d("AllowedApps", "=== isAllowed called ===")
        android.util.Log.d("AllowedApps", "Package: $packageName")
        android.util.Log.d("AllowedApps", "Thread: $threadName")
        android.util.Log.d("AllowedApps", "Timestamp: $timestamp")
        android.util.Log.d("AllowedApps", "Context provided: ${context != null}")

        // Never show Sayph Agent
        if (packageName == SAYPH_AGENT_PACKAGE) {
            android.util.Log.d("AllowedApps", "Result: FALSE (Sayph Agent)")
            return false
        }

        // Get registration status
        val isRegistered = if (context != null) {
            android.util.Log.d("AllowedApps", "Checking with context (fresh)")
            SayphRegistrationChecker.isDeviceRegistered(context)
        } else {
            android.util.Log.d("AllowedApps", "Checking from cache")
            getRegistrationStatusFromCache()
        }

        android.util.Log.d("AllowedApps", "Registration status: $isRegistered")
        android.util.Log.d("AllowedApps", "Cache status: ${getCacheStatus()}")

        if (!isRegistered) {
            android.util.Log.d("AllowedApps", "Result: FALSE (not registered)")
            return false
        }

        val inAllowedList = isInAllowedList(packageName)
        android.util.Log.d("AllowedApps", "In allowed list: $inAllowedList")
        android.util.Log.d("AllowedApps", "Result: $inAllowedList")

        return inAllowedList
    }

    /**
     * Track when and from where registration checks happen
     */
    private val checkHistory = Collections.synchronizedList(mutableListOf<CheckRecord>())
    private const val MAX_HISTORY = 50

    data class CheckRecord(
        val timestamp: Long,
        val packageName: String,
        val hadContext: Boolean,
        val registrationStatus: Boolean,
        val result: Boolean,
        val threadName: String,
        val stackTrace: String
    )

    fun logCheck(packageName: String, hadContext: Boolean, registrationStatus: Boolean, result: Boolean) {
        val record = CheckRecord(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            hadContext = hadContext,
            registrationStatus = registrationStatus,
            result = result,
            threadName = Thread.currentThread().name,
            stackTrace = Thread.currentThread().stackTrace.take(5).joinToString("\n") { "  at $it" }
        )

        checkHistory.add(record)
        if (checkHistory.size > MAX_HISTORY) {
            checkHistory.removeAt(0)
        }
    }

    /**
     * Dump check history for debugging
     */
    fun dumpCheckHistory(): String {
        return buildString {
            appendLine("=== Check History (last ${checkHistory.size} checks) ===")
            checkHistory.forEach { record ->
                appendLine("Timestamp: ${record.timestamp}")
                appendLine("Package: ${record.packageName}")
                appendLine("Had Context: ${record.hadContext}")
                appendLine("Registration: ${record.registrationStatus}")
                appendLine("Result: ${record.result}")
                appendLine("Thread: ${record.threadName}")
                appendLine("Stack trace:")
                appendLine(record.stackTrace)
                appendLine("---")
            }
        }
    }

    /**
     * Get detailed diagnostic information
     */
    fun getDiagnosticInfo(context: Context): String {
        return buildString {
            appendLine("=== AllowedApps Diagnostic Info ===")
            appendLine("Cache Status: ${getCacheStatus()}")
            appendLine("Listener Count: ${registrationListeners.size}")

            try {
                val freshStatus = SayphRegistrationChecker.isDeviceRegistered(context)
                appendLine("Fresh Registration Check: $freshStatus")
            } catch (e: Exception) {
                appendLine("Fresh Registration Check: ERROR - ${e.message}")
            }

            appendLine("Cached Registration: $cachedRegistrationStatus")
            appendLine("Last Check: ${System.currentTimeMillis() - lastRegistrationCheck}ms ago")
            appendLine("Allowed Packages: ${allowedBasePackages.size}")
            appendLine("\nRecent check history:")
            appendLine(dumpCheckHistory())
        }
    }

    /**
     * Wrapper to ensure SayphRegistrationChecker is called safely
     */
    private fun safeCheckRegistration(context: Context): Boolean {
        return try {
            val result = SayphRegistrationChecker.isDeviceRegistered(context)
            android.util.Log.d("AllowedApps", "SayphRegistrationChecker returned: $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("AllowedApps", "Error checking registration status", e)
            // Fail closed - if we can't check, assume not registered
            false
        }
    }

    /**
     * Test method to verify cache and registration checker are in sync
     */
    fun verifySynchronization(context: Context): Boolean {
        val cachedStatus = getRegistrationStatusFromCache()
        val freshStatus = safeCheckRegistration(context)
        val cacheAge = System.currentTimeMillis() - lastRegistrationCheck

        val inSync = cachedStatus == freshStatus || cacheAge > CACHE_DURATION_MS

        android.util.Log.d("AllowedApps", "Synchronization check:")
        android.util.Log.d("AllowedApps", "  Cached: $cachedStatus")
        android.util.Log.d("AllowedApps", "  Fresh: $freshStatus")
        android.util.Log.d("AllowedApps", "  Cache age: ${cacheAge}ms")
        android.util.Log.d("AllowedApps", "  In sync: $inSync")

        if (!inSync) {
            android.util.Log.w("AllowedApps", "WARNING: Cache and fresh check are out of sync!")
        }

        return inSync
    }
}
