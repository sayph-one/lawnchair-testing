package app.lawnchair.ui.downtime

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DowntimeContact(
    val name: String,
    val phone: String,
)

/**
 * Full-screen downtime overlay shown on Lawnchair when a routine is active.
 * Theme and copy adapt to the routine type (bedtime, school, dinner, custom).
 */
@Composable
fun DowntimeScreen(
    routineType: String,
    routineName: String,
    endTimeMillis: Long,
    contacts: List<DowntimeContact>,
    onCallContact: (DowntimeContact) -> Unit,
) {
    val theme = remember(routineType) { DowntimeThemes.forType(routineType) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(theme.gradientStart, theme.gradientEnd),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Hero animation
            LottieHero(theme = theme)

            Spacer(modifier = Modifier.height(24.dp))

            // Heading word (e.g. "Bedtime")
            Text(
                text = theme.headingWord,
                color = Color.White,
                style = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                ),
                textAlign = TextAlign.Center,
            )

            // "lock enabled" subtitle
            Text(
                text = theme.subheading,
                color = Color.White.copy(alpha = 0.6f),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Time remaining + back-at pill
            TimeRemainingCard(
                endTimeMillis = endTimeMillis,
                accentColor = theme.accentColor,
            )
        }

        // Emergency contacts pinned to bottom
        if (contacts.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                EmergencyContactsSection(
                    contacts = contacts,
                    accentColor = theme.accentColor,
                    onCallContact = onCallContact,
                )
            }
        }
    }
}

@Composable
private fun LottieHero(theme: DowntimeTheme) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(theme.lottieResId))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 0.6f,
    )

    // Recolor all strokes and fills to match the accent
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.STROKE_COLOR,
            value = theme.accentColor.toArgb(),
            keyPath = arrayOf("**"),
        ),
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = theme.accentColor.toArgb(),
            keyPath = arrayOf("**"),
        ),
    )

    // Soft glow behind the animation
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            theme.accentColor.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(180.dp),
            dynamicProperties = dynamicProperties,
        )
    }
}

@Composable
private fun TimeRemainingCard(
    endTimeMillis: Long,
    accentColor: Color,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endTimeMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val remainingMs = (endTimeMillis - now).coerceAtLeast(0)
    val remainingText = remember(remainingMs) { formatRemaining(remainingMs) }
    val backAtText = remember(endTimeMillis) {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        "Back at ${fmt.format(Date(endTimeMillis))}"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(32.dp),
                )
                .padding(horizontal = 36.dp, vertical = 18.dp),
        ) {
            Text(
                text = remainingText,
                color = Color.White,
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp,
                ),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = backAtText,
            color = accentColor.copy(alpha = 0.9f),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            ),
        )
    }
}

@Composable
private fun EmergencyContactsSection(
    contacts: List<DowntimeContact>,
    accentColor: Color,
    onCallContact: (DowntimeContact) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "NEED HELP? TAP TO CALL",
            color = Color.White.copy(alpha = 0.5f),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            contacts.take(4).forEach { contact ->
                EmergencyContactAvatar(
                    contact = contact,
                    accentColor = accentColor,
                    onClick = { onCallContact(contact) },
                )
            }
        }
    }
}

@Composable
private fun EmergencyContactAvatar(
    contact: DowntimeContact,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val initials = remember(contact.name) {
        contact.name
            .split(" ")
            .mapNotNull { part -> part.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "?" }
    }
    val firstName = remember(contact.name) {
        contact.name.split(" ").firstOrNull() ?: contact.name
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "avatarPress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.28f),
                            accentColor.copy(alpha = 0.12f),
                        ),
                    ),
                )
                .border(
                    width = 1.5.dp,
                    color = accentColor.copy(alpha = 0.55f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = Color.White,
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = firstName,
            color = Color.White.copy(alpha = 0.85f),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

/** Format remaining milliseconds as "5h 32m", "42m", or "Almost done". */
private fun formatRemaining(remainingMs: Long): String {
    if (remainingMs <= 0) return "Almost done"
    val minutes = (remainingMs / 60_000).toInt()
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours >= 1 -> "${hours}h ${mins}m"
        mins >= 1 -> "${mins}m"
        else -> "<1m"
    }
}
