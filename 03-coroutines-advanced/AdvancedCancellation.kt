/**
 * # Advanced Cancellation Patterns and Resource Management
 * 
 * ## Problem Description
 * Production systems require sophisticated cancellation handling for graceful
 * shutdowns, resource cleanup, timeout management, and cooperative cancellation.
 * Simple cancellation isn't enough - we need patterns that handle complex
 * scenarios while preventing resource leaks and data corruption.
 * 
 * ## Solution Approach
 * Advanced cancellation patterns include:
 * - Cooperative cancellation with proper cleanup
 * - Graceful shutdown sequences with timeouts
 * - Resource management during cancellation
 * - Cancellation with compensation actions
 * - Hierarchical cancellation strategies
 * 
 * ## Key Learning Points
 * - Cancellation is cooperative and requires explicit checks
 * - Resource cleanup must be guaranteed even during cancellation
 * - Graceful shutdown prevents data loss and corruption
 * - Timeout handling requires careful resource management
 * - Cancellation propagation follows job hierarchies
 * 
 * ## Performance Considerations
 * - Cancellation signal propagation: ~0.1-1μs
 * - Resource cleanup overhead: ~1-1000μs depending on resources
 * - Graceful shutdown: ~10-10000μs depending on complexity
 * - Cooperative checks add minimal overhead (~1-5ns)
 * 
 * ## Common Pitfalls
 * - Not checking for cancellation in long-running loops
 * - Blocking operations that can't be cancelled
 * - Resource leaks during cancellation
 * - Not using NonCancellable for critical cleanup
 * - Ignoring CancellationException handling
 * 
 * ## Real-World Applications
 * - Application shutdown sequences
 * - Request timeout handling
 * - Database transaction rollback
 * - Network connection cleanup
 * - Background task management
 * 
 * ## Related Concepts
 * - JobLifecycle.kt - Basic cancellation concepts
 * - CustomScopes.kt - Scope-level cancellation
 * - ExceptionHandling.kt - Exception during cancellation
 */

package coroutines.advanced

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Cooperative cancellation patterns
 * 
 * Cancellation Cooperation:
 * 
 * Long-Running Operation:
 * ┌─────────────────────────────────────────┐
 * │ for (item in items) {                   │
 * │   ensureActive() ←── Cancellation Check │
 * │   processItem(item)                     │
 * │   yield() ←── Cooperative Suspension    │
 * │ }                                       │
 * └─────────────────────────────────────────┘
 * 
 * Cancellation Signal:
 * Parent ──cancel()──> Child ──ensureActive()──> CancellationException
 */
class CooperativeCancellation {
    
    fun demonstrateBasicCooperativeCancellation() = runBlocking {
        println("=== Basic Cooperative Cancellation ===")
        
        val job = launch {
            try {
                repeat(1000) { iteration ->
                    // Cooperative cancellation check
                    ensureActive()
                    
                    // Simulate work
                    Thread.sleep(50) // Intentionally blocking to show cancellation
                    
                    if (iteration % 10 == 0) {
                        println("  Processed batch $iteration")
                    }
                }
                println("  All batches completed")
            } catch (e: CancellationException) {
                println("  Operation cancelled at some iteration")
                throw e // Re-throw to complete cancellation
            }
        }
        
        delay(300) // Let it run for a bit
        println("Cancelling job...")
        job.cancel()
        job.join()
        
        println("Basic cooperative cancellation completed\n")
    }
    
    fun demonstrateYieldBasedCancellation() = runBlocking {
        println("=== Yield-Based Cancellation ===")
        
        val job = launch {
            try {
                repeat(100) { iteration ->
                    // yield() checks for cancellation and suspends briefly
                    yield()
                    
                    // CPU-intensive work
                    var sum = 0
                    repeat(100_000) { sum += it }
                    
                    if (iteration % 20 == 0) {
                        println("  Yield-based: Completed iteration $iteration, sum=$sum")
                    }
                }
                println("  Yield-based: All iterations completed")
            } catch (e: CancellationException) {
                println("  Yield-based: Cancelled gracefully")
                throw e
            }
        }
        
        delay(200)
        job.cancel()
        job.join()
        
        println("Yield-based cancellation completed\n")
    }
    
    fun demonstrateCustomCancellationChecks() = runBlocking {
        println("=== Custom Cancellation Checks ===")
        
        suspend fun processLargeDataset(data: List<String>) {
            var processed = 0
            
            try {
                for ((index, item) in data.withIndex()) {
                    // Custom cancellation check with progress reporting
                    if (!isActive) {
                        println("    Cancellation detected at item $index")
                        throw CancellationException("Processing cancelled")
                    }
                    
                    // Simulate processing
                    delay(Random.nextLong(10, 50))
                    processed++
                    
                    if (index % 10 == 0) {
                        println("    Processed $processed/${data.size} items")
                    }
                }
                
                println("    Successfully processed all $processed items")
                
            } catch (e: CancellationException) {
                println("    Processing cancelled. Processed $processed/${data.size} items")
                throw e
            }
        }
        
        val data = (1..50).map { "Item-$it" }
        val job = launch {
            processLargeDataset(data)
        }
        
        delay(400)
        job.cancel()
        job.join()
        
        println("Custom cancellation checks completed\n")
    }
}

/**
 * Resource cleanup during cancellation
 */
class CancellationResourceCleanup {
    
    class ManagedResource(private val name: String) {
        private val isAcquired = AtomicBoolean(false)
        
        fun acquire(): Boolean {
            if (isAcquired.compareAndSet(false, true)) {
                println("    Resource '$name' acquired")
                return true
            }
            return false
        }
        
        fun release() {
            if (isAcquired.compareAndSet(true, false)) {
                println("    Resource '$name' released")
            }
        }
        
        fun isAcquired(): Boolean = isAcquired.get()
    }
    
    fun demonstrateResourceCleanup() = runBlocking {
        println("=== Resource Cleanup During Cancellation ===")
        
        val job = launch {
            val resource1 = ManagedResource("Database")
            val resource2 = ManagedResource("Network")
            val resource3 = ManagedResource("FileHandle")
            
            try {
                // Acquire resources
                resource1.acquire()
                resource2.acquire()
                resource3.acquire()
                
                // Simulate work
                repeat(20) { i ->
                    ensureActive() // Check for cancellation
                    println("  Working with resources: step $i")
                    delay(100)
                }
                
                println("  Work completed successfully")
                
            } catch (e: CancellationException) {
                println("  Work cancelled, cleaning up resources...")
                throw e
            } finally {
                // Guaranteed cleanup even if cancelled
                resource1.release()
                resource2.release()
                resource3.release()
                println("  All resources cleaned up")
            }
        }
        
        delay(600)
        job.cancel()
        job.join()
        
        println("Resource cleanup demo completed\n")
    }
    
    fun demonstrateNonCancellableCleanup() = runBlocking {
        println("=== Non-Cancellable Cleanup ===")
        
        val job = launch {
            val criticalResource = ManagedResource("CriticalData")
            
            try {
                criticalResource.acquire()
                
                repeat(15) { i ->
                    ensureActive()
                    println("  Critical operation: step $i")
                    delay(100)
                }
                
            } catch (e: CancellationException) {
                println("  Critical operation cancelled")
                
                // Critical cleanup that MUST complete
                withContext(NonCancellable) {
                    println("  Performing critical cleanup (non-cancellable)...")
                    
                    // Simulate critical cleanup operations
                    delay(200) // This delay cannot be cancelled
                    criticalResource.release()
                    
                    // Save critical state
                    delay(100) // This delay also cannot be cancelled
                    println("  Critical state saved")
                    
                    // Notify external systems
                    delay(50)
                    println("  External systems notified")
                }
                
                println("  Critical cleanup completed")
                throw e
            }
        }
        
        delay(400)
        job.cancel()
        job.join()
        
        println("Non-cancellable cleanup completed\n")
    }
    
    fun demonstrateResourcePoolCleanup() = runBlocking {
        println("=== Resource Pool Cleanup ===")
        
        class ResourcePool(private val name: String, initialSize: Int) {
            private val available = AtomicInteger(initialSize)
            private val inUse = AtomicInteger(0)
            
            fun acquire(): Boolean {
                if (available.get() > 0) {
                    available.decrementAndGet()
                    inUse.incrementAndGet()
                    println("    Pool '$name': Acquired resource (available: ${available.get()}, in-use: ${inUse.get()})")
                    return true
                }
                return false
            }
            
            fun release() {
                if (inUse.get() > 0) {
                    inUse.decrementAndGet()
                    available.incrementAndGet()
                    println("    Pool '$name': Released resource (available: ${available.get()}, in-use: ${inUse.get()})")
                }
            }
            
            fun forceReleaseAll() {
                val released = inUse.getAndSet(0)
                available.addAndGet(released)
                if (released > 0) {
                    println("    Pool '$name': Force released $released resources")
                }
            }
            
            fun getStats(): String = "available: ${available.get()}, in-use: ${inUse.get()}"
        }
        
        val dbPool = ResourcePool("Database", 5)
        val networkPool = ResourcePool("Network", 3)
        
        supervisorScope {
            // Multiple tasks using resources
            repeat(8) { taskId ->
                launch {
                    try {
                        if (dbPool.acquire() && networkPool.acquire()) {
                            repeat(10) { step ->
                                ensureActive()
                                println("  Task $taskId: Step $step")
                                delay(50)
                            }
                        }
                    } catch (e: CancellationException) {
                        println("  Task $taskId: Cancelled")
                        throw e
                    } finally {
                        dbPool.release()
                        networkPool.release()
                    }
                }
            }
            
            delay(300)
            
            // Cancel all tasks
            println("Cancelling all tasks...")
            coroutineContext[Job]?.cancelChildren()
            
            // Cleanup any remaining resources
            withContext(NonCancellable) {
                dbPool.forceReleaseAll()
                networkPool.forceReleaseAll()
                println("  Final stats - DB: ${dbPool.getStats()}, Network: ${networkPool.getStats()}")
            }
        }
        
        println("Resource pool cleanup completed\n")
    }
}

/**
 * Graceful shutdown patterns
 */
class GracefulShutdown {
    
    class WorkerService(private val serviceName: String) {
        private val isRunning = AtomicBoolean(false)
        private var serviceJob: Job? = null
        
        fun start(scope: CoroutineScope) {
            if (isRunning.compareAndSet(false, true)) {
                serviceJob = scope.launch {
                    try {
                        println("  $serviceName: Started")
                        
                        while (isActive) {
                            // Simulate work
                            delay(200)
                            println("    $serviceName: Processing...")
                        }
                        
                    } catch (e: CancellationException) {
                        println("  $serviceName: Received shutdown signal")
                        
                        // Graceful shutdown sequence
                        withContext(NonCancellable) {
                            println("    $serviceName: Finishing current work...")
                            delay(100) // Finish current work
                            
                            println("    $serviceName: Saving state...")
                            delay(50) // Save state
                            
                            println("    $serviceName: Releasing resources...")
                            delay(30) // Release resources
                        }
                        
                        throw e
                    } finally {
                        isRunning.set(false)
                        println("  $serviceName: Stopped")
                    }
                }
            }
        }
        
        suspend fun stop() {
            serviceJob?.cancel()
            serviceJob?.join()
        }
        
        fun isRunning(): Boolean = isRunning.get()
    }
    
    fun demonstrateGracefulShutdown() = runBlocking {
        println("=== Graceful Shutdown ===")
        
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        
        val services = listOf(
            WorkerService("UserService"),
            WorkerService("OrderService"),
            WorkerService("NotificationService"),
            WorkerService("AuditService")
        )
        
        // Start all services
        services.forEach { it.start(serviceScope) }
        
        delay(800) // Let services run
        
        println("Initiating graceful shutdown...")
        
        // Shutdown services in reverse order (dependency-aware)
        services.reversed().forEach { service ->
            service.stop()
        }
        
        serviceScope.cancel()
        println("Graceful shutdown completed\n")
    }
    
    fun demonstrateTimedShutdown() = runBlocking {
        println("=== Timed Shutdown ===")
        
        suspend fun timedShutdown(
            job: Job,
            gracefulTimeoutMs: Long = 5000,
            forceTimeoutMs: Long = 2000
        ) {
            println("Starting graceful shutdown (${gracefulTimeoutMs}ms timeout)...")
            
            job.cancel() // Signal cancellation
            
            try {
                withTimeout(gracefulTimeoutMs) {
                    job.join() // Wait for graceful completion
                }
                println("Graceful shutdown completed successfully")
            } catch (e: TimeoutCancellationException) {
                println("Graceful shutdown timed out, forcing termination...")
                
                try {
                    withTimeout(forceTimeoutMs) {
                        job.join() // Wait a bit more for force shutdown
                    }
                } catch (e: TimeoutCancellationException) {
                    println("Force shutdown also timed out - system may be unresponsive")
                }
            }
        }
        
        val longRunningJob = launch {
            try {
                repeat(50) { i ->
                    ensureActive()
                    println("  Long task: iteration $i")
                    delay(200)
                }
            } catch (e: CancellationException) {
                println("  Long task: Received cancellation, cleaning up...")
                
                // Simulate slow cleanup
                withContext(NonCancellable) {
                    delay(1000) // This should complete within graceful timeout
                    println("  Long task: Cleanup completed")
                }
                
                throw e
            }
        }
        
        delay(1000) // Let it run
        
        timedShutdown(longRunningJob, gracefulTimeoutMs = 2000, forceTimeoutMs = 1000)
        
        println("Timed shutdown completed\n")
    }
}

/**
 * Timeout handling with cancellation
 */
class TimeoutHandling {
    
    fun demonstrateBasicTimeouts() = runBlocking {
        println("=== Basic Timeout Handling ===")
        
        suspend fun longOperation(): String {
            delay(3000)
            return "Operation completed"
        }
        
        // Timeout with exception
        try {
            val result = withTimeout(1000) {
                longOperation()
            }
            println("Result: $result")
        } catch (e: TimeoutCancellationException) {
            println("Operation timed out after 1000ms")
        }
        
        // Timeout with null result
        val result = withTimeoutOrNull(1000) {
            longOperation()
        }
        
        if (result != null) {
            println("Result: $result")
        } else {
            println("Operation timed out, got null result")
        }
        
        println("Basic timeout handling completed\n")
    }
    
    fun demonstrateNestedTimeouts() = runBlocking {
        println("=== Nested Timeout Handling ===")
        
        suspend fun outerOperation(): String {
            return withTimeout(2000) { // Outer timeout
                println("  Outer operation started")
                
                val part1 = withTimeout(800) { // Inner timeout 1
                    delay(500)
                    "Part 1 completed"
                }
                
                val part2 = withTimeoutOrNull(800) { // Inner timeout 2
                    delay(1000) // This will timeout
                    "Part 2 completed"
                }
                
                "$part1, ${part2 ?: "Part 2 timed out"}"
            }
        }
        
        try {
            val result = outerOperation()
            println("Result: $result")
        } catch (e: TimeoutCancellationException) {
            println("Outer operation timed out")
        }
        
        println("Nested timeout handling completed\n")
    }
    
    fun demonstrateTimeoutWithCleanup() = runBlocking {
        println("=== Timeout with Resource Cleanup ===")
        
        class TimedResource(private val name: String) {
            private var acquired = false
            
            fun acquire() {
                acquired = true
                println("    Resource '$name' acquired")
            }
            
            fun release() {
                if (acquired) {
                    acquired = false
                    println("    Resource '$name' released")
                }
            }
        }
        
        suspend fun operationWithCleanup(): String {
            val resource = TimedResource("TimedDB")
            
            try {
                resource.acquire()
                
                // This will timeout
                withTimeout(500) {
                    delay(1000) // Longer than timeout
                    "Operation completed"
                }
                
            } catch (e: TimeoutCancellationException) {
                println("  Operation timed out, cleaning up...")
                
                // Cleanup in NonCancellable context
                withContext(NonCancellable) {
                    delay(100) // Cleanup time
                    resource.release()
                    println("  Cleanup completed after timeout")
                }
                
                throw e
            } finally {
                // Additional cleanup if needed
                resource.release()
            }
        }
        
        try {
            operationWithCleanup()
        } catch (e: TimeoutCancellationException) {
            println("Caught timeout exception after cleanup")
        }
        
        println("Timeout with cleanup completed\n")
    }
    
    fun demonstrateCustomTimeoutHandling() = runBlocking {
        println("=== Custom Timeout Handling ===")
        
        class TimeoutManager {
            suspend fun <T> executeWithCustomTimeout(
                timeoutMs: Long,
                onTimeout: suspend () -> Unit = {},
                operation: suspend () -> T
            ): Result<T> {
                return try {
                    val result = withTimeout(timeoutMs) {
                        operation()
                    }
                    Result.success(result)
                } catch (e: TimeoutCancellationException) {
                    println("  Custom timeout handler triggered")
                    onTimeout()
                    Result.failure(e)
                }
            }
            
            suspend fun <T> executeWithRetryOnTimeout(
                timeoutMs: Long,
                maxRetries: Int,
                operation: suspend () -> T
            ): Result<T> {
                repeat(maxRetries) { attempt ->
                    try {
                        val result = withTimeout(timeoutMs) {
                            operation()
                        }
                        if (attempt > 0) {
                            println("  Operation succeeded on retry $attempt")
                        }
                        return Result.success(result)
                    } catch (e: TimeoutCancellationException) {
                        println("  Attempt ${attempt + 1} timed out")
                        if (attempt < maxRetries - 1) {
                            delay(100 * (attempt + 1)) // Exponential backoff
                        }
                    }
                }
                return Result.failure(TimeoutCancellationException("Operation failed after $maxRetries attempts"))
            }
        }
        
        val timeoutManager = TimeoutManager()
        
        // Custom timeout with callback
        val result1 = timeoutManager.executeWithCustomTimeout(
            timeoutMs = 300,
            onTimeout = {
                println("  Custom timeout callback: Logging timeout event")
                delay(50) // Could send alert, log to monitoring system, etc.
            }
        ) {
            delay(500) // This will timeout
            "Success"
        }
        
        result1.fold(
            onSuccess = { value -> println("Result: $value") },
            onFailure = { exception -> println("Failed: ${exception.message}") }
        )
        
        // Retry on timeout
        val result2 = timeoutManager.executeWithRetryOnTimeout(
            timeoutMs = 200,
            maxRetries = 3
        ) {
            if (Random.nextBoolean()) {
                delay(300) // Sometimes timeout
            } else {
                delay(100) // Sometimes succeed
            }
            "Retry success"
        }
        
        result2.fold(
            onSuccess = { value -> println("Retry result: $value") },
            onFailure = { exception -> println("Retry failed: ${exception.message}") }
        )
        
        println("Custom timeout handling completed\n")
    }
}

/**
 * Cancellation in producer-consumer scenarios
 */
class ProducerConsumerCancellation {
    
    fun demonstrateProducerConsumerCancellation() = runBlocking {
        println("=== Producer-Consumer Cancellation ===")
        
        data class WorkItem(val id: Int, val data: String)
        
        val channel = kotlinx.coroutines.channels.Channel<WorkItem>(capacity = 10)
        
        // Producer
        val producer = launch {
            try {
                repeat(50) { i ->
                    ensureActive() // Check for cancellation
                    
                    val item = WorkItem(i, "Data-$i")
                    channel.send(item)
                    println("  Produced: ${item.id}")
                    
                    delay(50) // Production delay
                }
            } catch (e: CancellationException) {
                println("  Producer cancelled")
                throw e
            } finally {
                channel.close()
                println("  Producer closed channel")
            }
        }
        
        // Consumer
        val consumer = launch {
            try {
                for (item in channel) {
                    ensureActive() // Check for cancellation
                    
                    println("  Consuming: ${item.id}")
                    delay(80) // Processing delay
                }
                println("  Consumer completed normally")
            } catch (e: CancellationException) {
                println("  Consumer cancelled")
                throw e
            } finally {
                println("  Consumer cleanup completed")
            }
        }
        
        delay(1000) // Let them work
        
        println("Cancelling producer-consumer...")
        producer.cancel()
        consumer.cancel()
        
        producer.join()
        consumer.join()
        
        println("Producer-consumer cancellation completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    runBlocking {
        // Cooperative cancellation
        CooperativeCancellation().demonstrateBasicCooperativeCancellation()
        CooperativeCancellation().demonstrateYieldBasedCancellation()
        CooperativeCancellation().demonstrateCustomCancellationChecks()
        
        // Resource cleanup
        CancellationResourceCleanup().demonstrateResourceCleanup()
        CancellationResourceCleanup().demonstrateNonCancellableCleanup()
        CancellationResourceCleanup().demonstrateResourcePoolCleanup()
        
        // Graceful shutdown
        GracefulShutdown().demonstrateGracefulShutdown()
        GracefulShutdown().demonstrateTimedShutdown()
        
        // Timeout handling
        TimeoutHandling().demonstrateBasicTimeouts()
        TimeoutHandling().demonstrateNestedTimeouts()
        TimeoutHandling().demonstrateTimeoutWithCleanup()
        TimeoutHandling().demonstrateCustomTimeoutHandling()
        
        // Producer-consumer cancellation
        ProducerConsumerCancellation().demonstrateProducerConsumerCancellation()
        
        println("=== Advanced Cancellation Summary ===")
        println("✅ Cooperative Cancellation:")
        println("   - Use ensureActive() or yield() in long-running operations")
        println("   - Check isActive property for custom cancellation logic")
        println("   - Always re-throw CancellationException after cleanup")
        println()
        println("✅ Resource Management:")
        println("   - Use try/finally blocks for guaranteed cleanup")
        println("   - Use NonCancellable context for critical cleanup operations")
        println("   - Implement resource pools with proper cancellation handling")
        println()
        println("✅ Graceful Shutdown:")
        println("   - Signal cancellation first, then wait for completion")
        println("   - Implement timed shutdown with graceful and force timeouts")
        println("   - Shutdown services in dependency order")
        println()
        println("✅ Timeout Handling:")
        println("   - Use withTimeout for operations that must complete quickly")
        println("   - Use withTimeoutOrNull when null is acceptable fallback")
        println("   - Implement custom timeout logic with retry and fallback")
        println()
        println("✅ Best Practices:")
        println("   - Make all long-running operations cancellation-aware")
        println("   - Test cancellation scenarios in your application")
        println("   - Monitor resource cleanup in production")
        println("   - Implement proper error handling during cancellation")
    }
}