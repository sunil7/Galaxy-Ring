package com.galaxy.ring.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Material 3 Color Roles — Primary Palette (Cool Teal / Cyan Accent)
// =============================================================================

// Dark Theme Primary & Tonal Accents
val PrimaryDark = Color(0xFF38BDF8)           // Sky / Cool Cyan 400
val OnPrimaryDark = Color(0xFF003544)         // High-contrast text/icon on primary (contrast > 6.5:1)
val PrimaryContainerDark = Color(0xFF0A3748)  // Deep teal container
val OnPrimaryContainerDark = Color(0xFFBAE6FD) // High-contrast tint on primary container

// Light Theme Primary & Tonal Accents
val PrimaryLight = Color(0xFF0369A1)          // Cool Teal/Cyan 700 (contrast against white > 5.5:1)
val OnPrimaryLight = Color(0xFFFFFFFF)        // High-contrast text/icon on primary (contrast > 5.5:1)
val PrimaryContainerLight = Color(0xFFE0F2FE) // Soft cool teal container
val OnPrimaryContainerLight = Color(0xFF075985) // Deep teal on container (contrast > 7:1)

// Secondary Accent (Neon Emerald / Bio-sensor tracking)
val SecondaryDark = Color(0xFF10B981)
val OnSecondaryDark = Color(0xFF003822)
val SecondaryContainerDark = Color(0xFF064E3B)
val OnSecondaryContainerDark = Color(0xFFA7F3D0)

val SecondaryLight = Color(0xFF059669)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFD1FAE5)
val OnSecondaryContainerLight = Color(0xFF064E3B)

// Tertiary Accent (Electric Violet / Nocturnal sleep analytics)
val TertiaryDark = Color(0xFFA855F7)
val OnTertiaryDark = Color(0xFF380D6B)
val TertiaryContainerDark = Color(0xFF4C1D95)
val OnTertiaryContainerDark = Color(0xFFE9D5FF)

val TertiaryLight = Color(0xFF7C3AED)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFEDE9FE)
val OnTertiaryContainerLight = Color(0xFF4C1D95)

// Error Semantic
val ErrorDark = Color(0xFFF43F5E)
val OnErrorDark = Color(0xFF4C0519)
val ErrorContainerDark = Color(0xFF881337)
val OnErrorContainerDark = Color(0xFFFFE4E6)

val ErrorLight = Color(0xFFE11D48)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFE4E6)
val OnErrorContainerLight = Color(0xFF881337)

// =============================================================================
// Layered Neutral Surfaces (Dark Scheme)
// =============================================================================
val DarkBackground = Color(0xFF090D16)
val DarkOnBackground = Color(0xFFF1F5F9)

val DarkSurface = Color(0xFF0F172A)
val DarkOnSurface = Color(0xFFF8FAFC)          // Contrast against DarkSurface: 17.5:1 (>= 4.5:1)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)   // Contrast against DarkSurface: 7.2:1 (>= 4.5:1)

val DarkSurfaceDim = Color(0xFF090D16)
val DarkSurfaceBright = Color(0xFF243048)
val DarkSurfaceContainerLowest = Color(0xFF070B12)
val DarkSurfaceContainerLow = Color(0xFF0E1422)
val DarkSurfaceContainer = Color(0xFF141C2E)
val DarkSurfaceContainerHigh = Color(0xFF1B253B)
val DarkSurfaceContainerHighest = Color(0xFF222F49)

val DarkOutline = Color(0xFF334155)
val DarkOutlineVariant = Color(0xFF1E293B)

// =============================================================================
// Layered Neutral Surfaces (Light Scheme)
// =============================================================================
val LightBackground = Color(0xFFF8FAFC)
val LightOnBackground = Color(0xFF0F172A)

val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0F172A)         // Contrast against LightSurface: 17.5:1 (>= 4.5:1)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightOnSurfaceVariant = Color(0xFF334155)  // Contrast against LightSurface: 10.3:1 (>= 4.5:1)

val LightSurfaceDim = Color(0xFFE2E8F0)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF8FAFC)
val LightSurfaceContainer = Color(0xFFF1F5F9)
val LightSurfaceContainerHigh = Color(0xFFE2E8F0)
val LightSurfaceContainerHighest = Color(0xFFCBD5E1)

val LightOutline = Color(0xFF94A3B8)
val LightOutlineVariant = Color(0xFFE2E8F0)

// =============================================================================
// Shared Telemetry & Biometric Accent Colors (for charts, sparklines, badges)
// =============================================================================
val CyberCyan = Color(0xFF38BDF8)
val NeonEmerald = Color(0xFF10B981)
val ElectricViolet = Color(0xFFA855F7)
val AmberGlow = Color(0xFFF59E0B)
val RosePulse = Color(0xFFF43F5E)
val HrvTeal = Color(0xFF2DD4BF)

val DarkCardBorder = DarkOutline
val HeartRateRed = RosePulse
val Spo2Cyan = CyberCyan
val SleepViolet = ElectricViolet
val ActivityGreen = NeonEmerald
val StressAmber = AmberGlow

val TextPrimary = DarkOnSurface
val TextSecondary = DarkOnSurfaceVariant
val TextMuted = Color(0xFF64748B)
val RingDivider = Color(0xFF1E293B)
