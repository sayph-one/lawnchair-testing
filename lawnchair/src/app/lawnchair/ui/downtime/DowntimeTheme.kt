package app.lawnchair.ui.downtime

import androidx.compose.ui.graphics.Color
import com.android.launcher3.R

/**
 * Visual theme for the downtime overlay, varies by routine type.
 * Each theme defines the gradient, accent colour, copy, and the Lottie animation
 * shown as the centerpiece.
 */
data class DowntimeTheme(
    val gradientStart: Color,
    val gradientEnd: Color,
    val accentColor: Color,
    val headingWord: String,   // large word, e.g. "Bedtime"
    val subheading: String,    // small text, e.g. "lock enabled"
    val lottieResId: Int,
)

object DowntimeThemes {
    fun forType(type: String?): DowntimeTheme = when (type?.lowercase()) {
        "bedtime" -> DowntimeTheme(
            gradientStart = Color(0xFF0B1430),
            gradientEnd = Color(0xFF2B2157),
            accentColor = Color(0xFFB8A9FF), // soft lavender
            headingWord = "Bedtime",
            subheading = "lock enabled",
            lottieResId = R.raw.lottie_downtime_bedtime,
        )
        "school" -> DowntimeTheme(
            gradientStart = Color(0xFF0F3B4C),
            gradientEnd = Color(0xFF1B6F7A),
            accentColor = Color(0xFFFFD166), // warm gold
            headingWord = "School",
            subheading = "lock enabled",
            lottieResId = R.raw.lottie_downtime_school,
        )
        "dinner" -> DowntimeTheme(
            gradientStart = Color(0xFF3A1414),
            gradientEnd = Color(0xFF7A2E1A),
            accentColor = Color(0xFFFFB366), // warm amber
            headingWord = "Dinner",
            subheading = "lock enabled",
            lottieResId = R.raw.lottie_downtime_dinner,
        )
        else -> DowntimeTheme(
            gradientStart = Color(0xFF0B1929),
            gradientEnd = Color(0xFF1F3A5F),
            accentColor = Color(0xFFFE5757), // brand red
            headingWord = "Device",
            subheading = "lock enabled",
            lottieResId = R.raw.lottie_downtime_custom,
        )
    }
}
