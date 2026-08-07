package org.wikitide.wikiportal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import org.wikitide.wikiportal.data.model.Rank
import org.wikitide.wikiportal.util.RankUtil

class DragReorderState<T> internal constructor(
    initialItems: List<T>,
    private val id: (T) -> String,
    private val rank: (T) -> Rank,
    private val haptics: HapticFeedback,
    private val onMove: (id: String, newRank: Rank) -> Unit,
) {
    var items: List<T> by mutableStateOf(initialItems)
        private set
    var draggingId: String? by mutableStateOf(null)
        private set
    var dragOffsetY: Float by mutableStateOf(0f)
        private set

    private val positions = mutableMapOf<String, Float>()

    fun onItemsChanged(newItems: List<T>) {
        if (draggingId == null) items = newItems
    }

    internal fun recordPosition(itemId: String, centerY: Float) {
        positions[itemId] = centerY
    }

    fun onDragStart(itemId: String) {
        draggingId = itemId
        dragOffsetY = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDragEnd() {
        val draggedId = draggingId ?: return
        draggingId = null
        dragOffsetY = 0f
        val index = items.indexOfFirst { id(it) == draggedId }
        if (index < 0) return
        val loRank = items.getOrNull(index - 1)?.let(rank)?.value ?: ""
        val hiRank = items.getOrNull(index + 1)?.let(rank)?.value
        onMove(draggedId, Rank(RankUtil.between(loRank, hiRank)))
    }

    fun onDrag(itemId: String, deltaY: Float) {
        dragOffsetY += deltaY
        val order = items
        val currentIndex = order.indexOfFirst { id(it) == itemId }
        if (currentIndex < 0) return
        val originalCenter = positions[itemId] ?: return
        val draggedCenter = originalCenter + dragOffsetY

        val neighborIndex = when {
            dragOffsetY < 0 && currentIndex > 0 -> currentIndex - 1
            dragOffsetY > 0 && currentIndex < order.lastIndex -> currentIndex + 1
            else -> return
        }
        val neighborCenter = positions[id(order[neighborIndex])] ?: return
        val crossedNeighbor = if (neighborIndex > currentIndex) draggedCenter > neighborCenter else draggedCenter < neighborCenter
        if (crossedNeighbor) {
            val reordered = order.toMutableList()
            val moved = reordered.removeAt(currentIndex)
            reordered.add(neighborIndex, moved)
            items = reordered
            dragOffsetY -= (neighborCenter - originalCenter)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun <T> rememberDragReorderState(
    items: List<T>,
    id: (T) -> String,
    rank: (T) -> Rank,
    key: Any?,
    onMove: (id: String, newRank: Rank) -> Unit,
): DragReorderState<T> {
    val haptics = LocalHapticFeedback.current
    val currentOnMove by rememberUpdatedState(onMove)
    val state = remember(key) { DragReorderState(items, id, rank, haptics) { itemId, newRank -> currentOnMove(itemId, newRank) } }
    LaunchedEffect(items) { state.onItemsChanged(items) }
    return state
}

fun Modifier.trackDragPosition(state: DragReorderState<*>, itemId: String): Modifier =
    onGloballyPositioned { coordinates -> state.recordPosition(itemId, coordinates.positionInRoot().y + coordinates.size.height / 2f) }
