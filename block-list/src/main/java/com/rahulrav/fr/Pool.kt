package com.rahulrav.fr

import java.util.concurrent.atomic.AtomicLong

/**
 * An Object pool that keeps track of scrap objects in a fixed size [ArrayDeque].
 *
 * @param size represents the size of the Object pool.
 * @param factory is a function that can be used to create the instances of the Object for the pool.
 * @param T represents an object that be re-used.
 */
@PublishedApi
internal open class Pool<T>(
    private val size: Int,
    private val isDebug: Boolean,
    private val factory: (owner: Pool<T>) -> T,
) {
    private var counter: AtomicLong? = null

    init {
        if (isDebug) {
            counter = AtomicLong(0L)
        }
    }

    // This class is intentionally lock free.
    // This is because, the only place where we recycle objects is using a ThreadLocal pool.
    internal val scrapPool: ArrayDeque<T> = ArrayDeque(size)

    init {
        // Eagerly create the objects for the pool
        repeat(size) { scrapPool.addLast(factory(this)) }
    }

    /** Obtain an instance of the object from the pool if possible. */
    @PublishedApi
    internal open fun obtain(): T {
        return obtainElement()
    }

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun obtainElement(): T {
        // Fallback to allocations when the scrap pool is empty.
        // It's not safe to drop trace packets the way given we might drop the packet which
        // represents ending the trace section. This will result in unmatched begin and ends.
        val element = scrapPool.removeFirstOrNull() ?: factory(this)
        if (isDebug && element != null) {
            counter?.incrementAndGet()
        }
        return element
    }

    @PublishedApi
    internal open fun release(element: T) {
        releaseElement(element)
    }

    @Suppress("NOTHING_TO_INLINE")
    internal inline fun releaseElement(element: T) {
        if (isDebug) {
            counter?.decrementAndGet()
        }
        if (scrapPool.size < size) {
            scrapPool.addFirst(element)
        }
    }

    internal fun count(): Long {
        return counter?.get() ?: 0L
    }
}
