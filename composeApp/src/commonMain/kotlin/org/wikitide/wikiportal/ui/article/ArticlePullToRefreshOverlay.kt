package org.wikitide.wikiportal.ui.article

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A manual swipe-to-refresh: a thin invisible strip at the very top
 * edge that tracks a downward drag starting there, driving [state] by
 * hand rather than the usual Modifier.pullToRefresh. That modifier
 * depends on the nested scroll protocol, which a native WebView does
 * not take part in, so it would never receive the drag at all.
 *
 * [tabKey] scopes the internal drag-tracking state to whichever tab
 * this overlay is currently drawn for, the same way the caller keys
 * its own per-tab state, so switching tabs doesn't carry over a drag
 * that was already in progress on a different one.
 */
@Composable
fun ArticlePullToRefreshOverlay(
    tabKey: Any,
    state: PullToRefreshState,
    isRefreshing: Boolean,
    onRefreshingChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dragAmount by remember(tabKey) { mutableStateOf(0f) }
    val pullThresholdPx = with(LocalDensity.current) { PullToRefreshDefaults.PositionalThreshold.toPx() }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.TopCenter)
                .pointerInput(tabKey) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val triggered = dragAmount >= pullThresholdPx && !isRefreshing
                            dragAmount = 0f
                            scope.launch {
                                if (triggered) {
                                    state.animateToThreshold()
                                    onRefreshingChange(true)
                                    onRefresh()
                                } else {
                                    state.animateToHidden()
                                }
                            }
                        },
                        onVerticalDrag = { change, delta ->
                            if (!isRefreshing) {
                                dragAmount = (dragAmount + delta).coerceIn(0f, pullThresholdPx * 1.5f)
                                change.consume()
                                scope.launch {
                                    state.snapTo((dragAmount / pullThresholdPx).coerceIn(0f, 1f))
                                }
                            }
                        },
                    )
                },
        )

        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
