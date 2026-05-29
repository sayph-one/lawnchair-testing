package app.lawnchair.ui.permissions

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.lawnchair.ui.downtime.DowntimeContact
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.android.launcher3.R

/**
 * Visual constants for the permissions overlay — Sayph brand colours.
 * Mirrors the downtime "custom" theme (deep navy gradient + bright red accent) so the
 * permissions overlay and downtime overlay feel like a single design family.
 */
private object PermissionsPalette {
    val gradientStart = Color(0xFF0B1929)
    val gradientEnd = Color(0xFF1F3A5F)
    val accentColor = Color(0xFFFE5757) // SayphRed
}

/**
 * Full-screen overlay shown on the Lawnchair home screen when the device is registered but
 * one or more required permissions are missing. Visual language mirrors
 * [app.lawnchair.ui.downtime.DowntimeScreen] — Lottie hero with glow, gradient bg,
 * emergency contacts pinned to bottom — using Sayph brand navy + red.
 *
 * [callingEnabled] is false when the device has no default phone app set: tapping an
 * emergency contact in that state would route a call to the telephony stack but no
 * InCallService would render the call UI, leaving the user with no way to hang up. When
 * disabled, contact avatars are dimmed and tapping them is a no-op; a small note explains.
 */
@Composable
fun PermissionsScreen(
    contacts: List<DowntimeContact>,
    callingEnabled: Boolean,
    onOpenWizard: () -> Unit,
    onCallContact: (DowntimeContact) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PermissionsPalette.gradientStart,
                        PermissionsPalette.gradientEnd,
                    ),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LottieHero(accentColor = PermissionsPalette.accentColor)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Set-up",
                color = Color.White,
                style = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                ),
                textAlign = TextAlign.Center,
            )

            Text(
                text = "incomplete",
                color = Color.White.copy(alpha = 0.6f),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Sayph needs a few more permissions before this device is ready to use.",
                color = Color.White.copy(alpha = 0.78f),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 22.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            OpenSetupButton(
                accentColor = PermissionsPalette.accentColor,
                onClick = onOpenWizard,
            )
        }

        if (contacts.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                EmergencyContactsSection(
                    contacts = contacts,
                    accentColor = PermissionsPalette.accentColor,
                    callingEnabled = callingEnabled,
                    onCallContact = onCallContact,
                )
            }
        }
    }
}

@Composable
private fun LottieHero(accentColor: Color) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lottie_permissions_incomplete),
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 0.6f,
    )

    // Same recolouring trick used by DowntimeScreen — every keypath tinted to the accent.
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.STROKE_COLOR,
            value = accentColor.toArgb(),
            keyPath = arrayOf("**"),
        ),
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = accentColor.toArgb(),
            keyPath = arrayOf("**"),
        ),
    )

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
                            accentColor.copy(alpha = 0.18f),
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
private fun OpenSetupButton(
    accentColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "openSetupPress",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(accentColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Open setup",
            color = Color.White,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}

@Composable
private fun EmergencyContactsSection(
    contacts: List<DowntimeContact>,
    accentColor: Color,
    callingEnabled: Boolean,
    onCallContact: (DowntimeContact) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (callingEnabled) "NEED HELP? TAP TO CALL" else "EMERGENCY CONTACTS",
            color = Color.White.copy(alpha = if (callingEnabled) 0.5f else 0.35f),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            contacts.take(4).forEach { contact ->
                EmergencyContactAvatar(
                    contact = contact,
                    accentColor = accentColor,
                    enabled = callingEnabled,
                    onClick = { onCallContact(contact) },
                )
            }
        }

        if (!callingEnabled) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Calling unavailable until the phone app is set up.",
                color = Color.White.copy(alpha = 0.45f),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmergencyContactAvatar(
    contact: DowntimeContact,
    accentColor: Color,
    enabled: Boolean,
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
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "avatarPress",
    )

    val disabledAlpha = 0.35f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else disabledAlpha
            }
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
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
