/**
 * # Dispatchers: Thread Management in Coroutines
 * 
 * ## Problem Description
 * Different types of work require different threading strategies. CPU-intensive
 * work should run on dedicated computation threads, I/O operations need threads
 * that can block, and UI updates must happen on the main thread. Dispatchers
 * solve this by providing appropriate thread pools for different workloads.
 * 
 * ## Solution Approach
 * Kotlin provides several built-in dispatchers:
 * - Dispatchers.Main: For UI updates and main thread work
 * - Dispatchers.IO: For blocking I/O operations
 * - Dispatchers.Default: For CPU-intensive computations
 * - Dispatchers.Unconfined: For testing and special cases
 * - Custom dispatchers: For specific requirements
 * 
 * ## Key Learning Points
 * - Different dispatchers for different workload types
 * - Context switching with withContext()
 * - Dispatcher inheritance in coroutine hierarchies
 * - Performance implications of dispatcher choice
 * - When and how to create custom dispatchers
 * 
 * ## Performance Considerations
 * - Main dispatcher: Single thread, avoid blocking
 * - IO dispatcher: 64+ threads, can handle blocking
 * - Default dispatcher: CPU core count threads
 * - Context switching overhead: ~10-50 nanoseconds
 * - Thread pool reuse eliminates creation overhead
 * 
 * ## Common Pitfalls
 * - Blocking the Main dispatcher
 * - Using wrong dispatcher for workload type
 * - Excessive context switching
 * - Not understanding dispatcher inheritance
 * - Creating unnecessary custom dispatchers
 * 
 * ## Real-World Applications
 * - Android UI updates (Main)
 * - Network API calls (IO)
 * - Image processing (Default)
 * - Database operations (IO)
 * - Mathematical computations (Default)
 * 
 * ## Related Concepts
 * - CoroutineBuilders.kt - Context propagation
 * - StructuredConcurrency.kt - Scope and context
 * - PerformanceComparison.kt - Threading performance
 */

package coroutines

import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Built-in dispatchers overview
 * 
 * Dispatcher Architecture:
 * 
 * ┌─────────────────┐    ┌─────────────────┐
 * │ Dispatchers.Main│    │Dispatchers.IO   │
 * │   (UI Thread)   │    │ (64+ threads)   │
 * └─────────────────┘    └─────────────────┘
 *          │                       │
 *          │                       │
 * ┌─────────────────┐    ┌─────────────────┐
 * │Dispatchers.     │    │ Custom          │
 * │Default          │    │ Dispatchers     │
 * │(CPU cores)      │    │                 │
 * └─────────────────┘    └─────────────────┘
 */
class DispatcherOverview {
    
    fun demonstrateBuiltInDispatchers() = runBlocking {
        println("=== Built-in Dispatchers Overview ===")
        
        // Default dispatcher - CPU-intensive work
        launch(Dispatchers.Default) {
            val threadName = Thread.currentThread().name
            println("Default dispatcher: $threadName")
            
            // Simulate CPU-intensive work
            var result = 0
            repeat(1_000_000) { result += it }
            println("CPU work completed on Default: $result")
        }
        
        // IO dispatcher - blocking operations
        launch(Dispatchers.IO) {
            val threadName = Thread.currentThread().name
            println("IO dispatcher: $threadName")
            
            // Simulate blocking I/O
            Thread.sleep(100) // This is okay on IO dispatcher
            println("Blocking I/O completed on IO")
        }
        
        // Unconfined dispatcher - inherits caller's thread
        launch(Dispatchers.Unconfined) {
            val threadName = Thread.currentThread().name
            println("Unconfined dispatcher (before delay): $threadName")
            
            delay(10) // After suspension, may run on different thread
            
            val threadNameAfter = Thread.currentThread().name
            println("Unconfined dispatcher (after delay): $threadNameAfter")
        }
        
        delay(200) // Wait for all to complete
        println("Built-in dispatchers demo completed\n")
    }
    
    fun demonstrateDispatcherCharacteristics() = runBlocking {
        println("=== Dispatcher Characteristics ===")
        
        // Default dispatcher info
        println("Default dispatcher:")
        println("  Thread pool size: ${(Dispatchers.Default as CoroutineDispatcher).toString()}")
        println("  Intended for: CPU-intensive work")
        
        // IO dispatcher info
        println("\nIO dispatcher:")
        println("  Thread pool size: Minimum 64 threads")
        println("  Intended for: Blocking I/O operations")
        
        // Test dispatcher limits
        testDispatcherLimits()
        
        println()
    }
    
    private suspend fun testDispatcherLimits() {
        println("\nTesting dispatcher limits:")
        
        // Test Default dispatcher with many CPU tasks
        val defaultJobs = (1..20).map { taskId ->
            async(Dispatchers.Default) {
                val threadName = Thread.currentThread().name
                // Simulate CPU work
                var sum = 0
                repeat(100_000) { sum += it }
                "Task $taskId on $threadName: $sum"
            }
        }
        
        // Test IO dispatcher with many blocking tasks
        val ioJobs = (1..100).map { taskId ->
            async(Dispatchers.IO) {
                val threadName = Thread.currentThread().name
                Thread.sleep(50) // Blocking operation
                "Blocking task $taskId on $threadName"
            }
        }
        
        defaultJobs.awaitAll()
        ioJobs.awaitAll()
        
        println("  Default and IO dispatchers handled concurrent load successfully")
    }
}

/**
 * Context switching with withContext()
 */
class ContextSwitching {
    
    fun demonstrateContextSwitching() = runBlocking {
        println("=== Context Switching ===")
        
        val startThread = Thread.currentThread().name
        println("Starting on thread: $startThread")
        
        // Switch to Default dispatcher for CPU work
        val cpuResult = withContext(Dispatchers.Default) {
            val currentThread = Thread.currentThread().name
            println("CPU work on thread: $currentThread")
            
            // Simulate heavy computation
            (1..1000).fold(0) { acc, n -> acc + n * n }
        }
        
        println("CPU result: $cpuResult")
        
        // Switch to IO dispatcher for blocking work
        val ioResult = withContext(Dispatchers.IO) {
            val currentThread = Thread.currentThread().name
            println("I/O work on thread: $currentThread")
            
            // Simulate blocking I/O
            Thread.sleep(100)
            "I/O operation completed"
        }
        
        println("I/O result: $ioResult")
        
        val endThread = Thread.currentThread().name
        println("Ending on thread: $endThread")
        
        println("Context switching demo completed\n")
    }
    
    fun demonstrateNestedContextSwitching() = runBlocking {
        println("=== Nested Context Switching ===")
        
        launch(Dispatchers.Default) {
            println("1. Starting on Default: ${Thread.currentThread().name}")
            
            val result1 = withContext(Dispatchers.IO) {
                println("2. Switched to IO: ${Thread.currentThread().name}")
                Thread.sleep(50)
                
                val nestedResult = withContext(Dispatchers.Default) {
                    println("3. Nested switch to Default: ${Thread.currentThread().name}")
                    (1..100).sum()
                }
                
                println("4. Back to IO: ${Thread.currentThread().name}")
                "IO result with nested: $nestedResult"
            }
            
            println("5. Back to Default: ${Thread.currentThread().name}")
            println("Final result: $result1")
        }.join()
        
        println("Nested context switching completed\n")
    }
    
    fun demonstrateContextInheritance() = runBlocking(Dispatchers.Default) {
        println("=== Context Inheritance ===")
        
        println("Parent context: ${Thread.currentThread().name}")
        
        // Child inherits parent dispatcher
        launch {
            println("Child (inherited): ${Thread.currentThread().name}")
        }
        
        // Child with explicit dispatcher
        launch(Dispatchers.IO) {
            println("Child (explicit IO): ${Thread.currentThread().name}")
        }
        
        // Nested coroutine inherits immediate parent
        launch(Dispatchers.IO) {
            println("Parent IO: ${Thread.currentThread().name}")
            
            launch {
                println("Child of IO (inherited): ${Thread.currentThread().name}")
            }
            
            delay(50)
        }
        
        delay(100)
        println("Context inheritance demo completed\n")
    }
}

/**
 * Performance comparison between dispatchers
 */
class DispatcherPerformance {
    
    fun compareDispatcherPerformance() = runBlocking {
        println("=== Dispatcher Performance Comparison ===")
        
        val iterations = 1000
        
        // CPU-intensive task
        suspend fun cpuIntensiveTask(): Int {
            return (1..1000).fold(0) { acc, n -> acc + n }
        }
        
        // I/O simulation task
        suspend fun ioTask(): String {
            delay(1) // Non-blocking I/O simulation
            return "I/O result"
        }
        
        // Blocking I/O task
        suspend fun blockingIoTask(): String {
            Thread.sleep(1) // Blocking I/O simulation
            return "Blocking I/O result"
        }
        
        // Test CPU tasks on different dispatchers
        println("CPU-intensive tasks:")
        
        val defaultTime = measureTimeMillis {
            (1..iterations).map {
                async(Dispatchers.Default) { cpuIntensiveTask() }
            }.awaitAll()
        }
        println("  Default dispatcher: ${defaultTime}ms")
        
        val ioTimeForCpu = measureTimeMillis {
            (1..iterations).map {
                async(Dispatchers.IO) { cpuIntensiveTask() }
            }.awaitAll()
        }
        println("  IO dispatcher: ${ioTimeForCpu}ms (suboptimal)")
        
        // Test I/O tasks on different dispatchers
        println("\nNon-blocking I/O tasks:")
        
        val defaultTimeForIo = measureTimeMillis {
            (1..100).map {
                async(Dispatchers.Default) { ioTask() }
            }.awaitAll()
        }
        println("  Default dispatcher: ${defaultTimeForIo}ms")
        
        val ioTimeForIo = measureTimeMillis {
            (1..100).map {
                async(Dispatchers.IO) { ioTask() }
            }.awaitAll()
        }
        println("  IO dispatcher: ${ioTimeForIo}ms")
        
        // Test blocking I/O (only on IO dispatcher)
        println("\nBlocking I/O tasks:")
        
        val blockingTime = measureTimeMillis {
            (1..50).map {
                async(Dispatchers.IO) { blockingIoTask() }
            }.awaitAll()
        }
        println("  IO dispatcher: ${blockingTime}ms")
        
        println("(Blocking tasks should NEVER run on Default dispatcher)")
        println()
    }
    
    fun demonstrateDispatcherSaturation() = runBlocking {
        println("=== Dispatcher Saturation ===")
        
        // Saturate Default dispatcher with blocking operations
        println("Saturating Default dispatcher with blocking calls (DON'T DO THIS):")
        
        val badTime = measureTimeMillis {
            val jobs = (1..8).map { // Assuming 8 cores
                async(Dispatchers.Default) {
                    println("Blocking Default thread: ${Thread.currentThread().name}")
                    Thread.sleep(500) // BAD: Blocking the Default dispatcher
                    "Bad result"
                }
            }
            
            // This additional CPU task will be delayed
            val cpuTask = async(Dispatchers.Default) {
                val start = System.currentTimeMillis()
                (1..1_000_000).sum()
                val end = System.currentTimeMillis()
                "CPU task delayed by: ${end - start}ms"
            }
            
            jobs.awaitAll()
            val result = cpuTask.await()
            println("  $result")
        }
        println("  Bad approach took: ${badTime}ms")
        
        // Proper approach: use IO for blocking, Default for CPU
        println("\nProper dispatcher usage:")
        
        val goodTime = measureTimeMillis {
            val blockingJobs = (1..8).map {
                async(Dispatchers.IO) {
                    Thread.sleep(500) // GOOD: Blocking on IO dispatcher
                    "Good blocking result"
                }
            }
            
            val cpuTask = async(Dispatchers.Default) {
                val start = System.currentTimeMillis()
                (1..1_000_000).sum()
                val end = System.currentTimeMillis()
                "CPU task completed in: ${end - start}ms"
            }
            
            blockingJobs.awaitAll()
            val result = cpuTask.await()
            println("  $result")
        }
        println("  Good approach took: ${goodTime}ms")
        println()
    }
}

/**
 * Custom dispatchers
 */
class CustomDispatchers {
    
    fun demonstrateCustomDispatchers() = runBlocking {
        println("=== Custom Dispatchers ===")
        
        // Create custom single-threaded dispatcher
        val singleThreadDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "CustomSingleThread")
        }.asCoroutineDispatcher()
        
        try {
            // All tasks run on the same thread
            val jobs = (1..5).map { taskId ->
                async(singleThreadDispatcher) {
                    val threadName = Thread.currentThread().name
                    delay(Random.nextLong(50, 150))
                    "Task $taskId on $threadName"
                }
            }
            
            val results = jobs.awaitAll()
            results.forEach { println("  $it") }
            
        } finally {
            singleThreadDispatcher.close()
        }
        
        // Create custom limited thread pool
        val limitedDispatcher = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "CustomLimited-${Thread.activeCount()}")
        }.asCoroutineDispatcher()
        
        try {
            // Tasks run on limited thread pool
            val jobs = (1..10).map { taskId ->
                async(limitedDispatcher) {
                    val threadName = Thread.currentThread().name
                    Thread.sleep(100) // Simulate blocking work
                    "Limited task $taskId on $threadName"
                }
            }
            
            val results = jobs.awaitAll()
            val uniqueThreads = results.map { it.substringAfter("on ") }.toSet()
            println("  Tasks used ${uniqueThreads.size} threads: $uniqueThreads")
            
        } finally {
            limitedDispatcher.close()
        }
        
        println("Custom dispatchers demo completed\n")
    }
    
    fun demonstrateDispatcherUseCase() = runBlocking {
        println("=== Custom Dispatcher Use Cases ===")
        
        // Use case 1: Database connection pool dispatcher
        val dbDispatcher = Executors.newFixedThreadPool(5) { runnable ->
            Thread(runnable, "DBConnection-${System.nanoTime()}")
        }.asCoroutineDispatcher()
        
        try {
            suspend fun databaseQuery(query: String): String {
                // This would use a real database connection
                Thread.sleep(100) // Simulate DB query
                return "Result for: $query"
            }
            
            println("Database operations on custom dispatcher:")
            val queries = listOf(
                "SELECT * FROM users",
                "SELECT * FROM orders", 
                "SELECT * FROM products"
            )
            
            val results = queries.map { query ->
                async(dbDispatcher) {
                    val result = databaseQuery(query)
                    val threadName = Thread.currentThread().name
                    "  $result (on $threadName)"
                }
            }.awaitAll()
            
            results.forEach { println(it) }
            
        } finally {
            dbDispatcher.close()
        }
        
        // Use case 2: Image processing dispatcher
        val imageDispatcher = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "ImageProcessor-${System.nanoTime()}")
        }.asCoroutineDispatcher()
        
        try {
            suspend fun processImage(imageName: String): String {
                // Simulate CPU-intensive image processing
                repeat(100_000) { /* processing */ }
                return "Processed: $imageName"
            }
            
            println("\nImage processing on custom dispatcher:")
            val images = listOf("photo1.jpg", "photo2.jpg", "photo3.jpg", "photo4.jpg")
            
            val processedImages = images.map { image ->
                async(imageDispatcher) {
                    val result = processImage(image)
                    val threadName = Thread.currentThread().name
                    "  $result (on $threadName)"
                }
            }.awaitAll()
            
            processedImages.forEach { println(it) }
            
        } finally {
            imageDispatcher.close()
        }
        
        println()
    }
}

/**
 * Dispatcher selection guidelines
 */
class DispatcherSelection {
    
    fun demonstrateSelectionGuidelines() = runBlocking {
        println("=== Dispatcher Selection Guidelines ===")
        
        // Guideline 1: CPU-intensive work → Default
        println("1. CPU-intensive work → Dispatchers.Default")
        launch(Dispatchers.Default) {
            val result = (1..1_000_000).fold(0L) { acc, n -> acc + n }
            println("  Mathematical computation: $result")
        }
        
        // Guideline 2: Blocking I/O → IO
        println("\n2. Blocking I/O operations → Dispatchers.IO")
        launch(Dispatchers.IO) {
            // Simulate file I/O
            Thread.sleep(50)
            println("  File read operation completed")
        }
        
        // Guideline 3: Non-blocking I/O → any dispatcher
        println("\n3. Non-blocking I/O → any dispatcher (often Default)")
        launch(Dispatchers.Default) {
            delay(50) // Non-blocking delay
            println("  Network API call completed")
        }
        
        // Guideline 4: UI updates → Main (not available in this environment)
        println("\n4. UI updates → Dispatchers.Main")
        println("  (Not demonstrated - requires Android/Desktop UI environment)")
        
        // Guideline 5: Testing → Unconfined
        println("\n5. Testing scenarios → Dispatchers.Unconfined")
        launch(Dispatchers.Unconfined) {
            println("  Test operation on ${Thread.currentThread().name}")
            delay(1)
            println("  After delay on ${Thread.currentThread().name}")
        }
        
        delay(100)
        println("\nSelection guidelines completed\n")
    }
    
    fun demonstrateCommonMistakes() = runBlocking {
        println("=== Common Dispatcher Mistakes ===")
        
        // Mistake 1: Blocking Default dispatcher
        println("❌ Mistake 1: Blocking Default dispatcher")
        println("  Don't: Thread.sleep() on Dispatchers.Default")
        println("  Do: Thread.sleep() on Dispatchers.IO")
        
        // Mistake 2: CPU work on IO dispatcher
        println("\n❌ Mistake 2: CPU-intensive work on IO dispatcher")
        println("  Don't: Heavy computation on Dispatchers.IO")
        println("  Do: Heavy computation on Dispatchers.Default")
        
        // Mistake 3: Unnecessary context switching
        println("\n❌ Mistake 3: Unnecessary context switching")
        
        // Bad: Multiple context switches
        val badTime = measureTimeMillis {
            repeat(100) {
                withContext(Dispatchers.Default) {
                    // Minimal work, high switching overhead
                    it * 2
                }
            }
        }
        println("  Bad approach (frequent switching): ${badTime}ms")
        
        // Good: Batch work in single context
        val goodTime = measureTimeMillis {
            withContext(Dispatchers.Default) {
                repeat(100) {
                    // Same work, single context
                    it * 2
                }
            }
        }
        println("  Good approach (single context): ${goodTime}ms")
        
        println("\nCommon mistakes review completed\n")
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Dispatcher overview
    DispatcherOverview().demonstrateBuiltInDispatchers()
    DispatcherOverview().demonstrateDispatcherCharacteristics()
    
    // Context switching
    ContextSwitching().demonstrateContextSwitching()
    ContextSwitching().demonstrateNestedContextSwitching()
    ContextSwitching().demonstrateContextInheritance()
    
    // Performance comparison
    DispatcherPerformance().compareDispatcherPerformance()
    DispatcherPerformance().demonstrateDispatcherSaturation()
    
    // Custom dispatchers
    CustomDispatchers().demonstrateCustomDispatchers()
    CustomDispatchers().demonstrateDispatcherUseCase()
    
    // Selection guidelines
    DispatcherSelection().demonstrateSelectionGuidelines()
    DispatcherSelection().demonstrateCommonMistakes()
    
    println("=== Dispatchers Summary ===")
    println("- Dispatchers.Default: CPU-intensive work (core count threads)")
    println("- Dispatchers.IO: Blocking I/O operations (64+ threads)")
    println("- Dispatchers.Main: UI updates (single UI thread)")
    println("- Dispatchers.Unconfined: Testing and special cases")
    println("- Use withContext() for context switching")
    println("- Create custom dispatchers for specialized needs")
    println("- Choose dispatcher based on workload characteristics")
    println("- Avoid blocking Default dispatcher")
    println("- Minimize unnecessary context switching")
}