package com.galaxy.ring.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * MetricCard: Backwards-compatible delegator to [com.galaxy.ring.ui.components.MetricCard].
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
    com.galaxy.ring.ui.components.MetricCard(
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
 * EmptyState: Backwards-compatible delegator to [com.galaxy.ring.ui.components.EmptyState].
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
    com.galaxy.ring.ui.components.EmptyState(
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
 * SectionHeader: Backwards-compatible delegator to [com.galaxy.ring.ui.components.SectionHeader].
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    com.galaxy.ring.ui.components.SectionHeader(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        action = action
    )
}
