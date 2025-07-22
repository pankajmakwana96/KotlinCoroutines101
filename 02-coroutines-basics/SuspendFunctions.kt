/**
 * # Suspend Functions: The Foundation of Coroutines
 * 
 * ## Problem Description
 * Traditional blocking functions tie up entire threads while waiting for I/O
 * or other operations. This wastes resources and limits scalability.
 * Suspend functions solve this by allowing functions to pause execution
 * without blocking the thread.
 * 
 * ## Solution Approach
 * Suspend functions use continuation-passing style (CPS) transformation
 * to enable pausable execution. The Kotlin compiler transforms suspend
 * functions into state machines that can be paused and resumed.
 * 
 * ## Key Learning Points
 * - Suspend functions can only be called from coroutines or other suspend functions
 * - Compilation transforms suspend functions into state machines
 * - Continuations capture the execution state at suspension points
 * - Suspension is cooperative - functions must explicitly suspend
 * - No thread blocking occurs during suspension
 * 
 * ## Performance Considerations
 * - Suspend functions have minimal overhead (~10ns for suspension)
 * - No thread creation or blocking costs
 * - Memory usage scales with number of suspension points
 * - Compiler optimizations reduce state machine overhead
 * 
 * ## Common Pitfalls
 * - Calling blocking functions inside suspend functions
 * - Not understanding that suspend doesn't guarantee thread switching
 * - Forgetting that suspend functions need coroutine context
 * - Assuming suspend functions are automatically concurrent
 * 
 * ## Real-World Applications
 * - Network API calls
 * - Database operations
 * - File I/O operations
 * - Timer and delay operations
 * - User interface updates
 * 
 * ## Related Concepts
 * - CoroutineBuilders.kt - How to call suspend functions
 * - Dispatchers.kt - Thread context for suspension
 * - JobLifecycle.kt - Cancellation during suspension
 */

package coroutines

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

/**
 * Basic suspend function demonstration
 * 
 * Compiler transformation visualization:
 * 
 * Original suspend function:
 * suspend fun doWork() {
 *     delay(1000)
 *     println("Work done")
 * }
 * 
 * Transformed by compiler:
 * fun doWork(continuation: Continuation<Unit>): Any {
 *     when (continuation.label) {
 *         0 -> {
 *             continuation.label = 1
 *             return delay(1000, continuation)
 *         }
 *         1 -> {
 *             println("Work done")
 *             return COROUTINE_COMPLETED
 *         }
 *     }
 * }
 */
class SuspendBasics {
    
    suspend fun simpleDelay(millis: Long) {
        println("Starting delay of ${millis}ms at ${System.currentTimeMillis()}")
        delay(millis) // Suspension point
        println("Delay completed at ${System.currentTimeMillis()}")
    }
    
    suspend fun networkCall(): String {
        println("Starting network call...")
        delay(500) // Simulate network latency
        println("Network call completed")
        return "Response data"
    }
    
    suspend fun databaseQuery(): List<String> {
        println("Starting database query...")
        delay(200) // Simulate database query time
        println("Database query completed")
        return listOf("Record 1", "Record 2", "Record 3")
    }
    
    fun demonstrateBasicSuspension() = runBlocking {
        println("=== Basic Suspend Function Demonstration ===")
        
        val totalTime = measureTimeMillis {
            // Sequential execution - each suspend function waits for the previous
            simpleDelay(300)
            val response = networkCall()
            val data = databaseQuery()
            
            println("Response: $response")
            println("Data: $data")
        }
        
        println("Total time: ${totalTime}ms (should be ~1000ms)\n")
    }
}

/**
 * Suspension points and state machines
 */
class SuspensionPoints {
    
    suspend fun multipleSuspensionPoints(): String {
        println("Step 1: Starting complex operation")
        
        // Suspension point 1
        delay(100)
        println("Step 2: First delay completed")
        
        // Suspension point 2
        val data = fetchData()
        println("Step 3: Data fetched: $data")
        
        // Suspension point 3
        delay(100) 
        println("Step 4: Second delay completed")
        
        return "Operation completed with: $data"
    }
    
    private suspend fun fetchData(): String {
        delay(50) // Another suspension point
        return "important data"
    }
    
    /**
     * Compiler generates a state machine like this:
     * 
     * State 0: Initial call
     * State 1: After first delay(100)
     * State 2: After fetchData() call
     * State 3: After second delay(100)
     * State 4: Return result
     */
    fun demonstrateStateMachine() = runBlocking {
        println("=== Suspension Points and State Machine ===")
        
        val result = multipleSuspensionPoints()
        println("Final result: $result\n")
    }
}

/**
 * Continuation capture demonstration
 */
class ContinuationDemo {
    
    suspend fun demonstrateContinuation(): String {
        val localVar = "captured"
        println("Before suspension, localVar = $localVar")
        
        // At this point, the continuation captures:
        // - The local variable 'localVar'
        // - The execution state (which line to resume at)
        // - The call stack information
        delay(100)
        
        // After resumption, localVar is still available
        println("After suspension, localVar = $localVar")
        return "Continuation preserved state"
    }
    
    suspend fun complexStateCapture(): Int {
        var counter = 0
        val data = mutableListOf<String>()
        
        repeat(3) { iteration ->
            println("Iteration $iteration, counter = $counter")
            data.add("Item $iteration")
            
            // Suspension point - all local state is captured
            delay(50)
            
            counter++
            println("After suspension: iteration $iteration, counter = $counter")
        }
        
        println("Final data: $data")
        return counter
    }
    
    fun demonstrateContinuations() = runBlocking {
        println("=== Continuation State Capture ===")
        
        val result1 = demonstrateContinuation()
        println("Result 1: $result1")
        
        val result2 = complexStateCapture()
        println("Result 2: $result2\n")
    }
}

/**
 * Suspend function composition
 */
class SuspendComposition {
    
    suspend fun step1(): String {
        delay(100)
        return "Step 1 completed"
    }
    
    suspend fun step2(input: String): String {
        delay(100)
        return "$input -> Step 2 completed"
    }
    
    suspend fun step3(input: String): String {
        delay(100)
        return "$input -> Step 3 completed"
    }
    
    // Composing suspend functions
    suspend fun sequentialPipeline(): String {
        val result1 = step1()
        val result2 = step2(result1)
        val result3 = step3(result2)
        return result3
    }
    
    // Concurrent composition using async
    suspend fun concurrentComposition(): List<String> = coroutineScope {
        val deferred1 = async { step1() }
        val deferred2 = async { step2("Concurrent") }
        val deferred3 = async { step3("Parallel") }
        
        listOf(deferred1.await(), deferred2.await(), deferred3.await())
    }
    
    fun demonstrateComposition() = runBlocking {
        println("=== Suspend Function Composition ===")
        
        val sequentialTime = measureTimeMillis {
            val result = sequentialPipeline()
            println("Sequential result: $result")
        }
        println("Sequential time: ${sequentialTime}ms")
        
        val concurrentTime = measureTimeMillis {
            val results = concurrentComposition()
            println("Concurrent results: $results")
        }
        println("Concurrent time: ${concurrentTime}ms\n")
    }
}

/**
 * Exception handling in suspend functions
 */
class SuspendExceptions {
    
    suspend fun riskySuspendFunction(): String {
        delay(100)
        
        if (Math.random() < 0.5) {
            throw RuntimeException("Random failure in suspend function")
        }
        
        return "Success!"
    }
    
    suspend fun handleSuspendExceptions(): String {
        return try {
            riskySuspendFunction()
        } catch (e: Exception) {
            println("Caught exception: ${e.message}")
            delay(50) // Can still suspend in catch block
            "Recovered from error"
        }
    }
    
    suspend fun finallyInSuspend(): String {
        return try {
            delay(100)
            riskySuspendFunction()
        } catch (e: Exception) {
            println("Exception in suspend function: ${e.message}")
            throw e
        } finally {
            // Finally block can also contain suspend functions
            delay(50)
            println("Cleanup completed")
        }
    }
    
    fun demonstrateExceptions() = runBlocking {
        println("=== Exception Handling in Suspend Functions ===")
        
        repeat(3) { attempt ->
            try {
                val result = handleSuspendExceptions()
                println("Attempt ${attempt + 1}: $result")
            } catch (e: Exception) {
                println("Attempt ${attempt + 1}: Unhandled - ${e.message}")
            }
        }
        
        println("\nTesting finally blocks:")
        try {
            finallyInSuspend()
        } catch (e: Exception) {
            println("Finally demo completed with exception: ${e.message}")
        }
        println()
    }
}

/**
 * Suspend vs regular function performance comparison
 */
class PerformanceComparison {
    
    // Regular blocking function
    fun blockingFunction(): String {
        Thread.sleep(100) // Blocks the thread
        return "Blocking result"
    }
    
    // Suspend non-blocking function
    suspend fun suspendingFunction(): String {
        delay(100) // Suspends without blocking
        return "Suspending result"
    }
    
    fun comparePerformance() {
        println("=== Performance Comparison: Blocking vs Suspending ===")
        
        // Test blocking functions with limited threads
        val blockingTime = measureTimeMillis {
            runBlocking {
                val jobs = (1..10).map {
                    launch(Dispatchers.IO) { // Limited thread pool
                        blockingFunction()
                    }
                }
                jobs.forEach { it.join() }
            }
        }
        println("10 blocking functions: ${blockingTime}ms")
        
        // Test suspend functions
        val suspendingTime = measureTimeMillis {
            runBlocking {
                val jobs = (1..10).map {
                    launch {
                        suspendingFunction()
                    }
                }
                jobs.forEach { it.join() }
            }
        }
        println("10 suspending functions: ${suspendingTime}ms")
        
        // Test with many more coroutines (impossible with threads)
        val manySuspendTime = measureTimeMillis {
            runBlocking {
                val jobs = (1..1000).map {
                    launch {
                        delay(100)
                    }
                }
                jobs.forEach { it.join() }
            }
        }
        println("1000 suspending coroutines: ${manySuspendTime}ms")
        println("(1000 threads would likely crash or be very slow)\n")
    }
}

/**
 * Suspend function best practices
 */
class SuspendBestPractices {
    
    // ✅ Good: Proper suspend function
    suspend fun goodSuspendFunction(): String {
        // Only contains suspending calls or CPU work
        delay(100)
        val result = performCpuWork()
        return result
    }
    
    // ❌ Bad: Blocking call in suspend function
    suspend fun badSuspendFunction(): String {
        Thread.sleep(100) // Don't do this! Blocks the thread
        return "This blocks the thread"
    }
    
    // ✅ Good: Convert blocking to suspending
    suspend fun fixedSuspendFunction(): String {
        withContext(Dispatchers.IO) {
            // Blocking calls should be wrapped in appropriate dispatcher
            Thread.sleep(100)
        }
        return "This properly manages blocking"
    }
    
    private fun performCpuWork(): String {
        // CPU-intensive work is fine in suspend functions
        var result = ""
        repeat(1000) {
            result += "computed "
        }
        return result.take(20)
    }
    
    // ✅ Good: Cancellation-aware suspend function
    suspend fun cancellationAwareSuspend(): String {
        repeat(10) { iteration ->
            ensureActive() // Check for cancellation
            delay(100)
            println("Processing iteration $iteration")
        }
        return "All iterations completed"
    }
    
    fun demonstrateBestPractices() = runBlocking {
        println("=== Suspend Function Best Practices ===")
        
        // Good practice
        val goodResult = goodSuspendFunction()
        println("Good result: $goodResult")
        
        // Fixed blocking
        val fixedResult = fixedSuspendFunction()
        println("Fixed result: $fixedResult")
        
        // Cancellation awareness
        val job = launch {
            cancellationAwareSuspend()
        }
        
        delay(450) // Let it run for a bit
        job.cancel()
        job.join()
        println("Cancellation-aware function was cancelled")
        println()
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Basic suspend function mechanics
    SuspendBasics().demonstrateBasicSuspension()
    
    // Suspension points and state machines
    SuspensionPoints().demonstrateStateMachine()
    
    // Continuation state capture
    ContinuationDemo().demonstrateContinuations()
    
    // Function composition
    SuspendComposition().demonstrateComposition()
    
    // Exception handling
    SuspendExceptions().demonstrateExceptions()
    
    // Performance comparison
    PerformanceComparison().comparePerformance()
    
    // Best practices
    SuspendBestPractices().demonstrateBestPractices()
    
    println("=== Suspend Functions Summary ===")
    println("- Suspend functions enable non-blocking concurrency")
    println("- Compiler generates state machines for suspension points")
    println("- Continuations capture and restore execution state")
    println("- Only suspend at suspension points (delay, other suspend functions)")
    println("- Avoid blocking calls in suspend functions")
    println("- Use appropriate dispatchers for blocking operations")
    println("- Make functions cancellation-aware with ensureActive()")
    println("- Suspend functions are the foundation of all coroutine patterns")
}