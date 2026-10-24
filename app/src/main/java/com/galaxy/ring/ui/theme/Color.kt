package com.galaxy.ring.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Cool Teal / Blue Primary Palette
// =============================================================================
val PrimaryDark = Color(0xFF38BDF8)           // Cool sky cyan / teal primary (Dark)
val OnPrimaryDark = Color(0xFF003549)
val PrimaryContainerDark = Color(0xFF004D68)
val OnPrimaryContainerDark = Color(0xFFBBE9FF)

val PrimaryLight = Color(0xFF0284C7)          // Cool teal blue primary (Light)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFBAE6FD)
val OnPrimaryContainerLight = Color(0xFF001F2C)

// =============================================================================
// Secondary Palette (Vital Emerald / Mint)
// =============================================================================
val SecondaryDark = Color(0xFF34D399)
val OnSecondaryDark = Color(0xFF003822)
val SecondaryContainerDark = Color(0xFF005234)
val OnSecondaryContainerDark = Color(0xFFA7F3D0)

val SecondaryLight = Color(0xFF059669)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFA7F3D0)
val OnSecondaryContainerLight = Color(0xFF002113)

// =============================================================================
// Tertiary Palette (Restful Violet)
// =============================================================================
val TertiaryDark = Color(0xFFA78BFA)
val OnTertiaryDark = Color(0xFF2E1065)
val TertiaryContainerDark = Color(0xFF4C1D95)
val OnTertiaryContainerDark = Color(0xFFDDD6FE)

val TertiaryLight = Color(0xFF7C3AED)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFDDD6FE)
val OnTertiaryContainerLight = Color(0xFF2E1065)

// =============================================================================
// Error Palette
// =============================================================================
val ErrorDark = Color(0xFFF87171)
val OnErrorDark = Color(0xFF450A0A)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFECACA)

val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)

// =============================================================================
// Neutral Surfaces & Containers (Dark)
// Body text contrast ≥ 4.5:1 against surfaces (F8FAFC on 0F172A is 17.5:1)
// =============================================================================
val BackgroundDark = Color(0xFF090D16)
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF111827)
val OnSurfaceDark = Color(0xFFF8FAFC)
val SurfaceVariantDark = Color(0xFF1F293D)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF334155)
val OutlineVariantDark = Color(0xFF1E293B)

val SurfaceContainerLowestDark = Color(0xFF060910)
val SurfaceContainerLowDark = Color(0xFF0B111E)
val SurfaceContainerDark = Color(0xFF111827)
val SurfaceContainerHighDark = Color(0xFF182236)
val SurfaceContainerHighestDark = Color(0xFF1F293D)

// =============================================================================
// Neutral Surfaces & Containers (Light)
// Body text contrast ≥ 4.5:1 against surfaces (0F172A on FFFFFF is 17.5:1)
// =============================================================================
val BackgroundLight = Color(0xFFF8FAFC)
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val OnSurfaceVariantLight = Color(0xFF334155)
val OutlineLight = Color(0xFFCBD5E1)
val OutlineVariantLight = Color(0xFFE2E8F0)

val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF8FAFC)
val SurfaceContainerLight = Color(0xFFF1F5F9)
val SurfaceContainerHighLight = Color(0xFFE2E8F0)
val SurfaceContainerHighestLight = Color(0xFFCBD5E1)

// =============================================================================
// Semantic Metric Accents (Stable compatibility references)
// =============================================================================
val CyberCyan = PrimaryDark
val NeonEmerald = Color(0xFF10B981)
val ElectricViolet = Color(0xFFA855F7)
val AmberGlow = Color(0xFFF59E0B)
val RosePulse = Color(0xFFF43F5E)

val HeartRateRed = RosePulse
val Spo2Cyan = CyberCyan
val SleepViolet = ElectricViolet
val ActivityGreen = NeonEmerald
val StressAmber = AmberGlow
val HrvTeal = Color(0xFF2DD4BF)

// Backward-compatible surface references
val DarkBackground = BackgroundDark
val DarkSurface = SurfaceDark
val DarkSurfaceVariant = SurfaceVariantDark
val DarkCardBorder = OutlineDark

// Typography helper references
val TextPrimary = OnSurfaceDark
val TextSecondary = OnSurfaceVariantDark
val TextMuted = Color(0xFF64748B)
val RingDivider = OutlineVariantDark
