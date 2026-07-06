# BlockLinkedList

`BlockLinkedList` is a highly optimized, `List`-like data structure written in Kotlin that combines
the best characteristics of an `ArrayList` and a `LinkedList`.

It is specifically designed for high-performance use cases where list allocations are frequent
and garbage collection (GC) overhead needs to be minimized. It achieves this by storing elements
in a sequence of fixed-size blocks and recycling those blocks via a thread-local pool once they are
no longer needed.

---

## Key Features
- **$\mathcal{O}(1)$ Random Access**: Constant-time lookup of elements using rapid bitwise shifts
  to resolve block offsets instantly.
- **Low GC Pressure**: Implements eager recycling of blocks back into a `ThreadLocal` pool upon
  being cleared, reducing memory allocation churn.
- **Automatic Resource Cleanup**: Offers an inline `.use { ... }` extension function to guarantee
  that blocks are automatically cleared and returned to the pool after use.

---

## Performance Characteristics

| Operation                 |                    `BlockLinkedList`                     |                       `ArrayList`                        |       `LinkedList`       | Description                                                             |
|:--------------------------|:--------------------------------------------------------:|:--------------------------------------------------------:|:------------------------:|:------------------------------------------------------------------------|
| **Append (`+=` / `add`)** | $\mathcal{O}(1)$ amortized / $\mathcal{O}(N)$ worst-case | $\mathcal{O}(1)$ amortized / $\mathcal{O}(N)$ worst-case |     $\mathcal{O}(1)$     | Avoids array-copy resizing penalty unless you exceed `blkCount` blocks. |
| **Random Access (`get`)** |                   **$\mathcal{O}(1)$**                   |                     $\mathcal{O}(1)$                     |     $\mathcal{O}(N)$     | Bitwise-shift block index resolution.                                   |
| **Iteration (`forEach`)** |                   **$\mathcal{O}(N)$**                   |                     $\mathcal{O}(N)$                     |     $\mathcal{O}(N)$     | Linear time with very low traversal overhead.                           |
| **Memory Allocation**     |                     **Low / Pooled**                     |                   High (Reallocations)                   | High (Per-node wrappers) | Recycles internal array blocks automatically.                           |

---

## How It Works

`BlockLinkedList` represents a hybrid approach:

1. **Blocked Storage**: The list consists of a array of internal `Block` objects. Each block wraps
   an array of size $2^6 = 64$ (`BLOCK_CAPACITY`).
2. **Thread-Local Block Pool**: When a list is `clear()`ed, blocks exceeding the configured warm
   reserve count (`blkCount`) are returned to a thread-local object pool (`BLOCK_POOL`), allowing
   them to be instantly reused by other lists on the same thread without prompting heap allocation
   or garbage collection.

---

## Usage Examples

### 1. Basic Operations

```kotlin
import com.rahulrav.fr.BlockLinkedList

fun main() {
    // Create a list with an initial capacity of 4 blocks (up to 256 elements)
    val list = BlockLinkedList<String>(blkCount = 4)

    // Append elements using the += operator (guaranteed O(1) time)
    list += "Kotlin"
    list += "Android"
    list += "Performance"

    // Retrieve elements (guaranteed O(1) constant-time lookup)
    val element = list[1] // "Android"
    println("Element at index 1: $element")
    println("List size: ${list.size}")

    // Iterating over the list
    list.forEach { item ->
        println(item)
    }

    // Reset the list and recycle excess blocks to the thread-local pool
    list.clear()
}
```

### 2. Automatic Lifecycle Management with `.use`

Using the `.use` extension function ensures that the list is immediately cleared and its blocks are
returned to the pool, even if an exception occurs during execution. This prevents memory leaks.

```kotlin
import com.rahulrav.fr.BlockLinkedList
import com.rahulrav.fr.use

fun doWork(list: BlockLinkedList<Int>) {
    list.use {
        // Use the list, and automatically release all the _excess_ blocks to the
        // ThreadLocal pool when done.
    }
}
```

---

## Thread Safety

Individual instances of `BlockLinkedList` are **not thread-safe** and must not be accessed
concurrently by multiple threads without external synchronization.

However, the underlying `Block` pool is fully insulated on a per-thread basis using `ThreadLocal`.
This guarantees that pooling remains entirely thread-safe and safe to use across different threads
without contention.

---

## Limitations

- **Primitive Specialization**: Currently, this implementation does not yet specialize the
  underlying block array for primitive types (e.g., `IntArray`, `LongArray`). Storing primitive
  types will result in automatic box/unbox overhead.
