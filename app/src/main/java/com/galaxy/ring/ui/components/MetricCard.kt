package com.galaxy.ring.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.ring.ui.theme.ShapeMedium
import com.galaxy.ring.ui.theme.Spacing

/**
 * MetricCard renders a glanceable, high-contrast biometric telemetry card.
 *
 * Adheres to Material 3 surface hierarchy and accessibility guidelines (minimum 48dp touch targets
 * and intentional decorative icon semantics). Displays category title, accent icon badge, primary
 * telemetry numeral, measurement unit, and optional secondary contextual status or sparkline.
 *
 * @param title The uppercase header or category name of the metric (e.g. "HEART RATE").
 * @param value The primary numeric value or state string (e.g. "72").
 * @param modifier Optional [Modifier] applied to the card root.
 * @param unit Optional measurement unit displayed adjacent to the value (e.g. "bpm").
 * @param subtitle Optional contextual footer description or timestamp.
 * @param icon Optional [ImageVector] displayed inside the accent icon badge.
 * @param iconContentDescription Optional accessible content description for the icon. Defaults to null (intentional decorative icon when displayed adjacent to the visible title text).
 * @param accentColor Semantic accent tint for the icon badge and highlights.
 * @param onClick Optional click handler; when provided, card satisfies minimum 48dp touch target.
 * @param testTag Optional test tag for automated testing.
 * @param trailingContent Optional custom composable slotted into the top-right header area.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val cardModifier = modifier
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .then(
            if (onClick != null) {
                Modifier
                    .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                    .clip(ShapeMedium)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick
                    )
            } else {
                Modifier
            }
        )

    Card(
        modifier = cardModifier,
        shape = ShapeMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            // Header row: Title + optional badge icon and trailing slot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (icon != null) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = iconContentDescription, // null is intentional when adjacent to visible title text
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trailingContent?.invoke()
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Body row: Large value number + unit
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs + 2.dp)
            ) {
                Text(
                    text = value.ifEmpty { "—" },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (value.isNotEmpty() && value != "—") {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xxs)
                    )
                }
            }

            // Footer row: Optional contextual subtitle
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xxs + 2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
