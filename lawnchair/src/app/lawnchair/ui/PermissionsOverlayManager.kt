package app.lawnchair.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.util.AllowedApps
import app.lawnchair.util.SayphPermissionsChecker

/**
 * Full-screen overlay shown when the device is registered but is missing one or more
 * permissions the Sayph Agent requires. Tapping the primary button deep-links into the
 * Agent's `PermissionsWizardActivity` via the `com.sayph.action.OPEN_PERMISSIONS_WIZARD`
 * intent action.
 *
 * Mirrors the structure of [RegistrationOverlayManager] (programmatic LinearLayout overlay
 * attached to the decor view) rather than [DowntimeOverlayManager] (Compose + Lottie). Visual
 * consistency with the registration overlay is intentional — both represent a "device not
 * ready" state for which the launcher is blocked.
 */
class PermissionsOverlayManager(
    private val context: Context,
    private val parentContainer: ViewGroup,
) {

    var onPermissionsChanged: (() -> Unit)? = null

    private var overlayContainer: FrameLayout? = null
    private var isOverlayVisible = false

    private companion object {
        const val TAG = "PermissionsOverlay"
        const val OVERLAY_COLOR = 0xFF1d4576.toInt()
        const val PRIMARY_TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val BUTTON_BG_COLOR = 0xFFFFFFFF.toInt()
        const val BUTTON_TEXT_COLOR = 0xFF1d4576.toInt()
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
        android.util.Log.d(TAG, "updateOverlayVisibility - permissionsOk=$permissionsOk, visible=$isOverlayVisible")

        if (!permissionsOk && !isOverlayVisible) {
            showPermissionsOverlay()
            onPermissionsChanged?.invoke()
        } else if (permissionsOk && isOverlayVisible) {
            hidePermissionsOverlay()
            onPermissionsChanged?.invoke()
        }
    }

    private fun showPermissionsOverlay() {
        if (isOverlayVisible) return

        try {
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val minWidth = (screenWidth * 0.90).toInt()

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(OVERLAY_COLOR)
                setPadding(60, 50, 60, 50)
                minimumWidth = minWidth
            }

            val titleText = TextView(context).apply {
                text = "Set-up incomplete"
                textSize = 22f
                setTextColor(PRIMARY_TEXT_COLOR)
                gravity = Gravity.CENTER
                setPadding(10, 0, 10, 20)
            }

            val bodyText = TextView(context).apply {
                text = "Sayph needs a few more permissions before this device can be used. " +
                    "Open the Sayph setup to grant them."
                textSize = 14f
                setTextColor(PRIMARY_TEXT_COLOR)
                gravity = Gravity.CENTER
                setPadding(10, 0, 10, 40)
            }

            val openButton = Button(context).apply {
                text = "Open setup"
                textSize = 16f
                setTextColor(BUTTON_TEXT_COLOR)
                setBackgroundColor(BUTTON_BG_COLOR)
                setPadding(48, 20, 48, 20)
                minimumWidth = 200
                setOnClickListener {
                    android.util.Log.d(TAG, "Open setup button clicked")
                    openPermissionsWizard()
                }
            }

            card.addView(titleText)
            card.addView(bodyText)
            card.addView(openButton)

            val container = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(OVERLAY_COLOR)
                fitsSystemWindows = false
                clipToPadding = false
                isClickable = true
                isFocusable = true
                elevation = 100f
            }

            val cardParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER
            }
            container.addView(card, cardParams)

            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
                ?: parentContainer
            decorView.addView(container)
            overlayContainer = container
            isOverlayVisible = true

            android.util.Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show permissions overlay", e)
        }
    }

    private fun hidePermissionsOverlay() {
        overlayContainer?.let { container ->
            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
            decorView?.removeView(container) ?: parentContainer.removeView(container)
        }
        overlayContainer = null
        isOverlayVisible = false
        android.util.Log.d(TAG, "Overlay hidden")
    }

    private fun openPermissionsWizard() {
        try {
            val intent = Intent(WIZARD_ACTION).apply {
                setPackage(SAYPH_AGENT_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to open permissions wizard", e)
            // Fallback: try opening the agent's launcher
            try {
                val fallback = context.packageManager.getLaunchIntentForPackage(SAYPH_AGENT_PACKAGE)
                if (fallback != null) {
                    context.startActivity(fallback)
                }
            } catch (inner: Exception) {
                android.util.Log.e(TAG, "Failed to open Sayph Agent fallback", inner)
            }
        }
    }

    fun refreshStatus() {
        SayphPermissionsChecker.forceRefresh()
        updateOverlayVisibility()
    }

    /**
     * Hide the permissions overlay regardless of underlying state. Used by the launcher's
     * overlay-precedence logic when a higher-priority overlay (registration) must take over.
     */
    fun forceHide() {
        if (isOverlayVisible) hidePermissionsOverlay()
    }

    fun isOverlayCurrentlyVisible(): Boolean = isOverlayVisible

    fun destroy() {
        AllowedApps.removePermissionsListener(permissionsListener)
        hidePermissionsOverlay()
    }
}
