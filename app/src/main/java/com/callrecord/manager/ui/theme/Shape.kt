package com.callrecord.manager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),    // Small chips, tags
    small = RoundedCornerShape(12.dp),        // Small cards, inputs
    medium = RoundedCornerShape(16.dp),       // Cards
    large = RoundedCornerShape(20.dp),        // Dialogs
    extraLarge = RoundedCornerShape(24.dp)    // FAB, large buttons
)

// Specific shape constants for direct use
val CardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(24.dp)
val DialogShape = RoundedCornerShape(20.dp)
val StatusTagShape = RoundedCornerShape(12.dp)
val SearchBarShape = RoundedCornerShape(28.dp)
val AvatarShape = RoundedCornerShape(50)       // Circular
val BottomSheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
val PlayerButtonShape = RoundedCornerShape(50)  // Circular play button
