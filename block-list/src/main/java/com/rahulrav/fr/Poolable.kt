package com.rahulrav.fr

/** Represents an object that can be reused using a [Pool]. This avoids GC churn. */
public abstract class Poolable<T : Poolable<T>>
internal constructor(internal open val owner: Pool<T>) {
    /** Recycles the object, and hands it back to the pool. */
    public abstract fun recycle()
}
