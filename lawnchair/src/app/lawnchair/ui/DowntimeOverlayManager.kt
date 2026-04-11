package app.lawnchair.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.lawnchair.ui.downtime.DowntimeContact
import app.lawnchair.ui.downtime.DowntimeScreen
import app.lawnchair.util.SayphDowntimeChecker

/**
 * Manages a full-screen Compose overlay shown over the Lawnchair launcher while a
 * downtime routine is active. The overlay is added to the Activity's decor view so
 * it sits above all launcher content.
 */
class DowntimeOverlayManager(
    private val context: Context,
    private val parentContainer: ViewGroup,
) {

    private var overlayContainer: FrameLayout? = null
    private var composeView: ComposeView? = null
    private var isOverlayVisible = false

    // Currently displayed state; used to decide whether to recompose in-place.
    private var currentType: String = ""
    private var currentName: String = ""
    private var currentEndMillis: Long = 0L

    // Mutable state driving the Compose content so we can update without
    // tearing down the overlay (keeps Lottie animation running smoothly).
    private val contentState = ComposeContentState()

    private companion object {
        const val TAG = "DowntimeOverlay"
    }

    fun updateOverlayVisibility() {
        val status = SayphDowntimeChecker.getDowntimeStatus(context)

        Log.d(TAG, "updateOverlayVisibility - inDowntime=${status.isInDowntime}, visible=$isOverlayVisible")

        if (status.isInDowntime && !isOverlayVisible) {
            showDowntimeOverlay(status.routineName, status.routineType, status.endTimeMillis)
        } else if (status.isInDowntime && isOverlayVisible) {
            updateContent(status.routineName, status.routineType, status.endTimeMillis)
        } else if (!status.isInDowntime && isOverlayVisible) {
            hideDowntimeOverlay()
        }
    }

    private fun showDowntimeOverlay(routineName: String, routineType: String, endTimeMillis: Long) {
        if (isOverlayVisible) return

        try {
            currentName = routineName
            currentType = routineType
            currentEndMillis = endTimeMillis

            contentState.routineType = routineType
            contentState.routineName = routineName
            contentState.endTimeMillis = endTimeMillis
            contentState.contacts = loadContacts()

            val compose = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val type by remember { contentState.routineTypeState }
                    val name by remember { contentState.routineNameState }
                    val end by remember { contentState.endTimeMillisState }
                    val contacts by remember { contentState.contactsState }

                    DowntimeScreen(
                        routineType = type,
                        routineName = name,
                        endTimeMillis = end,
                        contacts = contacts,
                        onCallContact = { contact ->
                            launchDialer(contact)
                        },
                    )
                }
            }
            composeView = compose

            val container = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isClickable = true
                isFocusable = true
                elevation = 100f
                fitsSystemWindows = false
                addView(
                    compose,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }

            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
                ?: parentContainer
            decorView.addView(container)
            overlayContainer = container
            isOverlayVisible = true

            Log.d(TAG, "Overlay shown - routine=$routineName type=$routineType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun updateContent(routineName: String, routineType: String, endTimeMillis: Long) {
        if (routineType != currentType) {
            currentType = routineType
            contentState.routineType = routineType
        }
        if (routineName != currentName) {
            currentName = routineName
            contentState.routineName = routineName
        }
        if (endTimeMillis != currentEndMillis) {
            currentEndMillis = endTimeMillis
            contentState.endTimeMillis = endTimeMillis
        }
        val fresh = loadContacts()
        if (fresh != contentState.contacts) {
            contentState.contacts = fresh
        }
    }

    private fun hideDowntimeOverlay() {
        overlayContainer?.let { container ->
            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
            decorView?.removeView(container) ?: parentContainer.removeView(container)
        }
        overlayContainer = null
        composeView = null
        isOverlayVisible = false
        Log.d(TAG, "Overlay hidden")
    }

    private fun loadContacts(): List<DowntimeContact> {
        return SayphDowntimeChecker.getEmergencyContacts(context).map {
            DowntimeContact(name = it.name, phone = it.phone)
        }
    }

    private fun launchDialer(contact: DowntimeContact) {
        // Place the call directly via ACTION_CALL. This bypasses the Dialer's
        // blocked launcher activity and goes straight to DialerActivity (the
        // ACTION_CALL handler), so emergency calls still work during downtime.
        // Falls back to ACTION_DIAL if CALL_PHONE permission is denied.
        val callPermission = android.Manifest.permission.CALL_PHONE
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, callPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        try {
            val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:${contact.phone}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: SecurityException) {
            // Fallback if CALL_PHONE was somehow denied at startActivity time
            Log.w(TAG, "ACTION_CALL denied, falling back to ACTION_DIAL", e)
            try {
                val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Both ACTION_CALL and ACTION_DIAL failed", fallbackError)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch dialer for ${contact.name}", e)
        }
    }

    fun refreshStatus() {
        SayphDowntimeChecker.forceRefresh()
        updateOverlayVisibility()
    }

    fun isOverlayCurrentlyVisible(): Boolean = isOverlayVisible

    /**
     * Debug only: force-show the overlay with synthetic state so designers
     * and developers can preview each routine type without waiting for a
     * real routine to trigger.
     */
    fun debugPreview(routineType: String) {
        if (isOverlayVisible) {
            hideDowntimeOverlay()
        }

        val previewName = when (routineType.lowercase()) {
            "bedtime" -> "Bedtime"
            "school" -> "School Hours"
            "dinner" -> "Dinner Time"
            else -> "Preview Routine"
        }
        // Synthetic end time: 8 hours from now for bedtime, shorter for others
        val previewDurationMs = when (routineType.lowercase()) {
            "bedtime" -> 8L * 60 * 60 * 1000
            "school" -> 6L * 60 * 60 * 1000
            "dinner" -> 45L * 60 * 1000
            else -> 2L * 60 * 60 * 1000
        }
        val previewEnd = System.currentTimeMillis() + previewDurationMs

        showDowntimeOverlay(previewName, routineType, previewEnd)

        // Inject sample emergency contacts so the bottom section renders
        contentState.contacts = listOf(
            DowntimeContact(name = "Mum", phone = "+447700900001"),
            DowntimeContact(name = "Dad", phone = "+447700900002"),
            DowntimeContact(name = "Grandma", phone = "+447700900003"),
        )
    }

    /** Debug only: hide whatever is currently shown. */
    fun debugHide() {
        hideDowntimeOverlay()
    }

    fun destroy() {
        hideDowntimeOverlay()
    }

    /**
     * Holds mutable Compose state so the UI can update in-place without
     * tearing down the overlay and restarting animations.
     */
    private class ComposeContentState {
        val routineTypeState = androidx.compose.runtime.mutableStateOf("")
        val routineNameState = androidx.compose.runtime.mutableStateOf("")
        val endTimeMillisState = androidx.compose.runtime.mutableStateOf(0L)
        val contactsState = androidx.compose.runtime.mutableStateOf<List<DowntimeContact>>(emptyList())

        var routineType: String
            get() = routineTypeState.value
            set(value) { routineTypeState.value = value }

        var routineName: String
            get() = routineNameState.value
            set(value) { routineNameState.value = value }

        var endTimeMillis: Long
            get() = endTimeMillisState.value
            set(value) { endTimeMillisState.value = value }

        var contacts: List<DowntimeContact>
            get() = contactsState.value
            set(value) { contactsState.value = value }
    }
}
