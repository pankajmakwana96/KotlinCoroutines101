/**
 * # Job Lifecycle and Cancellation
 * 
 * ## Problem Description
 * Managing coroutine lifecycles manually is error-prone and can lead to
 * resource leaks, zombie coroutines, and inconsistent application state.
 * Understanding Job lifecycle and proper cancellation is essential for
 * robust coroutine-based applications.
 * 
 * ## Solution Approach
 * Jobs provide a handle to manage coroutine lifecycle with:
 * - State tracking (New, Active, Completing, Completed, Cancelling, Cancelled)
 * - Cancellation support with cooperative cancellation
 * - Parent-child relationships for structured lifecycle management
 * - Exception handling and propagation
 * 
 * ## Key Learning Points
 * - Job states and transitions
 * - Cooperative vs preemptive cancellation
 * - CancellationException handling
 * - Job completion and joining
 * - SupervisorJob for independent child failure handling
 * 
 * ## Performance Considerations
 * - Cancellation checks add minimal overhead (~1-5ns)
 * - Proper cancellation prevents resource waste
 * - Job completion callbacks enable efficient cleanup
 * - Early cancellation saves CPU cycles
 * 
 * ## Common Pitfalls
 * - Not checking for cancellation in long-running loops
 * - Catching and swallowing CancellationException
 * - Not using ensureActive() in CPU-intensive work
 * - Forgetting to cancel jobs in cleanup code
 * - Using regular Job instead of SupervisorJob when appropriate
 * 
 * ## Real-World Applications
 * - Background task management
 * - Request processing with timeouts
 * - Resource cleanup on cancellation
 * - UI lifecycle management
 * - Graceful application shutdown
 * 
 * ## Related Concepts
 * - StructuredConcurrency.kt - Job hierarchies
 * - CoroutineBuilders.kt - Job creation
 * - ContextAndInheritance.kt - Job propagation
 */

package coroutines

import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * Job state transitions
 * 
 * Job State Machine:
 * 
 * NEW ──start()──> ACTIVE ──complete()──> COMPLETING ──> COMPLETED
 *  │                 │                        │            │
 *  │                 │                        │            │
 *  └─cancel()────────┼─cancel()───────────────┼──cancel()──┘
 *                    │                        │
 *                    ▼                        ▼
 *                CANCELLING ──────────> CANCELLED
 *                    │                        │
 *                    └─ cleanup ──────────────┘
 * 
 * States:
 * - NEW: Created but not started
 * - ACTIVE: Running normally
 * - COMPLETING: Finishing, waiting for children
 * - COMPLETED: Successfully finished
 * - CANCELLING: Being cancelled, cleanup in progress
 * - CANCELLED: Cancellation completed
 */
class JobLifecycleDemo {
    
    fun demonstrateJobStates() = runBlocking {
        println("=== Job State Transitions ===")
        
        // Create but don't start
        val job = Job()
        println("1. New job state: ${job.toString()}")
        
        // Start a coroutine
        val coroutineJob = launch(start = CoroutineStart.LAZY) {
            println("   Coroutine started")
            delay(1000)
            println("   Coroutine completed")
        }
        
        println("2. Lazy job state: ${coroutineJob.toString()}")
        
        // Start the coroutine
        coroutineJob.start()
        println("3. Active job state: ${coroutineJob.toString()}")
        
        delay(100) // Let it run briefly
        println("4. Running job state: ${coroutineJob.toString()}")
        
        // Wait for completion
        coroutineJob.join()
        println("5. Completed job state: ${coroutineJob.toString()}")
        
        // Demonstrate cancellation states
        val cancellableJob = launch {
            try {
                repeat(10) { i ->
                    println("   Working: $i")
                    delay(200)
                }
            } catch (e: CancellationException) {
                println("   Job was cancelled")
                throw e
            }
        }
        
        delay(300) // Let it run briefly
        println("6. Before cancellation: ${cancellableJob.toString()}")
        
        cancellableJob.cancel()
        println("7. After cancel(): ${cancellableJob.toString()}")
        
        cancellableJob.join()
        println("8. After join(): ${cancellableJob.toString()}")
        
        println()
    }
    
    fun demonstrateJobProperties() = runBlocking {
        println("=== Job Properties ===")
        
        val job = launch {
            repeat(5) { i ->
                println("   Working: $i")
                delay(200)
            }
        }
        
        // Monitor job properties
        repeat(6) { check ->
            println("Check $check:")
            println("  isActive: ${job.isActive}")
            println("  isCompleted: ${job.isCompleted}")
            println("  isCancelled: ${job.isCancelled}")
            
            if (check == 2) {
                println("  Cancelling job...")
                job.cancel()
            }
            
            delay(250)
        }
        
        job.join()
        println("Final state:")
        println("  isActive: ${job.isActive}")
        println("  isCompleted: ${job.isCompleted}")
        println("  isCancelled: ${job.isCancelled}")
        println()
    }
}

/**
 * Cooperative cancellation patterns
 */
class CooperativeCancellation {
    
    fun demonstrateCooperativeCancellation() = runBlocking {
        println("=== Cooperative Cancellation ===")
        
        // ✅ Good: Cooperative cancellation with ensureActive()
        val cooperativeJob = launch {
            try {
                repeat(1000) { i ->
                    ensureActive() // Check for cancellation
                    
                    // Simulate work
                    var sum = 0
                    repeat(100000) { sum += it }
                    
                    if (i % 100 == 0) {
                        println("   Cooperative: Processed $i iterations")
                    }
                }
                println("   Cooperative: All work completed")
            } catch (e: CancellationException) {
                println("   Cooperative: Cancelled at iteration")
                throw e
            }
        }
        
        delay(200) // Let it run briefly
        cooperativeJob.cancel()
        cooperativeJob.join()
        
        // ❌ Bad: Non-cooperative cancellation
        val nonCooperativeJob = launch {
            repeat(1000) { i ->
                // No cancellation checks!
                var sum = 0
                repeat(100000) { sum += it }
                
                if (i % 100 == 0) {
                    println("   Non-cooperative: Processed $i iterations")
                }
            }
            println("   Non-cooperative: All work completed")
        }
        
        delay(200) // Let it run briefly
        nonCooperativeJob.cancel()
        
        // This will wait much longer because the job isn't checking for cancellation
        val startTime = System.currentTimeMillis()
        nonCooperativeJob.join()
        val duration = System.currentTimeMillis() - startTime
        println("   Non-cooperative job took ${duration}ms to cancel")
        
        println()
    }
    
    fun demonstrateCancellationInSuspendFunctions() = runBlocking {
        println("=== Cancellation in Suspend Functions ===")
        
        suspend fun suspendingWork() {
            repeat(5) { i ->
                println("   Suspending work: $i")
                delay(200) // Automatically checks for cancellation
            }
        }
        
        suspend fun blockingWork() {
            repeat(5) { i ->
                println("   Blocking work: $i")
                Thread.sleep(200) // Does NOT check for cancellation
            }
        }
        
        // Suspend functions are automatically cancellable
        println("1. Cancelling suspending work:")
        val suspendJob = launch {
            try {
                suspendingWork()
            } catch (e: CancellationException) {
                println("   Suspending work was cancelled")
            }
        }
        
        delay(350) // Let it run briefly
        suspendJob.cancel()
        suspendJob.join()
        
        // Blocking work is harder to cancel
        println("\n2. Cancelling blocking work:")
        val blockingJob = launch {
            try {
                blockingWork()
            } catch (e: CancellationException) {
                println("   Blocking work was cancelled")
            }
        }
        
        delay(350) // Let it run briefly
        blockingJob.cancel()
        
        val startTime = System.currentTimeMillis()
        blockingJob.join()
        val duration = System.currentTimeMillis() - startTime
        println("   Blocking work took ${duration}ms to cancel")
        
        println()
    }
    
    fun demonstrateCancellationWithResources() = runBlocking {
        println("=== Cancellation with Resource Cleanup ===")
        
        class Resource(val name: String) {
            init {
                println("     Resource $name acquired")
            }
            
            fun close() {
                println("     Resource $name released")
            }
        }
        
        val job = launch {
            val resource1 = Resource("Database")
            val resource2 = Resource("Network")
            
            try {
                repeat(10) { i ->
                    ensureActive() // Check for cancellation
                    println("   Processing with resources: $i")
                    delay(200)
                }
                println("   Work completed successfully")
            } catch (e: CancellationException) {
                println("   Work was cancelled, cleaning up...")
                throw e // Re-throw to complete cancellation
            } finally {
                // Cleanup always happens
                resource1.close()
                resource2.close()
                println("   Resource cleanup completed")
            }
        }
        
        delay(600) // Let it run briefly
        println("Cancelling job with resources...")
        job.cancel()
        job.join()
        
        println()
    }
}

/**
 * Job completion and joining
 */
class JobCompletion {
    
    fun demonstrateJobJoining() = runBlocking {
        println("=== Job Joining Patterns ===")
        
        // Pattern 1: Join single job
        println("1. Single job join:")
        val job1 = launch {
            repeat(3) { i ->
                println("   Job1: $i")
                delay(200)
            }
        }
        
        job1.join() // Wait for completion
        println("   Job1 completed")
        
        // Pattern 2: Join multiple jobs
        println("\n2. Multiple job join:")
        val jobs = (1..3).map { id ->
            launch {
                repeat(2) { i ->
                    println("   Job$id: $i")
                    delay(150)
                }
            }
        }
        
        jobs.forEach { it.join() }
        println("   All jobs completed")
        
        // Pattern 3: Join with timeout
        println("\n3. Join with timeout:")
        val slowJob = launch {
            delay(2000)
            println("   Slow job completed")
        }
        
        val completed = withTimeoutOrNull(500) {
            slowJob.join()
            true
        }
        
        if (completed == null) {
            println("   Slow job timed out")
            slowJob.cancel()
        }
        
        println()
    }
    
    fun demonstrateJobCompletion() = runBlocking {
        println("=== Job Completion Callbacks ===")
        
        val job = Job()
        
        // Add completion handler
        job.invokeOnCompletion { exception ->
            if (exception == null) {
                println("   Job completed successfully")
            } else {
                println("   Job completed with exception: ${exception.message}")
            }
        }
        
        // Start some work
        val workJob = launch(job) {
            repeat(3) { i ->
                println("   Working: $i")
                delay(200)
            }
            
            if (Random.nextBoolean()) {
                throw RuntimeException("Random failure")
            }
        }
        
        try {
            workJob.join()
        } catch (e: Exception) {
            println("   Caught exception: ${e.message}")
        }
        
        println()
    }
    
    fun demonstrateChildJobCompletion() = runBlocking {
        println("=== Child Job Completion ===")
        
        val parentJob = launch {
            println("   Parent started")
            
            val child1 = launch {
                repeat(2) { i ->
                    println("     Child1: $i")
                    delay(300)
                }
            }
            
            val child2 = launch {
                repeat(3) { i ->
                    println("     Child2: $i")
                    delay(200)
                }
            }
            
            // Parent can wait for specific children
            child1.join()
            println("     Child1 finished")
            
            child2.join()
            println("     Child2 finished")
            
            println("   Parent work completed")
        }
        
        parentJob.join()
        println("   Parent job completed")
        
        println()
    }
}

/**
 * SupervisorJob for independent error handling
 */
class SupervisorJobDemo {
    
    fun demonstrateSupervisorJob() = runBlocking {
        println("=== SupervisorJob vs Regular Job ===")
        
        // Regular Job: child failure cancels siblings
        println("1. Regular Job behavior:")
        try {
            coroutineScope {
                launch {
                    delay(200)
                    println("   Child1: Working...")
                    delay(300)
                    println("   Child1: Completed")
                }
                
                launch {
                    delay(100)
                    println("   Child2: About to fail...")
                    throw RuntimeException("Child2 failed")
                }
                
                launch {
                    try {
                        repeat(5) { i ->
                            println("   Child3: Step $i")
                            delay(150)
                        }
                    } catch (e: CancellationException) {
                        println("   Child3: Cancelled due to sibling failure")
                    }
                }
            }
        } catch (e: Exception) {
            println("   Regular scope exception: ${e.message}")
        }
        
        // SupervisorJob: child failures are independent
        println("\n2. SupervisorJob behavior:")
        supervisorScope {
            launch {
                delay(200)
                println("   Child1: Working...")
                delay(300)
                println("   Child1: Completed")
            }
            
            launch {
                delay(100)
                println("   Child2: About to fail...")
                throw RuntimeException("Child2 failed")
            }
            
            launch {
                repeat(5) { i ->
                    println("   Child3: Step $i")
                    delay(150)
                }
                println("   Child3: Completed independently")
            }
            
            delay(800) // Wait for children
        }
        
        println()
    }
    
    fun demonstrateCustomSupervisorJob() = runBlocking {
        println("=== Custom SupervisorJob ===")
        
        val supervisorJob = SupervisorJob()
        val scope = CoroutineScope(supervisorJob + Dispatchers.Default)
        
        // Add completion handler to supervisor
        supervisorJob.invokeOnCompletion { exception ->
            println("   Supervisor completed: ${exception?.message ?: "success"}")
        }
        
        try {
            // Start multiple independent tasks
            val task1 = scope.launch {
                repeat(3) { i ->
                    println("   Task1: $i")
                    delay(200)
                }
                println("   Task1: Completed")
            }
            
            val task2 = scope.launch {
                delay(300)
                throw RuntimeException("Task2 failed")
            }
            
            val task3 = scope.launch {
                repeat(4) { i ->
                    println("   Task3: $i")
                    delay(180)
                }
                println("   Task3: Completed")
            }
            
            // Wait for all tasks (some may fail)
            try {
                task1.join()
            } catch (e: Exception) {
                println("   Task1 failed: ${e.message}")
            }
            
            try {
                task2.join()
            } catch (e: Exception) {
                println("   Task2 failed: ${e.message}")
            }
            
            try {
                task3.join()
            } catch (e: Exception) {
                println("   Task3 failed: ${e.message}")
            }
            
        } finally {
            // Clean up supervisor scope
            supervisorJob.cancel()
        }
        
        println()
    }
}

/**
 * Advanced cancellation patterns
 */
class AdvancedCancellation {
    
    fun demonstrateCancellationWithTimeout() = runBlocking {
        println("=== Cancellation with Timeout ===")
        
        suspend fun longRunningTask(): String {
            repeat(10) { i ->
                println("   Long task: step $i")
                delay(300)
            }
            return "Task completed"
        }
        
        // Timeout cancellation
        val result = withTimeoutOrNull(1000) {
            longRunningTask()
        }
        
        if (result != null) {
            println("   Result: $result")
        } else {
            println("   Task timed out and was cancelled")
        }
        
        // Manual timeout with cancellation
        val job = launch {
            try {
                longRunningTask()
            } catch (e: CancellationException) {
                println("   Task was cancelled manually")
            }
        }
        
        delay(800)
        job.cancel("Manual timeout")
        job.join()
        
        println()
    }
    
    fun demonstrateCancellationReasons() = runBlocking {
        println("=== Cancellation Reasons ===")
        
        val job = launch {
            try {
                repeat(100) { i ->
                    ensureActive()
                    println("   Working: $i")
                    delay(100)
                }
            } catch (e: CancellationException) {
                println("   Cancelled: ${e.message}")
                throw e
            }
        }
        
        delay(350)
        job.cancel("User requested cancellation")
        job.join()
        
        // Check cancellation cause
        val cancellationException = job.getCancellationException()
        println("   Cancellation cause: ${cancellationException.message}")
        
        println()
    }
    
    fun demonstrateNonCancellableCleanup() = runBlocking {
        println("=== Non-Cancellable Cleanup ===")
        
        val job = launch {
            try {
                repeat(10) { i ->
                    println("   Working: $i")
                    delay(200)
                }
            } catch (e: CancellationException) {
                println("   Job cancelled, performing cleanup...")
                
                // Critical cleanup that must complete
                withContext(NonCancellable) {
                    println("   Saving critical data...")
                    delay(300) // This won't be cancelled
                    println("   Critical data saved")
                    
                    println("   Notifying external systems...")
                    delay(200) // This won't be cancelled
                    println("   External systems notified")
                }
                
                println("   Cleanup completed")
                throw e
            }
        }
        
        delay(600)
        job.cancel()
        job.join()
        
        println()
    }
}

/**
 * Job hierarchy and parent-child relationships
 */
class JobHierarchy {
    
    fun demonstrateJobHierarchy() = runBlocking {
        println("=== Job Hierarchy ===")
        
        val parentJob = Job()
        println("1. Parent job created: ${parentJob}")
        
        val childJob1 = Job(parent = parentJob)
        val childJob2 = Job(parent = parentJob)
        
        println("2. Child jobs created:")
        println("   Child1: ${childJob1}")
        println("   Child2: ${childJob2}")
        
        // Start work with child jobs
        val work1 = launch(childJob1) {
            try {
                repeat(5) { i ->
                    println("   Work1: $i")
                    delay(200)
                }
            } catch (e: CancellationException) {
                println("   Work1 cancelled")
            }
        }
        
        val work2 = launch(childJob2) {
            try {
                repeat(5) { i ->
                    println("   Work2: $i")
                    delay(250)
                }
            } catch (e: CancellationException) {
                println("   Work2 cancelled")
            }
        }
        
        delay(600)
        
        // Cancel parent - should cancel all children
        println("3. Cancelling parent job...")
        parentJob.cancel()
        
        work1.join()
        work2.join()
        
        println("4. Job states after cancellation:")
        println("   Parent: ${parentJob}")
        println("   Child1: ${childJob1}")
        println("   Child2: ${childJob2}")
        
        println()
    }
    
    fun demonstrateJobInheritance() = runBlocking {
        println("=== Job Inheritance ===")
        
        launch {
            println("   Parent coroutine started")
            val parentJob = coroutineContext[Job]
            println("   Parent job: ${parentJob}")
            
            launch {
                println("     Child coroutine started")
                val childJob = coroutineContext[Job]
                println("     Child job: ${childJob}")
                println("     Child parent: ${childJob?.parent}")
                
                delay(500)
                println("     Child completed")
            }
            
            delay(300)
            println("   Parent completed")
        }.join()
        
        println()
    }
}

/**
 * Main demonstration function
 */
fun main() {
    // Job lifecycle basics
    JobLifecycleDemo().demonstrateJobStates()
    JobLifecycleDemo().demonstrateJobProperties()
    
    // Cooperative cancellation
    CooperativeCancellation().demonstrateCooperativeCancellation()
    CooperativeCancellation().demonstrateCancellationInSuspendFunctions()
    CooperativeCancellation().demonstrateCancellationWithResources()
    
    // Job completion
    JobCompletion().demonstrateJobJoining()
    JobCompletion().demonstrateJobCompletion()
    JobCompletion().demonstrateChildJobCompletion()
    
    // SupervisorJob
    SupervisorJobDemo().demonstrateSupervisorJob()
    SupervisorJobDemo().demonstrateCustomSupervisorJob()
    
    // Advanced cancellation
    AdvancedCancellation().demonstrateCancellationWithTimeout()
    AdvancedCancellation().demonstrateCancellationReasons()
    AdvancedCancellation().demonstrateNonCancellableCleanup()
    
    // Job hierarchy
    JobHierarchy().demonstrateJobHierarchy()
    JobHierarchy().demonstrateJobInheritance()
    
    println("=== Job Lifecycle Summary ===")
    println("- Jobs go through states: New → Active → Completing → Completed")
    println("- Cancellation is cooperative - check with ensureActive()")
    println("- Use join() to wait for job completion")
    println("- SupervisorJob allows independent child failure handling")
    println("- Always handle CancellationException properly")
    println("- Use NonCancellable context for critical cleanup")
    println("- Parent jobs manage child lifecycles automatically")
    println("- Proper cancellation prevents resource leaks")
}