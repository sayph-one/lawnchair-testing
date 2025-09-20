package app.lawnchair.ui

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        } else if (!needsRegistration && isOverlayVisible) {
            android.util.Log.d("RegistrationOverlay", "Hiding registration overlay")
            hideRegistrationOverlay()
            // ADD THIS: Trigger refresh when overlay is hidden (device registered)
            android.util.Log.d("RegistrationOverlay", "Device registered - triggering callback")
            onRegistrationChanged?.invoke()
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

                val layoutParams = ViewGroup.MarginLayoutParams(
                    targetWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    // optional extra margins so it never hits edges
                    val margin = (screenWidth * 0.05).toInt()
                    setMargins(margin, 0, margin, 0)
                }

                overlay.layoutParams = layoutParams

                // Add and center
                parentContainer.addView(overlay)
                isOverlayVisible = true

                overlay.post {
                    centerOverlayInParent(overlay)
                }

                android.util.Log.d("RegistrationOverlay", "Overlay added with dynamic width $targetWidth")
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

            val layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            fallbackLayout.layoutParams = layoutParams
            parentContainer.addView(fallbackLayout)

            overlayView = fallbackLayout
            isOverlayVisible = true

            // Center the fallback overlay
            fallbackLayout.post {
                centerOverlayInParent(fallbackLayout)
            }

            android.util.Log.d("RegistrationOverlay", "Fallback overlay created with minWidth: $minWidth")

        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to create fallback overlay", e)
        }
    }

    /**
     * Center the overlay in the parent container
     */
    private fun centerOverlayInParent(overlay: View) {
        try {
            val parentWidth = parentContainer.width
            val parentHeight = parentContainer.height
            val overlayWidth = overlay.width
            val overlayHeight = overlay.height

            if (parentWidth > 0 && parentHeight > 0 && overlayWidth > 0 && overlayHeight > 0) {
                val x = (parentWidth - overlayWidth) / 2
                val y = (parentHeight - overlayHeight) / 2

                overlay.x = x.toFloat()
                overlay.y = y.toFloat()

                android.util.Log.d("RegistrationOverlay", "Overlay centered at ($x, $y)")
            } else {
                android.util.Log.w("RegistrationOverlay", "Could not center overlay - dimensions not ready")
            }
        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to center overlay", e)
        }
    }

    /**
     * Hide the registration overlay
     */
    private fun hideRegistrationOverlay() {
        overlayView?.let { overlay ->
            parentContainer.removeView(overlay)
            overlayView = null
            isOverlayVisible = false
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
     * Clean up resources
     */
    fun destroy() {
        AllowedApps.removeRegistrationListener(registrationListener)
        hideRegistrationOverlay()
    }
}
