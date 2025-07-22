/**
 * # Deadlock Detection and Prevention
 * 
 * ## Problem Description
 * Deadlocks occur when two or more threads are blocked forever, each waiting
 * for a resource that another thread holds. This creates a circular dependency
 * that cannot be resolved, causing the application to hang.
 * 
 * ## Solution Approach
 * This example demonstrates various deadlock scenarios and prevention strategies:
 * - Lock ordering to prevent circular waits
 * - Timeout-based lock acquisition
 * - Lock-free programming
 * - Deadlock detection algorithms
 * 
 * ## Key Learning Points
 * - Four conditions necessary for deadlock (Coffman conditions)
 * - Common deadlock patterns in concurrent programming
 * - Prevention strategies and their trade-offs
 * - How to detect and recover from deadlocks
 * - Livelock vs deadlock differences
 * 
 * ## Performance Considerations
 * - Deadlocks cause complete thread blocking (infinite wait)
 * - Prevention adds complexity and potential performance overhead
 * - Timeout mechanisms can help but may cause spurious failures
 * - Lock-free algorithms eliminate deadlock possibility
 * 
 * ## Common Pitfalls
 * - Nested synchronized blocks with different lock orders
 * - Not releasing locks in finally blocks
 * - Using different lock objects for the same logical resource
 * - Ignoring the possibility of deadlock in complex systems
 * 
 * ## Real-World Applications
 * - Database transaction management
 * - Resource allocation in operating systems
 * - Network protocol implementations
 * - Web server connection handling
 * - Message queue processing
 * 
 * ## Related Concepts
 * - ThreadSafety.kt - Basic synchronization
 * - RaceConditions.kt - Other concurrency issues
 * - ThreadPools.kt - Thread management
 */

package threading

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Classic deadlock scenario: Dining Philosophers Problem
 * 
 * Deadlock visualization:
 * 
 * Thread A: Lock(resource1) ──> Wait(resource2) ┐
 *                                                │
 * Thread B: Lock(resource2) ──> Wait(resource1) ┘
 * 
 * Result: Circular wait - both threads blocked forever
 */
class DeadlockDemo {
    
    private val resource1 = Any()
    private val resource2 = Any()
    
    fun demonstrateDeadlock() {
        println("=== Deadlock Demonstration ===")
        println("Starting two threads that will deadlock...")
        
        val thread1 = thread(name = "Thread-1") {
            synchronized(resource1) {
                println("Thread-1: Acquired resource1")
                Thread.sleep(100) // Give other thread time to acquire resource2
                
                println("Thread-1: Trying to acquire resource2...")
                synchronized(resource2) {
                    println("Thread-1: Acquired resource2")
                }
            }
        }
        
        val thread2 = thread(name = "Thread-2") {
            synchronized(resource2) {
                println("Thread-2: Acquired resource2")
                Thread.sleep(100) // Give other thread time to acquire resource1
                
                println("Thread-2: Trying to acquire resource1...")
                synchronized(resource1) {
                    println("Thread-2: Acquired resource1")
                }
            }
        }
        
        // Wait for 3 seconds to see deadlock
        thread1.join(3000)
        thread2.join(3000)
        
        if (thread1.isAlive || thread2.isAlive) {
            println("DEADLOCK DETECTED! Threads are still running after 3 seconds")
            println("Thread-1 state: ${thread1.state}")
            println("Thread-2 state: ${thread2.state}")
            
            // Force termination (not recommended in production!)
            @Suppress("DEPRECATION")
            thread1.stop()
            @Suppress("DEPRECATION") 
            thread2.stop()
        }
        println()
    }
}

/**
 * Deadlock prevention using consistent lock ordering
 */
class OrderedLockingDemo {
    
    private val resource1 = Any()
    private val resource2 = Any()
    
    // Define a consistent ordering for locks
    private val locks = listOf(resource1, resource2).sortedBy { System.identityHashCode(it) }
    
    fun demonstrateOrderedLocking() {
        println("=== Ordered Locking Prevention ===")
        
        val thread1 = thread(name = "OrderedThread-1") {
            // Always acquire locks in the same order
            synchronized(locks[0]) {
                println("OrderedThread-1: Acquired first lock")
                Thread.sleep(100)
                
                synchronized(locks[1]) {
                    println("OrderedThread-1: Acquired second lock")
                    println("OrderedThread-1: Work completed successfully")
                }
            }
        }
        
        val thread2 = thread(name = "OrderedThread-2") {
            // Same lock order prevents deadlock
            synchronized(locks[0]) {
                println("OrderedThread-2: Acquired first lock")
                Thread.sleep(100)
                
                synchronized(locks[1]) {
                    println("OrderedThread-2: Acquired second lock")
                    println("OrderedThread-2: Work completed successfully")
                }
            }
        }
        
        thread1.join()
        thread2.join()
        println("Both threads completed successfully - no deadlock!\n")
    }
}

/**
 * Timeout-based deadlock prevention
 */
class TimeoutLockingDemo {
    
    private val lock1 = ReentrantLock()
    private val lock2 = ReentrantLock()
    
    fun demonstrateTimeoutLocking() {
        println("=== Timeout-Based Prevention ===")
        
        val thread1 = thread(name = "TimeoutThread-1") {
            try {
                if (lock1.tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        println("TimeoutThread-1: Acquired lock1")
                        Thread.sleep(100)
                        
                        if (lock2.tryLock(500, TimeUnit.MILLISECONDS)) {
                            try {
                                println("TimeoutThread-1: Acquired lock2")
                                println("TimeoutThread-1: Work completed")
                            } finally {
                                lock2.unlock()
                            }
                        } else {
                            println("TimeoutThread-1: Timeout acquiring lock2, backing off")
                        }
                    } finally {
                        lock1.unlock()
                    }
                }
            } catch (e: InterruptedException) {
                println("TimeoutThread-1: Interrupted")
            }
        }
        
        val thread2 = thread(name = "TimeoutThread-2") {
            try {
                if (lock2.tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        println("TimeoutThread-2: Acquired lock2")
                        Thread.sleep(100)
                        
                        if (lock1.tryLock(500, TimeUnit.MILLISECONDS)) {
                            try {
                                println("TimeoutThread-2: Acquired lock1")
                                println("TimeoutThread-2: Work completed")
                            } finally {
                                lock1.unlock()
                            }
                        } else {
                            println("TimeoutThread-2: Timeout acquiring lock1, backing off")
                        }
                    } finally {
                        lock2.unlock()
                    }
                }
            } catch (e: InterruptedException) {
                println("TimeoutThread-2: Interrupted")
            }
        }
        
        thread1.join()
        thread2.join()
        println("Timeout-based approach completed\n")
    }
}

/**
 * Lock-free solution using atomic operations
 */
class LockFreeDemo {
    
    private val counter1 = AtomicBoolean(false)
    private val counter2 = AtomicBoolean(false)
    
    fun demonstrateLockFree() {
        println("=== Lock-Free Solution ===")
        
        val thread1 = thread(name = "LockFreeThread-1") {
            // Try to acquire both resources atomically
            if (counter1.compareAndSet(false, true)) {
                println("LockFreeThread-1: Acquired resource1")
                
                if (counter2.compareAndSet(false, true)) {
                    println("LockFreeThread-1: Acquired resource2")
                    
                    // Do work
                    Thread.sleep(100)
                    println("LockFreeThread-1: Work completed")
                    
                    // Release resources
                    counter2.set(false)
                    counter1.set(false)
                } else {
                    // Failed to acquire second resource, release first
                    println("LockFreeThread-1: Failed to acquire resource2, releasing resource1")
                    counter1.set(false)
                }
            } else {
                println("LockFreeThread-1: Failed to acquire resource1")
            }
        }
        
        val thread2 = thread(name = "LockFreeThread-2") {
            Thread.sleep(50) // Slight delay to increase contention
            
            if (counter1.compareAndSet(false, true)) {
                println("LockFreeThread-2: Acquired resource1")
                
                if (counter2.compareAndSet(false, true)) {
                    println("LockFreeThread-2: Acquired resource2")
                    
                    // Do work
                    Thread.sleep(100)
                    println("LockFreeThread-2: Work completed")
                    
                    // Release resources
                    counter2.set(false)
                    counter1.set(false)
                } else {
                    println("LockFreeThread-2: Failed to acquire resource2, releasing resource1")
                    counter1.set(false)
                }
            } else {
                println("LockFreeThread-2: Failed to acquire resource1")
            }
        }
        
        thread1.join()
        thread2.join()
        println("Lock-free approach completed\n")
    }
}

/**
 * Bank transfer deadlock scenario
 */
class BankTransferDeadlock {
    
    data class Account(val id: Int, var balance: Double)
    
    fun demonstrateTransferDeadlock() {
        println("=== Bank Transfer Deadlock ===")
        
        val account1 = Account(1, 1000.0)
        val account2 = Account(2, 1000.0)
        
        val thread1 = thread(name = "Transfer1") {
            transfer(account1, account2, 100.0)
        }
        
        val thread2 = thread(name = "Transfer2") {
            transfer(account2, account1, 50.0)
        }
        
        // Wait for potential deadlock
        thread1.join(2000)
        thread2.join(2000)
        
        if (thread1.isAlive || thread2.isAlive) {
            println("DEADLOCK in bank transfers!")
            @Suppress("DEPRECATION")
            thread1.stop()
            @Suppress("DEPRECATION")
            thread2.stop()
        }
        println()
    }
    
    private fun transfer(from: Account, to: Account, amount: Double) {
        synchronized(from) {
            println("${Thread.currentThread().name}: Locked account ${from.id}")
            Thread.sleep(100) // Simulate processing time
            
            synchronized(to) {
                println("${Thread.currentThread().name}: Locked account ${to.id}")
                
                if (from.balance >= amount) {
                    from.balance -= amount
                    to.balance += amount
                    println("${Thread.currentThread().name}: Transferred $amount from ${from.id} to ${to.id}")
                }
            }
        }
    }
    
    fun demonstrateSafeTransfer() {
        println("=== Safe Bank Transfer ===")
        
        val account1 = Account(1, 1000.0)
        val account2 = Account(2, 1000.0)
        
        val thread1 = thread(name = "SafeTransfer1") {
            safeTransfer(account1, account2, 100.0)
        }
        
        val thread2 = thread(name = "SafeTransfer2") {
            safeTransfer(account2, account1, 50.0)
        }
        
        thread1.join()
        thread2.join()
        
        println("Account 1 final balance: ${account1.balance}")
        println("Account 2 final balance: ${account2.balance}")
        println()
    }
    
    private fun safeTransfer(from: Account, to: Account, amount: Double) {
        // Always lock accounts in order of their ID to prevent deadlock
        val firstLock = if (from.id < to.id) from else to
        val secondLock = if (from.id < to.id) to else from
        
        synchronized(firstLock) {
            println("${Thread.currentThread().name}: Locked account ${firstLock.id}")
            Thread.sleep(100)
            
            synchronized(secondLock) {
                println("${Thread.currentThread().name}: Locked account ${secondLock.id}")
                
                if (from.balance >= amount) {
                    from.balance -= amount
                    to.balance += amount
                    println("${Thread.currentThread().name}: Transferred $amount from ${from.id} to ${to.id}")
                }
            }
        }
    }
}

/**
 * Livelock demonstration (related to deadlock)
 */
class LivelockDemo {
    
    private val lock1 = ReentrantLock()
    private val lock2 = ReentrantLock()
    
    fun demonstrateLivelock() {
        println("=== Livelock Demonstration ===")
        
        val thread1 = thread(name = "PoliteThread-1") {
            repeat(10) { attempt ->
                if (lock1.tryLock()) {
                    try {
                        println("PoliteThread-1: Got lock1, trying lock2...")
                        
                        if (lock2.tryLock()) {
                            try {
                                println("PoliteThread-1: Got both locks, doing work")
                                Thread.sleep(100)
                                return@repeat // Exit loop on success
                            } finally {
                                lock2.unlock()
                            }
                        } else {
                            println("PoliteThread-1: Can't get lock2, being polite and releasing lock1")
                        }
                    } finally {
                        lock1.unlock()
                    }
                    
                    // Be polite and wait before retrying
                    Thread.sleep(50)
                } else {
                    println("PoliteThread-1: Can't get lock1, attempt $attempt")
                    Thread.sleep(50)
                }
            }
            println("PoliteThread-1: Gave up after 10 attempts")
        }
        
        val thread2 = thread(name = "PoliteThread-2") {
            repeat(10) { attempt ->
                if (lock2.tryLock()) {
                    try {
                        println("PoliteThread-2: Got lock2, trying lock1...")
                        
                        if (lock1.tryLock()) {
                            try {
                                println("PoliteThread-2: Got both locks, doing work")
                                Thread.sleep(100)
                                return@repeat // Exit loop on success
                            } finally {
                                lock1.unlock()
                            }
                        } else {
                            println("PoliteThread-2: Can't get lock1, being polite and releasing lock2")
                        }
                    } finally {
                        lock2.unlock()
                    }
                    
                    // Be polite and wait before retrying
                    Thread.sleep(50)
                } else {
                    println("PoliteThread-2: Can't get lock2, attempt $attempt")
                    Thread.sleep(50)
                }
            }
            println("PoliteThread-2: Gave up after 10 attempts")
        }
        
        thread1.join()
        thread2.join()
        println("Livelock demonstration completed\n")
    }
}

/**
 * Deadlock detection utility
 */
class DeadlockDetector {
    
    fun detectDeadlock(): List<Thread> {
        val threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean()
        val deadlockedThreads = threadMXBean.findDeadlockedThreads()
        
        return if (deadlockedThreads != null) {
            val threadInfos = threadMXBean.getThreadInfo(deadlockedThreads)
            threadInfos.map { Thread.getAllStackTraces().keys.first { thread -> thread.id == it.threadId } }
        } else {
            emptyList()
        }
    }
    
    fun startDeadlockMonitoring() {
        thread(name = "DeadlockMonitor", isDaemon = true) {
            while (!Thread.currentThread().isInterrupted) {
                val deadlockedThreads = detectDeadlock()
                if (deadlockedThreads.isNotEmpty()) {
                    println("DEADLOCK DETECTED!")
                    deadlockedThreads.forEach { thread ->
                        println("Deadlocked thread: ${thread.name} (${thread.state})")
                    }
                }
                Thread.sleep(1000) // Check every second
            }
        }
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Start deadlock monitoring
    val detector = DeadlockDetector()
    detector.startDeadlockMonitoring()
    
    // Demonstrate deadlock
    DeadlockDemo().demonstrateDeadlock()
    
    // Show prevention strategies
    OrderedLockingDemo().demonstrateOrderedLocking()
    TimeoutLockingDemo().demonstrateTimeoutLocking()
    LockFreeDemo().demonstrateLockFree()
    
    // Bank transfer examples
    val bankDemo = BankTransferDeadlock()
    bankDemo.demonstrateTransferDeadlock()
    bankDemo.demonstrateSafeTransfer()
    
    // Livelock demonstration
    LivelockDemo().demonstrateLivelock()
    
    println("=== Deadlock Prevention Summary ===")
    println("1. Use consistent lock ordering")
    println("2. Use timeout-based lock acquisition")
    println("3. Avoid nested locks when possible") 
    println("4. Consider lock-free alternatives")
    println("5. Use deadlock detection tools")
    println("6. Design systems to minimize shared resources")
    println("7. Test thoroughly under high concurrency")
}