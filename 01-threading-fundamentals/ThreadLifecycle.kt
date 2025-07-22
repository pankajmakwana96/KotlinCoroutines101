/**
 * # Thread Lifecycle and State Management
 * 
 * ## Problem Description
 * Understanding thread states is crucial for effective concurrent programming.
 * Threads go through various states during their lifecycle, and knowing these
 * states helps in debugging and optimizing concurrent applications.
 * 
 * ## Solution Approach
 * This example demonstrates all thread states with practical scenarios and
 * provides tools to observe state transitions in real-time.
 * 
 * ## Key Learning Points
 * - Thread states: NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED
 * - State transitions and what triggers them
 * - How to monitor thread states programmatically
 * - Thread interruption and graceful shutdown
 * 
 * ## Performance Considerations
 * - Thread creation overhead: ~1-2MB memory per thread
 * - Context switching cost: 1-100 microseconds
 * - OS thread limits: typically 1000-10000 threads max
 * 
 * ## Common Pitfalls
 * - Creating too many threads leads to resource exhaustion
 * - Not handling InterruptedException properly
 * - Assuming thread priorities guarantee execution order
 * 
 * ## Real-World Applications
 * - Background task processing
 * - I/O operations
 * - Parallel computations
 * - Server request handling
 * 
 * ## Related Concepts
 * - ThreadSafety.kt - Thread synchronization
 * - ThreadPools.kt - Efficient thread management
 * - Coroutine basics - Modern alternative approach
 */

package threading

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/**
 * Visual representation of thread lifecycle:
 * 
 * NEW ──start()──> RUNNABLE ──scheduler──> RUNNING
 *  │                  │                      │
 *  └─ GC if not       │                      │
 *     started         │                      │
 *                     │                      │
 * TERMINATED <────────┴──────────────────────┘
 *      │                                     │
 *      └─ run() completes                    │
 *                                            │
 * BLOCKED <─────── synchronized/lock ────────┘
 *      │                                     │
 * WAITING <─────── wait()/join() ────────────┘
 *      │                                     │
 * TIMED_WAITING <── sleep()/wait(ms) ────────┘
 */

class ThreadLifecycleDemo {
    
    fun demonstrateAllStates() {
        println("=== Thread Lifecycle Demonstration ===\n")
        
        // NEW state
        demonstrateNewState()
        
        // RUNNABLE state  
        demonstrateRunnableState()
        
        // BLOCKED state
        demonstrateBlockedState()
        
        // WAITING state
        demonstrateWaitingState()
        
        // TIMED_WAITING state
        demonstrateTimedWaitingState()
        
        // TERMINATED state
        demonstrateTerminatedState()
        
        println("\n=== All thread states demonstrated ===")
    }
    
    private fun demonstrateNewState() {
        println("1. NEW State:")
        
        val newThread = Thread {
            println("  Thread is running!")
        }
        
        // Thread is in NEW state until start() is called
        println("  Thread state before start(): ${newThread.state}")
        assert(newThread.state == Thread.State.NEW) { "Expected NEW state" }
        
        newThread.start()
        Thread.sleep(100) // Give it time to complete
        
        println("  Thread state after completion: ${newThread.state}")
        println()
    }
    
    private fun demonstrateRunnableState() {
        println("2. RUNNABLE State:")
        
        val runnableThread = thread {
            println("  Thread is in RUNNABLE state")
            // Simulate some work
            var sum = 0
            for (i in 1..1000000) {
                sum += i
            }
            println("  Computation result: $sum")
        }
        
        // Check state while running (might catch RUNNABLE)
        Thread.sleep(10)
        println("  Thread state during execution: ${runnableThread.state}")
        
        runnableThread.join() // Wait for completion
        println()
    }
    
    private fun demonstrateBlockedState() {
        println("3. BLOCKED State:")
        
        val lock = ReentrantLock()
        
        val blockingThread = thread {
            println("  Thread 1: Acquiring lock...")
            lock.lock()
            try {
                println("  Thread 1: Lock acquired, sleeping...")
                Thread.sleep(2000) // Hold lock for 2 seconds
            } finally {
                lock.unlock()
                println("  Thread 1: Lock released")
            }
        }
        
        Thread.sleep(100) // Let first thread acquire lock
        
        val blockedThread = thread {
            println("  Thread 2: Trying to acquire lock...")
            lock.lock() // This will block
            try {
                println("  Thread 2: Finally got the lock!")
            } finally {
                lock.unlock()
            }
        }
        
        Thread.sleep(500) // Let second thread get blocked
        println("  Thread 2 state while blocked: ${blockedThread.state}")
        
        blockingThread.join()
        blockedThread.join()
        println()
    }
    
    private fun demonstrateWaitingState() {
        println("4. WAITING State:")
        
        val monitor = Object()
        var dataReady = false
        
        val waitingThread = thread {
            synchronized(monitor) {
                while (!dataReady) {
                    println("  Thread: Waiting for data...")
                    monitor.wait() // Enters WAITING state
                }
                println("  Thread: Data received, continuing...")
            }
        }
        
        Thread.sleep(500) // Let thread enter wait state
        println("  Thread state while waiting: ${waitingThread.state}")
        
        // Notify the waiting thread
        thread {
            Thread.sleep(1000)
            synchronized(monitor) {
                dataReady = true
                monitor.notify()
                println("  Notifier: Data prepared and thread notified")
            }
        }
        
        waitingThread.join()
        println()
    }
    
    private fun demonstrateTimedWaitingState() {
        println("5. TIMED_WAITING State:")
        
        val sleepingThread = thread {
            println("  Thread: Going to sleep for 2 seconds...")
            Thread.sleep(2000) // Enters TIMED_WAITING state
            println("  Thread: Woke up from sleep")
        }
        
        Thread.sleep(500) // Let thread enter sleep
        println("  Thread state while sleeping: ${sleepingThread.state}")
        
        sleepingThread.join()
        println()
    }
    
    private fun demonstrateTerminatedState() {
        println("6. TERMINATED State:")
        
        val terminatingThread = thread {
            println("  Thread: Executing and about to finish...")
            // Thread will terminate when run() method completes
        }
        
        terminatingThread.join() // Wait for completion
        println("  Thread state after termination: ${terminatingThread.state}")
        println()
    }
}

/**
 * Advanced thread monitoring utility for tracking state changes
 */
class ThreadStateMonitor {
    
    fun monitorThreadStates(threads: List<Thread>, durationMs: Long = 5000) {
        println("=== Thread State Monitoring ===")
        
        val startTime = System.currentTimeMillis()
        val states = mutableMapOf<Thread, MutableList<Pair<Long, Thread.State>>>()
        
        // Initialize state tracking
        threads.forEach { thread ->
            states[thread] = mutableListOf()
        }
        
        // Monitor thread states
        while (System.currentTimeMillis() - startTime < durationMs) {
            threads.forEach { thread ->
                val currentTime = System.currentTimeMillis() - startTime
                val currentState = thread.state
                states[thread]?.add(currentTime to currentState)
            }
            Thread.sleep(50) // Check every 50ms
        }
        
        // Report state transitions
        states.forEach { (thread, stateHistory) ->
            println("\nThread: ${thread.name}")
            var previousState: Thread.State? = null
            
            stateHistory.forEach { (time, state) ->
                if (state != previousState) {
                    println("  ${time}ms: $state")
                    previousState = state
                }
            }
        }
    }
}

/**
 * Thread interruption and graceful shutdown example
 */
class InterruptibleTask : Runnable {
    
    override fun run() {
        try {
            println("Task started, checking for interruption...")
            
            // Simulate long-running work with interruption checks
            for (i in 1..10) {
                // Check if thread has been interrupted
                if (Thread.currentThread().isInterrupted) {
                    println("Task was interrupted at iteration $i")
                    return
                }
                
                println("Working... iteration $i")
                Thread.sleep(500) // This throws InterruptedException if interrupted
            }
            
            println("Task completed successfully")
            
        } catch (e: InterruptedException) {
            // Restore interrupted status
            Thread.currentThread().interrupt()
            println("Task interrupted via InterruptedException")
        }
    }
}

fun demonstrateInterruption() {
    println("=== Thread Interruption Demo ===")
    
    val task = InterruptibleTask()
    val thread = Thread(task, "InterruptibleWorker")
    
    thread.start()
    
    // Let it run for a bit, then interrupt
    Thread.sleep(2000)
    println("Interrupting thread...")
    thread.interrupt()
    
    thread.join()
    println("Thread interruption demo completed\n")
}

/**
 * Thread priority demonstration
 */
fun demonstrateThreadPriority() {
    println("=== Thread Priority Demo ===")
    
    val highPriorityThread = thread(start = false, name = "HighPriority") {
        repeat(5) {
            println("High priority thread working: $it")
            Thread.yield() // Suggest yielding to other threads
        }
    }
    
    val lowPriorityThread = thread(start = false, name = "LowPriority") {
        repeat(5) {
            println("Low priority thread working: $it")
            Thread.yield()
        }
    }
    
    // Set priorities (Note: This is just a hint to the OS scheduler)
    highPriorityThread.priority = Thread.MAX_PRIORITY
    lowPriorityThread.priority = Thread.MIN_PRIORITY
    
    println("Starting threads with different priorities...")
    lowPriorityThread.start()
    highPriorityThread.start()
    
    highPriorityThread.join()
    lowPriorityThread.join()
    
    println("Note: Thread priorities are hints to the OS scheduler")
    println("Actual execution order depends on OS scheduling algorithm\n")
}

/**
 * Main demonstration function
 */
fun main() {
    // Basic lifecycle demonstration
    ThreadLifecycleDemo().demonstrateAllStates()
    
    // Thread interruption
    demonstrateInterruption()
    
    // Thread priorities
    demonstrateThreadPriority()
    
    // Advanced monitoring example
    println("=== Advanced Monitoring Example ===")
    val monitoredThreads = listOf(
        thread(start = false, name = "Worker1") {
            Thread.sleep(1000)
            println("Worker1 completed")
        },
        thread(start = false, name = "Worker2") {
            Thread.sleep(2000) 
            println("Worker2 completed")
        }
    )
    
    val monitor = ThreadStateMonitor()
    
    // Start monitoring
    thread {
        monitor.monitorThreadStates(monitoredThreads, 3000)
    }
    
    // Start the monitored threads
    monitoredThreads.forEach { it.start() }
    
    // Wait for completion
    monitoredThreads.forEach { it.join() }
    
    Thread.sleep(500) // Let monitor finish
}