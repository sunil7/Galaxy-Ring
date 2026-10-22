package com.galaxy.ring.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.galaxy.ring.ui.theme.Spacing

/**
 * StatPill displays a compact status indicator, biometric badge, or glanceable tag.
 *
 * Supports an optional leading icon, status badge dot, label, optional value, and
 * click interaction with compliant 48dp minimum touch target.
 *
 * @param label The descriptive label of the status or metric (e.g. "Resting", "Battery", "Ready").
 * @param modifier Optional [Modifier] applied to the pill container.
 * @param value Optional numeric or short value string (e.g. "86%").
 * @param icon Optional leading [ImageVector] for the pill.
 * @param iconContentDescription Optional content description for the icon. Defaults to null (intentional decorative icon when displayed adjacent to the visible label text).
 * @param accentColor Color accent applied to the icon, dot, or selection border.
 * @param showDot When true, renders a small colored status dot before the label.
 * @param isSelected When true, highlights the pill with accent background and border.
 * @param onClick Optional click callback; when set, ensures minimum 48dp touch target.
 * @param testTag Optional test tag for automated testing.
 */
@Composable
fun StatPill(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    showDot: Boolean = false,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val pillModifier = modifier
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .then(
            if (onClick != null) {
                Modifier
                    .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick
                    )
            } else {
                Modifier.defaultMinSize(minHeight = 32.dp)
            }
        )

    val containerColor = if (isSelected) {
        accentColor.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val borderColor = if (isSelected) {
        accentColor.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Surface(
        modifier = pillModifier,
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription, // null is intentional when adjacent to visible label text
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}
