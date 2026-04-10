package app.lawnchair.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

object SayphDowntimeChecker {
    private const val TAG = "SayphDowntimeChecker"

    private const val AUTHORITY = "com.sayph.sayphagent.provider"
    private val DOWNTIME_URI = Uri.parse("content://$AUTHORITY/downtime")
    private val EMERGENCY_CONTACTS_URI = Uri.parse("content://$AUTHORITY/emergency_contacts")

    private const val COLUMN_IS_IN_DOWNTIME = "is_in_downtime"
    private const val COLUMN_DOWNTIME_END_MILLIS = "downtime_end_millis"
    private const val COLUMN_ROUTINE_NAME = "routine_name"
    private const val COLUMN_ROUTINE_TYPE = "routine_type"
    private const val COLUMN_CONTACT_NAME = "name"
    private const val COLUMN_CONTACT_PHONE = "phone"

    // Cache
    private var lastCheck = 0L
    private var cachedStatus: DowntimeStatus? = null
    private var cachedContacts: List<EmergencyContact> = emptyList()
    private const val CACHE_DURATION_MS = 5000L

    data class DowntimeStatus(
        val isInDowntime: Boolean,
        val endTimeMillis: Long,
        val routineName: String,
        val routineType: String
    )

    data class EmergencyContact(
        val name: String,
        val phone: String
    )

    fun getDowntimeStatus(context: Context): DowntimeStatus {
        val now = System.currentTimeMillis()
        cachedStatus?.let {
            if (now - lastCheck < CACHE_DURATION_MS) return it
        }

        val status = queryDowntimeStatus(context)
        cachedStatus = status
        lastCheck = now
        return status
    }

    fun isInDowntime(context: Context): Boolean {
        return getDowntimeStatus(context).isInDowntime
    }

    fun getEmergencyContacts(context: Context): List<EmergencyContact> {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CACHE_DURATION_MS && cachedContacts.isNotEmpty()) {
            return cachedContacts
        }

        val contacts = queryEmergencyContacts(context)
        cachedContacts = contacts
        return contacts
    }

    fun forceRefresh() {
        lastCheck = 0L
        cachedStatus = null
        cachedContacts = emptyList()
    }

    private fun queryDowntimeStatus(context: Context): DowntimeStatus {
        try {
            val cursor: Cursor? = context.contentResolver.query(
                DOWNTIME_URI, null, null, null, null
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val isInDowntime = c.getInt(c.getColumnIndexOrThrow(COLUMN_IS_IN_DOWNTIME)) == 1
                    val endMillis = c.getLong(c.getColumnIndexOrThrow(COLUMN_DOWNTIME_END_MILLIS))
                    val routineName = c.getString(c.getColumnIndexOrThrow(COLUMN_ROUTINE_NAME))
                    // routine_type column is new; default to "custom" if older provider versions don't have it
                    val routineType = try {
                        c.getString(c.getColumnIndexOrThrow(COLUMN_ROUTINE_TYPE)) ?: "custom"
                    } catch (e: IllegalArgumentException) {
                        "custom"
                    }

                    Log.d(TAG, "Downtime status: inDowntime=$isInDowntime, endMillis=$endMillis, routine=$routineName, type=$routineType")
                    return DowntimeStatus(isInDowntime, endMillis, routineName, routineType)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying downtime status", e)
        }

        return DowntimeStatus(isInDowntime = false, endTimeMillis = 0L, routineName = "", routineType = "")
    }

    private fun queryEmergencyContacts(context: Context): List<EmergencyContact> {
        val contacts = mutableListOf<EmergencyContact>()

        try {
            val cursor: Cursor? = context.contentResolver.query(
                EMERGENCY_CONTACTS_URI, null, null, null, null
            )

            cursor?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow(COLUMN_CONTACT_NAME))
                    val phone = c.getString(c.getColumnIndexOrThrow(COLUMN_CONTACT_PHONE))
                    if (name.isNotEmpty() && phone.isNotEmpty()) {
                        contacts.add(EmergencyContact(name, phone))
                    }
                }
            }

            Log.d(TAG, "Retrieved ${contacts.size} emergency contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying emergency contacts", e)
        }

        return contacts
    }
}
