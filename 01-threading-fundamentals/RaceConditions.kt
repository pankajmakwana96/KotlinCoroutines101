/**
 * # Race Conditions: Demonstration and Prevention
 * 
 * ## Problem Description
 * Race conditions occur when multiple threads access shared data concurrently
 * and the final result depends on the unpredictable timing of thread execution.
 * This leads to non-deterministic behavior and data corruption.
 * 
 * ## Solution Approach
 * This example demonstrates various race conditions in action and shows
 * multiple prevention strategies:
 * - Proper synchronization
 * - Atomic operations
 * - Immutable data structures
 * - Thread-local storage
 * 
 * ## Key Learning Points
 * - Race conditions cause non-deterministic behavior
 * - Even simple operations like increment are not atomic
 * - Compound operations require additional synchronization
 * - Different prevention strategies for different scenarios
 * - How to detect and reproduce race conditions
 * 
 * ## Performance Considerations
 * - Race conditions can cause silent data corruption
 * - Prevention mechanisms add overhead but ensure correctness
 * - Lock-free solutions often provide better performance
 * - Immutable objects eliminate race conditions entirely
 * 
 * ## Common Pitfalls
 * - Assuming single operations are atomic
 * - Incomplete synchronization (only protecting some accesses)
 * - False sharing between CPU cores
 * - ABA problems in lock-free algorithms
 * 
 * ## Real-World Applications
 * - Bank account balance updates
 * - Web server session management
 * - Cache invalidation
 * - Statistics counters
 * - Resource allocation
 * 
 * ## Related Concepts
 * - ThreadSafety.kt - Synchronization mechanisms
 * - VolatileAndAtomic.kt - Memory model details
 * - DeadlockPrevention.kt - Advanced synchronization
 */

package threading

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Classic race condition: Non-atomic increment
 * 
 * The increment operation count++ is actually three operations:
 * 1. Read current value from memory
 * 2. Add 1 to the value  
 * 3. Write new value back to memory
 * 
 * Timeline showing race condition:
 * 
 * Thread A: read(0) -----> add(1) -----> write(1)
 * Thread B:     read(0) -----> add(1) -----> write(1)
 * 
 * Result: Both threads write 1, but expected result is 2
 */
class RacyCounter {
    private var count = 0
    
    fun increment() {
        // This is NOT atomic!
        val temp = count  // Read
        count = temp + 1  // Modify and Write
    }
    
    fun get(): Int = count
}

/**
 * Bank account with race condition in transfer operation
 */
class UnsafeBankAccount(private var balance: Double) {
    
    fun withdraw(amount: Double): Boolean {
        if (balance >= amount) {
            // Race condition: Another thread could modify balance here!
            Thread.sleep(1) // Simulate processing delay
            balance -= amount
            return true
        }
        return false
    }
    
    fun deposit(amount: Double) {
        balance += amount
    }
    
    fun getBalance(): Double = balance
}

/**
 * Thread-safe bank account using synchronization
 */
class SafeBankAccount(private var balance: Double) {
    
    @Synchronized
    fun withdraw(amount: Double): Boolean {
        if (balance >= amount) {
            Thread.sleep(1) // Simulate processing delay
            balance -= amount
            return true
        }
        return false
    }
    
    @Synchronized
    fun deposit(amount: Double) {
        balance += amount
    }
    
    @Synchronized
    fun getBalance(): Double = balance
}

/**
 * Lazy initialization with race condition
 */
class UnsafeSingleton {
    companion object {
        private var instance: UnsafeSingleton? = null
        
        fun getInstance(): UnsafeSingleton {
            if (instance == null) {
                // Multiple threads could enter here simultaneously!
                Thread.sleep(10) // Simulate expensive initialization
                instance = UnsafeSingleton()
            }
            return instance!!
        }
    }
}

/**
 * Thread-safe lazy initialization using double-checked locking
 */
class SafeSingleton {
    companion object {
        @Volatile
        private var instance: SafeSingleton? = null
        
        fun getInstance(): SafeSingleton {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        Thread.sleep(10) // Simulate expensive initialization
                        instance = SafeSingleton()
                    }
                }
            }
            return instance!!
        }
    }
}

/**
 * Producer-consumer with race condition
 */
class UnsafeBuffer<T>(private val capacity: Int) {
    private val buffer = mutableListOf<T>()
    
    fun put(item: T): Boolean {
        if (buffer.size < capacity) {
            // Race condition: size could change between check and add
            Thread.sleep(1)
            buffer.add(item)
            return true
        }
        return false
    }
    
    fun take(): T? {
        if (buffer.isNotEmpty()) {
            // Race condition: buffer could become empty between check and remove
            Thread.sleep(1)
            return buffer.removeAt(0)
        }
        return null
    }
    
    fun size(): Int = buffer.size
}

/**
 * Thread-safe buffer using synchronization
 */
class SafeBuffer<T>(private val capacity: Int) {
    private val buffer = mutableListOf<T>()
    
    @Synchronized
    fun put(item: T): Boolean {
        if (buffer.size < capacity) {
            Thread.sleep(1)
            buffer.add(item)
            return true
        }
        return false
    }
    
    @Synchronized
    fun take(): T? {
        if (buffer.isNotEmpty()) {
            Thread.sleep(1)
            return buffer.removeAt(0)
        }
        return null
    }
    
    @Synchronized
    fun size(): Int = buffer.size
}

/**
 * Statistics collector with multiple race conditions
 */
class RacyStatistics {
    private var count = 0
    private var sum = 0.0
    private var min = Double.MAX_VALUE
    private var max = Double.MIN_VALUE
    
    fun addValue(value: Double) {
        count++           // Race condition 1
        sum += value      // Race condition 2
        
        // Race conditions 3 & 4: min and max updates
        if (value < min) min = value
        if (value > max) max = value
    }
    
    fun getAverage(): Double = if (count > 0) sum / count else 0.0
    fun getCount(): Int = count
    fun getMin(): Double = min
    fun getMax(): Double = max
}

/**
 * Thread-safe statistics using atomic operations
 */
class AtomicStatistics {
    private val count = AtomicInteger(0)
    private val sum = AtomicReference(0.0)
    private val min = AtomicReference(Double.MAX_VALUE)
    private val max = AtomicReference(Double.MIN_VALUE)
    
    fun addValue(value: Double) {
        count.incrementAndGet()
        
        // Atomic update of sum
        sum.updateAndGet { current -> current + value }
        
        // Atomic update of min
        min.updateAndGet { current -> minOf(current, value) }
        
        // Atomic update of max
        max.updateAndGet { current -> maxOf(current, value) }
    }
    
    fun getAverage(): Double {
        val c = count.get()
        return if (c > 0) sum.get() / c else 0.0
    }
    
    fun getCount(): Int = count.get()
    fun getMin(): Double = min.get()
    fun getMax(): Double = max.get()
}

/**
 * Race condition detection utility
 */
class RaceDetector {
    
    fun demonstrateCounterRace() {
        println("=== Counter Race Condition ===")
        
        repeat(5) { attempt ->
            val counter = RacyCounter()
            val numThreads = 10
            val incrementsPerThread = 1000
            
            val threads = (1..numThreads).map {
                thread {
                    repeat(incrementsPerThread) {
                        counter.increment()
                    }
                }
            }
            
            threads.forEach { it.join() }
            
            val expected = numThreads * incrementsPerThread
            val actual = counter.get()
            
            println("Attempt ${attempt + 1}: Expected=$expected, Actual=$actual, Lost=${expected - actual}")
        }
        println()
    }
    
    fun demonstrateBankAccountRace() {
        println("=== Bank Account Race Condition ===")
        
        repeat(3) { attempt ->
            val account = UnsafeBankAccount(1000.0)
            val numThreads = 10
            val withdrawAmount = 100.0
            
            val results = mutableListOf<Boolean>()
            val threads = (1..numThreads).map {
                thread {
                    val success = account.withdraw(withdrawAmount)
                    synchronized(results) {
                        results.add(success)
                    }
                }
            }
            
            threads.forEach { it.join() }
            
            val successfulWithdrawals = results.count { it }
            val expectedBalance = 1000.0 - (successfulWithdrawals * withdrawAmount)
            val actualBalance = account.getBalance()
            
            println("Attempt ${attempt + 1}:")
            println("  Successful withdrawals: $successfulWithdrawals")
            println("  Expected balance: $expectedBalance")
            println("  Actual balance: $actualBalance")
            println("  Inconsistency: ${actualBalance != expectedBalance}")
        }
        println()
    }
    
    fun demonstrateSingletonRace() {
        println("=== Singleton Race Condition ===")
        
        repeat(3) { attempt ->
            // Reset singleton (for demo purposes)
            val field = UnsafeSingleton::class.java.getDeclaredField("instance")
            field.isAccessible = true
            field.set(null, null)
            
            val instances = mutableSetOf<UnsafeSingleton>()
            val threads = (1..10).map {
                thread {
                    val instance = UnsafeSingleton.getInstance()
                    synchronized(instances) {
                        instances.add(instance)
                    }
                }
            }
            
            threads.forEach { it.join() }
            
            println("Attempt ${attempt + 1}: Created ${instances.size} instances (should be 1)")
        }
        println()
    }
}

/**
 * Solutions demonstration
 */
fun demonstrateSolutions() {
    println("=== Race Condition Solutions ===")
    
    // 1. Fixed counter using atomic operations
    println("1. Atomic Counter Solution:")
    val atomicCounter = AtomicInteger(0)
    val threads1 = (1..10).map {
        thread {
            repeat(1000) {
                atomicCounter.incrementAndGet()
            }
        }
    }
    threads1.forEach { it.join() }
    println("  Expected: 10000, Actual: ${atomicCounter.get()} ✓")
    
    // 2. Fixed bank account using synchronization
    println("\n2. Synchronized Bank Account Solution:")
    val safeAccount = SafeBankAccount(1000.0)
    val results = mutableListOf<Boolean>()
    val threads2 = (1..10).map {
        thread {
            val success = safeAccount.withdraw(100.0)
            synchronized(results) {
                results.add(success)
            }
        }
    }
    threads2.forEach { it.join() }
    
    val successfulWithdrawals = results.count { it }
    val expectedBalance = 1000.0 - (successfulWithdrawals * 100.0)
    val actualBalance = safeAccount.getBalance()
    println("  Successful withdrawals: $successfulWithdrawals")
    println("  Expected balance: $expectedBalance")
    println("  Actual balance: $actualBalance")
    println("  Consistent: ${actualBalance == expectedBalance} ✓")
    
    // 3. Thread-safe singleton
    println("\n3. Thread-Safe Singleton Solution:")
    val instances = mutableSetOf<SafeSingleton>()
    val threads3 = (1..10).map {
        thread {
            val instance = SafeSingleton.getInstance()
            synchronized(instances) {
                instances.add(instance)
            }
        }
    }
    threads3.forEach { it.join() }
    println("  Created ${instances.size} instances (should be 1) ✓")
    
    println()
}

/**
 * Advanced race condition: Lost update problem
 */
fun demonstrateLostUpdate() {
    println("=== Lost Update Problem ===")
    
    // Simulate concurrent updates to a shared counter
    data class Account(var balance: Int)
    
    val account = Account(100)
    val updates = mutableListOf<Int>()
    
    // Multiple threads trying to add different amounts
    val threads = listOf(10, 20, 30, 40, 50).map { amount ->
        thread {
            // Read-modify-write without proper synchronization
            val currentBalance = account.balance
            Thread.sleep(Random.nextLong(1, 10)) // Simulate processing time
            account.balance = currentBalance + amount
            
            synchronized(updates) {
                updates.add(amount)
            }
        }
    }
    
    threads.forEach { it.join() }
    
    val expectedBalance = 100 + updates.sum()
    val actualBalance = account.balance
    
    println("Updates attempted: $updates")
    println("Expected final balance: $expectedBalance")
    println("Actual final balance: $actualBalance") 
    println("Lost updates: ${expectedBalance - actualBalance}")
    println()
}

/**
 * Main demonstration function
 */
fun main() {
    val detector = RaceDetector()
    
    // Demonstrate various race conditions
    detector.demonstrateCounterRace()
    detector.demonstrateBankAccountRace() 
    detector.demonstrateSingletonRace()
    
    // Show the lost update problem
    demonstrateLostUpdate()
    
    // Demonstrate solutions
    demonstrateSolutions()
    
    println("=== Key Takeaways ===")
    println("- Race conditions are subtle and hard to reproduce consistently")
    println("- They often manifest as data corruption or lost updates")
    println("- Prevention requires identifying all shared mutable state")
    println("- Use atomic operations for simple cases")
    println("- Use synchronization for complex operations")
    println("- Consider immutable data structures when possible")
    println("- Always test concurrent code under realistic conditions")
}