package org.wikitide.wikiportal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

private data class RowBounds(val top: Float, val height: Float) {
    val center: Float get() = top + height / 2f
    operator fun contains(y: Float): Boolean = y in top..(top + height)
}

@Stable
class DragReorderState<T> internal constructor(
    initialItems: List<T>,
    private val id: (T) -> String,
    private val rank: (T) -> Rank,
    private val isContainer: (T) -> Boolean,
    private val haptics: HapticFeedback,
    private val onMove: (id: String, newRank: Rank) -> Unit,
    private val onDropIntoContainer: ((id: String, containerId: String) -> Unit)?,
    private val onExitBounds: ((id: String, direction: Int) -> Unit)?,
) {
    var items: List<T> by mutableStateOf(initialItems)
        private set
    var draggingId: String? by mutableStateOf(null)
        private set
    var dragOffsetY: Float by mutableFloatStateOf(0f)
        private set

    var hoverContainerId: String? by mutableStateOf(null)
        private set

    private val positions = mutableMapOf<String, RowBounds>()

    fun onItemsChanged(newItems: List<T>) {
        if (draggingId == null) items = newItems
    }

    internal fun recordPosition(itemId: String, top: Float, height: Float) {
        positions[itemId] = RowBounds(top, height)
    }

    fun onDragStart(itemId: String) {
        draggingId = itemId
        dragOffsetY = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDragEnd() {
        val draggedId = draggingId ?: return
        val finalOffsetY = dragOffsetY
        val hoveredContainer = hoverContainerId
        draggingId = null
        dragOffsetY = 0f
        hoverContainerId = null

        val index = items.indexOfFirst { id(it) == draggedId }
        if (index < 0) return

        if (hoveredContainer != null) {
            onDropIntoContainer?.invoke(draggedId, hoveredContainer)
            return
        }

        if (onExitBounds != null && !isContainer(items[index])) {
            val ownHeight = positions[draggedId]?.height ?: 0f
            if (ownHeight > 0f) {
                if (index == 0 && finalOffsetY < -ownHeight) {
                    onExitBounds.invoke(draggedId, -1)
                    return
                }
                if (index == items.lastIndex && finalOffsetY > ownHeight) {
                    onExitBounds.invoke(draggedId, 1)
                    return
                }
            }
        }

        val loRank = items.getOrNull(index - 1)?.let(rank)?.value ?: ""
        val hiRank = items.getOrNull(index + 1)?.let(rank)?.value
        onMove(draggedId, Rank(RankUtil.between(loRank, hiRank)))
    }

    fun onDrag(itemId: String, deltaY: Float) {
        dragOffsetY += deltaY
        val order = items
        val currentIndex = order.indexOfFirst { id(it) == itemId }
        if (currentIndex < 0) return
        val draggedItem = order[currentIndex]
        val originalCenter = positions[itemId]?.center ?: return
        val draggedCenter = originalCenter + dragOffsetY

        if (onDropIntoContainer != null && !isContainer(draggedItem)) {
            val hovered = order.firstOrNull { candidate ->
                id(candidate) != itemId && isContainer(candidate) && positions[id(candidate)]?.contains(draggedCenter) == true
            }
            val hoveredId = hovered?.let(id)
            if (hoveredId != hoverContainerId) {
                hoverContainerId = hoveredId
                if (hoveredId != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (hoveredId != null) return
        }

        val neighborIndex = when {
            dragOffsetY < 0 && currentIndex > 0 -> currentIndex - 1
            dragOffsetY > 0 && currentIndex < order.lastIndex -> currentIndex + 1
            else -> return
        }
        val neighborCenter = positions[id(order[neighborIndex])]?.center ?: return
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
    isContainer: (T) -> Boolean = { false },
    onMove: (id: String, newRank: Rank) -> Unit,
    onDropIntoContainer: ((id: String, containerId: String) -> Unit)? = null,
    onExitBounds: ((id: String, direction: Int) -> Unit)? = null,
): DragReorderState<T> {
    val haptics = LocalHapticFeedback.current
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDropIntoContainer by rememberUpdatedState(onDropIntoContainer)
    val currentOnExitBounds by rememberUpdatedState(onExitBounds)
    val state = remember(key) {
        DragReorderState(
            initialItems = items,
            id = id,
            rank = rank,
            isContainer = isContainer,
            haptics = haptics,
            onMove = { itemId, newRank -> currentOnMove(itemId, newRank) },
            onDropIntoContainer = { itemId, containerId -> currentOnDropIntoContainer?.invoke(itemId, containerId) },
            onExitBounds = { itemId, direction -> currentOnExitBounds?.invoke(itemId, direction) },
        )
    }
    LaunchedEffect(items) { state.onItemsChanged(items) }
    return state
}

fun Modifier.trackDragPosition(state: DragReorderState<*>, itemId: String): Modifier =
    onGloballyPositioned { coordinates -> state.recordPosition(itemId, coordinates.positionInRoot().y, coordinates.size.height.toFloat()) }
