package app.lawnchair.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
    private var overlayContainer: FrameLayout? = null
    private var isOverlayVisible = false
    private var savedStatusBarColor: Int = 0
    private var savedNavBarColor: Int = 0

    private companion object {
        const val OVERLAY_COLOR = 0xFF1d4576.toInt()
    }

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

                // Full-screen opaque container that blocks interaction with apps
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

                // Position content at ~35% from top (golden ratio) instead of dead center
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

                // Add container to parent
                parentContainer.addView(container)
                overlayContainer = container
                isOverlayVisible = true
                setSystemBarColors(OVERLAY_COLOR)
                setOverlayWallpaper()

                android.util.Log.d("RegistrationOverlay", "Overlay added as full-screen registration screen")
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

            // Create a full-screen opaque container that blocks interaction with apps
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
            setSystemBarColors(OVERLAY_COLOR)
            setOverlayWallpaper()

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
            restoreSystemBarColors()
            restoreWallpaper()

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

    private fun setSystemBarColors(color: Int) {
        (context as? Activity)?.window?.let { window ->
            savedStatusBarColor = window.statusBarColor
            savedNavBarColor = window.navigationBarColor
            window.statusBarColor = color
            window.navigationBarColor = color
        }
    }

    private fun restoreSystemBarColors() {
        (context as? Activity)?.window?.let { window ->
            window.statusBarColor = savedStatusBarColor
            window.navigationBarColor = savedNavBarColor
        }
    }

    private fun setOverlayWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(context)
            val displayMetrics = context.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.density

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(OVERLAY_COLOR)

            // Draw Sayph logo from resources
            val logoBitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                context.resources.getIdentifier("sayph_logo", "drawable", context.packageName),
            )
            if (logoBitmap != null) {
                val logoWidth = (200 * density).toInt()
                val logoHeight = (logoWidth * logoBitmap.height.toFloat() / logoBitmap.width).toInt()
                val scaled = Bitmap.createScaledBitmap(logoBitmap, logoWidth, logoHeight, true)
                val logoX = (width - logoWidth) / 2f
                val logoY = height * 0.42f
                canvas.drawBitmap(scaled, logoX, logoY, null)
                scaled.recycle()
                logoBitmap.recycle()

                // Draw "Device not registered" text below logo
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xB3FFFFFF.toInt() // white 70%
                    textSize = 16 * density
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT
                }
                val textY = logoY + logoHeight + (48 * density)
                canvas.drawText("Device not registered", width / 2f, textY, textPaint)
            }

            wm.setBitmap(bitmap)
            bitmap.recycle()
            android.util.Log.d("RegistrationOverlay", "Branded wallpaper set")
        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to set overlay wallpaper", e)
        }
    }

    private fun restoreWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(context)
            val defaultBitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources, com.android.launcher3.R.drawable.default_wallpaper
            )
            if (defaultBitmap != null) {
                wm.setBitmap(defaultBitmap)
                android.util.Log.d("RegistrationOverlay", "Wallpaper restored to default")
            }
        } catch (e: Exception) {
            android.util.Log.e("RegistrationOverlay", "Failed to restore wallpaper", e)
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        AllowedApps.removeRegistrationListener(registrationListener)
        hideRegistrationOverlay()
    }
}
