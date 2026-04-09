package app.lawnchair.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.util.SayphDowntimeChecker

class DowntimeOverlayManager(
    private val context: Context,
    private val parentContainer: ViewGroup
) {

    private var overlayContainer: FrameLayout? = null
    private var isOverlayVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    private companion object {
        const val TAG = "DowntimeOverlay"
        const val OVERLAY_COLOR = 0xFF1d4576.toInt()
        const val COUNTDOWN_INTERVAL_MS = 60_000L // Update every minute
    }

    fun updateOverlayVisibility() {
        val status = SayphDowntimeChecker.getDowntimeStatus(context)

        Log.d(TAG, "updateOverlayVisibility - inDowntime: ${status.isInDowntime}, isOverlayVisible: $isOverlayVisible")

        if (status.isInDowntime && !isOverlayVisible) {
            showDowntimeOverlay(status.routineName, status.endTimeMillis)
        } else if (status.isInDowntime && isOverlayVisible) {
            // Update the countdown and routine name
            updateCountdown(status.endTimeMillis)
        } else if (!status.isInDowntime && isOverlayVisible) {
            hideDowntimeOverlay()
        }
    }

    private fun showDowntimeOverlay(routineName: String, endTimeMillis: Long) {
        if (isOverlayVisible) return

        try {
            val inflater = LayoutInflater.from(context)
            val layoutResId = context.resources.getIdentifier(
                "downtime_overlay_widget", "layout", context.packageName
            )

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

            if (layoutResId != 0) {
                val overlayView = inflater.inflate(layoutResId, container, false)
                setupOverlayContent(overlayView, routineName, endTimeMillis)

                val displayMetrics = context.resources.displayMetrics
                val topMargin = (displayMetrics.heightPixels * 0.20).toInt()
                val overlayParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    this.topMargin = topMargin
                }
                container.addView(overlayView, overlayParams)
            } else {
                Log.w(TAG, "Layout not found, creating fallback")
                createFallbackContent(container, routineName, endTimeMillis)
            }

            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
                ?: parentContainer
            decorView.addView(container)
            overlayContainer = container
            isOverlayVisible = true

            startCountdownUpdates(endTimeMillis)
            Log.d(TAG, "Downtime overlay shown - routine: $routineName")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show downtime overlay", e)
        }
    }

    private fun setupOverlayContent(view: View, routineName: String, endTimeMillis: Long) {
        val routineNameId = context.resources.getIdentifier("downtime_routine_name", "id", context.packageName)
        view.findViewById<TextView?>(routineNameId)?.text = routineName

        val countdownId = context.resources.getIdentifier("downtime_countdown", "id", context.packageName)
        view.findViewById<TextView?>(countdownId)?.text = formatCountdown(endTimeMillis)

        // Add emergency contacts
        val containerId = context.resources.getIdentifier("downtime_contacts_container", "id", context.packageName)
        val headerId = context.resources.getIdentifier("downtime_emergency_header", "id", context.packageName)
        val contactsContainer = view.findViewById<LinearLayout?>(containerId)
        val headerView = view.findViewById<TextView?>(headerId)

        val contacts = SayphDowntimeChecker.getEmergencyContacts(context)
        if (contacts.isNotEmpty() && contactsContainer != null) {
            headerView?.visibility = View.VISIBLE
            contacts.forEach { contact ->
                contactsContainer.addView(createContactCard(contact))
            }
        }
    }

    private fun createContactCard(contact: SayphDowntimeChecker.EmergencyContact): View {
        val dp = { value: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
        }

        val cardBg = GradientDrawable().apply {
            setColor(0x33FFFFFF)
            cornerRadius = dp(12).toFloat()
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(context).apply {
            text = contact.name
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }

        val phoneText = TextView(context).apply {
            text = contact.phone
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 13f
        }

        textContainer.addView(nameText)
        textContainer.addView(phoneText)
        row.addView(textContainer)

        val callButton = Button(context).apply {
            text = "Call"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            val btnBg = GradientDrawable().apply {
                setColor(0xFF2E7D32.toInt()) // Green
                cornerRadius = dp(8).toFloat()
            }
            background = btnBg
            setPadding(dp(20), dp(8), dp(20), dp(8))
            minWidth = 0
            minimumWidth = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) }

            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initiate call to ${contact.name}", e)
                }
            }
        }

        row.addView(callButton)
        return row
    }

    private fun createFallbackContent(container: FrameLayout, routineName: String, endTimeMillis: Long) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(60, 50, 60, 50)
        }

        layout.addView(TextView(context).apply {
            text = "Device is in Downtime"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        if (routineName.isNotEmpty()) {
            layout.addView(TextView(context).apply {
                text = routineName
                textSize = 16f
                setTextColor(0xB3FFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16)
            })
        }

        layout.addView(TextView(context).apply {
            tag = "countdown_text"
            text = formatCountdown(endTimeMillis)
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        })

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }

        container.addView(layout, params)
    }

    private fun hideDowntimeOverlay() {
        stopCountdownUpdates()
        overlayContainer?.let { container ->
            val decorView = (context as? Activity)?.window?.decorView as? ViewGroup
            decorView?.removeView(container) ?: parentContainer.removeView(container)
            overlayContainer = null
            isOverlayVisible = false
            Log.d(TAG, "Downtime overlay hidden")
        }
    }

    private fun startCountdownUpdates(endTimeMillis: Long) {
        stopCountdownUpdates()
        countdownRunnable = object : Runnable {
            override fun run() {
                updateCountdown(endTimeMillis)
                handler.postDelayed(this, COUNTDOWN_INTERVAL_MS)
            }
        }
        handler.postDelayed(countdownRunnable!!, COUNTDOWN_INTERVAL_MS)
    }

    private fun stopCountdownUpdates() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun updateCountdown(endTimeMillis: Long) {
        val countdownText = formatCountdown(endTimeMillis)
        overlayContainer?.let { container ->
            // Try XML layout first
            val countdownId = context.resources.getIdentifier("downtime_countdown", "id", context.packageName)
            container.findViewById<TextView?>(countdownId)?.text = countdownText

            // Also try fallback tag
            container.findViewWithTag<TextView>("countdown_text")?.text = countdownText
        }

        // Check if downtime has ended
        if (endTimeMillis > 0 && System.currentTimeMillis() >= endTimeMillis) {
            Log.d(TAG, "Downtime period ended — refreshing")
            SayphDowntimeChecker.forceRefresh()
            updateOverlayVisibility()
        }
    }

    private fun formatCountdown(endTimeMillis: Long): String {
        if (endTimeMillis <= 0) return "Re-enables soon"

        val remainingMs = endTimeMillis - System.currentTimeMillis()
        if (remainingMs <= 0) return "Re-enables soon"

        val hours = remainingMs / (1000 * 60 * 60)
        val minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)

        return when {
            hours > 0 -> "Re-enables in ${hours}h ${minutes}m"
            minutes > 0 -> "Re-enables in ${minutes}m"
            else -> "Re-enables soon"
        }
    }

    fun refreshStatus() {
        SayphDowntimeChecker.forceRefresh()
        updateOverlayVisibility()
    }

    fun isOverlayCurrentlyVisible(): Boolean = isOverlayVisible

    fun destroy() {
        stopCountdownUpdates()
        hideDowntimeOverlay()
    }
}
