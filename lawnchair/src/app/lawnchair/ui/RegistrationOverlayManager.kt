package app.lawnchair.ui

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.util.AllowedApps
import app.lawnchair.util.SayphRegistrationChecker

class RegistrationOverlayManager(
    private val context: Context,
    private val parentContainer: ViewGroup
) {

    var onRegistrationChanged: (() -> Unit)? = null

    private var overlayView: View? = null
    private var overlayContainer: FrameLayout? = null
    private var isOverlayVisible = false

    private companion object {
        const val OVERLAY_COLOR = 0xFF1d4576.toInt()
    }

    private val registrationListener = {
        updateOverlayVisibility()
    }

    init {
        AllowedApps.addRegistrationListener(registrationListener)
    }

    fun updateOverlayVisibility() {
        val needsRegistration = AllowedApps.needsRegistration(context)

        android.util.Log.d("RegistrationOverlay", "updateOverlayVisibility - needsRegistration: $needsRegistration, isOverlayVisible: $isOverlayVisible")

        if (needsRegistration && !isOverlayVisible) {
            android.util.Log.d("RegistrationOverlay", "Showing registration overlay")
            showRegistrationOverlay()
            android.util.Log.d("RegistrationOverlay", "Device unregistered - triggering callback")
            onRegistrationChanged?.invoke()
        } else if (!needsRegistration && isOverlayVisible) {
            android.util.Log.d("RegistrationOverlay", "Hiding registration overlay")
            hideRegistrationOverlay()
        } else {
            android.util.Log.d("RegistrationOverlay", "No overlay change needed")
        }
    }

    private fun showRegistrationOverlay() {
        if (isOverlayVisible) {
            android.util.Log.d("RegistrationOverlay", "Overlay already visible, skipping")
            return
        }

        try {
            android.util.Log.d("RegistrationOverlay", "Attempting to show registration overlay")

            val inflater = LayoutInflater.from(context)
            val layoutResId = context.resources.getIdentifier(
                "registration_status_widget", "layout", context.packageName
            )

            if (layoutResId == 0) {
                android.util.Log.e("RegistrationOverlay", "Could not find registration_status_widget layout")
                createFallbackOverlay()
                return
            }

            overlayView = inflater.inflate(layoutResId, parentContainer, false)

            overlayView?.let { overlay ->
                android.util.Log.d("RegistrationOverlay", "Successfully inflated overlay view")

                val registerButtonId = context.resources.getIdentifier("register_button", "id", context.packageName)
                overlay.findViewById<Button?>(registerButtonId)?.let { button ->
                    button.setOnClickListener {
                        android.util.Log.d("RegistrationOverlay", "Register button clicked")
                        openSayphAgent()
                    }
                } ?: android.util.Log.w("RegistrationOverlay", "Could not find register button")

                val container = FrameLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(OVERLAY_COLOR)
                    fitsSystemWindows = false
                    clipToPadding = false
                    isClickable = true
                    isFocusable = true
                    elevation = 100f
                }

                val displayMetrics = context.resources.displayMetrics
                val topMargin = (displayMetrics.heightPixels * 0.28).toInt()
                val overlayParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    this.topMargin = topMargin
                }
                container.addView(overlay, overlayParams)

                // Add to decor view so overlay covers system bar areas too
                val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
                    ?: parentContainer
                decorView.addView(container)
                overlayContainer = container
                isOverlayVisible = true

                android.util.Log.d("RegistrationOverlay", "Overlay added as full-screen registration screen")
            }

        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to show registration overlay", e)
            createFallbackOverlay()
        }
    }

    private fun createFallbackOverlay() {
        try {
            android.util.Log.d("RegistrationOverlay", "Creating fallback overlay")

            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val minWidth = (screenWidth * 0.90).toInt()

            val fallbackLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xDD1d4576.toInt())
                setPadding(60, 50, 60, 50)
                minimumWidth = minWidth
            }

            val messageText = TextView(context).apply {
                text = "Device not registered with Sayph Agent.\nApps are hidden until registration is complete."
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(10, 0, 10, 40)
            }

            val registerButton = Button(context).apply {
                text = "Register"
                textSize = 16f
                setTextColor(0xFF1d4576.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(48, 20, 48, 20)
                minimumWidth = 200
                setOnClickListener {
                    android.util.Log.d("RegistrationOverlay", "Fallback register button clicked")
                    openSayphAgent()
                }
            }

            fallbackLayout.addView(messageText)
            fallbackLayout.addView(registerButton)

            val container = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0xFF1d4576.toInt())
                fitsSystemWindows = false
                clipToPadding = false
                isClickable = true
                isFocusable = true
                elevation = 100f
            }

            val overlayParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            container.addView(fallbackLayout, overlayParams)

            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
                ?: parentContainer
            decorView.addView(container)

            overlayView = fallbackLayout
            overlayContainer = container
            isOverlayVisible = true

            android.util.Log.d("RegistrationOverlay", "Fallback overlay created")

        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to create fallback overlay", e)
        }
    }

    private fun hideRegistrationOverlay() {
        overlayContainer?.let { container ->
            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
            decorView?.removeView(container) ?: parentContainer.removeView(container)
            overlayView = null
            overlayContainer = null
            isOverlayVisible = false

            android.util.Log.d("RegistrationOverlay", "Overlay hidden - triggering registration changed callback")
            onRegistrationChanged?.invoke()
        }
    }

    private fun openSayphAgent() {
        try {
            android.util.Log.d("RegistrationOverlay", "Attempting to open Sayph Agent")

            val intent = context.packageManager.getLaunchIntentForPackage("com.sayph.sayphagent")
            android.util.Log.d("RegistrationOverlay", "Launch intent for Sayph Agent: $intent")

            if (intent != null) {
                android.util.Log.d("RegistrationOverlay", "Starting Sayph Agent activity")
                context.startActivity(intent)
            } else {
                android.util.Log.w("RegistrationOverlay", "Sayph Agent not found")
                android.widget.Toast.makeText(
                    context,
                    "Sayph Agent app not found. Please install Sayph Agent to register this device.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to open Sayph Agent", e)
            android.widget.Toast.makeText(
                context,
                "Unable to open Sayph Agent app.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun refreshStatus() {
        android.util.Log.d("RegistrationOverlay", "refreshStatus() called")
        SayphRegistrationChecker.forceRefresh()
        updateOverlayVisibility()
    }

    fun forceShowOverlay() {
        android.util.Log.d("RegistrationOverlay", "forceShowOverlay() called for testing")
        if (!isOverlayVisible) {
            showRegistrationOverlay()
        }
    }

    fun forceHideOverlay() {
        android.util.Log.d("RegistrationOverlay", "forceHideOverlay() called for testing")
        if (isOverlayVisible) {
            hideRegistrationOverlay()
        }
    }

    fun toggleOverlay() {
        android.util.Log.d("RegistrationOverlay", "toggleOverlay() called - current state: $isOverlayVisible")
        if (isOverlayVisible) {
            forceHideOverlay()
        } else {
            forceShowOverlay()
        }
    }

    fun isOverlayCurrentlyVisible(): Boolean = isOverlayVisible


    fun destroy() {
        AllowedApps.removeRegistrationListener(registrationListener)
        hideRegistrationOverlay()
    }
}
