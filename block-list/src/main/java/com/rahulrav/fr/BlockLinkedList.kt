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
 * An implementation of a `List`-like data structure that combines the characteristics of an
 * `ArrayList` and a `LinkedList`. It avoids the overhead of copying the underlying array when
 * the list grows, while maintaining fast O(1) random access. This implementation uses [Poolable]
 * [Block]s to hold the items, and avoids GC pressure as much as possible by recycling
 * the blocks when [clear] is called.
 *
 * ### Performance Characteristics
 * - **Append (`+=` / [plusAssign])**: O(1) amortized and O(n) worst-case when you exceed [blkCount].
 * - **Random Access (`[]` / [get])**: O(1) constant-time lookup using bitwise shifts to resolve
 *   block offsets instantly.
 * - **Iteration ([forEach] / [forEachIndexed])**: O(N) linear time with very low overhead.
 *
 * ### Thread Safety
 * Individual instances of [BlockLinkedList] are **not** thread-safe and must not be accessed
 * concurrently by multiple threads without external synchronization. However, the underlying
 * [Block] pooling mechanism is thread-local and entirely safe to use across different threads.
 *
 * Note:
 * For primitive lists, we will need to specialize the implementation of the [Block] to avoid
 * boxing. This implementation does not do that yet.
 *
 * @param blkCount The initial number of blocks to pre-allocate and keep warm.
 *                 Defaults to 4 (representing 256 elements). When [clear] is called,
 *                 any blocks beyond this count are recycled back to the pool, while
 *                 exactly this many blocks are retained for immediate reuse.
 */
public class BlockLinkedList<T>(
    blkCount: Int = 4,
) {
    @JvmField
    internal val blkCount = blkCount.coerceIn(1, Int.MAX_VALUE)

    @JvmField
    @PublishedApi
    internal var blkArray: Array<Block> = Array(size = this.blkCount) { blockPool().obtain() }

    public var size: Int = 0
        internal set

    @JvmField
    internal var blkIndex: Int = 0

    @JvmField
    internal var currentBlk: Block = blkArray[0]

    /**
     * Appends the specified [value] to the end of this list.
     *
     * This operation is guaranteed to be O(1) and does not involve copying any existing elements,
     * as it simply writes to the current active [Block] or obtains a new one from the pool.
     */
    @Suppress("NOTHING_TO_INLINE")
    public operator fun plusAssign(value: T) {
        // Fast path
        if (currentBlk.bIdx < BLOCK_CAPACITY) {
            currentBlk.add(value)
        } else {
            blkIndex += 1
            val next: Block = if (blkIndex < blkArray.size) {
                blkArray[blkIndex]
            } else {
                // We ran out of blocks
                val placeholder = blockPool().obtain()
                val newBlkArray = Array(size = blkCount * 2) { placeholder }
                System.arraycopy(blkArray, 0, newBlkArray, 0, blkCount)
                for (i in blkCount until newBlkArray.size) {
                    newBlkArray[i] = blockPool().obtain()
                }
                placeholder.recycle()
                blkArray = newBlkArray
                blkArray[blkIndex]
            }
            next.add(value)
            currentBlk = next
        }
        size += 1
    }

    /**
     * Returns the element at the specified [index] in the list.
     *
     * @throws IndexOutOfBoundsException if the index is out of range (`index < 0 || index >= size`).
     */
    @Suppress("NOTHING_TO_INLINE")
    public operator fun get(index: Int): T {
        if (index !in 0..<size) throw IndexOutOfBoundsException("Index $index, Size: $size")
        // index / 64
        val offset = index.ushr(bitCount = BIT_COUNT)
        // index % 64
        val blkIndex = index.and(BLOCK_CAPACITY - 1)
        val block = blkArray[offset]
        return block[blkIndex]
    }

    /**
     * Resets the list to an empty state.
     *
     * To prevent memory leaks, references to all held items are cleared. If the list has grown
     * beyond its initial [blkCount], the excess blocks are recycled back into the thread-local pool
     * for reuse by other lists.
     */
    public fun clear() {
        blkIndex = 0
        size = 0
        // Check if we allocated more blocks
        // If not, all we need to do is to clear existing blocks.
        if (blkArray.size <= blkCount) {
            for (block in blkArray) {
                // Check if the block is actually being used before calling clear()
                // If we found a completely unused block, then we can stop.
                if (block.bIdx <= 0) break
                block.clear()
            }
        } else {
            // Recycle all the excess blocks
            for (i in blkCount until blkArray.size) {
                blkArray[i].recycle()
            }
            while (blkArray.size > blkCount) {
                val placeholder = blockPool().obtain()
                val newBlkArray = Array(size = blkCount) { placeholder }
                System.arraycopy(blkArray, 0, newBlkArray, 0, blkCount)
                blkArray = newBlkArray
                placeholder.recycle()
            }
            // Clear the remaining blocks
            for (block in blkArray) {
                block.clear()
            }
        }
        currentBlk = blkArray[blkIndex]
    }

    /**
     * Performs the given [block] action on each element.
     */
    public inline fun forEach(block: (element: T) -> Unit) {
        forEachIndexed { _, element -> block(element) }
    }

    /**
     * Performs the given [block] action on each element, providing its sequential index.
     */
    public inline fun forEachIndexed(block: (index: Int, element: T) -> Unit) {
        if (isEmpty()) return
        var blkIndex = 0
        // The absolute index
        var absIndex = 0
        while (blkIndex < blkArray.size && absIndex < size) {
            val block = blkArray[blkIndex]
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

    /**
     * Returns `true` if the list is empty (contains no elements), `false` otherwise.
     */
    @Suppress("NOTHING_TO_INLINE")
    public inline fun isEmpty() = size == 0

    /**
     * Returns `true` if the list is not empty (contains at least one element), `false` otherwise.
     */
    @Suppress("NOTHING_TO_INLINE")
    public inline fun isNotEmpty() = size > 0
}

/**
 * Executes the given [block] action on this [BlockLinkedList] and guarantees that
 * [BlockLinkedList.clear] is called immediately afterward, even if an exception is thrown.
 *
 * This is the recommended way to use temporary lists to ensure that blocks are eagerly
 * recycled and references to held items are cleared, avoiding memory leaks.
 *
 * ### Example:
 * ```kotlin
 * val list = BlockLinkedList<String>()
 * list.use {
 *     list += "item"
 *     // Do work...
 * } // list is automatically cleared here
 * ```
 *
 * Alternatively:
 *
 * ```kotlin
 * fun <T> doWork(list: BlockLinkedList<T>) {
 *   list.use {
 *     // Do the work, and at the end of the block
 *     // everything is automatically cleared.
 *   }
 * }
 * ```
 */
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
