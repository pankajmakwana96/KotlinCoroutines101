/**
 * # Structured Concurrency: Scope and Hierarchy
 * 
 * ## Problem Description
 * Traditional concurrent programming often leads to resource leaks, forgotten
 * background tasks, and difficult error handling. Structured concurrency solves
 * this by organizing coroutines in a hierarchical structure where parent
 * coroutines manage their children's lifecycles automatically.
 * 
 * ## Solution Approach
 * Structured concurrency ensures:
 * - Parent coroutines wait for all children to complete
 * - Cancellation propagates from parent to children
 * - Exceptions in children can cancel siblings
 * - Resources are properly cleaned up
 * - No coroutines are left running accidentally
 * 
 * ## Key Learning Points
 * - CoroutineScope defines boundaries for coroutine hierarchies
 * - Parent-child relationships enable automatic lifecycle management
 * - Cancellation propagation prevents resource leaks
 * - Exception handling strategies (regular vs supervisor jobs)
 * - Scope functions like coroutineScope and supervisorScope
 * 
 * ## Performance Considerations
 * - Minimal overhead for hierarchy management
 * - Automatic cleanup prevents memory leaks
 * - Proper cancellation saves CPU cycles
 * - Structured approach reduces debugging time
 * 
 * ## Common Pitfalls
 * - Using GlobalScope instead of proper scopes
 * - Not understanding cancellation propagation
 * - Mixing regular and supervisor jobs incorrectly
 * - Creating scope leaks by not cancelling properly
 * 
 * ## Real-World Applications
 * - Android Activities/ViewModels with lifecycleScope
 * - Web request processing with request-scoped coroutines
 * - Background service management
 * - Resource management (databases, files, networks)
 * - Parallel processing pipelines
 * 
 * ## Related Concepts
 * - CoroutineBuilders.kt - How scopes create coroutines
 * - JobLifecycle.kt - Job states and cancellation
 * - ContextAndInheritance.kt - Context propagation
 */

package coroutines

import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Basic structured concurrency principles
 * 
 * Hierarchy visualization:
 * 
 * CoroutineScope
 *     │
 *     ├─ Parent Coroutine
 *     │      │
 *     │      ├─ Child 1
 *     │      ├─ Child 2
 *     │      └─ Child 3
 *     │
 *     └─ Another Parent
 *            │
 *            ├─ Child A
 *            └─ Child B
 * 
 * Rules:
 * - Parent waits for all children
 * - Parent cancellation cancels all children
 * - Child exception may cancel siblings (depends on job type)
 */
class StructuredConcurrencyBasics {
    
    fun demonstrateBasicHierarchy() = runBlocking {
        println("=== Basic Structured Concurrency ===")
        
        println("Starting parent coroutine...")
        
        coroutineScope {
            println("Inside coroutineScope")
            
            // Child coroutine 1
            launch {
                repeat(3) { i ->
                    println("  Child 1 - step $i")
                    delay(200)
                }
                println("  Child 1 completed")
            }
            
            // Child coroutine 2
            launch {
                repeat(2) { i ->
                    println("  Child 2 - step $i")
                    delay(300)
                }
                println("  Child 2 completed")
            }
            
            // Child coroutine 3
            async {
                delay(100)
                println("  Child 3 (async) working...")
                delay(400)
                println("  Child 3 completed")
                "Result from child 3"
            }.await()
            
            println("All children started, parent continues...")
            delay(100)
            println("Parent work done")
        }
        
        println("coroutineScope completed - all children finished")
        println("This line only executes after ALL children complete\n")
    }
    
    fun demonstrateWaitingBehavior() = runBlocking {
        println("=== Parent Waits for Children ===")
        
        val startTime = System.currentTimeMillis()
        
        coroutineScope {
            // Fast child
            launch {
                delay(100)
                println("  Fast child completed at ${System.currentTimeMillis() - startTime}ms")
            }
            
            // Slow child
            launch {
                delay(500)
                println("  Slow child completed at ${System.currentTimeMillis() - startTime}ms")
            }
            
            // Medium child
            launch {
                delay(300)
                println("  Medium child completed at ${System.currentTimeMillis() - startTime}ms")
            }
            
            println("All children started at ${System.currentTimeMillis() - startTime}ms")
        }
        
        val endTime = System.currentTimeMillis() - startTime
        println("Parent completed at ${endTime}ms (waited for slowest child)")
        println()
    }
}

/**
 * Cancellation propagation in structured concurrency
 */
class CancellationPropagation {
    
    fun demonstrateParentCancellation() = runBlocking {
        println("=== Parent Cancellation Propagation ===")
        
        val parentJob = launch {
            println("Parent started")
            
            // Child 1
            launch {
                try {
                    repeat(10) { i ->
                        println("  Child 1 - iteration $i")
                        delay(200)
                    }
                    println("  Child 1 completed")
                } catch (e: CancellationException) {
                    println("  Child 1 was cancelled")
                }
            }
            
            // Child 2
            launch {
                try {
                    repeat(10) { i ->
                        println("  Child 2 - iteration $i")
                        delay(150)
                    }
                    println("  Child 2 completed")
                } catch (e: CancellationException) {
                    println("  Child 2 was cancelled")
                }
            }
            
            // Parent work
            try {
                delay(1000) // Parent runs for 1 second
                println("Parent work completed")
            } catch (e: CancellationException) {
                println("Parent was cancelled")
                throw e
            }
        }
        
        // Let it run for a bit, then cancel parent
        delay(600)
        println("Cancelling parent...")
        parentJob.cancel()
        parentJob.join()
        
        println("Parent cancellation demo completed\n")
    }
    
    fun demonstrateChildCancellation() = runBlocking {
        println("=== Child Cancellation (Does Not Affect Parent) ===")
        
        coroutineScope {
            val child1 = launch {
                try {
                    repeat(10) { i ->
                        println("  Child 1 - iteration $i")
                        delay(200)
                    }
                    println("  Child 1 completed")
                } catch (e: CancellationException) {
                    println("  Child 1 was cancelled")
                }
            }
            
            val child2 = launch {
                repeat(5) { i ->
                    println("  Child 2 - iteration $i")
                    delay(150)
                }
                println("  Child 2 completed")
            }
            
            // Let them run, then cancel only child1
            delay(500)
            println("Cancelling child 1...")
            child1.cancel()
            
            // Parent and child2 continue running
            delay(300)
            println("Parent continues after child1 cancellation")
        }
        
        println("Child cancellation demo completed\n")
    }
    
    fun demonstrateExceptionPropagation() = runBlocking {
        println("=== Exception Propagation ===")
        
        try {
            coroutineScope {
                // Child 1 - will complete
                launch {
                    repeat(2) { i ->
                        println("  Good child - step $i")
                        delay(200)
                    }
                    println("  Good child completed")
                }
                
                // Child 2 - will fail
                launch {
                    delay(300)
                    println("  Failing child about to throw exception")
                    throw RuntimeException("Child failure!")
                }
                
                // Child 3 - will be cancelled due to sibling failure
                launch {
                    try {
                        repeat(10) { i ->
                            println("  Another child - step $i")
                            delay(100)
                        }
                        println("  Another child completed")
                    } catch (e: CancellationException) {
                        println("  Another child was cancelled due to sibling failure")
                    }
                }
                
                println("All children started")
            }
        } catch (e: Exception) {
            println("Caught exception from coroutineScope: ${e.message}")
        }
        
        println("Exception propagation demo completed\n")
    }
}

/**
 * Different scope types and their behaviors
 */
class ScopeTypes {
    
    fun demonstrateCoroutineScope() = runBlocking {
        println("=== coroutineScope Behavior ===")
        
        try {
            coroutineScope {
                println("In coroutineScope")
                
                launch {
                    delay(200)
                    println("  Child 1 working")
                    delay(200)
                    println("  Child 1 completed")
                }
                
                launch {
                    delay(100)
                    println("  Child 2 working")
                    delay(100)
                    throw RuntimeException("Child 2 failed!")
                }
                
                launch {
                    try {
                        repeat(10) { i ->
                            println("  Child 3 - step $i")
                            delay(50)
                        }
                    } catch (e: CancellationException) {
                        println("  Child 3 cancelled due to sibling failure")
                    }
                }
            }
        } catch (e: Exception) {
            println("coroutineScope threw: ${e.message}")
        }
        
        println("coroutineScope demo completed\n")
    }
    
    fun demonstrateSupervisorScope() = runBlocking {
        println("=== supervisorScope Behavior ===")
        
        try {
            supervisorScope {
                println("In supervisorScope")
                
                launch {
                    delay(200)
                    println("  Child 1 working")
                    delay(200)
                    println("  Child 1 completed")
                }
                
                launch {
                    delay(100)
                    println("  Child 2 working")
                    delay(100)
                    throw RuntimeException("Child 2 failed!")
                }
                
                launch {
                    repeat(5) { i ->
                        println("  Child 3 - step $i")
                        delay(100)
                    }
                    println("  Child 3 completed (not cancelled)")
                }
                
                delay(600) // Wait for children
            }
        } catch (e: Exception) {
            println("supervisorScope threw: ${e.message}")
        }
        
        println("supervisorScope demo completed\n")
    }
    
    fun compareJobTypes() = runBlocking {
        println("=== Job vs SupervisorJob Comparison ===")
        
        println("1. Regular Job (failure cancels siblings):")
        try {
            coroutineScope {
                launch(Job()) {
                    delay(100)
                    throw RuntimeException("Failed!")
                }
                
                launch(Job()) {
                    try {
                        delay(300)
                        println("  Regular job sibling completed")
                    } catch (e: CancellationException) {
                        println("  Regular job sibling cancelled")
                    }
                }
            }
        } catch (e: Exception) {
            println("  Exception: ${e.message}")
        }
        
        println("\n2. SupervisorJob (failure doesn't cancel siblings):")
        supervisorScope {
            launch {
                delay(100)
                throw RuntimeException("Failed!")
            }
            
            launch {
                delay(300)
                println("  SupervisorJob sibling completed")
            }
            
            delay(400) // Wait for children
        }
        
        println("Job types comparison completed\n")
    }
}

/**
 * Scope management and custom scopes
 */
class ScopeManagement {
    
    fun demonstrateCustomScope() = runBlocking {
        println("=== Custom Scope Management ===")
        
        // Create custom scope with supervisor job
        val customScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineName("CustomScope")
        )
        
        try {
            // Start some work in custom scope
            val job1 = customScope.launch {
                repeat(3) { i ->
                    println("  Custom scope job 1 - step $i")
                    delay(200)
                }
                println("  Custom scope job 1 completed")
            }
            
            val job2 = customScope.launch {
                delay(300)
                println("  Custom scope job 2 working")
                delay(200)
                println("  Custom scope job 2 completed")
            }
            
            val job3 = customScope.launch {
                delay(100)
                throw RuntimeException("Custom scope job 3 failed")
            }
            
            // Wait for specific jobs
            job1.join()
            job2.join()
            
            try {
                job3.join()
            } catch (e: Exception) {
                println("  Job 3 failed as expected: ${e.message}")
            }
            
        } finally {
            // Always clean up custom scopes
            customScope.cancel()
            println("Custom scope cancelled")
        }
        
        println("Custom scope demo completed\n")
    }
    
    fun demonstrateScopeLifecycle() = runBlocking {
        println("=== Scope Lifecycle Management ===")
        
        class ServiceManager {
            private val serviceScope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default + CoroutineName("ServiceManager")
            )
            
            fun startServices() {
                println("Starting services...")
                
                serviceScope.launch {
                    repeat(5) { i ->
                        println("  Service 1 - heartbeat $i")
                        delay(300)
                    }
                    println("  Service 1 completed")
                }
                
                serviceScope.launch {
                    repeat(3) { i ->
                        println("  Service 2 - processing $i")
                        delay(500)
                    }
                    println("  Service 2 completed")
                }
                
                serviceScope.launch {
                    repeat(7) { i ->
                        println("  Service 3 - monitoring $i")
                        delay(200)
                    }
                    println("  Service 3 completed")
                }
            }
            
            fun stopServices() {
                println("Stopping services...")
                serviceScope.cancel()
                println("All services stopped")
            }
            
            fun isActive(): Boolean = serviceScope.isActive
        }
        
        val manager = ServiceManager()
        manager.startServices()
        
        // Let services run for a while
        delay(1000)
        println("Services running: ${manager.isActive()}")
        
        // Stop services
        manager.stopServices()
        println("Services running: ${manager.isActive()}")
        
        println("Scope lifecycle demo completed\n")
    }
}

/**
 * Resource management with structured concurrency
 */
class ResourceManagement {
    
    fun demonstrateResourceCleanup() = runBlocking {
        println("=== Resource Management ===")
        
        class DatabaseConnection {
            private var isConnected = false
            
            fun connect() {
                isConnected = true
                println("  Database connected")
            }
            
            fun close() {
                isConnected = false
                println("  Database closed")
            }
            
            suspend fun query(sql: String): String {
                if (!isConnected) throw IllegalStateException("Not connected")
                delay(100) // Simulate query time
                return "Result for: $sql"
            }
            
            fun isConnected() = isConnected
        }
        
        val connection = DatabaseConnection()
        connection.connect()
        
        try {
            coroutineScope {
                // Multiple concurrent database operations
                val query1 = async {
                    connection.query("SELECT * FROM users")
                }
                
                val query2 = async {
                    connection.query("SELECT * FROM orders")
                }
                
                val query3 = async {
                    delay(50)
                    connection.query("SELECT * FROM products")
                }
                
                // If any query fails, all are cancelled
                val results = listOf(query1.await(), query2.await(), query3.await())
                println("Query results: $results")
            }
        } catch (e: Exception) {
            println("Database operations failed: ${e.message}")
        } finally {
            // Resource cleanup is guaranteed
            connection.close()
        }
        
        println("Resource management demo completed\n")
    }
    
    fun demonstrateComplexResourceManagement() = runBlocking {
        println("=== Complex Resource Management ===")
        
        class ResourcePool {
            private val resources = mutableListOf<String>()
            
            fun acquire(): String {
                val resource = "Resource-${resources.size}"
                resources.add(resource)
                println("  Acquired: $resource")
                return resource
            }
            
            fun release(resource: String) {
                resources.remove(resource)
                println("  Released: $resource")
            }
            
            fun releaseAll() {
                val count = resources.size
                resources.clear()
                println("  Released all $count resources")
            }
        }
        
        val pool = ResourcePool()
        
        try {
            supervisorScope {
                // Task 1 - successful
                launch {
                    val resource = pool.acquire()
                    try {
                        delay(200)
                        println("  Task 1 completed with $resource")
                    } finally {
                        pool.release(resource)
                    }
                }
                
                // Task 2 - fails
                launch {
                    val resource = pool.acquire()
                    try {
                        delay(100)
                        throw RuntimeException("Task 2 failed")
                    } finally {
                        pool.release(resource)
                    }
                }
                
                // Task 3 - successful (not cancelled due to supervisorScope)
                launch {
                    val resource = pool.acquire()
                    try {
                        delay(300)
                        println("  Task 3 completed with $resource")
                    } finally {
                        pool.release(resource)
                    }
                }
                
                delay(400) // Wait for all tasks
            }
        } catch (e: Exception) {
            println("Some tasks failed: ${e.message}")
        } finally {
            // Emergency cleanup
            pool.releaseAll()
        }
        
        println("Complex resource management demo completed\n")
    }
}

/**
 * Best practices for structured concurrency
 */
class StructuredConcurrencyBestPractices {
    
    fun demonstrateBestPractices() = runBlocking {
        println("=== Structured Concurrency Best Practices ===")
        
        // ✅ Good: Use coroutineScope for structured operations
        suspend fun goodStructuredOperation(): String = coroutineScope {
            val result1 = async { fetchData("source1") }
            val result2 = async { fetchData("source2") }
            
            "Combined: ${result1.await()} + ${result2.await()}"
        }
        
        // ❌ Bad: Using GlobalScope
        fun badUnstructuredOperation(): Deferred<String> {
            return GlobalScope.async {
                // This coroutine has no structured parent!
                fetchData("global")
            }
        }
        
        // ✅ Good: Proper exception handling
        suspend fun goodExceptionHandling(): String? = try {
            coroutineScope {
                val job1 = async { riskyOperation(1) }
                val job2 = async { riskyOperation(2) }
                val job3 = async { riskyOperation(3) }
                
                "Results: ${job1.await()}, ${job2.await()}, ${job3.await()}"
            }
        } catch (e: Exception) {
            println("  Handled exception in structured way: ${e.message}")
            null
        }
        
        // ✅ Good: Use supervisorScope when appropriate
        suspend fun goodIndependentTasks(): List<String> = supervisorScope {
            val results = mutableListOf<String>()
            
            launch {
                try {
                    val result = riskyOperation(1)
                    results.add(result)
                } catch (e: Exception) {
                    println("  Task 1 failed: ${e.message}")
                }
            }
            
            launch {
                try {
                    val result = riskyOperation(2)
                    results.add(result)
                } catch (e: Exception) {
                    println("  Task 2 failed: ${e.message}")
                }
            }
            
            delay(300) // Wait for tasks
            results
        }
        
        // Demonstrate best practices
        println("1. Good structured operation:")
        val goodResult = goodStructuredOperation()
        println("  Result: $goodResult")
        
        println("\n2. Good exception handling:")
        val exceptionResult = goodExceptionHandling()
        println("  Result: $exceptionResult")
        
        println("\n3. Good independent tasks:")
        val independentResults = goodIndependentTasks()
        println("  Results: $independentResults")
        
        println("\n4. Bad unstructured operation (DON'T DO THIS):")
        val badDeferred = badUnstructuredOperation()
        val badResult = badDeferred.await()
        println("  Result: $badResult (but coroutine may leak!)")
        
        println("Best practices demo completed\n")
    }
    
    private suspend fun fetchData(source: String): String {
        delay(100)
        return "data from $source"
    }
    
    private suspend fun riskyOperation(id: Int): String {
        delay(Random.nextLong(50, 150))
        if (Random.nextBoolean()) {
            throw RuntimeException("Operation $id failed randomly")
        }
        return "Success $id"
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Basic structured concurrency
    StructuredConcurrencyBasics().demonstrateBasicHierarchy()
    StructuredConcurrencyBasics().demonstrateWaitingBehavior()
    
    // Cancellation propagation
    CancellationPropagation().demonstrateParentCancellation()
    CancellationPropagation().demonstrateChildCancellation()
    CancellationPropagation().demonstrateExceptionPropagation()
    
    // Different scope types
    ScopeTypes().demonstrateCoroutineScope()
    ScopeTypes().demonstrateSupervisorScope()
    ScopeTypes().compareJobTypes()
    
    // Scope management
    ScopeManagement().demonstrateCustomScope()
    ScopeManagement().demonstrateScopeLifecycle()
    
    // Resource management
    ResourceManagement().demonstrateResourceCleanup()
    ResourceManagement().demonstrateComplexResourceManagement()
    
    // Best practices
    StructuredConcurrencyBestPractices().demonstrateBestPractices()
    
    println("=== Structured Concurrency Summary ===")
    println("- Use coroutineScope for structured concurrent operations")
    println("- Use supervisorScope when child failures should be independent")
    println("- Parents automatically wait for all children to complete")
    println("- Cancellation propagates from parent to children")
    println("- Exceptions in children can cancel siblings (regular scope)")
    println("- Always clean up custom scopes to prevent leaks")
    println("- Avoid GlobalScope in favor of proper scoping")
    println("- Use structured concurrency for automatic resource management")
}