package app.lawnchair.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Thin client for the Sayph Agent's `content://com.sayph.sayphagent.provider/permissions` URI.
 *
 * Mirrors the structure of [SayphRegistrationChecker] / [SayphDowntimeChecker] so each piece
 * of agent state has the same shape on the Lawnchair side: a content-provider query, a short
 * cache, and a force-refresh hook for broadcast receivers.
 *
 * Defaults to `permissionsOk = true` if the URI is unavailable (older Agent without the
 * `/permissions` endpoint) so the overlay doesn't appear on devices running an outdated agent.
 */
object SayphPermissionsChecker {
    private const val TAG = "SayphPermissionsChecker"

    private const val AUTHORITY = "com.sayph.sayphagent.provider"
    private val PERMISSIONS_URI: Uri = Uri.parse("content://$AUTHORITY/permissions")

    private const val COLUMN_PERMISSIONS_OK = "permissions_ok"
    private const val COLUMN_MISSING_KEYS = "missing_keys"

    private const val CACHE_DURATION_MS = 5_000L
    private var lastCheck = 0L
    private var cachedStatus: PermissionsStatus? = null

    data class PermissionsStatus(
        val permissionsOk: Boolean,
        val missingKeys: String,
    )

    fun getPermissionsStatus(context: Context): PermissionsStatus {
        val now = System.currentTimeMillis()
        cachedStatus?.let {
            if (now - lastCheck < CACHE_DURATION_MS) return it
        }
        val status = queryPermissionsStatus(context)
        cachedStatus = status
        lastCheck = now
        return status
    }

    fun arePermissionsOk(context: Context): Boolean = getPermissionsStatus(context).permissionsOk

    fun forceRefresh() {
        lastCheck = 0L
        cachedStatus = null
    }

    private fun queryPermissionsStatus(context: Context): PermissionsStatus {
        try {
            val cursor: Cursor? = context.contentResolver.query(
                PERMISSIONS_URI, null, null, null, null,
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val okIdx = c.getColumnIndex(COLUMN_PERMISSIONS_OK)
                    val missingIdx = c.getColumnIndex(COLUMN_MISSING_KEYS)
                    val ok = if (okIdx >= 0) c.getInt(okIdx) == 1 else true
                    val missing = if (missingIdx >= 0) c.getString(missingIdx) ?: "" else ""
                    Log.d(TAG, "Permissions status: ok=$ok, missing=$missing")
                    return PermissionsStatus(ok, missing)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Permissions URI unavailable — assuming OK", e)
        }
        // Fail open — older Agent / transient query failures shouldn't brick the launcher.
        return PermissionsStatus(permissionsOk = true, missingKeys = "")
    }
}
