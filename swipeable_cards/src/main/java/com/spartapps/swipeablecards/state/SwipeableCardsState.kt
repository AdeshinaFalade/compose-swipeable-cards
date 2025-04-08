package com.spartapps.swipeablecards.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.spartapps.swipeablecards.ui.SwipeableCardDirection
import com.spartapps.swipeablecards.ui.SwipeableCardsDefaults

/**
 * Manages the state of a SwipeableCards stack.
 *
 * This class maintains the current position in the card stack and handles navigation
 * between cards while enforcing boundaries and tracking navigation possibilities.
 */
class SwipeableCardsState(
    val visibleCardsInStack: Int = SwipeableCardsDefaults.VISIBLE_CARDS_IN_STACK,
    initialCardIndex: Int = 0,
    private val itemCount: () -> Int,
) {

    /**
     * The size of the container holding the swipeable cards.
     * Used for calculating proper animation boundaries and card positioning.
     */
    var size by mutableStateOf(IntSize.Zero)
        private set

    /**
     * Stores the current drag offsets for each card by index.
     * Used to track and animate multiple cards independently.
     */
    val dragOffsets = mutableStateMapOf<Int, Offset>()

    /**
     * The index of the currently displayed top card.
     * Read-only from outside the class, modified through navigation methods.
     */
    var currentCardIndex by mutableIntStateOf(initialCardIndex)
        private set

    /**
     * Tracks cards that are currently in a swiping animation.
     * Used to maintain proper rendering order during transitions.
     */
    val swipingVisibleCards = mutableStateListOf<Int>()

    /**
     * Indicates whether backwards navigation is possible (true if not at first card).
     */
    var canSwipeBack by mutableStateOf(currentCardIndex > 0)
        private set

    val visibleCardIndexes = derivedStateOf {
        val maxVisible = currentCardIndex + visibleCardsInStack - 1
        val lastIndex = minOf(maxVisible, itemCount() - 1)
        (currentCardIndex..lastIndex).toList() + swipingVisibleCards
    }

    internal fun onDragOffsetChange(
        index: Int,
        offset: Offset,
    ) {
        dragOffsets[index] = offset
    }

    internal fun onSizeChange(size: IntSize) {
        this.size = size
    }

    /**
     * Goes back to the previous card in the stack.
     * Has no effect if already at the first card.
     * Updates [canSwipeBack] based on the new position.
     */
    fun goBack() {
        swipingVisibleCards.remove(currentCardIndex)
        if (currentCardIndex > 0) {
            currentCardIndex--
            canSwipeBack = currentCardIndex > 0
            dragOffsets.remove(currentCardIndex)
            swipingVisibleCards.remove(currentCardIndex)
        }
    }

    /**
     * Moves to the next card in the stack.
     * Has no effect if already at the last card.
     * Updates [canSwipeBack] based on the new position.
     */
    fun moveNext() {
        swipingVisibleCards.remove(currentCardIndex - 1)
        if (currentCardIndex < itemCount() -  1) {
            currentCardIndex++
            canSwipeBack = currentCardIndex > 0
        }
    }

    /**
     * Programmatically swipes the current top card in the specified direction.
     * This will animate the card off-screen and advance to the next card.
     *
     * @param direction The direction to swipe the card ([SwipeableCardDirection.Left] or [SwipeableCardDirection.Right]).
     */
    fun swipe(direction: SwipeableCardDirection) {
        val targetX = when (direction) {
            SwipeableCardDirection.Left -> -size.width.toFloat() * 1.5f
            SwipeableCardDirection.Right -> size.width.toFloat() * 1.5f
        }

        swipingVisibleCards.add(currentCardIndex)
        dragOffsets[currentCardIndex] = Offset(targetX, 0f)
        moveNext()
    }

    /**
     * Programmatically rewinds the card stack to the previous card with an animated transition.
     *
     * This method brings back the last dismissed card from off-screen and animates it into view.
     * It's useful for implementing an "undo" action after a swipe.
     *
     * The card will appear from the specified [direction] and smoothly return to its resting position.
     * Has no effect if the current card is the first in the stack.
     *
     * @param direction The direction from which the card should animate in
     *                  ([SwipeableCardDirection.Left] or [SwipeableCardDirection.Right]).
     */
    suspend fun rewind(direction: SwipeableCardDirection = SwipeableCardDirection.Left) {
        if (currentCardIndex == 0) return // can't go back from first card

        val targetIndex = currentCardIndex - 1
        currentCardIndex = targetIndex
        canSwipeBack = currentCardIndex > 0

        // Add to swiping cards to ensure it renders
        swipingVisibleCards.add(targetIndex)

        // Set initial offset so it looks like it's off-screen
        val initialX = when (direction) {
            SwipeableCardDirection.Left -> -size.width.toFloat() * 1.5f
            SwipeableCardDirection.Right -> size.width.toFloat() * 1.5f
        }

        dragOffsets[targetIndex] = Offset(initialX, 0f)

        // Animate it into place (offset → 0)
        animateCardToPosition(targetIndex)
    }

    private suspend fun animateCardToPosition(index: Int) {
        val animationSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
        val initialOffset = dragOffsets[index] ?: Offset.Zero

        // Animate X and Y separately
        val animX = Animatable(initialOffset.x)
        val animY = Animatable(initialOffset.y)


        animX.animateTo(0f, animationSpec) {
            dragOffsets[index] = Offset(value, animY.value)
        }
        animY.animateTo(0f, animationSpec) {
            dragOffsets[index] = Offset(animX.value, value)
        }


        // Remove from swiping list when done
        swipingVisibleCards.remove(index)
    }
}


