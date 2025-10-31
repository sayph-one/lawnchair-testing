package app.lawnchair.ui

import android.content.Context
import android.content.Intent
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

    // Add this property to allow passing a refresh callback
    var onRegistrationChanged: (() -> Unit)? = null

    private var overlayView: View? = null
    private var overlayContainer: FrameLayout? = null // Container to handle centering
    private var isOverlayVisible = false

    // Listener for when registration status changes
    private val registrationListener = {
        updateOverlayVisibility()
    }

    init {
        // Listen for registration status changes
        AllowedApps.addRegistrationListener(registrationListener)
    }

    /**
     * Check and update overlay visibility based on registration status
     */
    fun updateOverlayVisibility() {
        val needsRegistration = AllowedApps.needsRegistration(context)
        val wasOverlayVisible = isOverlayVisible

        android.util.Log.d("RegistrationOverlay", "updateOverlayVisibility - needsRegistration: $needsRegistration, isOverlayVisible: $isOverlayVisible")

        if (needsRegistration && !isOverlayVisible) {
            android.util.Log.d("RegistrationOverlay", "Showing registration overlay")
            showRegistrationOverlay()
            // TRIGGER CALLBACK WHEN BECOMING UNREGISTERED
            android.util.Log.d("RegistrationOverlay", "Device unregistered - triggering callback")
            onRegistrationChanged?.invoke()
        } else if (!needsRegistration && isOverlayVisible) {
            android.util.Log.d("RegistrationOverlay", "Hiding registration overlay")
            hideRegistrationOverlay()
            // Callback already triggered in hideRegistrationOverlay()
        } else {
            android.util.Log.d("RegistrationOverlay", "No overlay change needed")
        }
    }

    /**
     * Show the registration overlay
     */
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

                // Button wiring
                val registerButtonId = context.resources.getIdentifier("register_button", "id", context.packageName)
                overlay.findViewById<Button?>(registerButtonId)?.let { button ->
                    button.setOnClickListener {
                        android.util.Log.d("RegistrationOverlay", "Register button clicked")
                        openSayphAgent()
                    }
                } ?: android.util.Log.w("RegistrationOverlay", "Could not find register button")

                // --- Dynamic sizing here ---
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val targetWidth = (screenWidth * 0.85).toInt() // 85% of screen width

                // Create a full-screen container to handle centering
                val container = FrameLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Make container non-clickable so touches pass through to underlying views
                    isClickable = false
                    isFocusable = false
                }

                // Add overlay to container with centering
                val overlayParams = FrameLayout.LayoutParams(
                    targetWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                container.addView(overlay, overlayParams)

                // Add container to parent
                parentContainer.addView(container)
                overlayContainer = container
                isOverlayVisible = true

                android.util.Log.d("RegistrationOverlay", "Overlay added in centered container with width $targetWidth")
            }

        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to show registration overlay", e)
            createFallbackOverlay()
        }
    }



    /**
     * Create a simple fallback overlay when the XML layout can't be found
     */
    private fun createFallbackOverlay() {
        try {
            android.util.Log.d("RegistrationOverlay", "Creating fallback overlay")

            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val minWidth = (screenWidth * 0.90).toInt() // 85% of screen width as minimum

            val fallbackLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xDD1d4576.toInt()) // More opaque blue background
                setPadding(60, 50, 60, 50) // Increased padding
                minimumWidth = minWidth
            }

            val messageText = TextView(context).apply {
                text = "Device not registered with Sayph Agent.\nApps are hidden until registration is complete."
                textSize = 12f // Slightly larger text
                setTextColor(0xFFFFFFFF.toInt()) // White text
                gravity = android.view.Gravity.CENTER
                setPadding(10, 0, 10, 40) // More horizontal padding
            }

            val registerButton = Button(context).apply {
                text = "Register"
                textSize = 16f // Larger button text
                setTextColor(0xFF1d4576.toInt()) // Blue text
                setBackgroundColor(0xFFFFFFFF.toInt()) // White background
                setPadding(48, 20, 48, 20) // Larger button padding
                minimumWidth = 200 // Minimum button width
                setOnClickListener {
                    android.util.Log.d("RegistrationOverlay", "Fallback register button clicked")
                    openSayphAgent()
                }
            }

            fallbackLayout.addView(messageText)
            fallbackLayout.addView(registerButton)

            // Create a full-screen container to handle centering
            val container = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Make container non-clickable so touches pass through
                isClickable = false
                isFocusable = false
            }

            // Add fallback overlay to container with centering
            val overlayParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            container.addView(fallbackLayout, overlayParams)

            // Add container to parent
            parentContainer.addView(container)

            overlayView = fallbackLayout
            overlayContainer = container
            isOverlayVisible = true

            android.util.Log.d("RegistrationOverlay", "Fallback overlay created with minWidth: $minWidth and centered in container")

        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to create fallback overlay", e)
        }
    }

    /**
     * Hide the registration overlay
     */
    private fun hideRegistrationOverlay() {
        overlayContainer?.let { container ->
            parentContainer.removeView(container)
            overlayView = null
            overlayContainer = null
            isOverlayVisible = false

            // Trigger the callback when overlay is hidden (device registered)
            android.util.Log.d("RegistrationOverlay", "Overlay hidden - triggering registration changed callback")
            onRegistrationChanged?.invoke()
        }
    }

    /**
     * Open Sayph Agent app for registration
     */
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
                // Show error message instead of opening Play Store
                android.widget.Toast.makeText(
                    context,
                    "Sayph Agent app not found. Please install Sayph Agent to register this device.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to open Sayph Agent", e)
            // Show error message for any exceptions too
            android.widget.Toast.makeText(
                context,
                "Unable to open Sayph Agent app.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Force refresh registration status
     */
    fun refreshStatus() {
        android.util.Log.d("RegistrationOverlay", "refreshStatus() called")
        SayphRegistrationChecker.forceRefresh()
        updateOverlayVisibility()
    }

    /**
     * Debug method to force show the overlay (for testing UI positioning)
     */
    fun forceShowOverlay() {
        android.util.Log.d("RegistrationOverlay", "forceShowOverlay() called for testing")
        if (!isOverlayVisible) {
            showRegistrationOverlay()
        }
    }

    /**
     * Debug method to force hide the overlay (for testing)
     */
    fun forceHideOverlay() {
        android.util.Log.d("RegistrationOverlay", "forceHideOverlay() called for testing")
        if (isOverlayVisible) {
            hideRegistrationOverlay()
        }
    }

    /**
     * Debug method to toggle overlay visibility (for testing)
     */
    fun toggleOverlay() {
        android.util.Log.d("RegistrationOverlay", "toggleOverlay() called - current state: $isOverlayVisible")
        if (isOverlayVisible) {
            forceHideOverlay()
        } else {
            forceShowOverlay()
        }
    }

    /**
     * Get current overlay visibility state (for debugging)
     */
    fun isOverlayCurrentlyVisible(): Boolean = isOverlayVisible

    /**
     * Clean up resources
     */
    fun destroy() {
        AllowedApps.removeRegistrationListener(registrationListener)
        hideRegistrationOverlay()
    }
}
