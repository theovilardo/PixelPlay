package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Song
import kotlin.random.Random

object QueueUtils {

    fun <T> fisherYatesCopy(source: List<T>, random: Random = Random.Default): List<T> {
        if (source.size <= 1) return source.toList()
        val mutable = source.toMutableList()
        for (i in mutable.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            if (i != j) {
                val tmp = mutable[i]
                mutable[i] = mutable[j]
                mutable[j] = tmp
            }
        }
        return mutable
    }

    private fun generateShuffleOrder(size: Int, anchorIndex: Int, random: Random = Random.Default): IntArray {
        if (size <= 1) return IntArray(size) { it }

        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
        }

        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
        }

        val order = IntArray(size)
        var poolIndex = 0
        for (i in 0 until size) {
            order[i] = if (i == clampedAnchor) clampedAnchor else pool[poolIndex++]
        }
        return order
    }

    fun buildAnchoredShuffleQueue(
        currentQueue: List<Song>,
        anchorIndex: Int,
        random: Random = Random.Default
    ): List<Song> {
        if (currentQueue.size <= 1) return currentQueue.toList()
        val order = generateShuffleOrder(currentQueue.size, anchorIndex, random)
        return List(order.size) { idx -> currentQueue[order[idx]] }
    }
}
