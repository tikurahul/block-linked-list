package com.rahulrav.fr

import org.junit.Test
import kotlin.test.assertEquals

internal fun <T> BlockLinkedList<T>.prettyPrint() {
    println()
    forEachIndexed { index, element ->
        if (index > 0 && index % 10 == 0) println()
        print("$element\t")
    }
    println()
}

class BlockLinkedListTest {
    @Test
    fun testBasic() {
        val list = BlockLinkedList<String>()
        list += "Test"
        list += "Hello"
        assertEquals(0, list.blkIndex)
        assertEquals(2, list.size)
        assertEquals("Test", list[0])
        assertEquals("Hello", list[1])
        list.clear()
    }

    @Test
    fun testBlockOverflow() {
        val list = BlockLinkedList<String>(blkCount = 0)
        assertEquals(0, list.blkIndex)
        // There should always be 1 block at the very least.
        assertEquals(1, list.blkList.size)
        repeat(BLOCK_CAPACITY + 1) {
            list += "E $it"
        }
        assertEquals(1, list.blkIndex)
        assertEquals(BLOCK_CAPACITY + 1, list.size)
        list.prettyPrint()
        list.clear()
    }
}
