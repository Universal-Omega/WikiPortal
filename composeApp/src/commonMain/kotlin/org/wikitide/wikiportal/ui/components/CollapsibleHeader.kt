package org.wikitide.wikiportal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Stable
class CollapsibleHeaderState internal constructor(private val fullHeightPx: Float) {
    var collapsedPx: Float by mutableFloatStateOf(0f)
        private set

    val collapseFraction: Float
        get() = if (fullHeightPx <= 0f) 0f else (collapsedPx / fullHeightPx).coerceIn(0f, 1f)

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            val previous = collapsedPx
            collapsedPx = (collapsedPx - available.y).coerceIn(0f, fullHeightPx)
            return Offset(0f, -(collapsedPx - previous))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            val previous = collapsedPx
            collapsedPx = (collapsedPx - available.y).coerceIn(0f, fullHeightPx)
            return Offset(0f, previous - collapsedPx)
        }
    }

    /** Snaps back open, for when a tab or search results swap out the underlying list. */
    fun expand() {
        collapsedPx = 0f
    }
}

@Composable
fun rememberCollapsibleHeaderState(fullHeight: Dp): CollapsibleHeaderState {
    val fullHeightPx = with(LocalDensity.current) { fullHeight.toPx() }
    return remember(fullHeightPx) { CollapsibleHeaderState(fullHeightPx) }
}

@Composable
fun CollapsibleSearchFieldHost(
    collapseFraction: Float,
    fullHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(fullHeight * (1f - collapseFraction))
            .clipToBounds()
            .graphicsLayer { alpha = (1f - collapseFraction * 1.6f).coerceIn(0f, 1f) },
    ) {
        content()
    }
}

@Composable
fun CollapsedHeaderIconButton(
    visibleFraction: Float,
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = visibleFraction > 0.4f,
        modifier = modifier.graphicsLayer { alpha = visibleFraction },
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
