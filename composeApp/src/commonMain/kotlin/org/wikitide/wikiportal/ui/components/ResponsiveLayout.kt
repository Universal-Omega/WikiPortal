package org.wikitide.wikiportal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

/** Below this, always [WindowWidthClass.Compact], a single phone-width column. */
private val MEDIUM_WIDTH_BREAKPOINT = 600.dp

/** At and above this, [WindowWidthClass.Expanded], a tablet or desktop window with room to spare. */
private val EXPANDED_WIDTH_BREAKPOINT = 840.dp

@Stable
fun windowWidthClassFor(width: Dp): WindowWidthClass = when {
    width < MEDIUM_WIDTH_BREAKPOINT -> WindowWidthClass.Compact
    width < EXPANDED_WIDTH_BREAKPOINT -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
}

@Composable
fun ContentWidthLimiter(modifier: Modifier = Modifier, maxWidth: Dp = 720.dp, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = windowWidthClassFor(this.maxWidth)
        if (widthClass == WindowWidthClass.Expanded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = maxWidth).fillMaxHeight()) {
                    content()
                }
            }
        } else {
            content()
        }
    }
}

@Composable
fun TwoPaneLayout(
    showDetailOnCompact: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    listWidth: Dp = 320.dp,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = windowWidthClassFor(this.maxWidth)
        if (widthClass == WindowWidthClass.Expanded) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.widthIn(max = listWidth).fillMaxHeight()) { list() }
                Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
            }
        } else if (showDetailOnCompact) {
            Box(Modifier.fillMaxSize()) { detail() }
        } else {
            Box(Modifier.fillMaxWidth().fillMaxHeight()) { list() }
        }
    }
}
