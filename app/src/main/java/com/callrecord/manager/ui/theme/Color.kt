package com.callrecord.manager.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

// ===== Brand Colors =====
val BrandPrimaryDark = Color(0xFF1A1A2E)       // Deep blue-black
val BrandAccent = Color(0xFF10B981)             // Teal green
val BrandAccentDark = Color(0xFF34D399)         // Lighter teal for dark mode
val BrandError = Color(0xFFEF4444)              // Soft red
val BrandErrorDark = Color(0xFFF87171)          // Lighter red for dark mode
val BrandWarning = Color(0xFFF59E0B)            // Amber warning
val BrandInfo = Color(0xFF3B82F6)               // Blue info

// ===== Light Theme Colors =====
val LightPrimary = Color(0xFF1A1A2E)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE8E8F0)
val LightOnPrimaryContainer = Color(0xFF1A1A2E)

val LightSecondary = Color(0xFF6B7280)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFF3F4F6)
val LightOnSecondaryContainer = Color(0xFF374151)

val LightTertiary = Color(0xFF10B981)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFD1FAE5)
val LightOnTertiaryContainer = Color(0xFF065F46)

val LightBackground = Color(0xFFFAFAFA)
val LightOnBackground = Color(0xFF1A1A2E)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1A2E)
val LightSurfaceVariant = Color(0xFFF3F4F6)
val LightOnSurfaceVariant = Color(0xFF6B7280)
val LightSurfaceTint = Color(0xFF1A1A2E)

val LightOutline = Color(0xFFE5E7EB)
val LightOutlineVariant = Color(0xFFD1D5DB)

val LightError = Color(0xFFEF4444)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF991B1B)

val LightInverseSurface = Color(0xFF1A1A2E)
val LightInverseOnSurface = Color(0xFFF9FAFB)
val LightInversePrimary = Color(0xFF818CF8)

// ===== Dark Theme Colors =====
val DarkPrimary = Color(0xFF818CF8)             // Indigo light for dark mode
val DarkOnPrimary = Color(0xFF1A1A2E)
val DarkPrimaryContainer = Color(0xFF2A2A4A)
val DarkOnPrimaryContainer = Color(0xFFE0E0FF)

val DarkSecondary = Color(0xFF9CA3AF)
val DarkOnSecondary = Color(0xFF1F2937)
val DarkSecondaryContainer = Color(0xFF374151)
val DarkOnSecondaryContainer = Color(0xFFD1D5DB)

val DarkTertiary = Color(0xFF34D399)
val DarkOnTertiary = Color(0xFF064E3B)
val DarkTertiaryContainer = Color(0xFF065F46)
val DarkOnTertiaryContainer = Color(0xFFD1FAE5)

val DarkBackground = Color(0xFF0F0F0F)
val DarkOnBackground = Color(0xFFE5E7EB)
val DarkSurface = Color(0xFF1A1A1A)
val DarkOnSurface = Color(0xFFE5E7EB)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val DarkOnSurfaceVariant = Color(0xFF9CA3AF)
val DarkSurfaceTint = Color(0xFF818CF8)

val DarkOutline = Color(0xFF374151)
val DarkOutlineVariant = Color(0xFF4B5563)

val DarkError = Color(0xFFF87171)
val DarkOnError = Color(0xFF7F1D1D)
val DarkErrorContainer = Color(0xFF991B1B)
val DarkOnErrorContainer = Color(0xFFFECACA)

val DarkInverseSurface = Color(0xFFE5E7EB)
val DarkInverseOnSurface = Color(0xFF1A1A2E)
val DarkInversePrimary = Color(0xFF1A1A2E)

// ===== Semantic Status Colors =====
val StatusSuccess = Color(0xFF10B981)
val StatusSuccessContainer = Color(0xFFD1FAE5)
val StatusWarning = Color(0xFFF59E0B)
val StatusWarningContainer = Color(0xFFFEF3C7)
val StatusError = Color(0xFFEF4444)
val StatusErrorContainer = Color(0xFFFEE2E2)
val StatusInfo = Color(0xFF3B82F6)
val StatusInfoContainer = Color(0xFFDBEAFE)
val StatusPending = Color(0xFF8B5CF6)
val StatusPendingContainer = Color(0xFFEDE9FE)
val StatusProcessing = Color(0xFF0EA5E9)
val StatusProcessingContainer = Color(0xFFE0F2FE)

// Dark mode semantic colors
val StatusSuccessDark = Color(0xFF34D399)
val StatusSuccessContainerDark = Color(0xFF065F46)
val StatusWarningDark = Color(0xFFFBBF24)
val StatusWarningContainerDark = Color(0xFF78350F)
val StatusErrorDark = Color(0xFFF87171)
val StatusErrorContainerDark = Color(0xFF991B1B)
val StatusInfoDark = Color(0xFF60A5FA)
val StatusInfoContainerDark = Color(0xFF1E3A5F)
val StatusPendingDark = Color(0xFFA78BFA)
val StatusPendingContainerDark = Color(0xFF4C1D95)
val StatusProcessingDark = Color(0xFF38BDF8)
val StatusProcessingContainerDark = Color(0xFF0C4A6E)

// ===== Card Surface Colors =====
val CardSurfaceLight = Color(0xFFFFFFFF)
val CardSurfaceDark = Color(0xFF2A2A2A)

// ===== Dark-mode Aware Color Accessors =====
// Use these in composables to automatically pick light/dark variant

object StatusColors {
    val success: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusSuccessDark else StatusSuccess

    val successContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusSuccessContainerDark else StatusSuccessContainer

    val warning: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusWarningDark else StatusWarning

    val warningContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusWarningContainerDark else StatusWarningContainer

    val error: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusErrorDark else StatusError

    val errorContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusErrorContainerDark else StatusErrorContainer

    val info: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusInfoDark else StatusInfo

    val infoContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusInfoContainerDark else StatusInfoContainer

    val pending: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusPendingDark else StatusPending

    val pendingContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusPendingContainerDark else StatusPendingContainer

    val processing: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusProcessingDark else StatusProcessing

    val processingContainer: Color
        @Composable @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) StatusProcessingContainerDark else StatusProcessingContainer
}
