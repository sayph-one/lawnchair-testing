package app.lawnchair.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
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
import app.lawnchair.ui.permissions.PermissionsScreen
import app.lawnchair.util.AllowedApps
import app.lawnchair.util.SayphDowntimeChecker
import app.lawnchair.util.SayphPermissionsChecker

/**
 * Full-screen Compose overlay shown when the device is registered but missing one or more
 * required permissions. Visual language mirrors [DowntimeOverlayManager] — gradient
 * background, hero icon with glow, emergency contacts pinned to the bottom — but uses a
 * warm amber palette to distinguish "needs setup" from the bedtime/school routine themes.
 *
 * "Open setup" deep-links into the Sayph Agent's `PermissionsWizardActivity` via the
 * `com.sayph.action.OPEN_PERMISSIONS_WIZARD` action.
 */
class PermissionsOverlayManager(
    private val context: Context,
    private val parentContainer: ViewGroup,
) {

    var onPermissionsChanged: (() -> Unit)? = null

    private var overlayContainer: FrameLayout? = null
    private var composeView: ComposeView? = null
    private var isOverlayVisible = false

    // Mutable state driving the Compose content so we can refresh contacts without
    // tearing down the overlay.
    private val contentState = ComposeContentState()

    /**
     * True when the device has a default phone app set. When false, calling an emergency
     * contact via [TelecomManager.placeCall] succeeds at the telephony layer but no
     * InCallService renders the call screen — the user is stranded with an active call and
     * no way to hang up. We use this to dim the contact avatars and disable taps.
     */
    private fun hasDefaultDialer(): Boolean {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
        return !telecom.defaultDialerPackage.isNullOrEmpty()
    }

    private companion object {
        const val TAG = "PermissionsOverlay"
        const val WIZARD_ACTION = "com.sayph.action.OPEN_PERMISSIONS_WIZARD"
        const val SAYPH_AGENT_PACKAGE = "com.sayph.sayphagent"
    }

    private val permissionsListener = {
        updateOverlayVisibility()
    }

    init {
        AllowedApps.addPermissionsListener(permissionsListener)
    }

    fun updateOverlayVisibility() {
        val permissionsOk = SayphPermissionsChecker.arePermissionsOk(context)
        Log.d(TAG, "updateOverlayVisibility - permissionsOk=$permissionsOk, visible=$isOverlayVisible")

        if (!permissionsOk && !isOverlayVisible) {
            showOverlay()
            onPermissionsChanged?.invoke()
        } else if (!permissionsOk && isOverlayVisible) {
            // Still missing perms — refresh derived state in case it changed in-place.
            contentState.contacts = loadContacts()
            contentState.callingEnabled = hasDefaultDialer()
        } else if (permissionsOk && isOverlayVisible) {
            hideOverlay()
            onPermissionsChanged?.invoke()
        }
    }

    private fun showOverlay() {
        if (isOverlayVisible) return

        try {
            contentState.contacts = loadContacts()
            contentState.callingEnabled = hasDefaultDialer()

            val compose = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val contacts by remember { contentState.contactsState }
                    val callingEnabled by remember { contentState.callingEnabledState }
                    PermissionsScreen(
                        contacts = contacts,
                        callingEnabled = callingEnabled,
                        onOpenWizard = { openPermissionsWizard() },
                        onCallContact = { contact -> launchDialer(contact) },
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

            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun hideOverlay() {
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

    private fun openPermissionsWizard() {
        try {
            val intent = Intent(WIZARD_ACTION).apply {
                setPackage(SAYPH_AGENT_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open permissions wizard", e)
            try {
                val fallback = context.packageManager.getLaunchIntentForPackage(SAYPH_AGENT_PACKAGE)
                if (fallback != null) context.startActivity(fallback)
            } catch (inner: Exception) {
                Log.e(TAG, "Sayph Agent launch fallback failed", inner)
            }
        }
    }

    /**
     * Place a call to an emergency contact via [TelecomManager.placeCall], same path used
     * by the downtime overlay. Bypasses the dialer chooser by routing through the system
     * default phone app. Falls back to ACTION_DIAL targeted at the default dialer if
     * CALL_PHONE permission is not granted (which is itself a tracked missing permission
     * on first launch, but emergency calling is meant to keep working in degraded states).
     */
    private fun launchDialer(contact: DowntimeContact) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val uri = Uri.fromParts("tel", contact.phone, null)

        if (granted && telecom != null) {
            try {
                telecom.placeCall(uri, null)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "placeCall denied, falling back to ACTION_DIAL", e)
            } catch (e: Exception) {
                Log.w(TAG, "placeCall failed, falling back to ACTION_DIAL", e)
            }
        }

        try {
            val fallback = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                telecom?.defaultDialerPackage?.let { setPackage(it) }
            }
            context.startActivity(fallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch dialer for ${contact.name}", e)
        }
    }

    fun refreshStatus() {
        SayphPermissionsChecker.forceRefresh()
        updateOverlayVisibility()
    }

    /** Hide the overlay regardless of state — used by the launcher's precedence chain. */
    fun forceHide() {
        if (isOverlayVisible) hideOverlay()
    }

    fun isOverlayCurrentlyVisible(): Boolean = isOverlayVisible

    fun destroy() {
        AllowedApps.removePermissionsListener(permissionsListener)
        hideOverlay()
    }

    /** Holds mutable Compose state so the contacts list and calling state can refresh in-place. */
    private class ComposeContentState {
        val contactsState = androidx.compose.runtime.mutableStateOf<List<DowntimeContact>>(emptyList())
        val callingEnabledState = androidx.compose.runtime.mutableStateOf(false)
        var contacts: List<DowntimeContact>
            get() = contactsState.value
            set(value) {
                contactsState.value = value
            }
        var callingEnabled: Boolean
            get() = callingEnabledState.value
            set(value) {
                callingEnabledState.value = value
            }
    }
}
