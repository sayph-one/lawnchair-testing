package app.lawnchair.util

import android.content.Context
import android.util.Log

object DebugRegistrationHelper {
    private const val TAG = "RegistrationDebug"

    fun logRegistrationState(context: Context) {
        try {
            val needsRegistration = AllowedApps.needsRegistration(context)
            val isRegistered = SayphRegistrationChecker.isDeviceRegistered(context)
            val lastUpdateTime = SayphRegistrationChecker.getLastUpdateTime(context)
            val isSayphInstalled = try {
                context.packageManager.getPackageInfo("com.sayph.sayphagent", 0)
                true
            } catch (e: Exception) {
                false
            }

            // Test content provider connection
            val contentProviderWorking = SayphRegistrationChecker.testContentProviderConnection(context)

            Log.d(TAG, "=== REGISTRATION DEBUG STATE ===")
            Log.d(TAG, "Needs Registration: $needsRegistration")
            Log.d(TAG, "Is Registered: $isRegistered")
            Log.d(TAG, "Sayph Agent Installed: $isSayphInstalled")
            Log.d(TAG, "Content Provider Working: $contentProviderWorking")
            Log.d(TAG, "Last Update Time: $lastUpdateTime")
            Log.d(TAG, "Current Time: ${System.currentTimeMillis()}")
            Log.d(TAG, "Time Since Update: ${System.currentTimeMillis() - lastUpdateTime}ms")

            // Test a few app packages
            val testPackages = listOf(
                "com.sayph.sayphagent",
                "com.sec.android.app.camera",
                "com.android.settings"
            )

            testPackages.forEach { packageName ->
                val isAllowedWithContext = AllowedApps.isAllowed(packageName, context)
                val isAllowedCached = AllowedApps.isAllowed(packageName)
                Log.d(TAG, "Package: $packageName - Allowed(context): $isAllowedWithContext, Allowed(cached): $isAllowedCached")
            }

            Log.d(TAG, "=== END DEBUG STATE ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error logging registration state", e)
        }
    }

    fun logOverlayState(overlayManager: app.lawnchair.ui.RegistrationOverlayManager?) {
        Log.d(TAG, "=== OVERLAY DEBUG STATE ===")
        Log.d(TAG, "Overlay Manager: ${if (overlayManager != null) "Present" else "NULL"}")
        Log.d(TAG, "=== END OVERLAY STATE ===")
    }
}
