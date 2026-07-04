package com.rahulrav.fr.benchmark

import androidx.benchmark.BlackHole
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rahulrav.fr.BlockLinkedList
import com.rahulrav.fr.use
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalBenchmarkConfigApi::class)
@RunWith(AndroidJUnit4::class)
class BlockLinkedListBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun insertBenchmark() {
        val list = BlockLinkedList<Int>()
        benchmarkRule.measureRepeated {
            list.use {
                repeat(2 * 64) {
                    list += it
                }
            }
        }
    }

    @Test
    fun forEachBenchmark() {
        val list = BlockLinkedList<Int>()
        list.use {
            repeat(5 * 64) {
                list += it
            }
            benchmarkRule.measureRepeated {
                list.forEach { BlackHole.consume(it) }
            }
        }
    }
}
