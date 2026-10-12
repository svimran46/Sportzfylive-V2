package com.sportzfy.live.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.sportzfy.live.core.theme.SportzfyColors
import com.sportzfy.live.core.theme.SportzfyIcon
import com.sportzfy.live.core.theme.SportzfyIcons
import com.sportzfy.live.presentation.components.DrawerRow
import com.sportzfy.live.presentation.components.DrawerSectionLabel
import com.sportzfy.live.presentation.components.SportzfyWordmark
import com.sportzfy.live.presentation.components.tvFocus
import com.sportzfy.live.presentation.model.DrawerItem

/**
 * Side drawer: brand header, main links, sports and the Settings/About
 * footer. About 80% of the screen width, never wider than 320dp; it scrolls
 * on short screens and stays clear of the system bars.
 */
@Composable
fun SportzfyDrawer(
    selected: DrawerItem?,
    versionName: String,
    onSelect: (DrawerItem) -> Unit,
    onClose: () -> Unit,
) {
    val width = min(LocalConfiguration.current.screenWidthDp.dp * 0.8f, 320.dp)
    ModalDrawerSheet(
        modifier = Modifier.width(width).testTag("drawer"),
        drawerContainerColor = SportzfyColors.Slate,
        drawerContentColor = SportzfyColors.TextPrimary,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SportzfyWordmark()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.tvFocus(CircleShape)) {
                    SportzfyIcon(SportzfyIcons.Close, contentDescription = "Close menu")
                }
            }

            DrawerSection("Browse", DrawerItem.MAIN, selected, onSelect)
            Spacer(Modifier.height(8.dp))
            DrawerSection("Sports", DrawerItem.SPORTS, selected, onSelect)

            HorizontalDivider(color = SportzfyColors.Outline, modifier = Modifier.padding(vertical = 12.dp))
            DrawerItem.FOOTER.forEach { item ->
                DrawerRow(item.label, item.icon, selected == item, onClick = { onSelect(item) })
            }
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.labelSmall,
                color = SportzfyColors.TextMuted,
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun DrawerSection(
    title: String,
    items: List<DrawerItem>,
    selected: DrawerItem?,
    onSelect: (DrawerItem) -> Unit,
) {
    DrawerSectionLabel(title)
    items.forEach { item ->
        DrawerRow(item.label, item.icon, selected == item, onClick = { onSelect(item) })
    }
}

@get:DrawableRes
private val DrawerItem.icon: Int
    get() = when (this) {
        DrawerItem.HOME -> SportzfyIcons.Home
        DrawerItem.LIVE_TV -> SportzfyIcons.LiveTv
        DrawerItem.FIXTURES -> SportzfyIcons.Fixtures
        DrawerItem.SAVED -> SportzfyIcons.Saved
        DrawerItem.SEARCH -> SportzfyIcons.Search
        DrawerItem.FOOTBALL -> SportzfyIcons.Football
        DrawerItem.CRICKET -> SportzfyIcons.Cricket
        DrawerItem.OTHER_SPORTS -> SportzfyIcons.OtherSports
        DrawerItem.SETTINGS -> SportzfyIcons.Settings
        DrawerItem.ABOUT -> SportzfyIcons.About
    }
