package com.rahulrav.fr

internal const val BIT_COUNT = 6

/** Size of each block is 2 ^ 6 (64) elements. */
public const val BLOCK_CAPACITY = 1.shl(bitCount = BIT_COUNT)
private const val BLOCK_POOL_SIZE = 256

@PublishedApi
internal val SENTINEL: Array<Any?> = arrayOfNulls(size = BLOCK_CAPACITY)

internal val BLOCK_POOL = ThreadLocal.withInitial {
    Pool(size = BLOCK_POOL_SIZE, isDebug = false) { owner ->
        Block(owner = owner)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun blockPool(): Pool<Block> = BLOCK_POOL.get()!!

/**
 * An implementation of a `List` like data structure that has both the characteristics of an
 * `ArrayList` as well as a `LinkedList` to avoid needing to copy when the underlying array when
 * the list needs to be resized as the size of the `List` grows. This implementation uses [Poolable]
 * [Block]'s to hold on to the items, and avoids GC pressure as much as possible by recycling
 * the blocks when [clear] is called.
 *
 * Note:
 * For primitives lists, we will need to specialize the implementation of the [Block] to avoid
 * boxing. This implementation does not do that yet.
 */
public class BlockLinkedList<T>(
    blkCount: Int = 4,
) {
    @JvmField
    internal val blkCount = blkCount.coerceIn(1, Int.MAX_VALUE)

    @JvmField
    @PublishedApi
    internal var blkList = MutableList(size = this.blkCount) { blockPool().obtain() }

    public var size: Int = 0
        internal set

    @JvmField
    internal var blkIndex: Int = 0

    @JvmField
    internal var currentBlk: Block = blkList[0]

    @Suppress("NOTHING_TO_INLINE")
    public operator fun plusAssign(value: T) {
        // Fast path
        if (currentBlk.bIdx < BLOCK_CAPACITY) {
            currentBlk.add(value)
        } else {
            blkIndex += 1
            val next: Block = if (blkIndex < blkList.size) {
                blkList[blkIndex]
            } else {
                val next = blockPool().obtain()
                blkList.add(next)
                next
            }
            next.add(value)
            currentBlk = next
        }
        size += 1
    }

    @Suppress("NOTHING_TO_INLINE")
    public operator fun get(index: Int): T {
        if (index !in 0..<size) throw IndexOutOfBoundsException("Index $index, Size: $size")
        // index / 64
        val offset = index.ushr(bitCount = BIT_COUNT)
        // index % 64
        val blkIndex = index.and(BLOCK_CAPACITY - 1)
        val block = blkList[offset]
        return block[blkIndex]
    }

    public fun clear() {
        blkIndex = 0
        size = 0
        // Check if we allocated more blocks
        // If not, all we need to do is to clear existing blocks.
        if (blkList.size <= blkCount) {
            for (block in blkList) {
                // Check if the block is actually being used before calling clear()
                // If we found a completely unused block, then we can stop.
                if (block.bIdx <= 0) break
                block.clear()
            }
        } else {
            // Recycle all the excess blocks
            for (i in blkCount until blkList.size) {
                blkList[i].recycle()
            }
            while (blkList.size > blkCount) {
                blkList.removeLastOrNull()
            }
            // Clear the remaining blocks
            for (block in blkList) {
                block.clear()
            }
        }
        currentBlk = blkList[blkIndex]
    }

    public inline fun forEach(block: (element: T) -> Unit) {
        forEachIndexed { _, element -> block(element) }
    }

    public inline fun forEachIndexed(block: (index: Int, element: T) -> Unit) {
        if (isEmpty()) return
        var blkIndex = 0
        // The absolute index
        var absIndex = 0
        while (blkIndex < blkList.size && absIndex < size) {
            val block = blkList[blkIndex]
            // The number of items in the block
            val count = block.bIdx
            // Iterate through, and proceed to the next block
            repeat(count) { index ->
                block(absIndex, block[index])
                absIndex += 1
            }
            blkIndex += 1
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    public inline fun isEmpty() = size == 0

    @Suppress("NOTHING_TO_INLINE")
    public inline fun isNotEmpty() = size > 0
}

public inline fun <T, R> BlockLinkedList<T>.use(block: () -> R): R {
    try {
        return block()
    } finally {
        clear()
    }
}

@PublishedApi
internal class Block(override val owner: Pool<Block>) : Poolable<Block>(owner = owner) {
    @JvmField
    @PublishedApi
    internal val array: Array<Any?> = arrayOfNulls(size = BLOCK_CAPACITY)

    @JvmField
    @PublishedApi
    internal var bIdx: Int = 0

    @Suppress("NOTHING_TO_INLINE")
    @PublishedApi
    internal inline fun <T> add(value: T) {
        array[bIdx] = value
        bIdx += 1
    }

    @Suppress("NOTHING_TO_INLINE")
    @PublishedApi
    internal inline operator fun <T> get(index: Int): T {
        @Suppress("UNCHECKED_CAST") return array[index] as T
    }

    @Suppress("NOTHING_TO_INLINE")
    @PublishedApi
    internal inline fun clear() {
        if (bIdx > 0) {
            System.arraycopy(SENTINEL, 0, array, 0, bIdx)
        }
        bIdx = 0
    }

    override fun recycle() {
        clear()
        blockPool().release(this)
    }
}
