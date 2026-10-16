package com.sportzfy.live.core.theme

import androidx.compose.ui.graphics.Color

/** SPORTZFY design tokens (UI style guide). */
object SportzfyColors {
    /** Main app background. */
    val Charcoal = Color(0xFF0B0F17)

    /** Bars and sheets that sit directly on the background. */
    val Slate = Color(0xFF0E131D)

    /** Match and channel cards. */
    val SlateRaised = Color(0xFF171E2B)

    /** Active rows and subtle emphasis. */
    val SelectedSurface = Color(0xFF191F2C)

    /** Dividers and borders. */
    val Outline = Color(0xFF273246)

    /** Primary accent: selected tab, live badge, primary action. */
    val Rose = Color(0xFFF01E4F)
    val RoseDim = Color(0xFFA3123A)

    /** Filled accent surfaces under white text (buttons, selected chips): 5.1:1 with white. */
    val RoseFill = Color(0xFFD91442)

    /** Accent-coloured text on dark surfaces: 5.2:1 or better on every dark token. */
    val RoseText = Color(0xFFFF4D73)

    /** Starred channels. */
    val Gold = Color(0xFFFACC15)

    /** HD tags. */
    val Emerald = Color(0xFF10B981)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFAAB7CC)
    /** Tertiary text; 5.2:1 or better on every dark surface (WCAG AA). */
    val TextMuted = Color(0xFF8391A8)
}
