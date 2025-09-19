package app.lawnchair.util

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log

object SayphRegistrationChecker {
    private const val TAG = "SayphRegistrationChecker"

    // Content Provider constants - must match Sayph Agent provider
    private const val AUTHORITY = "com.sayph.sayphagent.provider"
    private const val PATH = "registration"
    private val CONTENT_URI = Uri.parse("content://$AUTHORITY/$PATH")

    // Column names - must match provider
    private const val COLUMN_IS_REGISTERED = "is_registered"
    private const val COLUMN_LAST_UPDATE = "last_update"
    private const val COLUMN_DEVICE_ID = "device_id"

    private const val SAYPH_AGENT_PACKAGE = "com.sayph.sayphagent"

    // Cache to avoid frequent content provider queries
    private var lastCheck = 0L
    private var cachedStatus = false
    private const val CACHE_DURATION_MS = 5000L // 5 seconds

    /**
     * Check if device is registered by querying Sayph Agent's Content Provider
     */
    fun isDeviceRegistered(context: Context): Boolean {
        val now = System.currentTimeMillis()

        // Return cached value if still fresh
        if (now - lastCheck < CACHE_DURATION_MS) {
            Log.d(TAG, "Returning cached status: $cachedStatus")
            return cachedStatus
        }

        try {
            // First check if Sayph Agent is installed
            if (!isSayphAgentInstalled(context)) {
                Log.w(TAG, "Sayph Agent not installed - device not registered")
                cachedStatus = false
                lastCheck = now
                return false
            }

            // Query the Content Provider
            val cursor: Cursor? = context.contentResolver.query(
                CONTENT_URI,
                arrayOf(COLUMN_IS_REGISTERED, COLUMN_LAST_UPDATE, COLUMN_DEVICE_ID),
                null,
                null,
                null
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val isRegistered = c.getInt(c.getColumnIndexOrThrow(COLUMN_IS_REGISTERED)) == 1
                    val lastUpdate = c.getLong(c.getColumnIndexOrThrow(COLUMN_LAST_UPDATE))
                    val deviceId = c.getString(c.getColumnIndexOrThrow(COLUMN_DEVICE_ID))

                    Log.d(TAG, "ContentProvider - isRegistered: $isRegistered, lastUpdate: $lastUpdate, deviceId: $deviceId")

                    // Check if the status is recent (within last 60 seconds)
                    val statusAge = now - lastUpdate
                    val isStatusFresh = statusAge < 60000L

                    if (!isStatusFresh) {
                        Log.w(TAG, "Registration status is stale (age: ${statusAge}ms), assuming not registered")
                        cachedStatus = false
                    } else {
                        cachedStatus = isRegistered
                    }
                } else {
                    Log.w(TAG, "No registration data returned from ContentProvider")
                    cachedStatus = false
                }
            } ?: run {
                Log.w(TAG, "Failed to query ContentProvider - cursor is null")
                cachedStatus = false
            }

            lastCheck = now
            Log.d(TAG, "Device registration status: $cachedStatus")

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception querying ContentProvider - permission denied", e)
            cachedStatus = false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking registration status via ContentProvider", e)
            cachedStatus = false
        }

        return cachedStatus
    }

    /**
     * Force refresh the registration status (clears cache)
     */
    fun forceRefresh() {
        lastCheck = 0L
    }

    /**
     * Check if Sayph Agent app is installed
     */
    private fun isSayphAgentInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SAYPH_AGENT_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Get the last update timestamp for the registration status
     */
    fun getLastUpdateTime(context: Context): Long {
        return try {
            val cursor: Cursor? = context.contentResolver.query(
                CONTENT_URI,
                arrayOf(COLUMN_LAST_UPDATE),
                null,
                null,
                null
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    c.getLong(c.getColumnIndexOrThrow(COLUMN_LAST_UPDATE))
                } else 0L
            } ?: 0L

        } catch (e: Exception) {
            Log.e(TAG, "Error getting last update time", e)
            0L
        }
    }

    /**
     * Test the content provider connection
     */
    fun testContentProviderConnection(context: Context): Boolean {
        return try {
            val cursor = context.contentResolver.query(CONTENT_URI, null, null, null, null)
            val isConnected = cursor != null
            cursor?.close()
            Log.d(TAG, "Content Provider connection test: ${if (isConnected) "SUCCESS" else "FAILED"}")
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Content Provider connection test FAILED", e)
            false
        }
    }
}
