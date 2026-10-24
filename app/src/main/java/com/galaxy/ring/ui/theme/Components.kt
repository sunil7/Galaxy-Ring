package com.galaxy.ring.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.galaxy.ring.ui.components.EmptyState as SharedEmptyState
import com.galaxy.ring.ui.components.MetricCard as SharedMetricCard
import com.galaxy.ring.ui.components.SectionHeader as SharedSectionHeader
import com.galaxy.ring.ui.components.StatPill as SharedStatPill

/**
 * Backward compatibility alias delegating to [com.galaxy.ring.ui.components.MetricCard].
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
    accentColor: Color = CyberCyan,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    SharedMetricCard(
        title = title,
        value = value,
        modifier = modifier,
        unit = unit,
        subtitle = subtitle,
        icon = icon,
        iconContentDescription = iconContentDescription,
        accentColor = accentColor,
        onClick = onClick,
        testTag = testTag,
        trailingContent = trailingContent
    )
}

/**
 * Backward compatibility alias delegating to [com.galaxy.ring.ui.components.EmptyState].
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    testTag: String? = null
) {
    SharedEmptyState(
        message = message,
        modifier = modifier,
        icon = icon,
        iconContentDescription = iconContentDescription,
        actionLabel = actionLabel,
        onAction = onAction,
        testTag = testTag
    )
}

/**
 * Backward compatibility alias delegating to [com.galaxy.ring.ui.components.SectionHeader].
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    SharedSectionHeader(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        action = action
    )
}

/**
 * Backward compatibility alias delegating to [com.galaxy.ring.ui.components.StatPill].
 */
@Composable
fun StatPill(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    accentColor: Color = CyberCyan,
    showDot: Boolean = false,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    SharedStatPill(
        label = label,
        modifier = modifier,
        value = value,
        icon = icon,
        iconContentDescription = iconContentDescription,
        accentColor = accentColor,
        showDot = showDot,
        isSelected = isSelected,
        onClick = onClick,
        testTag = testTag
    )
}
