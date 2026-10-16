package com.sportzfy.live.core.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR

/**
 * The app's single icon family: Lucide outline icons (2px stroke), shipped
 * as vector drawables. Unused icons are removed by resource shrinking.
 */
object SportzfyIcons {
    @DrawableRes val Home = LucideR.drawable.lucide_ic_house
    @DrawableRes val LiveTv = LucideR.drawable.lucide_ic_tv
    @DrawableRes val Today = LucideR.drawable.lucide_ic_calendar_days
    @DrawableRes val Tomorrow = LucideR.drawable.lucide_ic_calendar_clock
    @DrawableRes val Fixtures = LucideR.drawable.lucide_ic_calendar
    @DrawableRes val AllMatches = LucideR.drawable.lucide_ic_list
    @DrawableRes val Saved = LucideR.drawable.lucide_ic_star
    @DrawableRes val Search = LucideR.drawable.lucide_ic_search
    @DrawableRes val Menu = LucideR.drawable.lucide_ic_menu
    @DrawableRes val Close = LucideR.drawable.lucide_ic_x
    @DrawableRes val Back = LucideR.drawable.lucide_ic_arrow_left
    @DrawableRes val Refresh = LucideR.drawable.lucide_ic_refresh_cw
    @DrawableRes val Settings = LucideR.drawable.lucide_ic_settings
    @DrawableRes val About = LucideR.drawable.lucide_ic_info
    @DrawableRes val Football = LucideR.drawable.lucide_ic_goal
    // Lucide has no cricket icon; a trophy reads as "competition" without misleading.
    @DrawableRes val Cricket = LucideR.drawable.lucide_ic_trophy
    @DrawableRes val OtherSports = LucideR.drawable.lucide_ic_volleyball
    @DrawableRes val Play = LucideR.drawable.lucide_ic_play
    @DrawableRes val ChevronRight = LucideR.drawable.lucide_ic_chevron_right
    @DrawableRes val Shield = LucideR.drawable.lucide_ic_shield_check
    @DrawableRes val Alert = LucideR.drawable.lucide_ic_circle_alert
    @DrawableRes val Offline = LucideR.drawable.lucide_ic_wifi_off
    @DrawableRes val Announcement = LucideR.drawable.lucide_ic_megaphone
    @DrawableRes val ExternalLink = LucideR.drawable.lucide_ic_external_link
    @DrawableRes val Clock = LucideR.drawable.lucide_ic_clock
    @DrawableRes val Language = LucideR.drawable.lucide_ic_languages
    @DrawableRes val Delete = LucideR.drawable.lucide_ic_trash_2
    @DrawableRes val Star = LucideR.drawable.lucide_ic_star
    @DrawableRes val Pause = LucideR.drawable.lucide_ic_pause
    @DrawableRes val Previous = LucideR.drawable.lucide_ic_skip_back
    @DrawableRes val Next = LucideR.drawable.lucide_ic_skip_forward
    @DrawableRes val VolumeOn = LucideR.drawable.lucide_ic_volume_2
    @DrawableRes val VolumeOff = LucideR.drawable.lucide_ic_volume_x
    @DrawableRes val Fullscreen = LucideR.drawable.lucide_ic_maximize
    @DrawableRes val ExitFullscreen = LucideR.drawable.lucide_ic_minimize
    @DrawableRes val PictureInPicture = LucideR.drawable.lucide_ic_picture_in_picture_2
    @DrawableRes val Expand = LucideR.drawable.lucide_ic_maximize_2
    @DrawableRes val ChevronDown = LucideR.drawable.lucide_ic_chevron_down
    @DrawableRes val Globe = LucideR.drawable.lucide_ic_globe
}

@Composable
fun SportzfyIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 22.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
