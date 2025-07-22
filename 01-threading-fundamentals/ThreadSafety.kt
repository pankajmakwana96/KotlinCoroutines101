/**
 * # Thread Safety and Synchronization
 * 
 * ## Problem Description
 * When multiple threads access shared data concurrently without proper
 * synchronization, it leads to data corruption, race conditions, and
 * unpredictable behavior. This example demonstrates thread safety issues
 * and various synchronization mechanisms to solve them.
 * 
 * ## Solution Approach
 * Multiple synchronization strategies are presented:
 * - synchronized blocks and methods
 * - volatile variables for visibility
 * - Atomic classes for lock-free operations
 * - ReentrantLock for advanced locking
 * - Concurrent collections for thread-safe data structures
 * 
 * ## Key Learning Points
 * - Shared mutable state is dangerous in concurrent environments
 * - Synchronization ensures thread safety but has performance costs
 * - Different synchronization mechanisms for different use cases
 * - Memory visibility vs atomicity concerns
 * - Lock-free programming with atomic operations
 * 
 * ## Performance Considerations
 * - Synchronized blocks: ~25-50ns overhead per access
 * - Atomic operations: ~5-10ns overhead
 * - Volatile reads: similar to normal reads
 * - Volatile writes: memory barrier overhead (~10-20ns)
 * - Lock contention can cause significant slowdowns
 * 
 * ## Common Pitfalls
 * - Forgetting to synchronize all access to shared state
 * - Using synchronized on wrong objects
 * - Deadlock from nested synchronized blocks
 * - Performance degradation from over-synchronization
 * - Assuming volatile provides atomicity for compound operations
 * 
 * ## Real-World Applications
 * - Web server request counters
 * - Cache implementations
 * - Connection pools
 * - Shared configuration objects
 * - Statistics collection
 * 
 * ## Related Concepts
 * - RaceConditions.kt - See what happens without synchronization
 * - DeadlockPrevention.kt - Advanced locking scenarios
 * - VolatileAndAtomic.kt - Memory model details
 */

package threading

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Unsafe counter that demonstrates thread safety issues
 */
class UnsafeCounter {
    private var count = 0
    
    fun increment() {
        count++ // This is NOT atomic! It's actually: read -> add -> write
    }
    
    fun decrement() {
        count--
    }
    
    fun get(): Int = count
}

/**
 * Thread-safe counter using synchronized methods
 */
class SynchronizedCounter {
    private var count = 0
    
    @Synchronized
    fun increment() {
        count++
    }
    
    @Synchronized  
    fun decrement() {
        count--
    }
    
    @Synchronized
    fun get(): Int = count
}

/**
 * Thread-safe counter using synchronized blocks with custom lock
 */
class CustomLockCounter {
    private var count = 0
    private val lock = Any() // Custom lock object
    
    fun increment() {
        synchronized(lock) {
            count++
        }
    }
    
    fun decrement() {
        synchronized(lock) {
            count--
        }
    }
    
    fun get(): Int {
        synchronized(lock) {
            return count
        }
    }
}

/**
 * Lock-free thread-safe counter using atomic operations
 */
class AtomicCounter {
    private val count = AtomicInteger(0)
    
    fun increment() {
        count.incrementAndGet()
    }
    
    fun decrement() {
        count.decrementAndGet()
    }
    
    fun get(): Int = count.get()
    
    // Atomic compare-and-swap operation
    fun compareAndSet(expected: Int, new: Int): Boolean {
        return count.compareAndSet(expected, new)
    }
}

/**
 * Advanced thread-safe counter with ReentrantLock
 */
class ReentrantLockCounter {
    private var count = 0
    private val lock = ReentrantLock()
    
    fun increment() {
        lock.withLock {
            count++
        }
    }
    
    fun decrement() {
        lock.withLock {
            count--
        }
    }
    
    fun get(): Int {
        lock.withLock {
            return count
        }
    }
    
    // Try to acquire lock without blocking
    fun tryIncrement(): Boolean {
        return if (lock.tryLock()) {
            try {
                count++
                true
            } finally {
                lock.unlock()
            }
        } else {
            false
        }
    }
}

/**
 * Read-write lock example for scenarios with many readers, few writers
 */
class ReadWriteCounter {
    private var count = 0
    private val rwLock = ReentrantReadWriteLock()
    private val readLock = rwLock.readLock()
    private val writeLock = rwLock.writeLock()
    
    fun increment() {
        writeLock.withLock {
            count++
        }
    }
    
    fun decrement() {
        writeLock.withLock {
            count--
        }
    }
    
    fun get(): Int {
        readLock.withLock {
            return count
        }
    }
    
    // Multiple threads can read simultaneously
    fun getWithDelay(): Int {
        readLock.withLock {
            Thread.sleep(100) // Simulate slow read operation
            return count
        }
    }
}

/**
 * Thread-safe collections example
 */
class ThreadSafeCollections {
    
    // Thread-safe map using ConcurrentHashMap
    private val concurrentMap = ConcurrentHashMap<String, Int>()
    
    // Regular HashMap with synchronization (less efficient)
    private val regularMap = mutableMapOf<String, Int>()
    private val mapLock = Any()
    
    fun putConcurrent(key: String, value: Int) {
        concurrentMap[key] = value
    }
    
    fun getConcurrent(key: String): Int? {
        return concurrentMap[key]
    }
    
    fun putSynchronized(key: String, value: Int) {
        synchronized(mapLock) {
            regularMap[key] = value
        }
    }
    
    fun getSynchronized(key: String): Int? {
        synchronized(mapLock) {
            return regularMap[key]
        }
    }
    
    // Atomic operations on ConcurrentHashMap
    fun incrementConcurrent(key: String): Int {
        return concurrentMap.compute(key) { _, oldValue ->
            (oldValue ?: 0) + 1
        } ?: 1
    }
}

/**
 * Volatile variable example for visibility
 */
class VolatileExample {
    @Volatile
    private var flag = false
    
    @Volatile
    private var counter = 0
    
    fun writer() {
        counter = 42
        flag = true // This write happens-before any subsequent read of flag
    }
    
    fun reader(): Int? {
        return if (flag) {
            counter // This read is guaranteed to see counter = 42
        } else {
            null
        }
    }
}

/**
 * Performance comparison between different synchronization approaches
 */
class PerformanceComparison {
    
    fun compareCounterPerformance() {
        println("=== Counter Performance Comparison ===")
        
        val numThreads = 10
        val incrementsPerThread = 100_000
        
        // Test different counter implementations
        val counters = listOf(
            "Unsafe" to UnsafeCounter(),
            "Synchronized" to SynchronizedCounter(),
            "CustomLock" to CustomLockCounter(),
            "Atomic" to AtomicCounter(),
            "ReentrantLock" to ReentrantLockCounter()
        )
        
        counters.forEach { (name, counter) ->
            val startTime = System.nanoTime()
            
            val threads = (1..numThreads).map {
                thread {
                    repeat(incrementsPerThread) {
                        counter.increment()
                    }
                }
            }
            
            threads.forEach { it.join() }
            
            val endTime = System.nanoTime()
            val duration = (endTime - startTime) / 1_000_000 // Convert to milliseconds
            
            println("$name Counter:")
            println("  Final value: ${counter.get()}")
            println("  Expected: ${numThreads * incrementsPerThread}")
            println("  Time taken: ${duration}ms")
            println("  Operations/sec: ${(numThreads * incrementsPerThread * 1000) / duration}")
            println()
        }
    }
}

/**
 * Demonstration of synchronization patterns
 */
fun demonstrateSynchronization() {
    println("=== Thread Safety Demonstration ===\n")
    
    // 1. Show unsafe behavior
    println("1. Unsafe Counter (Race Conditions):")
    val unsafeCounter = UnsafeCounter()
    val threads1 = (1..5).map {
        thread {
            repeat(1000) {
                unsafeCounter.increment()
            }
        }
    }
    threads1.forEach { it.join() }
    println("  Expected: 5000, Actual: ${unsafeCounter.get()}")
    println("  Data corruption due to race conditions!\n")
    
    // 2. Show synchronized behavior
    println("2. Synchronized Counter (Thread Safe):")
    val syncCounter = SynchronizedCounter()
    val threads2 = (1..5).map {
        thread {
            repeat(1000) {
                syncCounter.increment()
            }
        }
    }
    threads2.forEach { it.join() }
    println("  Expected: 5000, Actual: ${syncCounter.get()}")
    println("  Correct result with synchronization!\n")
    
    // 3. Show atomic behavior
    println("3. Atomic Counter (Lock-Free):")
    val atomicCounter = AtomicCounter()
    val threads3 = (1..5).map {
        thread {
            repeat(1000) {
                atomicCounter.increment()
            }
        }
    }
    threads3.forEach { it.join() }
    println("  Expected: 5000, Actual: ${atomicCounter.get()}")
    println("  Correct result with atomic operations!\n")
}

/**
 * Demonstrate read-write lock benefits
 */
fun demonstrateReadWriteLock() {
    println("=== Read-Write Lock Demonstration ===")
    
    val counter = ReadWriteCounter()
    
    // Start multiple reader threads
    val readers = (1..5).map { threadId ->
        thread(name = "Reader-$threadId") {
            repeat(3) {
                val value = counter.getWithDelay()
                println("Reader-$threadId read value: $value")
            }
        }
    }
    
    // Start a writer thread
    val writer = thread(name = "Writer") {
        repeat(10) {
            counter.increment()
            println("Writer incremented to: ${counter.get()}")
            Thread.sleep(50)
        }
    }
    
    readers.forEach { it.join() }
    writer.join()
    println("Final counter value: ${counter.get()}\n")
}

/**
 * Demonstrate volatile visibility guarantees
 */
fun demonstrateVolatile() {
    println("=== Volatile Demonstration ===")
    
    val example = VolatileExample()
    
    val reader = thread(name = "Reader") {
        var attempts = 0
        while (true) {
            val result = example.reader()
            attempts++
            if (result != null) {
                println("Reader saw value: $result after $attempts attempts")
                break
            }
            if (attempts > 1000) {
                println("Reader gave up after $attempts attempts")
                break
            }
        }
    }
    
    // Let reader start
    Thread.sleep(100)
    
    val writer = thread(name = "Writer") {
        println("Writer setting values...")
        example.writer()
        println("Writer finished")
    }
    
    reader.join()
    writer.join()
    println()
}

/**
 * Main demonstration function
 */
fun main() {
    // Basic synchronization demonstration
    demonstrateSynchronization()
    
    // Read-write lock benefits
    demonstrateReadWriteLock()
    
    // Volatile memory visibility
    demonstrateVolatile()
    
    // Performance comparison
    PerformanceComparison().compareCounterPerformance()
    
    println("=== Thread Safety Summary ===")
    println("- Use synchronized for simple thread safety")
    println("- Use atomic classes for lock-free performance")
    println("- Use ReentrantLock for advanced features")
    println("- Use volatile for visibility without atomicity")
    println("- Use concurrent collections when possible")
    println("- Always measure performance impact of synchronization")
}