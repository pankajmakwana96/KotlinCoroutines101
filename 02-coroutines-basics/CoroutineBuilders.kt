/**
 * # Coroutine Builders: launch, async, runBlocking
 * 
 * ## Problem Description
 * Suspend functions need to be called from a coroutine context. Coroutine builders
 * provide different ways to start coroutines with different characteristics:
 * fire-and-forget, concurrent with results, or bridging to blocking code.
 * 
 * ## Solution Approach
 * Each builder serves a specific purpose:
 * - launch: Fire-and-forget concurrent execution
 * - async: Concurrent execution with deferred results
 * - runBlocking: Bridge between blocking and suspending worlds
 * - coroutineScope: Structured concurrency within suspend functions
 * 
 * ## Key Learning Points
 * - Different builders for different use cases
 * - Structured concurrency and automatic cancellation
 * - Exception propagation differences between builders
 * - When to use each builder appropriately
 * - Job and Deferred return types
 * 
 * ## Performance Considerations
 * - launch: Minimal overhead, no result handling
 * - async: Small overhead for result storage
 * - runBlocking: Should be avoided in production suspend functions
 * - coroutineScope: Zero overhead for structured concurrency
 * 
 * ## Common Pitfalls
 * - Using runBlocking inside suspend functions
 * - Not awaiting async results (memory leaks)
 * - Forgetting to handle exceptions in launch
 * - Using GlobalScope instead of proper scopes
 * 
 * ## Real-World Applications
 * - Background processing (launch)
 * - Parallel API calls (async)
 * - Testing coroutines (runBlocking)
 * - Main function entry points (runBlocking)
 * - Structured concurrent operations (coroutineScope)
 * 
 * ## Related Concepts
 * - SuspendFunctions.kt - Foundation concepts
 * - StructuredConcurrency.kt - Scope management
 * - JobLifecycle.kt - Job management
 */

package coroutines

import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Launch builder demonstration
 * 
 * launch characteristics:
 * - Fire-and-forget execution
 * - Returns Job (no result value)
 * - Exceptions propagate to parent scope
 * - Non-blocking start
 * 
 * Visual representation:
 * 
 * Parent Scope
 *     │
 *     ├─ launch { task1() } ──> Job
 *     ├─ launch { task2() } ──> Job  
 *     └─ launch { task3() } ──> Job
 *           │
 *           └─ All run concurrently
 */
class LaunchBuilder {
    
    fun demonstrateLaunchBasics() = runBlocking {
        println("=== Launch Builder Basics ===")
        
        println("Starting three background tasks...")
        
        val job1 = launch {
            repeat(3) { i ->
                println("Task 1 - Step $i")
                delay(300)
            }
            println("Task 1 completed")
        }
        
        val job2 = launch {
            repeat(2) { i ->
                println("Task 2 - Step $i")
                delay(500)
            }
            println("Task 2 completed")
        }
        
        val job3 = launch {
            repeat(4) { i ->
                println("Task 3 - Step $i")
                delay(200)
            }
            println("Task 3 completed")
        }
        
        // Main coroutine continues immediately
        println("All tasks started, main coroutine continues...")
        
        // Wait for all tasks to complete
        job1.join()
        job2.join()
        job3.join()
        
        println("All tasks completed\n")
    }
    
    fun demonstrateLaunchExceptionHandling() = runBlocking {
        println("=== Launch Exception Handling ===")
        
        // Exception in launch propagates to parent scope
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            println("Caught exception: ${exception.message}")
        }
        
        val job = launch(exceptionHandler) {
            println("Task starting...")
            delay(100)
            
            if (Random.nextBoolean()) {
                throw RuntimeException("Random failure in launch")
            }
            
            println("Task completed successfully")
        }
        
        job.join()
        println("Launch exception handling demo completed\n")
    }
    
    suspend fun backgroundProcessing() {
        println("=== Background Processing with Launch ===")
        
        // Using launch for background work in suspend function
        coroutineScope {
            launch {
                repeat(5) { i ->
                    println("Background processing: $i")
                    delay(200)
                }
            }
            
            launch {
                repeat(3) { i ->
                    println("Cleanup task: $i")
                    delay(300)
                }
            }
            
            // Main logic continues
            println("Main processing...")
            delay(600)
            println("Main processing completed")
        }
        // All child coroutines complete before this function returns
        println("Background processing demo completed\n")
    }
}

/**
 * Async builder demonstration
 * 
 * async characteristics:
 * - Concurrent execution with result
 * - Returns Deferred<T> (future-like)
 * - Exceptions stored in Deferred, thrown on await()
 * - Used for parallel computation
 * 
 * Visual representation:
 * 
 * Parent Scope
 *     │
 *     ├─ async { compute1() } ──> Deferred<Result1>
 *     ├─ async { compute2() } ──> Deferred<Result2>
 *     └─ async { compute3() } ──> Deferred<Result3>
 *           │
 *           └─ await() to get results
 */
class AsyncBuilder {
    
    suspend fun fetchUserData(userId: String): String {
        delay(300) // Simulate API call
        return "User data for $userId"
    }
    
    suspend fun fetchUserPosts(userId: String): List<String> {
        delay(400) // Simulate API call
        return listOf("Post 1", "Post 2", "Post 3")
    }
    
    suspend fun fetchUserFriends(userId: String): List<String> {
        delay(250) // Simulate API call
        return listOf("Friend A", "Friend B")
    }
    
    fun demonstrateAsyncBasics() = runBlocking {
        println("=== Async Builder Basics ===")
        
        val userId = "user123"
        
        // Sequential approach (slow)
        val sequentialTime = measureTimeMillis {
            val userData = fetchUserData(userId)
            val userPosts = fetchUserPosts(userId)
            val userFriends = fetchUserFriends(userId)
            
            println("Sequential results:")
            println("  User: $userData")
            println("  Posts: $userPosts")
            println("  Friends: $userFriends")
        }
        println("Sequential time: ${sequentialTime}ms")
        
        // Concurrent approach with async (fast)
        val concurrentTime = measureTimeMillis {
            val userDataDeferred = async { fetchUserData(userId) }
            val userPostsDeferred = async { fetchUserPosts(userId) }
            val userFriendsDeferred = async { fetchUserFriends(userId) }
            
            // Await all results
            val userData = userDataDeferred.await()
            val userPosts = userPostsDeferred.await()
            val userFriends = userFriendsDeferred.await()
            
            println("Concurrent results:")
            println("  User: $userData")
            println("  Posts: $userPosts")
            println("  Friends: $userFriends")
        }
        println("Concurrent time: ${concurrentTime}ms")
        println("Speedup: ${sequentialTime.toDouble() / concurrentTime}x\n")
    }
    
    fun demonstrateAsyncExceptionHandling() = runBlocking {
        println("=== Async Exception Handling ===")
        
        suspend fun riskyOperation(id: Int): String {
            delay(100)
            if (id == 2) {
                throw RuntimeException("Operation $id failed")
            }
            return "Result $id"
        }
        
        // Start multiple async operations
        val deferred1 = async { riskyOperation(1) }
        val deferred2 = async { riskyOperation(2) } // This will fail
        val deferred3 = async { riskyOperation(3) }
        
        // Handle results and exceptions
        try {
            val result1 = deferred1.await()
            println("Result 1: $result1")
        } catch (e: Exception) {
            println("Exception in operation 1: ${e.message}")
        }
        
        try {
            val result2 = deferred2.await()
            println("Result 2: $result2")
        } catch (e: Exception) {
            println("Exception in operation 2: ${e.message}")
        }
        
        try {
            val result3 = deferred3.await()
            println("Result 3: $result3")
        } catch (e: Exception) {
            println("Exception in operation 3: ${e.message}")
        }
        
        println("Async exception handling completed\n")
    }
    
    fun demonstrateAsyncAwaitAll() = runBlocking {
        println("=== Async awaitAll ===")
        
        suspend fun computeValue(id: Int): Int {
            delay(Random.nextLong(100, 500))
            val result = id * id
            println("Computed $id² = $result")
            return result
        }
        
        // Start multiple computations
        val deferredList = (1..5).map { id ->
            async { computeValue(id) }
        }
        
        // Wait for all to complete
        val results = deferredList.awaitAll()
        println("All results: $results")
        println("Sum: ${results.sum()}\n")
    }
}

/**
 * runBlocking builder demonstration
 * 
 * runBlocking characteristics:
 * - Blocks current thread until completion
 * - Bridges blocking and suspending worlds
 * - Should be used sparingly in production
 * - Main entry point for coroutines
 * 
 * Usage pattern:
 * 
 * Regular Code ──> runBlocking { ──> Suspend Functions
 *                              │
 *                              └──> Coroutine World
 */
class RunBlockingBuilder {
    
    fun demonstrateRunBlockingBasics() {
        println("=== runBlocking Basics ===")
        
        println("Before runBlocking")
        
        runBlocking {
            println("Inside runBlocking")
            delay(1000)
            println("After delay in runBlocking")
        }
        
        println("After runBlocking (this waits for completion)")
        println()
    }
    
    fun demonstrateRunBlockingUseCase() {
        println("=== runBlocking Use Cases ===")
        
        // Use case 1: Main function
        fun main() {
            runBlocking {
                // Coroutine code here
                delay(100)
                println("Main function with coroutines")
            }
        }
        
        // Use case 2: Testing
        fun testCoroutines() {
            runBlocking {
                val result = async { 
                    delay(100)
                    "test result"
                }.await()
                
                assert(result == "test result")
                println("Test passed: $result")
            }
        }
        
        // Use case 3: Blocking API integration
        fun getDataSync(): String {
            return runBlocking {
                delay(100) // Simulate async operation
                "Synchronous result from async operation"
            }
        }
        
        main()
        testCoroutines()
        val syncResult = getDataSync()
        println("Sync result: $syncResult\n")
    }
    
    fun demonstrateRunBlockingAntiPatterns() {
        println("=== runBlocking Anti-patterns ===")
        
        // ❌ BAD: Using runBlocking inside suspend function
        suspend fun badSuspendFunction() {
            runBlocking {
                delay(100) // This blocks the thread!
            }
        }
        
        // ✅ GOOD: Using coroutineScope instead
        suspend fun goodSuspendFunction() {
            coroutineScope {
                delay(100) // This suspends properly
            }
        }
        
        runBlocking {
            println("Demonstrating the difference...")
            
            val badTime = measureTimeMillis {
                // Don't actually call badSuspendFunction in production
                // badSuspendFunction() // Would block thread
                delay(100) // Simulating the bad pattern
            }
            
            val goodTime = measureTimeMillis {
                goodSuspendFunction()
            }
            
            println("Bad pattern time: ${badTime}ms (blocks thread)")
            println("Good pattern time: ${goodTime}ms (suspends properly)")
        }
        println()
    }
}

/**
 * coroutineScope builder demonstration
 * 
 * coroutineScope characteristics:
 * - Creates a scope for child coroutines
 * - Suspends until all children complete
 * - Cancellation propagates to children
 * - Exception from any child cancels all siblings
 * 
 * Structured concurrency visualization:
 * 
 * suspend fun() {
 *     coroutineScope {
 *         ├─ launch { child1() }
 *         ├─ async { child2() }
 *         └─ launch { child3() }
 *     } // Waits for all children
 *     // Continues after all complete
 * }
 */
class CoroutineScopeBuilder {
    
    suspend fun demonstrateCoroutineScope() {
        println("=== coroutineScope Builder ===")
        
        println("Starting coroutineScope...")
        
        coroutineScope {
            val job1 = launch {
                repeat(3) { i ->
                    println("Child 1 - iteration $i")
                    delay(200)
                }
            }
            
            val deferred = async {
                delay(300)
                "Result from async child"
            }
            
            val job2 = launch {
                repeat(2) { i ->
                    println("Child 2 - iteration $i")
                    delay(250)
                }
            }
            
            // All children run concurrently
            val result = deferred.await()
            println("Async result: $result")
            
            // coroutineScope waits for all children to complete
        }
        
        println("coroutineScope completed - all children finished\n")
    }
    
    suspend fun demonstrateCancellationPropagation() {
        println("=== Cancellation Propagation ===")
        
        try {
            coroutineScope {
                launch {
                    repeat(5) { i ->
                        println("Long task - step $i")
                        delay(300)
                    }
                    println("Long task completed")
                }
                
                launch {
                    delay(500)
                    println("Failing task about to fail...")
                    throw RuntimeException("Child task failed")
                }
                
                launch {
                    repeat(3) { i ->
                        println("Other task - step $i")
                        delay(400)
                    }
                    println("Other task completed")
                }
            }
        } catch (e: Exception) {
            println("Caught exception from coroutineScope: ${e.message}")
            println("All sibling coroutines were cancelled")
        }
        println()
    }
    
    suspend fun demonstrateNestedScopes() {
        println("=== Nested coroutineScopes ===")
        
        coroutineScope { // Outer scope
            println("Outer scope started")
            
            launch {
                println("Outer launch started")
                
                coroutineScope { // Inner scope
                    println("Inner scope started")
                    
                    launch {
                        repeat(3) { i ->
                            println("  Inner launch $i")
                            delay(150)
                        }
                    }
                    
                    async {
                        delay(200)
                        println("  Inner async completed")
                        "inner result"
                    }.await()
                    
                    println("Inner scope completed")
                }
                
                println("Outer launch completed")
            }
            
            delay(100)
            println("Outer scope other work")
        }
        
        println("All nested scopes completed\n")
    }
}

/**
 * Builder comparison and selection guide
 */
class BuilderComparison {
    
    fun demonstrateBuilderSelection() = runBlocking {
        println("=== Builder Selection Guide ===")
        
        // 1. Use launch for fire-and-forget operations
        println("1. Use launch for background work:")
        launch {
            repeat(3) { i ->
                println("  Background task $i")
                delay(100)
            }
        }
        
        delay(350) // Let background work finish
        
        // 2. Use async for concurrent operations with results
        println("\n2. Use async for concurrent computations:")
        val result1 = async { computeSquare(4) }
        val result2 = async { computeSquare(5) }
        val result3 = async { computeSquare(6) }
        
        val results = listOf(result1.await(), result2.await(), result3.await())
        println("  Computed squares: $results")
        
        // 3. Use coroutineScope for structured concurrency
        println("\n3. Use coroutineScope for structured operations:")
        val combinedResult = processData()
        println("  Combined result: $combinedResult")
        
        // 4. runBlocking only for bridging to blocking world
        println("\n4. runBlocking for main/test functions only")
        println("  (This demo is running in runBlocking)")
        
        println("\nBuilder selection completed\n")
    }
    
    private suspend fun computeSquare(n: Int): Int {
        delay(100)
        return n * n
    }
    
    private suspend fun processData(): String = coroutineScope {
        val data1 = async { fetchData("source1") }
        val data2 = async { fetchData("source2") }
        
        val processed = async {
            val result1 = data1.await()
            val result2 = data2.await()
            "Processed: $result1 + $result2"
        }
        
        processed.await()
    }
    
    private suspend fun fetchData(source: String): String {
        delay(150)
        return "data from $source"
    }
}

/**
 * Common patterns and best practices
 */
class BuilderPatterns {
    
    fun demonstrateCommonPatterns() = runBlocking {
        println("=== Common Builder Patterns ===")
        
        // Pattern 1: Parallel processing with async
        parallelProcessingPattern()
        
        // Pattern 2: Background tasks with launch
        backgroundTaskPattern()
        
        // Pattern 3: Resource management with coroutineScope
        resourceManagementPattern()
    }
    
    private suspend fun parallelProcessingPattern() {
        println("1. Parallel Processing Pattern:")
        
        data class User(val id: String, val profile: String, val posts: List<String>)
        
        suspend fun fetchProfile(userId: String): String {
            delay(200)
            return "Profile for $userId"
        }
        
        suspend fun fetchPosts(userId: String): List<String> {
            delay(300)
            return listOf("Post 1", "Post 2")
        }
        
        val userId = "user123"
        
        // Fetch profile and posts concurrently
        coroutineScope {
            val profileDeferred = async { fetchProfile(userId) }
            val postsDeferred = async { fetchPosts(userId) }
            
            val user = User(
                id = userId,
                profile = profileDeferred.await(),
                posts = postsDeferred.await()
            )
            
            println("  Created user: $user")
        }
    }
    
    private suspend fun backgroundTaskPattern() {
        println("\n2. Background Task Pattern:")
        
        coroutineScope {
            // Main processing
            val mainResult = async {
                delay(300)
                "Main processing result"
            }
            
            // Background tasks (fire-and-forget)
            launch {
                delay(100)
                println("  Background: Updating cache")
            }
            
            launch {
                delay(200)
                println("  Background: Sending analytics")
            }
            
            launch {
                delay(250)
                println("  Background: Cleaning temp files")
            }
            
            // Wait for main result, background tasks run independently
            val result = mainResult.await()
            println("  Main result: $result")
        }
    }
    
    private suspend fun resourceManagementPattern() {
        println("\n3. Resource Management Pattern:")
        
        class DatabaseConnection {
            fun connect() = println("  Database connected")
            fun close() = println("  Database closed")
            suspend fun query(sql: String): String {
                delay(100)
                return "Result for: $sql"
            }
        }
        
        val connection = DatabaseConnection()
        connection.connect()
        
        try {
            coroutineScope {
                val query1 = async { connection.query("SELECT * FROM users") }
                val query2 = async { connection.query("SELECT * FROM posts") }
                val query3 = async { connection.query("SELECT * FROM comments") }
                
                val results = listOf(query1.await(), query2.await(), query3.await())
                println("  Query results: $results")
            }
        } finally {
            connection.close()
        }
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Launch builder
    LaunchBuilder().demonstrateLaunchBasics()
    LaunchBuilder().demonstrateLaunchExceptionHandling()
    
    runBlocking {
        LaunchBuilder().backgroundProcessing()
    }
    
    // Async builder
    AsyncBuilder().demonstrateAsyncBasics()
    AsyncBuilder().demonstrateAsyncExceptionHandling()
    AsyncBuilder().demonstrateAsyncAwaitAll()
    
    // runBlocking builder
    RunBlockingBuilder().demonstrateRunBlockingBasics()
    RunBlockingBuilder().demonstrateRunBlockingUseCase()
    RunBlockingBuilder().demonstrateRunBlockingAntiPatterns()
    
    // coroutineScope builder
    runBlocking {
        CoroutineScopeBuilder().demonstrateCoroutineScope()
        CoroutineScopeBuilder().demonstrateCancellationPropagation()
        CoroutineScopeBuilder().demonstrateNestedScopes()
    }
    
    // Builder comparison
    BuilderComparison().demonstrateBuilderSelection()
    
    // Common patterns
    BuilderPatterns().demonstrateCommonPatterns()
    
    println("=== Coroutine Builders Summary ===")
    println("- launch: Fire-and-forget concurrent execution")
    println("- async: Concurrent execution with deferred results")
    println("- runBlocking: Bridge to blocking world (use sparingly)")
    println("- coroutineScope: Structured concurrency in suspend functions")
    println("- Choose the right builder for your use case")
    println("- Always handle exceptions appropriately")
    println("- Prefer structured concurrency over GlobalScope")
}