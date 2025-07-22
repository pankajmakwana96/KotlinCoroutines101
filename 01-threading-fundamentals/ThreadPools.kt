/**
 * # Thread Pools and Executor Framework
 * 
 * ## Problem Description
 * Creating threads for every task is expensive and can exhaust system resources.
 * Thread pools solve this by maintaining a pool of reusable threads, providing
 * better performance, resource management, and control over concurrency levels.
 * 
 * ## Solution Approach
 * This example demonstrates various thread pool types and configurations:
 * - Fixed thread pools for predictable workloads
 * - Cached thread pools for variable workloads  
 * - Single thread executors for sequential processing
 * - Scheduled executors for delayed/periodic tasks
 * - Custom thread pools with specific configurations
 * 
 * ## Key Learning Points
 * - Thread pools reuse threads to avoid creation overhead
 * - Different pool types for different use cases
 * - Queue management and rejection policies
 * - Thread pool sizing considerations
 * - Proper shutdown procedures
 * 
 * ## Performance Considerations
 * - Thread creation overhead: ~1-2ms per thread
 * - Thread pools amortize creation cost across many tasks
 * - Queue size affects memory usage and blocking behavior
 * - Pool size affects throughput and resource usage
 * - Context switching overhead with too many threads
 * 
 * ## Common Pitfalls
 * - Not shutting down pools properly (resource leaks)
 * - Incorrect pool sizing for workload characteristics
 * - Using wrong queue type for use case
 * - Blocking pool threads with long-running operations
 * - Not handling task exceptions properly
 * 
 * ## Real-World Applications
 * - Web server request handling
 * - Background task processing
 * - I/O operations (file, network, database)
 * - Image/video processing pipelines
 * - Batch job processing
 * 
 * ## Related Concepts
 * - ThreadLifecycle.kt - Understanding thread overhead
 * - ThreadSafety.kt - Synchronization in pools
 * - BlockingVsNonBlocking.kt - I/O patterns
 */

package threading

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Thread pool comparison utility
 * 
 * Pool Types Visualization:
 * 
 * FixedThreadPool(4):
 * [T1][T2][T3][T4] ──> [Task Queue: unlimited]
 * 
 * CachedThreadPool:
 * [T1][T2]... ──> Creates threads as needed, reuses idle threads
 * 
 * SingleThreadExecutor:
 * [T1] ──> [Task Queue: sequential processing]
 * 
 * ScheduledThreadPool:
 * [T1][T2] ──> [Delayed/Periodic Task Queue]
 */
class ThreadPoolComparison {
    
    fun comparePoolTypes() {
        println("=== Thread Pool Type Comparison ===\n")
        
        val taskCount = 20
        val tasks = (1..taskCount).map { taskId ->
            Callable {
                val startTime = System.currentTimeMillis()
                val threadName = Thread.currentThread().name
                
                // Simulate work
                Thread.sleep(Random.nextLong(100, 500))
                
                val endTime = System.currentTimeMillis()
                "Task $taskId completed by $threadName in ${endTime - startTime}ms"
            }
        }
        
        // Test different pool types
        testFixedThreadPool(tasks)
        testCachedThreadPool(tasks)
        testSingleThreadExecutor(tasks)
        testScheduledThreadPool()
    }
    
    private fun testFixedThreadPool(tasks: List<Callable<String>>) {
        println("1. Fixed Thread Pool (4 threads):")
        val startTime = System.currentTimeMillis()
        
        val executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "FixedPool-${Thread.activeCount()}")
        }
        
        try {
            val futures = executor.invokeAll(tasks)
            futures.forEach { future ->
                println("  ${future.get()}")
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        println("  Total time: ${totalTime}ms\n")
    }
    
    private fun testCachedThreadPool(tasks: List<Callable<String>>) {
        println("2. Cached Thread Pool (creates threads as needed):")
        val startTime = System.currentTimeMillis()
        
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "CachedPool-${System.nanoTime()}")
        }
        
        try {
            val futures = executor.invokeAll(tasks)
            futures.forEach { future ->
                println("  ${future.get()}")
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        println("  Total time: ${totalTime}ms\n")
    }
    
    private fun testSingleThreadExecutor(tasks: List<Callable<String>>) {
        println("3. Single Thread Executor (sequential processing):")
        val startTime = System.currentTimeMillis()
        
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "SingleThread-Worker")
        }
        
        try {
            val futures = executor.invokeAll(tasks.take(5)) // Only test first 5 for time
            futures.forEach { future ->
                println("  ${future.get()}")
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        println("  Total time: ${totalTime}ms\n")
    }
    
    private fun testScheduledThreadPool() {
        println("4. Scheduled Thread Pool (delayed/periodic tasks):")
        
        val executor = Executors.newScheduledThreadPool(2) { runnable ->
            Thread(runnable, "ScheduledPool-${Thread.activeCount()}")
        }
        
        try {
            // One-time delayed task
            val delayedFuture = executor.schedule({
                println("  Delayed task executed after 1 second")
            }, 1, TimeUnit.SECONDS)
            
            // Periodic task
            val periodicFuture = executor.scheduleAtFixedRate({
                println("  Periodic task executed at ${System.currentTimeMillis()}")
            }, 0, 500, TimeUnit.MILLISECONDS)
            
            // Let it run for a while
            Thread.sleep(3000)
            
            // Cancel periodic task
            periodicFuture.cancel(false)
            delayedFuture.get() // Ensure delayed task completes
            
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
        
        println("  Scheduled tasks completed\n")
    }
}

/**
 * Custom thread pool configuration
 */
class CustomThreadPool {
    
    fun demonstrateCustomConfiguration() {
        println("=== Custom Thread Pool Configuration ===")
        
        // Custom thread factory
        val threadFactory = ThreadFactory { runnable ->
            val thread = Thread(runnable, "CustomWorker-${System.nanoTime()}")
            thread.isDaemon = false
            thread.priority = Thread.NORM_PRIORITY
            thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                println("Uncaught exception in thread ${t.name}: ${e.message}")
            }
            thread
        }
        
        // Custom rejection policy
        val rejectionHandler = RejectedExecutionHandler { runnable, executor ->
            println("Task rejected: ${runnable}, Active threads: ${executor.activeCount}")
            // Could implement custom logic here (e.g., save to database, retry later)
        }
        
        // Create custom ThreadPoolExecutor
        val executor = ThreadPoolExecutor(
            2,                              // Core pool size
            4,                              // Maximum pool size  
            60L,                            // Keep alive time
            TimeUnit.SECONDS,               // Time unit
            ArrayBlockingQueue(5),          // Work queue (bounded)
            threadFactory,                  // Thread factory
            rejectionHandler                // Rejection policy
        )
        
        try {
            // Submit tasks to test various scenarios
            val taskCount = 15 // More than queue size + max threads
            
            repeat(taskCount) { taskId ->
                try {
                    executor.execute {
                        println("Executing task $taskId on ${Thread.currentThread().name}")
                        Thread.sleep(1000) // Simulate work
                        println("Task $taskId completed")
                    }
                } catch (e: RejectedExecutionException) {
                    println("Task $taskId was rejected")
                }
            }
            
            // Monitor pool state
            thread(name = "PoolMonitor") {
                repeat(10) {
                    println("Pool state - Active: ${executor.activeCount}, " +
                           "Pool size: ${executor.poolSize}, " +
                           "Queue size: ${executor.queue.size}")
                    Thread.sleep(500)
                }
            }
            
            Thread.sleep(6000) // Let tasks complete
            
        } finally {
            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                println("Forcing shutdown...")
                executor.shutdownNow()
            }
        }
        
        println("Custom thread pool demo completed\n")
    }
}

/**
 * Thread pool sizing demonstration
 */
class ThreadPoolSizing {
    
    fun demonstrateSizing() {
        println("=== Thread Pool Sizing Guidelines ===")
        
        val cpuCores = Runtime.getRuntime().availableProcessors()
        println("Available CPU cores: $cpuCores")
        
        // CPU-intensive tasks
        demonstrateCpuBoundTasks(cpuCores)
        
        // I/O-intensive tasks
        demonstrateIoBoundTasks(cpuCores)
    }
    
    private fun demonstrateCpuBoundTasks(cpuCores: Int) {
        println("\n1. CPU-Bound Tasks (computation-heavy):")
        println("   Recommended pool size: $cpuCores to ${cpuCores + 1}")
        
        val tasks = (1..20).map {
            Callable {
                val startTime = System.currentTimeMillis()
                // CPU-intensive calculation
                var sum = 0L
                repeat(10_000_000) { i ->
                    sum += i * i
                }
                val endTime = System.currentTimeMillis()
                "CPU task result: $sum, time: ${endTime - startTime}ms, thread: ${Thread.currentThread().name}"
            }
        }
        
        // Test with optimal pool size
        testPoolSize("CPU-Optimal", cpuCores, tasks)
        
        // Test with too many threads
        testPoolSize("CPU-TooMany", cpuCores * 4, tasks)
    }
    
    private fun demonstrateIoBoundTasks(cpuCores: Int) {
        println("\n2. I/O-Bound Tasks (waiting for I/O):")
        val ioOptimal = cpuCores * 2 // Common heuristic for I/O bound
        println("   Recommended pool size: $ioOptimal to ${cpuCores * 4}")
        
        val tasks = (1..20).map {
            Callable {
                val startTime = System.currentTimeMillis()
                // Simulate I/O delay
                Thread.sleep(200)
                val endTime = System.currentTimeMillis()
                "I/O task completed, time: ${endTime - startTime}ms, thread: ${Thread.currentThread().name}"
            }
        }
        
        // Test with I/O-optimized pool size
        testPoolSize("IO-Optimal", ioOptimal, tasks)
        
        // Test with CPU-sized pool (suboptimal for I/O)
        testPoolSize("IO-Suboptimal", cpuCores, tasks)
    }
    
    private fun testPoolSize(name: String, poolSize: Int, tasks: List<Callable<String>>) {
        val startTime = System.currentTimeMillis()
        
        val executor = Executors.newFixedThreadPool(poolSize) { runnable ->
            Thread(runnable, "$name-${Thread.activeCount()}")
        }
        
        try {
            val futures = executor.invokeAll(tasks)
            futures.forEach { it.get() } // Wait for completion
        } finally {
            executor.shutdown()
            executor.awaitTermination(30, TimeUnit.SECONDS)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        println("   $name ($poolSize threads): ${totalTime}ms")
    }
}

/**
 * Producer-Consumer pattern with thread pools
 */
class ProducerConsumerPool {
    
    private val buffer = LinkedBlockingQueue<String>(10)
    private val processedCount = AtomicInteger(0)
    
    fun demonstrateProducerConsumer() {
        println("=== Producer-Consumer with Thread Pools ===")
        
        val producerPool = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "Producer-${Thread.activeCount()}")
        }
        
        val consumerPool = Executors.newFixedThreadPool(3) { runnable ->
            Thread(runnable, "Consumer-${Thread.activeCount()}")
        }
        
        try {
            // Start producers
            repeat(2) { producerId ->
                producerPool.execute {
                    repeat(10) { itemId ->
                        val item = "Item-$producerId-$itemId"
                        try {
                            buffer.put(item) // Blocks if buffer is full
                            println("Produced: $item")
                            Thread.sleep(Random.nextLong(100, 300))
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return@execute
                        }
                    }
                }
            }
            
            // Start consumers
            repeat(3) { consumerId ->
                consumerPool.execute {
                    while (!Thread.currentThread().isInterrupted) {
                        try {
                            val item = buffer.poll(1, TimeUnit.SECONDS)
                            if (item != null) {
                                println("Consumer-$consumerId processing: $item")
                                Thread.sleep(Random.nextLong(200, 500)) // Simulate processing
                                val count = processedCount.incrementAndGet()
                                println("Consumer-$consumerId finished: $item (total processed: $count)")
                            }
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }
            }
            
            // Let it run
            Thread.sleep(8000)
            
        } finally {
            producerPool.shutdown()
            consumerPool.shutdown()
            
            producerPool.awaitTermination(5, TimeUnit.SECONDS)
            consumerPool.awaitTermination(5, TimeUnit.SECONDS)
        }
        
        println("Producer-Consumer demo completed. Total processed: ${processedCount.get()}\n")
    }
}

/**
 * Exception handling in thread pools
 */
class ThreadPoolExceptionHandling {
    
    fun demonstrateExceptionHandling() {
        println("=== Exception Handling in Thread Pools ===")
        
        val executor = Executors.newFixedThreadPool(2) { runnable ->
            val thread = Thread(runnable, "ExceptionTest-${Thread.activeCount()}")
            thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
                println("Uncaught exception in ${t.name}: ${e.message}")
            }
            thread
        }
        
        try {
            // Submit tasks that throw exceptions
            val future1 = executor.submit {
                println("Task 1 starting...")
                Thread.sleep(1000)
                throw RuntimeException("Task 1 failed!")
            }
            
            val future2 = executor.submit(Callable {
                println("Task 2 starting...")
                Thread.sleep(500)
                if (Random.nextBoolean()) {
                    throw IllegalStateException("Task 2 failed!")
                }
                "Task 2 completed successfully"
            })
            
            val future3 = executor.submit {
                println("Task 3 starting...")
                Thread.sleep(200)
                println("Task 3 completed successfully")
            }
            
            // Handle results and exceptions
            try {
                future1.get(2, TimeUnit.SECONDS)
            } catch (e: ExecutionException) {
                println("Future 1 threw: ${e.cause?.message}")
            }
            
            try {
                val result = future2.get(2, TimeUnit.SECONDS)
                println("Future 2 result: $result")
            } catch (e: ExecutionException) {
                println("Future 2 threw: ${e.cause?.message}")
            }
            
            future3.get(2, TimeUnit.SECONDS)
            
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
        
        println("Exception handling demo completed\n")
    }
}

/**
 * Thread pool monitoring and metrics
 */
class ThreadPoolMonitoring {
    
    fun demonstrateMonitoring() {
        println("=== Thread Pool Monitoring ===")
        
        val executor = ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            LinkedBlockingQueue(5)
        )
        
        // Start monitoring thread
        val monitor = thread(name = "PoolMonitor", isDaemon = true) {
            while (!Thread.currentThread().isInterrupted) {
                println("Pool metrics:")
                println("  Core pool size: ${executor.corePoolSize}")
                println("  Current pool size: ${executor.poolSize}")
                println("  Active threads: ${executor.activeCount}")
                println("  Task count: ${executor.taskCount}")
                println("  Completed tasks: ${executor.completedTaskCount}")
                println("  Queue size: ${executor.queue.size}")
                println("  Largest pool size: ${executor.largestPoolSize}")
                println()
                
                Thread.sleep(1000)
            }
        }
        
        try {
            // Submit various tasks
            repeat(15) { taskId ->
                executor.execute {
                    println("Task $taskId executing on ${Thread.currentThread().name}")
                    Thread.sleep(Random.nextLong(1000, 3000))
                    println("Task $taskId completed")
                }
                Thread.sleep(200) // Stagger submissions
            }
            
            Thread.sleep(10000) // Let tasks complete
            
        } finally {
            monitor.interrupt()
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
        
        println("Monitoring demo completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Compare different pool types
    ThreadPoolComparison().comparePoolTypes()
    
    // Custom thread pool configuration
    CustomThreadPool().demonstrateCustomConfiguration()
    
    // Thread pool sizing guidelines
    ThreadPoolSizing().demonstrateSizing()
    
    // Producer-consumer pattern
    ProducerConsumerPool().demonstrateProducerConsumer()
    
    // Exception handling
    ThreadPoolExceptionHandling().demonstrateExceptionHandling()
    
    // Monitoring and metrics
    ThreadPoolMonitoring().demonstrateMonitoring()
    
    println("=== Thread Pool Best Practices ===")
    println("1. Use appropriate pool type for your workload")
    println("2. Size pools based on workload characteristics:")
    println("   - CPU-bound: cores to cores+1")
    println("   - I/O-bound: cores*2 to cores*4")
    println("3. Always shutdown pools properly")
    println("4. Handle exceptions in submitted tasks")
    println("5. Monitor pool metrics in production")
    println("6. Use bounded queues to prevent memory exhaustion")
    println("7. Consider using CompletableFuture for modern async programming")
}